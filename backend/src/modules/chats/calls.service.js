/**
 * CUỘC GỌI THOẠI / VIDEO (mở khoá khi độ thân thiết đủ 20).
 *
 * Server chỉ làm "tổng đài" (signaling): tạo cuộc gọi, đổ chuông, nhận / từ chối / huỷ / kết thúc và chuyển tiếp
 * gói WebRTC (offer / answer / ICE candidate) giữa 2 máy qua WebSocket. Âm thanh - hình ảnh đi trực tiếp giữa 2 máy
 * (WebRTC P2P), không qua server.
 *
 * Vòng đời:  ringing ─ accept ─> accepted ─ end ─> ended
 *               ├─ reject (người nhận) ─> rejected
 *               ├─ cancel (người gọi)  ─> cancelled
 *               └─ hết CALL_RING_SECONDS không ai nhấc ─> missed
 * Kết thúc kiểu nào cũng ghi 1 dòng nhật ký vào cuộc trò chuyện (tin nhắn type = call).
 */
const env = require('../../config/env');
const db = require('../../config/db');
const ApiError = require('../../utils/ApiError');
const hub = require('../../realtime/hub');
const push = require('../../services/push');
const chats = require('./chats.service');

const ACTIVE = ['ringing', 'accepted'];
const OFFLINE_GRACE_MS = 20000; // mất kết nối quá lâu khi đang gọi -> tự kết thúc
const ringTimers = new Map(); // callId -> timer

const conflict = (message, code) => new ApiError(409, message, code);

function toCall(row, viewerId) {
  const answeredAt = row.answered_at ?? null;
  const endedAt = row.ended_at ?? null;
  return {
    id: Number(row.id),
    matchId: Number(row.match_id),
    callerId: Number(row.caller_id),
    calleeId: Number(row.callee_id),
    type: row.type, // voice | video
    status: row.status,
    isCaller: viewerId == null ? undefined : Number(row.caller_id) === Number(viewerId),
    createdAt: row.created_at,
    answeredAt,
    endedAt,
    durationSeconds: answeredAt && endedAt ? Math.max(0, Math.round((new Date(endedAt) - new Date(answeredAt)) / 1000)) : 0,
    ringSeconds: env.chat.callRingSeconds,
  };
}

async function getRow(callId) {
  const [row] = await db.query('SELECT * FROM calls WHERE id = ?', [callId]);
  return row;
}

/** Cuộc gọi mà userId là 1 trong 2 bên, không thì 404. */
async function loadCall(userId, callId) {
  const row = await getRow(callId);
  if (!row || (Number(row.caller_id) !== Number(userId) && Number(row.callee_id) !== Number(userId))) {
    throw new ApiError(404, 'Không tìm thấy cuộc gọi', 'CALL_NOT_FOUND');
  }
  return row;
}

const otherParty = (row, userId) => Number(Number(row.caller_id) === Number(userId) ? row.callee_id : row.caller_id);

function emit(row, event, extra = {}) {
  for (const uid of [row.caller_id, row.callee_id]) hub.sendToUser(uid, event, { call: toCall(row, uid), ...extra });
}

/**
 * Chuyển trạng thái an toàn khi 2 phía bấm cùng lúc: chỉ 1 request thắng (UPDATE ... WHERE status = <cũ>).
 * Trả về dòng mới, hoặc null nếu trạng thái đã bị đổi trước đó.
 */
async function transition(callId, from, to, { answered = false } = {}) {
  const sets = ['status = ?'];
  if (answered) sets.push('answered_at = UTC_TIMESTAMP()');
  else sets.push('ended_at = UTC_TIMESTAMP()');
  const result = await db.query(`UPDATE calls SET ${sets.join(', ')} WHERE id = ? AND status = ?`, [to, callId, from]);
  if (!result.affectedRows) return null;
  if (to !== 'accepted') clearRing(callId);
  return getRow(callId);
}

function clearRing(callId) {
  clearTimeout(ringTimers.get(Number(callId)));
  ringTimers.delete(Number(callId));
}

/** Ghi nhật ký + báo realtime khi cuộc gọi kết thúc (theo bất kỳ cách nào). */
async function finish(row, event) {
  const call = toCall(row);
  await chats.addCallLog(row, { callId: call.id, type: call.type, status: call.status, durationSeconds: call.durationSeconds });
  emit(row, event);
  if (call.status === 'missed' || call.status === 'cancelled') {
    // Người nhận không nghe máy: lưu thông báo + đẩy "Cuộc gọi nhỡ"
    const [caller] = await db.query('SELECT display_name FROM profiles WHERE user_id = ?', [row.caller_id]);
    await db.query('INSERT INTO notifications (user_id, type, payload) VALUES (?, ?, ?)', [
      row.callee_id,
      'missed_call',
      JSON.stringify({ callId: call.id, matchId: call.matchId, fromUserId: call.callerId, type: call.type }),
    ]);
    push.notify(row.callee_id, {
      type: 'missed_call',
      title: caller?.display_name || 'Cuộc gọi nhỡ',
      body: call.type === 'video' ? 'Cuộc gọi video nhỡ' : 'Cuộc gọi thoại nhỡ',
      data: { callId: call.id, matchId: call.matchId },
    });
  }
  return row;
}

