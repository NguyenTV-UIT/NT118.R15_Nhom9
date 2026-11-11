/**
 * AN TOÀN: chặn, bỏ chặn, danh sách chặn, báo cáo.
 * Chặn = hai bên không còn thấy nhau (feed, tìm kiếm, hồ sơ), huỷ tương hợp và ẩn cuộc trò chuyện.
 * Người bị chặn KHÔNG được thông báo là bị chặn - phía họ chỉ thấy cuộc trò chuyện biến mất.
 */
const env = require('../../config/env');
const db = require('../../config/db');
const ApiError = require('../../utils/ApiError');
const hub = require('../../realtime/hub');

const toPhotoUrl = (url) => (!url ? null : /^https?:\/\//.test(url) ? url : `${env.publicBaseUrl}${url}`);

async function requireTarget(userId, targetId) {
  if (Number(userId) === Number(targetId)) throw ApiError.badRequest('Không thể thực hiện với chính mình');
  const [target] = await db.query(
    `SELECT u.id, p.display_name FROM users u JOIN profiles p ON p.user_id = u.id WHERE u.id = ? AND u.status = 'active'`,
    [targetId],
  );
  if (!target) throw ApiError.notFound('Không tìm thấy người dùng');
  return target;
}

/** Huỷ tương hợp giữa 2 người (nếu có) + báo realtime cho cả hai để app gỡ cuộc trò chuyện. */
async function removeMatchBetween(userId, targetId) {
  const [u1, u2] = Number(userId) < Number(targetId) ? [userId, targetId] : [targetId, userId];
  const [match] = await db.query('SELECT id FROM matches WHERE user1_id = ? AND user2_id = ? AND unmatched_at IS NULL', [u1, u2]);
  if (!match) return null;
  await db.query('UPDATE matches SET unmatched_at = UTC_TIMESTAMP() WHERE id = ?', [match.id]);
  // Kết thúc cuộc gọi đang đổ chuông / đang diễn ra giữa 2 người
  await db.query(
    `UPDATE calls SET status = IF(status = 'ringing', 'cancelled', 'ended'), ended_at = UTC_TIMESTAMP()
      WHERE match_id = ? AND status IN ('ringing', 'accepted')`,
    [match.id],
  );
  const matchId = Number(match.id);
  hub.sendToUser(targetId, 'match.removed', { matchId, userId: Number(userId) });
  hub.sendToUser(userId, 'match.removed', { matchId, userId: Number(targetId) });
  return matchId;
}

/** POST /users/:id/block */
async function block(userId, targetId) {
  const target = await requireTarget(userId, targetId);
  await db.query('INSERT IGNORE INTO blocks (blocker_id, blocked_id) VALUES (?, ?)', [userId, targetId]);
  const matchId = await removeMatchBetween(userId, targetId);
  return { blockedUserId: Number(targetId), displayName: target.display_name, unmatched: matchId !== null };
}

/** DELETE /users/:id/block - bỏ chặn (không tự khôi phục tương hợp cũ; hai bên có thể gặp lại trên trang Khám phá). */
async function unblock(userId, targetId) {
  const result = await db.query('DELETE FROM blocks WHERE blocker_id = ? AND blocked_id = ?', [userId, targetId]);
  if (!result.affectedRows) throw ApiError.notFound('Bạn chưa chặn người này');
}

/** GET /users/me/blocks - Cài đặt > Danh sách chặn. */
async function listBlocks(userId) {
  const rows = await db.query(
    `SELECT b.blocked_id AS id, b.created_at, p.display_name,
            (SELECT ph.url FROM photos ph WHERE ph.user_id = b.blocked_id ORDER BY ph.position, ph.id LIMIT 1) AS avatar
       FROM blocks b JOIN profiles p ON p.user_id = b.blocked_id
      WHERE b.blocker_id = ?
      ORDER BY b.created_at DESC`,
    [userId],
  );
  return rows.map((r) => ({ id: Number(r.id), displayName: r.display_name, avatarUrl: toPhotoUrl(r.avatar), blockedAt: r.created_at }));
}

/**
 * POST /users/:id/report - màn ReportUser, nút "Báo cáo và chặn".
 * Lưu báo cáo (ẩn danh với người bị báo cáo) rồi chặn + huỷ tương hợp luôn.
 */
async function report(userId, targetId, { reason, details }) {
  const target = await requireTarget(userId, targetId);
  const result = await db.query('INSERT INTO reports (reporter_id, reported_id, reason, details) VALUES (?, ?, ?, ?)', [
    userId,
    targetId,
    reason,
    details || null,
  ]);
  await db.query('INSERT IGNORE INTO blocks (blocker_id, blocked_id) VALUES (?, ?)', [userId, targetId]);
  const matchId = await removeMatchBetween(userId, targetId);
  return { reportId: Number(result.insertId), blockedUserId: Number(targetId), displayName: target.display_name, unmatched: matchId !== null };
}

module.exports = { block, unblock, listBlocks, report, removeMatchBetween };
