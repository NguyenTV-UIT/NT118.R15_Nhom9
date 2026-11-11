/**
 * CHAT: danh sách cuộc trò chuyện, tin nhắn, đã đọc, gửi ảnh và ĐỘ THÂN THIẾT.
 *
 * Độ thân thiết của 1 cặp = min(số tin mình đã gửi, số tin người kia đã gửi), tối đa 20 (giống ChatDetailActivity):
 *   - đủ 10: mở khoá GỬI ẢNH
 *   - đủ 20: mở khoá GỌI THOẠI / VIDEO và được tính là "thân thiết"
 * Chỉ tin nhắn chữ và ảnh được tính (nhật ký cuộc gọi thì không).
 *
 * Chỉ 2 người đang tương hợp (chưa huỷ, không chặn nhau) mới nhắn được cho nhau.
 */
const fs = require('fs/promises');
const path = require('path');

const env = require('../../config/env');
const db = require('../../config/db');
const ApiError = require('../../utils/ApiError');
const hub = require('../../realtime/hub');
const push = require('../../services/push');
const usersService = require('../users/users.service');
const { UPLOAD_DIR } = require('../../middlewares/upload');

const CHAT_DIR = path.join(UPLOAD_DIR, 'chat');
const MAX = () => env.chat.intimacyCall; // thang đo tối đa (20)

// ═════════════════════ ĐỘ THÂN THIẾT ═════════════════════

/** row: dòng matches (có user1_id, user1_msg_count, user2_msg_count). */
function intimacyOf(row, userId) {
  const max = MAX();
  const isUser1 = Number(row.user1_id) === Number(userId);
  const c1 = Number(row.user1_msg_count);
  const c2 = Number(row.user2_msg_count);
  const mine = Math.min(max, isUser1 ? c1 : c2);
  const partner = Math.min(max, isUser1 ? c2 : c1);
  const score = Math.min(mine, partner);
  return {
    score, // min(mine, partner) - hiển thị "score / 20 tin"
    max,
    mine, // "Bạn 14/20"
    partner, // "Grace 12/20"
    photoAt: env.chat.intimacyPhoto,
    callAt: env.chat.intimacyCall,
    photoUnlocked: score >= env.chat.intimacyPhoto,
    callUnlocked: score >= env.chat.intimacyCall,
    isCloseFriend: score >= env.chat.intimacyCall,
  };
}

// ═════════════════════ TRUY CẬP CUỘC TRÒ CHUYỆN ═════════════════════

/**
 * Lấy match mà userId là thành viên và còn hiệu lực. Không thấy / đã huỷ / bị chặn / người kia bị khoá -> 404.
 * Trả về dòng matches + partnerId.
 */
async function loadMatch(userId, matchId, run = db.query) {
  const [row] = await run(
    `SELECT m.id, m.user1_id, m.user2_id, m.user1_msg_count, m.user2_msg_count, m.created_at
       FROM matches m
       JOIN users u ON u.id = IF(m.user1_id = ?, m.user2_id, m.user1_id)
      WHERE m.id = ? AND (m.user1_id = ? OR m.user2_id = ?)
        AND m.unmatched_at IS NULL AND u.status = 'active'`,
    [userId, matchId, userId, userId],
  );
  if (!row) throw new ApiError(404, 'Không tìm thấy cuộc trò chuyện', 'CHAT_NOT_FOUND');
  const partnerId = Number(Number(row.user1_id) === Number(userId) ? row.user2_id : row.user1_id);
  if (await usersService.isBlockedBetween(userId, partnerId)) {
    throw new ApiError(404, 'Không tìm thấy cuộc trò chuyện', 'CHAT_NOT_FOUND');
  }
  return { ...row, id: Number(row.id), partnerId };
}

// ═════════════════════ ĐỊNH DẠNG TIN NHẮN ═════════════════════

function parseCall(content) {
  try {
    return JSON.parse(content);
  } catch {
    return null;
  }
}