/** Hết thời gian đổ chuông mà chưa ai nhấc máy. */
async function expire(callId) {
  const row = await transition(callId, 'ringing', 'missed');
  if (row) await finish(row, 'call.missed');
}

/** Dọn cuộc gọi đổ chuông quá hạn (phòng khi server khởi động lại làm mất timer). */
async function expireStale(userIds) {
  const rows = await db.query(
    `SELECT id FROM calls WHERE status = 'ringing' AND (caller_id IN (?) OR callee_id IN (?))
        AND created_at < UTC_TIMESTAMP() - INTERVAL ${Number(env.chat.callRingSeconds) + 5} SECOND`,
    [userIds, userIds],
  );
  for (const r of rows) await expire(r.id);
}

/**
 * POST /chats/:matchId/calls  { type: "voice" | "video" } - bắt đầu gọi.
 * Người nhận: sự kiện call.incoming (WebSocket) hoặc thông báo đẩy nếu đang không mở app.
 */
async function start(userId, matchId, { type }) {
  const match = await chats.loadMatch(userId, matchId);
  const i = chats.intimacyOf(match, userId);
  if (!i.callUnlocked) {
    throw new ApiError(403, `Cần đủ ${i.callAt} tin nhắn mỗi bên để gọi thoại / video (hiện ${i.score}/${i.callAt})`, 'CALL_LOCKED', [
      { field: 'intimacy', message: `Bạn ${i.mine}/${i.callAt}, đối phương ${i.partner}/${i.callAt}` },
    ]);
  }
  const calleeId = match.partnerId;
  await expireStale([userId, calleeId]);

  const busy = await db.query(
    `SELECT id, caller_id, callee_id FROM calls
      WHERE status IN (?) AND (caller_id IN (?) OR callee_id IN (?))`,
    [ACTIVE, [userId, calleeId], [userId, calleeId]],
  );
  if (busy.some((c) => Number(c.caller_id) === Number(userId) || Number(c.callee_id) === Number(userId))) {
    throw conflict('Bạn đang trong một cuộc gọi khác', 'CALL_BUSY');
  }
  if (busy.length) throw conflict('Người này đang bận, hãy gọi lại sau', 'CALLEE_BUSY');

  const ins = await db.query('INSERT INTO calls (match_id, caller_id, callee_id, type, created_at) VALUES (?, ?, ?, ?, UTC_TIMESTAMP())', [
    match.id,
    userId,
    calleeId,
    type,
  ]);
  const row = await getRow(ins.insertId);
  const callId = Number(row.id);

  const [caller] = await db.query(
    `SELECT p.display_name, (SELECT ph.url FROM photos ph WHERE ph.user_id = p.user_id ORDER BY ph.position, ph.id LIMIT 1) AS avatar
       FROM profiles p WHERE p.user_id = ?`,
    [userId],
  );
  const from = {
    id: Number(userId),
    displayName: caller?.display_name || null,
    avatarUrl: caller?.avatar ? require('../users/users.service').toPhotoUrl(caller.avatar) : null,
  };
  const delivered = hub.sendToUser(calleeId, 'call.incoming', { call: toCall(row, calleeId), from, iceServers: env.iceServers });
  if (!delivered) {
    push.notify(calleeId, {
      type: 'incoming_call',
      title: from.displayName || 'Cuộc gọi đến',
      body: type === 'video' ? 'Đang gọi video cho bạn...' : 'Đang gọi cho bạn...',
      data: { callId, matchId: match.id, type },
    });
  }

  const timer = setTimeout(() => expire(callId).catch((err) => console.error('[call]', err.message)), env.chat.callRingSeconds * 1000);
  timer.unref();
  ringTimers.set(callId, timer);

  return { call: toCall(row, userId), calleeOnline: delivered > 0, iceServers: env.iceServers };
}

/** POST /calls/:callId/accept - người nhận nhấc máy. */
async function accept(userId, callId) {
  const call = await loadCall(userId, callId);
  if (Number(call.callee_id) !== Number(userId)) throw ApiError.forbidden('Chỉ người nhận mới nhấc máy được');
  const row = await transition(callId, 'ringing', 'accepted', { answered: true });
  if (!row) throw conflict('Cuộc gọi đã kết thúc', 'CALL_NOT_RINGING');
  clearRing(callId);
  emit(row, 'call.accepted');
  return { call: toCall(row, userId), iceServers: env.iceServers };
}

/** POST /calls/:callId/reject - người nhận từ chối. */
async function reject(userId, callId) {
  const call = await loadCall(userId, callId);
  if (Number(call.callee_id) !== Number(userId)) throw ApiError.forbidden('Chỉ người nhận mới từ chối được');
  const row = await transition(callId, 'ringing', 'rejected');
  if (!row) throw conflict('Cuộc gọi đã kết thúc', 'CALL_NOT_RINGING');
  await finish(row, 'call.rejected');
  return { call: toCall(row, userId) };
}

