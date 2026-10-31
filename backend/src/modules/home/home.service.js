const db = require('../../config/db');
const ApiError = require('../../utils/ApiError');
const { ageFromBirthday } = require('../../utils/age');
const usersService = require('../users/users.service');
const matching = require('./matching');

const CANDIDATE_POOL = 300; // số ứng viên tối đa lấy ra để chấm điểm mỗi lần

/** Thông tin người xem: giới tính, vị trí, tuổi, sở thích, tiêu chí đã lưu. */
async function loadViewer(userId) {
  const [row] = await db.query(
    `SELECT p.gender, p.birthday, p.latitude, p.longitude,
            pr.interested_in, pr.min_age, pr.max_age, pr.max_distance_km
       FROM profiles p LEFT JOIN preferences pr ON pr.user_id = p.user_id
      WHERE p.user_id = ?`,
    [userId],
  );
  if (!row) throw ApiError.notFound('Không tìm thấy hồ sơ');
  const interestIds = (await db.query('SELECT interest_id FROM user_interests WHERE user_id = ?', [userId])).map(
    (r) => Number(r.interest_id),
  );
  return {
    id: userId,
    gender: row.gender,
    age: ageFromBirthday(row.birthday),
    lat: row.latitude == null ? null : Number(row.latitude),
    lng: row.longitude == null ? null : Number(row.longitude),
    interestIds,
    prefs: {
      gender: row.interested_in || 'everyone',
      minAge: row.min_age ?? 18,
      maxAge: row.max_age ?? 100,
      maxDistanceKm: row.max_distance_km ?? null,
    },
  };
}

/**
 * Lõi tìm ứng viên: lọc bằng SQL rồi chấm điểm bằng matching.js.
 * filters: { gender, minAge, maxAge, maxDistanceKm, strictDistance, interestIds, city, q,
 *            hasPhoto, sort: 'score'|'distance'|'newest'|'active', limit }
 */