const CALL_PREVIEW = {
  ended: (c) => (c.type === 'video' ? 'Cuộc gọi video' : 'Cuộc gọi thoại'),
  missed: () => 'Cuộc gọi nhỡ',
  cancelled: () => 'Cuộc gọi nhỡ',
  rejected: () => 'Cuộc gọi bị từ chối',
};

/** Dòng xem trước hiển thị ở danh sách chat. */
function previewOf(type, content) {
  if (type === 'image') return '📷 Hình ảnh';
  if (type === 'call') {
    const c = parseCall(content) || {};
    return (CALL_PREVIEW[c.status] || CALL_PREVIEW.ended)(c);
  }
  return content;
}

/**
 * Tin nhắn trả cho app.
 *   type = text  : content = nội dung
 *   type = image : content = imageUrl = URL ảnh đầy đủ
 *   type = call  : call = { callId, type: voice|video, status: ended|missed|cancelled|rejected, durationSeconds }
 */
function toMessage(row, viewerId) {
  const type = row.type;
  const msg = {
    id: Number(row.id),
    matchId: Number(row.match_id),
    senderId: Number(row.sender_id),
    fromMe: Number(row.sender_id) === Number(viewerId),
    type,
    content: type === 'image' ? usersService.toPhotoUrl(row.content) : type === 'call' ? null : row.content,
    preview: previewOf(type, row.content),
    clientId: row.client_id ?? null,
    isRead: Boolean(row.read_at),
    readAt: row.read_at ?? null,
    createdAt: row.created_at,
  };
  if (type === 'image') msg.imageUrl = msg.content;
  if (type === 'call') msg.call = parseCall(row.content);
  return msg;
}

async function getMessageRow(id, run = db.query) {
  const [row] = await run('SELECT id, match_id, sender_id, type, content, client_id, read_at, created_at FROM messages WHERE id = ?', [id]);
  return row;
}

// ═════════════════════ DANH SÁCH CUỘC TRÒ CHUYỆN ═════════════════════

/**
 * Mọi tương hợp còn hiệu lực của userId kèm người kia, tin nhắn cuối, số tin chưa đọc, độ thân thiết.
 * Dùng chung cho GET /chats, GET /chats/intimacy và GET /home/matches.
 */
async function listConversations(userId) {
  const rows = await db.query(
    `SELECT m.id AS match_id, m.created_at AS matched_at, m.user1_id, m.user1_msg_count, m.user2_msg_count,
            u.id, ${usersService.PUBLIC_PROFILE_COLUMNS},
            NULL AS distance_km,
            lm.id AS lm_id, lm.content AS lm_content, lm.type AS lm_type, lm.sender_id AS lm_sender_id,
            lm.created_at AS lm_created_at, lm.read_at AS lm_read_at,
            (SELECT COUNT(*) FROM messages x WHERE x.match_id = m.id AND x.sender_id <> ? AND x.read_at IS NULL) AS unread_count
       FROM matches m
       JOIN users u    ON u.id = IF(m.user1_id = ?, m.user2_id, m.user1_id)
       JOIN profiles p ON p.user_id = u.id
       LEFT JOIN messages lm ON lm.id = (SELECT MAX(id) FROM messages WHERE match_id = m.id)
      WHERE (m.user1_id = ? OR m.user2_id = ?)
        AND m.unmatched_at IS NULL
        AND u.status = 'active'
        AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id = ? AND b.blocked_id = u.id)
                                                  OR (b.blocker_id = u.id AND b.blocked_id = ?))
      ORDER BY COALESCE(lm.created_at, m.created_at) DESC, m.id DESC`,
    [userId, userId, userId, userId, userId, userId],
  );
  const extras = await usersService.loadExtras(rows.map((r) => Number(r.id)));
  return rows.map((r) => ({
    matchId: Number(r.match_id),
    matchedAt: r.matched_at,
    user: usersService.toPublicProfile(r, extras),
    lastMessage: r.lm_id
      ? {
          id: Number(r.lm_id),
          type: r.lm_type,
          content: r.lm_type === 'image' ? usersService.toPhotoUrl(r.lm_content) : r.lm_type === 'call' ? null : r.lm_content,
          preview: previewOf(r.lm_type, r.lm_content),
          fromMe: Number(r.lm_sender_id) === Number(userId),
          isRead: Boolean(r.lm_read_at),
          createdAt: r.lm_created_at,
        }
      : null,
    lastActivityAt: r.lm_created_at || r.matched_at,
    unreadCount: Number(r.unread_count),
    intimacy: intimacyOf(r, userId),
  }));
}