/** POST /calls/:callId/cancel - người gọi cúp máy khi bên kia chưa nhấc. */
async function cancel(userId, callId) {
  const call = await loadCall(userId, callId);
  if (Number(call.caller_id) !== Number(userId)) throw ApiError.forbidden('Chỉ người gọi mới huỷ được');
  const row = await transition(callId, 'ringing', 'cancelled');
  if (!row) throw conflict('Cuộc gọi đã được nhấc máy hoặc đã kết thúc', 'CALL_NOT_RINGING');
  await finish(row, 'call.cancelled');
  return { call: toCall(row, userId) };
}

/**
 * POST /calls/:callId/end - nút cúp máy đỏ: dùng được ở MỌI lúc.
 * Đang nói chuyện -> ended; còn đổ chuông -> người gọi = cancel, người nhận = reject; đã kết thúc rồi -> trả về trạng thái hiện tại.
 */
async function end(userId, callId) {
  const call = await loadCall(userId, callId);
  if (call.status === 'ringing') {
    return Number(call.caller_id) === Number(userId) ? cancel(userId, callId) : reject(userId, callId);
  }
  if (call.status === 'accepted') {
    const row = await transition(callId, 'accepted', 'ended');
    if (row) {
      await finish(row, 'call.ended');
      return { call: toCall(row, userId) };
    }
  }
  return { call: toCall(await getRow(callId), userId) };
}

/** GET /calls/:callId */
async function get(userId, callId) {
  return { call: toCall(await loadCall(userId, callId), userId) };
}

/** GET /calls/active - cuộc gọi đang đổ chuông / đang diễn ra của mình (mở app từ thông báo đẩy, thanh "đang gọi"). */
async function getActive(userId) {
  await expireStale([userId]);
  const [row] = await db.query(
    'SELECT * FROM calls WHERE status IN (?) AND (caller_id = ? OR callee_id = ?) ORDER BY id DESC LIMIT 1',
    [ACTIVE, userId, userId],
  );
  return { call: row ? toCall(row, userId) : null, iceServers: env.iceServers };
}

/** Kết thúc mọi cuộc gọi đang mở của 1 match (khi huỷ tương hợp / chặn). */
async function endForMatch(matchId) {
  const rows = await db.query('SELECT id, status FROM calls WHERE match_id = ? AND status IN (?)', [matchId, ACTIVE]);
  for (const r of rows) {
    const row = await transition(r.id, r.status, r.status === 'ringing' ? 'cancelled' : 'ended');
    if (row) emit(row, r.status === 'ringing' ? 'call.cancelled' : 'call.ended');
  }
}

// ═════════════════════ REALTIME ═════════════════════

/**
 * App gửi {event:"call.signal", data:{callId, signal:{...}}} -> chuyển nguyên gói cho bên kia.
 * signal là gì tuỳ app (WebRTC: {type:"offer"|"answer", sdp} hoặc {type:"candidate", candidate, sdpMid, sdpMLineIndex};
 * cũng dùng được để báo tắt mic / camera: {type:"mute", audio:false}).
 */
hub.on('call.signal', async (user, data) => {
  const callId = Number(data.callId);
  if (!Number.isInteger(callId) || callId <= 0) throw ApiError.badRequest('Thiếu callId');
  const call = await loadCall(user.id, callId);
  if (!ACTIVE.includes(call.status)) throw conflict('Cuộc gọi đã kết thúc', 'CALL_ENDED');
  const sent = hub.sendToUser(otherParty(call, user.id), 'call.signal', { callId, fromUserId: user.id, signal: data.signal ?? null });
  if (!sent) throw conflict('Bên kia đang mất kết nối', 'PEER_OFFLINE');
});

/** Một bên rớt mạng hẳn (đóng hết kết nối) khi đang gọi: chờ 1 lúc, không quay lại thì kết thúc cuộc gọi. */
hub.events.on('offline', (userId) => {
  const timer = setTimeout(async () => {
    try {
      if (hub.isOnline(userId)) return;
      const rows = await db.query(
        `SELECT id, status, caller_id FROM calls
          WHERE (status = 'accepted' AND (caller_id = ? OR callee_id = ?)) OR (status = 'ringing' AND caller_id = ?)`,
        [userId, userId, userId],
      );
      for (const r of rows) {
        const to = r.status === 'ringing' ? 'cancelled' : 'ended';
        const row = await transition(r.id, r.status, to);
        if (row) await finish(row, to === 'cancelled' ? 'call.cancelled' : 'call.ended');
      }
    } catch (err) {
      console.error('[call]', err.message);
    }
  }, OFFLINE_GRACE_MS);
  timer.unref();
});

module.exports = { start, accept, reject, cancel, end, get, getActive, endForMatch, toCall };