async function findCandidates(viewer, f) {
  const hasLoc = viewer.lat != null && viewer.lng != null;
  // Toạ độ lấy từ DB (kiểu số) nên nhúng trực tiếp vào SQL an toàn
  const distanceExpr = hasLoc
    ? `CASE WHEN p.latitude IS NULL THEN NULL
       ELSE ST_Distance_Sphere(POINT(p.longitude, p.latitude), POINT(${viewer.lng}, ${viewer.lat})) / 1000 END`
    : 'NULL';
  const myInterests = viewer.interestIds.map(Number).filter(Number.isInteger);
  const commonExpr = myInterests.length
    ? `(SELECT COUNT(*) FROM user_interests ui WHERE ui.user_id = u.id AND ui.interest_id IN (${myInterests.join(',')}))`
    : '0';

  const where = [
    'u.id <> ?',
    "u.status = 'active'",
    // ứng viên cũng phải muốn tìm giới tính của mình
    "(cp.interested_in IS NULL OR cp.interested_in = 'everyone' OR cp.interested_in = ?)",
    'TIMESTAMPDIFF(YEAR, p.birthday, UTC_DATE()) BETWEEN ? AND ?',
    'NOT EXISTS (SELECT 1 FROM swipes s WHERE s.swiper_id = ? AND s.swiped_id = u.id)',
    `NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id = ? AND b.blocked_id = u.id)
                                        OR (b.blocker_id = u.id AND b.blocked_id = ?))`,
  ];
  const params = [viewer.id, viewer.gender, f.minAge, f.maxAge, viewer.id, viewer.id, viewer.id];

  if (f.gender && f.gender !== 'everyone') {
    where.push('p.gender = ?');
    params.push(f.gender);
  }
  if (hasLoc && f.maxDistanceKm) {
    // Mặc định: người chưa bật vị trí vẫn hiện. Khi user tự chọn filter khoảng cách thì loại họ ra.
    where.push(f.strictDistance ? `${distanceExpr} <= ?` : `(p.latitude IS NULL OR ${distanceExpr} <= ?)`);
    params.push(f.maxDistanceKm);
  }
  if (f.interestIds?.length) {
    where.push('EXISTS (SELECT 1 FROM user_interests fi WHERE fi.user_id = u.id AND fi.interest_id IN (?))');
    params.push(f.interestIds);
  }
  if (f.city) {
    where.push('p.city LIKE ?');
    params.push(`%${f.city}%`);
  }
  if (f.q) {
    where.push('p.display_name LIKE ?');
    params.push(`%${f.q}%`);
  }
  if (f.hasPhoto) {
    where.push('EXISTS (SELECT 1 FROM photos ph WHERE ph.user_id = u.id)');
  }

  const poolOrder = {
    distance: '(distance_km IS NULL), distance_km, u.id DESC',
    newest: 'u.created_at DESC, u.id DESC',
  }[f.sort] || 'u.last_active_at DESC, u.id DESC';

  const rows = await db.query(
    `SELECT u.id, u.last_active_at, u.created_at,
            p.display_name, p.birthday, p.gender, p.bio, p.job_title, p.school, p.city,
            ${distanceExpr} AS distance_km,
            ${commonExpr} AS common_interests,
            (SELECT COUNT(*) FROM user_interests ui2 WHERE ui2.user_id = u.id) AS interest_count,
            EXISTS (SELECT 1 FROM photos ph2 WHERE ph2.user_id = u.id) AS has_photo,
            EXISTS (SELECT 1 FROM swipes s2 WHERE s2.swiper_id = u.id AND s2.swiped_id = ?
                                              AND s2.action IN ('like','superlike')) AS liked_me
       FROM users u
       JOIN profiles p ON p.user_id = u.id
       LEFT JOIN preferences cp ON cp.user_id = u.id
      WHERE ${where.join('\n        AND ')}
      ORDER BY ${poolOrder}
      LIMIT ?`,
    [viewer.id, ...params, CANDIDATE_POOL],
  );

  const me = { age: viewer.age, interestCount: myInterests.length, radiusKm: f.maxDistanceKm };
  const scored = rows.map((r) => ({
    row: r,
    ...matching.score(
      {
        distanceKm: r.distance_km == null ? null : Number(r.distance_km),
        commonInterests: Number(r.common_interests),
        lastActiveAt: r.last_active_at,
        age: ageFromBirthday(r.birthday),
        hasPhoto: Boolean(r.has_photo),
        hasBio: Boolean(r.bio),
        interestCount: Number(r.interest_count),
        likedMe: Boolean(r.liked_me),
      },
      me,
    ),
  }));

  // Sắp xếp theo điểm (mặc định); các kiểu khác đã sắp sẵn bằng SQL
  if (!f.sort || f.sort === 'score') scored.sort((a, b) => b.rankScore - a.rankScore);
  const top = scored.slice(0, f.limit);

  const extras = await usersService.loadExtras(top.map((s) => Number(s.row.id)));
  return top.map(({ row, matchScore }) => ({
    ...usersService.toPublicProfile(row, extras),
    matchScore,
    commonInterestCount: Number(row.common_interests),
  }));
}

/**
 * (1) KẾT QUẢ MẶC ĐỊNH cho trang Home - chưa search / chưa thêm filter.
 * Dựa trên thông tin đã cung cấp khi đăng ký (giới tính muốn tìm, tuổi, vị trí, sở thích)
 * và tiêu chí đã lưu (preferences). Sắp xếp theo matchScore.
 */
async function getFeed(userId, limit) {
  const viewer = await loadViewer(userId);
  return findCandidates(viewer, { ...viewer.prefs, limit, sort: 'score' });
}

/**
 * (2) KẾT QUẢ KHI THÊM FILTER. Filter nào không gửi thì dùng tiêu chí đã lưu.
 * Filter chỉ áp dụng cho lần tìm này, không ghi đè preferences
 * (muốn lưu làm mặc định thì gọi PUT /users/me/preferences).
 */
async function search(userId, query) {
  const viewer = await loadViewer(userId);
  const f = {
    gender: query.gender ?? viewer.prefs.gender,
    minAge: query.minAge ?? viewer.prefs.minAge,
    maxAge: query.maxAge ?? viewer.prefs.maxAge,
    maxDistanceKm: query.maxDistanceKm ?? viewer.prefs.maxDistanceKm,
    strictDistance: query.maxDistanceKm !== undefined,
    interestIds: query.interestIds,
    city: query.city,
    q: query.q,
    hasPhoto: query.hasPhoto,
    sort: query.sort,
    limit: query.limit,
  };
  if (f.minAge > f.maxAge) throw ApiError.badRequest('minAge phải nhỏ hơn hoặc bằng maxAge');
  return findCandidates(viewer, f);
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

module.exports = { getFeed, search, swipe, getMatches, unmatch };