const fold = (s) =>
  String(s || '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase();

/**
 * GET /chats - màn Tin nhắn (ChatListActivity) + tìm kiếm (ChatSearchActivity).
 *   filter: all | close (thân thiết) | unread | new (tương hợp chưa nhắn tin nào)
 *   q: tìm theo tên (không phân biệt dấu) hoặc nội dung tin nhắn cuối
 */
async function listChats(userId, { q, filter = 'all', limit = 100 }) {
  const all = await listConversations(userId);
  const meta = {
    total: all.length,
    closeFriendsCount: all.filter((c) => c.intimacy.isCloseFriend).length, // "♥ 2 thân thiết"
    unreadChats: all.filter((c) => c.unreadCount > 0).length,
    unreadMessages: all.reduce((n, c) => n + c.unreadCount, 0), // số trên icon tab Tin nhắn
  };
  let items = all;
  if (filter === 'close') items = items.filter((c) => c.intimacy.isCloseFriend);
  else if (filter === 'unread') items = items.filter((c) => c.unreadCount > 0);
  else if (filter === 'new') items = items.filter((c) => !c.lastMessage);
  if (q) {
    const needle = fold(q);
    items = items.filter(
      (c) => fold(c.user.displayName).includes(needle) || (c.lastMessage?.type === 'text' && fold(c.lastMessage.content).includes(needle)),
    );
  }
  return { items: items.slice(0, limit), meta };
}

/**
 * GET /chats/intimacy - màn Thân thiết (ChatIntimacyActivity):
 *   closeFriends: đã đủ 20/20;  inProgress: đang tiến tới (kèm câu gợi ý còn thiếu bao nhiêu tin).
 */
async function getIntimacyOverview(userId) {
  const all = await listConversations(userId);
  const item = (c) => ({ matchId: c.matchId, user: c.user, intimacy: c.intimacy, hint: intimacyHint(c.intimacy, c.user.displayName) });
  const closeFriends = all.filter((c) => c.intimacy.isCloseFriend).map(item);
  const inProgress = all
    .filter((c) => !c.intimacy.isCloseFriend && (c.intimacy.mine > 0 || c.intimacy.partner > 0))
    .sort((a, b) => b.intimacy.score - a.intimacy.score || b.intimacy.mine + b.intimacy.partner - (a.intimacy.mine + a.intimacy.partner))
    .map(item);
  return { closeFriendsCount: closeFriends.length, closeFriends, inProgress };
}

/** VD "Grace cần thêm 8 tin để gọi & video" / "Bạn cần thêm 3 tin để gửi ảnh". */
function intimacyHint(i, partnerName) {
  if (i.callUnlocked) return 'Đã mở khóa mọi tính năng';
  const target = i.photoUnlocked ? i.callAt : i.photoAt;
  const feature = i.photoUnlocked ? 'gọi & video' : 'gửi ảnh';
  const parts = [];
  if (i.partner < target) parts.push(`${partnerName} cần thêm ${target - i.partner} tin`);
  if (i.mine < target) parts.push(`Bạn cần thêm ${target - i.mine} tin`);
  return `${parts.join(', ')} để ${feature}`;
}

/** GET /chats/:matchId - đầu màn ChatDetail: người kia + độ thân thiết + số tin chưa đọc. */
async function getChat(userId, matchId) {
  const match = await loadMatch(userId, matchId);
  const [{ unread }] = await db.query(
    'SELECT COUNT(*) AS unread FROM messages WHERE match_id = ? AND sender_id <> ? AND read_at IS NULL',
    [match.id, userId],
  );
  const user = await usersService.getPublicProfile(userId, match.partnerId);
  const intimacy = intimacyOf(match, userId);
  return {
    matchId: match.id,
    matchedAt: match.created_at,
    user,
    unreadCount: Number(unread),
    intimacy,
    hint: intimacyHint(intimacy, user.displayName),
    limits: { maxMessageLength: env.chat.maxMessageLength },
  };
}

// ═════════════════════ TIN NHẮN ═════════════════════

/**
 * GET /chats/:matchId/messages?before=<id>&limit=30
 * Trả 1 trang tin nhắn theo thứ tự CŨ -> MỚI (để hiển thị từ trên xuống). Kéo lên xem tiếp: before = meta.nextBefore.
 * Lấy tin mới sau khi mất kết nối: after=<id tin cuối đang có>.
 */
async function getMessages(userId, matchId, { before, after, limit }) {
  const match = await loadMatch(userId, matchId);
  const where = ['match_id = ?'];
  const params = [match.id];
  if (before) {
    where.push('id < ?');
    params.push(before);
  }
  if (after) {
    where.push('id > ?');
    params.push(after);
  }
  const rows = await db.query(
    `SELECT id, match_id, sender_id, type, content, client_id, read_at, created_at
       FROM messages WHERE ${where.join(' AND ')}
      ORDER BY id ${after ? 'ASC' : 'DESC'} LIMIT ?`,
    [...params, limit + 1],
  );
  const hasMore = rows.length > limit;
  const page = rows.slice(0, limit);
  if (!after) page.reverse();
  return {
    items: page.map((r) => toMessage(r, userId)),
    meta: {
      hasMore,
      nextBefore: !after && hasMore && page.length ? Number(page[0].id) : null,
      nextAfter: after && hasMore && page.length ? Number(page[page.length - 1].id) : null,
      intimacy: intimacyOf(match, userId),
    },
  };
}

/** Mốc vừa đạt được nhờ tin nhắn này: 'photo' (đủ 10) | 'call' (đủ 20) | null -> app hiện dialog chúc mừng. */
function unlockedBetween(before, after) {
  if (!before.callUnlocked && after.callUnlocked) return 'call';
  if (!before.photoUnlocked && after.photoUnlocked) return 'photo';
  return null;
}

/**
 * Lưu 1 tin nhắn (text | image) + cộng bộ đếm + báo realtime cho cả 2 phía.
 * clientId: id do app tự sinh; gửi lại cùng clientId (mạng chập chờn) không tạo tin trùng.
 */
async function createMessage(userId, match, input) {
  try {
    return await insertMessage(userId, match, input);
  } catch (err) {
    // 2 request cùng clientId tới gần như đồng thời: request sau trả lại tin đã lưu
    if (err.code === 'ER_DUP_ENTRY' && input.clientId) return insertMessage(userId, match, input);
    throw err;
  }
}

async function insertMessage(userId, match, { type, content, clientId }) {
  if (clientId) {
    const [dup] = await db.query(
      'SELECT id, match_id, sender_id, type, content, client_id, read_at, created_at FROM messages WHERE match_id = ? AND sender_id = ? AND client_id = ?',
      [match.id, userId, clientId],
    );
    if (dup) return { message: toMessage(dup, userId), intimacy: intimacyOf(match, userId), unlocked: null, duplicate: true };
  }

  const counter = Number(match.user1_id) === Number(userId) ? 'user1_msg_count' : 'user2_msg_count';
  const { row, before, after } = await db.transaction(async (conn) => {
    const run = async (sql, params) => (await conn.query(sql, params))[0];
    // Khoá dòng match để 2 tin gửi cùng lúc không làm sai bộ đếm / mốc mở khoá
    const [locked] = await run(
      'SELECT id, user1_id, user2_id, user1_msg_count, user2_msg_count, unmatched_at FROM matches WHERE id = ? FOR UPDATE',
      [match.id],
    );
    if (!locked || locked.unmatched_at) throw new ApiError(404, 'Không tìm thấy cuộc trò chuyện', 'CHAT_NOT_FOUND');
    const ins = await run('INSERT INTO messages (match_id, sender_id, type, content, client_id) VALUES (?, ?, ?, ?, ?)', [
      match.id,
      userId,
      type,
      content,
      clientId || null,
    ]);
    await run(`UPDATE matches SET ${counter} = ${counter} + 1 WHERE id = ?`, [match.id]);
    const updated = { ...locked, [counter]: Number(locked[counter]) + 1 };
    return { row: await getMessageRow(ins.insertId, run), before: intimacyOf(locked, userId), after: updated };
  });

  const unlocked = unlockedBetween(before, intimacyOf(after, userId));
  const partnerId = match.partnerId;
  const mine = { message: toMessage(row, userId), intimacy: intimacyOf(after, userId), unlocked };
  const theirs = { message: toMessage(row, partnerId), intimacy: intimacyOf(after, partnerId), unlocked };

  // Người nhận đang mở app -> WebSocket; không thì gửi thông báo đẩy
  const delivered = hub.sendToUser(partnerId, 'message.new', theirs);
  hub.sendToUser(userId, 'message.new', mine); // đồng bộ các thiết bị khác của người gửi
  if (!delivered) notifyOffline(userId, partnerId, match.id, mine.message).catch(() => {});
  return mine;
}

async function notifyOffline(senderId, receiverId, matchId, message) {
  const [sender] = await db.query('SELECT display_name FROM profiles WHERE user_id = ?', [senderId]);
  await push.notify(receiverId, {
    type: 'new_message',
    title: sender?.display_name || 'Tin nhắn mới',
    body: message.preview,
    data: { matchId, messageId: message.id, senderId: Number(senderId) },
  });
}

/** POST /chats/:matchId/messages  { content, clientId? } */
async function sendText(userId, matchId, { content, clientId }) {
  const match = await loadMatch(userId, matchId);
  return createMessage(userId, match, { type: 'text', content, clientId });
}

/** Chạy TRƯỚC khi nhận file: chưa đủ độ thân thiết thì từ chối luôn, khỏi tốn công upload. */
async function assertPhotoUnlocked(userId, matchId) {
  const match = await loadMatch(userId, matchId);
  const i = intimacyOf(match, userId);
  if (!i.photoUnlocked) {
    throw new ApiError(403, `Cần đủ ${i.photoAt} tin nhắn mỗi bên để gửi ảnh (hiện ${i.score}/${i.photoAt})`, 'PHOTO_LOCKED', [
      { field: 'intimacy', message: `Bạn ${i.mine}/${i.photoAt}, đối phương ${i.partner}/${i.photoAt}` },
    ]);
  }
  return match;
}

/** POST /chats/:matchId/photos (multipart, field "photo") - gửi ảnh trong chat (mở khoá khi độ thân thiết >= 10). */
async function sendPhoto(userId, matchId, file, { clientId } = {}) {
  let match;
  try {
    match = await assertPhotoUnlocked(userId, matchId);
  } catch (err) {
    await fs.unlink(file.path).catch(() => {});
    throw err;
  }
  // Ảnh chat để riêng thư mục uploads/chat cho dễ dọn
  await fs.mkdir(CHAT_DIR, { recursive: true });
  await fs.rename(file.path, path.join(CHAT_DIR, file.filename));
  return createMessage(userId, match, { type: 'image', content: `/uploads/chat/${file.filename}`, clientId });
}

/**
 * POST /chats/:matchId/read - đánh dấu đã đọc mọi tin của người kia (gọi khi mở / đang xem màn chat).
 * Người kia nhận sự kiện message.read để đổi tick "đã xem".
 */
async function markRead(userId, matchId) {
  const match = await loadMatch(userId, matchId);
  const [last] = await db.query(
    'SELECT MAX(id) AS id FROM messages WHERE match_id = ? AND sender_id <> ? AND read_at IS NULL',
    [match.id, userId],
  );
  if (!last?.id) return { matchId: match.id, readCount: 0, lastReadMessageId: null };
  const result = await db.query(
    'UPDATE messages SET read_at = UTC_TIMESTAMP() WHERE match_id = ? AND sender_id <> ? AND read_at IS NULL AND id <= ?',
    [match.id, userId, last.id],
  );
  const payload = { matchId: match.id, readerId: Number(userId), lastReadMessageId: Number(last.id), readAt: new Date().toISOString() };
  hub.sendToUser(match.partnerId, 'message.read', payload);
  hub.sendToUser(userId, 'message.read', payload);
  return { matchId: match.id, readCount: Number(result.affectedRows), lastReadMessageId: Number(last.id) };
}

/**
 * Ghi nhật ký cuộc gọi vào cuộc trò chuyện (tin nhắn type = call, người gửi = người gọi). Không tính độ thân thiết.
 * Dùng bởi calls.service.
 */
async function addCallLog(call, summary) {
  const ins = await db.query('INSERT INTO messages (match_id, sender_id, type, content) VALUES (?, ?, ?, ?)', [
    call.match_id,
    call.caller_id,
    'call',
    JSON.stringify(summary),
  ]);
  const row = await getMessageRow(ins.insertId);
  for (const uid of [call.caller_id, call.callee_id]) {
    hub.sendToUser(uid, 'message.new', { message: toMessage(row, uid), intimacy: null, unlocked: null });
  }
  return row;
}

// ═════════════════════ REALTIME: đang nhập, online/offline ═════════════════════

/** App gửi {event:"typing", data:{matchId, isTyping}} -> chuyển cho người kia. */
hub.on('typing', async (user, data) => {
  const matchId = Number(data.matchId);
  if (!Number.isInteger(matchId) || matchId <= 0) throw ApiError.badRequest('Thiếu matchId');
  const match = await loadMatch(user.id, matchId);
  hub.sendToUser(match.partnerId, 'typing', { matchId: match.id, userId: user.id, isTyping: data.isTyping !== false });
});

/** Báo cho những người đang tương hợp biết mình vừa online / offline (nếu mình cho hiện trạng thái hoạt động). */
async function broadcastPresence(userId, online) {
  const [me] = await db.query('SELECT show_active_status FROM profiles WHERE user_id = ?', [userId]);
  if (!me || !Number(me.show_active_status)) return;
  const partners = await db.query(
    `SELECT IF(user1_id = ?, user2_id, user1_id) AS partner_id FROM matches
      WHERE (user1_id = ? OR user2_id = ?) AND unmatched_at IS NULL`,
    [userId, userId, userId],
  );
  const payload = { userId: Number(userId), isOnline: online, lastActiveAt: new Date().toISOString() };
  for (const p of partners) hub.sendToUser(p.partner_id, 'presence', payload);
}
hub.events.on('online', (id) => broadcastPresence(id, true).catch((err) => console.error('[presence]', err.message)));
hub.events.on('offline', (id) => broadcastPresence(id, false).catch((err) => console.error('[presence]', err.message)));

module.exports = {
  intimacyOf,
  intimacyHint,
  loadMatch,
  toMessage,
  listConversations,
  listChats,
  getIntimacyOverview,
  getChat,
  getMessages,
  sendText,
  assertPhotoUnlocked,
  sendPhoto,
  markRead,
  addCallLog,
};
