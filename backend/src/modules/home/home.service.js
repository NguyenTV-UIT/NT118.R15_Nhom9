const db = require('../../config/db');
const ApiError = require('../../utils/ApiError');
const usersService = require('../users/users.service');

// Khoảng cách (km) giữa ứng viên p và người dùng hiện tại me; NULL nếu 1 trong 2 chưa có vị trí.
const DISTANCE_SQL = `CASE WHEN p.latitude IS NULL OR me.latitude IS NULL THEN NULL
  ELSE ST_Distance_Sphere(POINT(p.longitude, p.latitude), POINT(me.longitude, me.latitude)) / 1000 END`;

/**
 * Danh sách gợi ý cho trang Home (thẻ để quẹt).
 * Lọc theo: giới tính & độ tuổi mình muốn, ứng viên cũng muốn giới tính của mình,
 * khoảng cách tối đa, bỏ người đã quẹt, đã chặn / bị chặn.
 */
async function getFeed(userId, limit) {
  const rows = await db.query(
    `SELECT u.id, p.display_name, p.birthday, p.gender, p.bio, p.job_title, p.school, p.city,
            ${DISTANCE_SQL} AS distance_km
       FROM users u
       JOIN profiles p     ON p.user_id = u.id
       JOIN profiles me    ON me.user_id = ?
       JOIN preferences mp ON mp.user_id = me.user_id
       LEFT JOIN preferences cp ON cp.user_id = u.id
      WHERE u.id <> me.user_id
        AND u.status = 'active'
        AND (mp.interested_in = 'everyone' OR p.gender = mp.interested_in)
        AND (cp.interested_in IS NULL OR cp.interested_in = 'everyone' OR cp.interested_in = me.gender)
        AND TIMESTAMPDIFF(YEAR, p.birthday, UTC_DATE()) BETWEEN mp.min_age AND mp.max_age
        AND (${DISTANCE_SQL} IS NULL OR ${DISTANCE_SQL} <= mp.max_distance_km)
        AND NOT EXISTS (SELECT 1 FROM swipes s WHERE s.swiper_id = me.user_id AND s.swiped_id = u.id)
        AND NOT EXISTS (SELECT 1 FROM blocks b
                         WHERE (b.blocker_id = me.user_id AND b.blocked_id = u.id)
                            OR (b.blocker_id = u.id AND b.blocked_id = me.user_id))
      ORDER BY (distance_km IS NULL), distance_km, u.last_active_at DESC, u.id DESC
      LIMIT ?`,
    [userId, limit],
  );

  const extras = await usersService.loadExtras(rows.map((r) => Number(r.id)));
  return rows.map((r) => usersService.toPublicProfile(r, extras));
}

/** Quẹt like / pass / superlike. Nếu hai bên cùng like thì tạo match. */
async function swipe(userId, targetId, action) {
  if (userId === targetId) throw ApiError.badRequest('Không thể tự quẹt chính mình');

  const [target] = await db.query("SELECT id FROM users WHERE id = ? AND status = 'active'", [targetId]);
  if (!target) throw ApiError.notFound('Không tìm thấy người dùng');
  if (await usersService.isBlockedBetween(userId, targetId)) throw ApiError.notFound('Không tìm thấy người dùng');

  const result = await db.transaction(async (conn) => {
    await conn.query(
      `INSERT INTO swipes (swiper_id, swiped_id, action) VALUES (?, ?, ?)
       ON DUPLICATE KEY UPDATE action = VALUES(action), created_at = UTC_TIMESTAMP()`,
      [userId, targetId, action],
    );
    if (action === 'pass') return { matched: false };

    const [[liked]] = await conn.query(
      "SELECT action FROM swipes WHERE swiper_id = ? AND swiped_id = ? AND action IN ('like','superlike')",
      [targetId, userId],
    );

    if (action === 'superlike') {
      await conn.query('INSERT INTO notifications (user_id, type, payload) VALUES (?, ?, ?)', [
        targetId,
        'super_like',
        JSON.stringify({ fromUserId: userId }),
      ]);
    }
    if (!liked) return { matched: false };

    const [u1, u2] = userId < targetId ? [userId, targetId] : [targetId, userId];
    const [[existing]] = await conn.query(
      'SELECT id, unmatched_at FROM matches WHERE user1_id = ? AND user2_id = ? FOR UPDATE',
      [u1, u2],
    );

    let matchId;
    if (!existing) {
      const [ins] = await conn.query('INSERT INTO matches (user1_id, user2_id) VALUES (?, ?)', [u1, u2]);
      matchId = Number(ins.insertId);
    } else if (existing.unmatched_at) {
      matchId = Number(existing.id);
      await conn.query('UPDATE matches SET unmatched_at = NULL, created_at = UTC_TIMESTAMP() WHERE id = ?', [matchId]);
    } else {
      return { matched: true, matchId: Number(existing.id) }; // đã match từ trước
    }

    await conn.query('INSERT INTO notifications (user_id, type, payload) VALUES ?', [
      [
        [userId, 'new_match', JSON.stringify({ matchId, userId: targetId })],
        [targetId, 'new_match', JSON.stringify({ matchId, userId })],
      ],
    ]);
    return { matched: true, matchId };
  });

  if (result.matched) {
    result.user = await usersService.getPublicProfile(userId, targetId);
  }
  return result;
}

/** Danh sách những người đã match (kèm tin nhắn cuối cùng nếu có). */
async function getMatches(userId) {
  const rows = await db.query(
    `SELECT m.id AS match_id, m.created_at AS matched_at,
            u.id, p.display_name, p.birthday, p.gender, p.bio, p.job_title, p.school, p.city,
            NULL AS distance_km,
            lm.content AS last_message, lm.type AS last_message_type,
            lm.sender_id AS last_sender_id, lm.created_at AS last_message_at
       FROM matches m
       JOIN users u    ON u.id = IF(m.user1_id = ?, m.user2_id, m.user1_id)
       JOIN profiles p ON p.user_id = u.id
       LEFT JOIN messages lm ON lm.id = (SELECT MAX(id) FROM messages WHERE match_id = m.id)
      WHERE (m.user1_id = ? OR m.user2_id = ?)
        AND m.unmatched_at IS NULL
        AND u.status = 'active'
      ORDER BY COALESCE(lm.created_at, m.created_at) DESC`,
    [userId, userId, userId],
  );

  const extras = await usersService.loadExtras(rows.map((r) => Number(r.id)));
  return rows.map((r) => ({
    matchId: Number(r.match_id),
    matchedAt: r.matched_at,
    user: usersService.toPublicProfile(r, extras),
    lastMessage: r.last_message
      ? {
          content: r.last_message,
          type: r.last_message_type,
          fromMe: Number(r.last_sender_id) === userId,
          createdAt: r.last_message_at,
        }
      : null,
  }));
}

async function unmatch(userId, matchId) {
  const result = await db.query(
    `UPDATE matches SET unmatched_at = UTC_TIMESTAMP()
      WHERE id = ? AND (user1_id = ? OR user2_id = ?) AND unmatched_at IS NULL`,
    [matchId, userId, userId],
  );
  if (!result.affectedRows) throw ApiError.notFound('Không tìm thấy match');
}

module.exports = { getFeed, swipe, getMatches, unmatch };
