const db = require('../../config/db');
const ApiError = require('../../utils/ApiError');
const { ageFromBirthday } = require('../../utils/age');
const usersService = require('../users/users.service');
const matching = require('./matching');
const taste = require('./taste');
const chats = require('../chats/chats.service');
const safety = require('../safety/safety.service');
const hub = require('../../realtime/hub');
const push = require('../../services/push');

const CANDIDATE_POOL = 300; // số ứng viên tối đa lấy ra để chấm điểm mỗi lần
const MIN_FEED = 5; // feed mặc định có ít hơn số này thì tự nới filter (Mức 3)
// Các bước nới: bán kính × factor, độ tuổi ± ageDelta
const WIDEN_STEPS = [
  { distanceFactor: 2, ageDelta: 2 },
  { distanceFactor: 4, ageDelta: 5 },
  { distanceFactor: 10, ageDelta: 10 },
];
const MAX_WIDEN_KM = 500;

const LIFESTYLE_COLUMNS = {
  relationshipGoal: 'relationship_goal',
  zodiac: 'zodiac',
  smoking: 'smoking',
  drinking: 'drinking',
  exercise: 'exercise',
  education: 'education',
};

/** Thông tin người xem: giới tính, vị trí, tuổi, sở thích, tiêu chí đã lưu. */
async function loadViewer(userId) {
  const [row] = await db.query(
    `SELECT p.gender, p.birthday, p.latitude, p.longitude, p.city, p.completed_at,
            pr.interested_in, pr.min_age, pr.max_age, pr.max_distance_km
       FROM profiles p LEFT JOIN preferences pr ON pr.user_id = p.user_id
      WHERE p.user_id = ?`,
    [userId],
  );
  if (!row) throw ApiError.notFound('Không tìm thấy hồ sơ');
  // Chưa hoàn tất onboarding (chưa có ngày sinh / giới tính / ảnh...) thì chưa xem được trang Home
  if (!row.completed_at) {
    throw new ApiError(
      403,
      'Bạn cần hoàn tất hồ sơ trước khi xem gợi ý',
      'PROFILE_INCOMPLETE',
      [{ field: 'onboarding', message: 'Gọi GET /api/users/me để xem còn thiếu gì, sau đó POST /api/users/me/onboarding/complete' }],
    );
  }
  const interestIds = (await db.query('SELECT interest_id FROM user_interests WHERE user_id = ?', [userId])).map(
    (r) => Number(r.interest_id),
  );
  return {
    id: userId,
    gender: row.gender,
    age: ageFromBirthday(row.birthday),
    lat: row.latitude == null ? null : Number(row.latitude),
    lng: row.longitude == null ? null : Number(row.longitude),
    city: row.city || null,
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
 * filters: { gender, minAge, maxAge, maxDistanceKm, strictDistance, interestIds, city, sameCity, q, hasPhoto,
 *            relationshipGoal[], zodiac[], smoking[], drinking[], exercise[], education[],
 *            minHeight, maxHeight, minWeight, maxWeight,
 *            sort: 'score'|'distance'|'newest'|'active', limit }
 * userTaste: gu đã học (taste.js) hoặc null -> chấm điểm cá nhân hoá
 */
async function findCandidates(viewer, f, userTaste = null) {
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
    'p.completed_at IS NOT NULL', // chỉ hiện người đã hoàn tất hồ sơ
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
  if (f.sameCity && viewer.city) {
    where.push('LOWER(p.city) = LOWER(?)');
    params.push(viewer.city);
  }
  // Bộ lọc nâng cao (màn Filters bước 2): mỗi mục chọn được nhiều giá trị, không gửi = "Không quan trọng"
  for (const [key, column] of Object.entries(LIFESTYLE_COLUMNS)) {
    if (f[key]?.length) {
      where.push(`p.${column} IN (?)`);
      params.push(f[key]);
    }
  }
  // Lọc theo chiều cao / cân nặng: người chưa khai thì không lọt. Cân nặng chỉ dùng để lọc, không trả về trong hồ sơ.
  for (const [min, max, column] of [
    ['minHeight', 'maxHeight', 'height_cm'],
    ['minWeight', 'maxWeight', 'weight_kg'],
  ]) {
    if (f[min] != null) {
      where.push(`p.${column} >= ?`);
      params.push(f[min]);
    }
    if (f[max] != null) {
      where.push(`p.${column} <= ?`);
      params.push(f[max]);
    }
  }

  const poolOrder = {
    distance: '(distance_km IS NULL), distance_km, u.id DESC',
    newest: 'u.created_at DESC, u.id DESC',
  }[f.sort] || 'u.last_active_at DESC, u.id DESC';

  const rows = await db.query(
    `SELECT u.id, u.created_at,
            ${usersService.PUBLIC_PROFILE_COLUMNS},
            ${distanceExpr} AS distance_km,
            ${commonExpr} AS common_interests,
            (SELECT GROUP_CONCAT(ui2.interest_id) FROM user_interests ui2 WHERE ui2.user_id = u.id) AS interest_ids,
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
  const scored = rows.map((r) => {
    const interestIds = taste.parseIds(r.interest_ids);
    const c = {
      distanceKm: r.distance_km == null ? null : Number(r.distance_km),
      commonInterests: Number(r.common_interests),
      interestIds,
      lastActiveAt: r.last_active_at,
      age: ageFromBirthday(r.birthday),
      hasPhoto: Boolean(r.has_photo),
      hasBio: Boolean(r.bio),
      interestCount: interestIds.length,
      likedMe: Boolean(r.liked_me),
    };
    return { row: r, c, ...matching.score(c, me, userTaste) };
  });

  // Sắp xếp theo điểm (mặc định); các kiểu khác đã sắp sẵn bằng SQL
  if (!f.sort || f.sort === 'score') scored.sort((a, b) => b.rankScore - a.rankScore);
  const top = scored.slice(0, f.limit);

  const extras = await usersService.loadExtras(top.map((s) => Number(s.row.id)));
  const ctx = { myInterests: new Set(myInterests), taste: userTaste };
  return top.map(({ row, c, matchScore }) => {
    const profile = usersService.toPublicProfile(row, extras);
    return {
      ...profile,
      matchScore,
      commonInterestCount: c.commonInterests,
      reasons: buildReasons(profile, c, ctx),
    };
  });
}

/** Lý do gợi ý hiển thị trên thẻ (tối đa 3). */
function buildReasons(profile, c, { myInterests, taste: t }) {
  const reasons = [];
  const common = profile.interests.filter((i) => myInterests.has(i.id)).map((i) => i.name);
  if (common.length) reasons.push(`Cùng thích ${common.slice(0, 2).join(', ')}`);

  if (t && matching.personalizationLevel(t.swipeCount) > 0) {
    const aff = t.interestAffinity || {};
    const liked = profile.interests
      .filter((i) => !myInterests.has(i.id) && Number(aff[i.id]) >= 0.3)
      .map((i) => i.name);
    if (liked.length) reasons.push(`Hợp gu của bạn: ${liked.slice(0, 2).join(', ')}`);
    else if (t.ageMean != null && c.age != null && Math.abs(c.age - t.ageMean) <= Math.max(2, t.ageStd || 0)) {
      reasons.push('Đúng độ tuổi bạn hay thích');
    }
  }
  // profile.distanceKm = null khi người đó tắt "Hiện khoảng cách" -> không tiết lộ qua lý do gợi ý
  if (profile.distanceKm != null && c.distanceKm < 5) reasons.push(`Cách bạn ${profile.distanceKm} km`);
  if (profile.lastActiveAt && Date.now() - new Date(profile.lastActiveAt).getTime() < 24 * 3600 * 1000) {
    reasons.push('Vừa hoạt động');
  }
  return reasons.slice(0, 3);
}

/** Thông tin cá nhân hoá trả kèm response (meta). */
function personalizationMeta(userTaste, swipeCount) {
  const level = matching.personalizationLevel(userTaste?.swipeCount);
  return {
    level, // α: 0 = thuật toán mặc định, tối đa 0.7
    swipeCount,
    minSwipes: matching.MIN_SWIPES,
    swipesUntilPersonalized: Math.max(0, matching.MIN_SWIPES - swipeCount),
  };
}

async function countSwipes(userId) {
  const [{ total }] = await db.query('SELECT COUNT(*) AS total FROM swipes WHERE swiper_id = ?', [userId]);
  return Number(total);
}

/**
 * (1) KẾT QUẢ MẶC ĐỊNH cho trang Home - chưa search / chưa thêm filter.
 * Dựa trên thông tin đã cung cấp khi đăng ký (giới tính muốn tìm, tuổi, vị trí, sở thích)
 * và tiêu chí đã lưu (preferences). Sắp xếp theo matchScore (cá nhân hoá khi đủ lượt quẹt).
 * Mức 3: có ít hơn MIN_FEED người thì tự nới bán kính / độ tuổi theo từng bước.
 * Trả về { items, meta }.
 */
async function getFeed(userId, limit) {
  const viewer = await loadViewer(userId);
  const userTaste = await taste.getTaste(userId);
  const base = { ...viewer.prefs, limit, sort: 'score' };

  let items = await findCandidates(viewer, base, userTaste);
  let expanded = null;
  const want = Math.min(limit, MIN_FEED);

  for (let i = 0; i < WIDEN_STEPS.length && items.length < want; i++) {
    const step = WIDEN_STEPS[i];
    const f = {
      ...base,
      minAge: Math.max(18, base.minAge - step.ageDelta),
      maxAge: Math.min(100, base.maxAge + step.ageDelta),
      maxDistanceKm: base.maxDistanceKm ? Math.min(MAX_WIDEN_KM, base.maxDistanceKm * step.distanceFactor) : null,
    };
    const wider = await findCandidates(viewer, f, userTaste);
    // Chỉ coi là "đã mở rộng" khi thật sự tìm thêm được người
    if (wider.length > items.length) {
      items = wider;
      expanded = { step: i + 1, minAge: f.minAge, maxAge: f.maxAge, maxDistanceKm: f.maxDistanceKm };
    }
  }

  if (expanded) {
    // Đánh dấu người nằm ngoài tiêu chí gốc và xếp họ sau người trong tiêu chí
    const p = viewer.prefs;
    for (const u of items) {
      u.outsidePreferences =
        u.age < p.minAge || u.age > p.maxAge || (u.distanceKm != null && p.maxDistanceKm && u.distanceKm > p.maxDistanceKm);
      if (u.outsidePreferences) u.reasons = ['Ngoài phạm vi tìm kiếm', ...u.reasons].slice(0, 3);
    }
    items.sort((a, b) => Number(a.outsidePreferences) - Number(b.outsidePreferences));
  }

  return {
    items,
    meta: {
      personalization: personalizationMeta(userTaste, await countSwipes(userId)),
      expanded,
      original: { minAge: viewer.prefs.minAge, maxAge: viewer.prefs.maxAge, maxDistanceKm: viewer.prefs.maxDistanceKm },
    },
  };
}

/**
 * (2) KẾT QUẢ KHI THÊM FILTER. Filter nào không gửi thì dùng tiêu chí đã lưu.
 * Filter chỉ áp dụng cho lần tìm này, không ghi đè preferences
 * (muốn lưu làm mặc định thì gọi PUT /users/me/preferences).
 * Filter người dùng tự chọn là ranh giới cứng: KHÔNG tự nới, chỉ cá nhân hoá thứ tự sắp xếp.
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
    sameCity: query.sameCity,
    relationshipGoal: query.relationshipGoal,
    zodiac: query.zodiac,
    smoking: query.smoking,
    drinking: query.drinking,
    exercise: query.exercise,
    education: query.education,
    minHeight: query.minHeight,
    maxHeight: query.maxHeight,
    minWeight: query.minWeight,
    maxWeight: query.maxWeight,
    sort: query.sort,
    limit: query.limit,
  };
  // Bộ lọc app có hiển thị nhưng hồ sơ chưa có dữ liệu tương ứng -> bỏ qua và báo lại trong meta
  const ignoredFilters = [];
  if (query.orientation) ignoredFilters.push({ filter: 'orientation', reason: 'Hồ sơ chưa có thông tin xu hướng tính dục' });
  if (query.sameDistrict) ignoredFilters.push({ filter: 'sameDistrict', reason: 'Hồ sơ chưa có thông tin quận / huyện' });
  if (query.sameCity && !viewer.city) ignoredFilters.push({ filter: 'sameCity', reason: 'Bạn chưa cập nhật thành phố trong hồ sơ' });
  if (f.minAge > f.maxAge) throw ApiError.badRequest('minAge phải nhỏ hơn hoặc bằng maxAge');
  const userTaste = await taste.getTaste(userId);
  const items = await findCandidates(viewer, f, userTaste);
  return { items, meta: { personalization: personalizationMeta(userTaste, await countSwipes(userId)), ignoredFilters } };
}

/** Gu đã học của người dùng (tính lại ngay), kèm tên sở thích - cho màn hình "Gu của bạn" / báo cáo. */
async function getTasteSummary(userId) {
  const t = await taste.refresh(userId);
  const affinity = t?.interestAffinity || {};
  const ids = Object.keys(affinity).map(Number);
  const names = ids.length ? await db.query('SELECT id, name FROM interests WHERE id IN (?)', [ids]) : [];
  const nameOf = new Map(names.map((r) => [Number(r.id), r.name]));
  const list = ids
    .map((id) => ({ id, name: nameOf.get(id) || `#${id}`, affinity: affinity[id] }))
    .sort((a, b) => b.affinity - a.affinity);

  const level = matching.personalizationLevel(t?.swipeCount);
  return {
    personalization: personalizationMeta(t, t?.swipeCount || 0),
    active: level > 0,
    likeCount: t?.likeCount || 0,
    preferredAge: t?.ageMean == null ? null : { mean: t.ageMean, std: t.ageStd },
    preferredDistanceKm: t?.distanceMean ?? null,
    likedInterests: list.filter((x) => x.affinity > 0),
    dislikedInterests: list.filter((x) => x.affinity < 0),
    weights: {
      default: matching.DEFAULT_WEIGHTS,
      personal: t?.weights ? Object.fromEntries(Object.entries(t.weights).filter(([k]) => k !== 'mix')) : null,
      effective: Object.fromEntries(
        Object.entries(matching.blendWeights(t?.weights, level)).map(([k, v]) => [k, Math.round(v * 10) / 10]),
      ),
      tasteMix: t?.weights?.mix || null,
    },
  };
}

/** Quẹt like / pass / superlike. Nếu hai bên cùng like thì tạo match. */
async function swipe(userId, targetId, action) {
  if (userId === targetId) throw ApiError.badRequest('Không thể tự quẹt chính mình');

  const [target] = await db.query(
    `SELECT u.id FROM users u JOIN profiles p ON p.user_id = u.id
      WHERE u.id = ? AND u.status = 'active' AND p.completed_at IS NOT NULL`,
    [targetId],
  );
  if (!target) throw ApiError.notFound('Không tìm thấy người dùng');
  if (await usersService.isBlockedBetween(userId, targetId)) throw ApiError.notFound('Không tìm thấy người dùng');

  const result = await db.transaction(async (conn) => {
    const [[previous]] = await conn.query('SELECT action FROM swipes WHERE swiper_id = ? AND swiped_id = ?', [userId, targetId]);
    const alreadyLiked = Boolean(previous && previous.action !== 'pass');
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

    if (!liked) {
      // Người kia chưa thích mình: mình xuất hiện trong tab "Lượt thích" của họ (GET /home/likes)
      if (!alreadyLiked) {
        await conn.query('INSERT INTO notifications (user_id, type, payload) VALUES (?, ?, ?)', [
          targetId,
          action === 'superlike' ? 'super_like' : 'new_like',
          JSON.stringify({ fromUserId: userId }),
        ]);
      }
      return { matched: false, notifyLike: !alreadyLiked };
    }

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
    return { matched: true, matchId, isNew: true };
  });
  const { notifyLike, isNew } = result;
  delete result.notifyLike;
  delete result.isNew;

  // Cập nhật gu (mỗi 10 lượt quẹt) - chạy nền, không làm chậm response
  taste.maybeUpdate(userId).catch((err) => console.error('[taste]', err.message));

  if (result.matched) {
    result.user = await usersService.getPublicProfile(userId, targetId);
    if (isNew) announceMatch(userId, targetId, result.matchId).catch((err) => console.error('[match]', err.message));
  } else if (notifyLike) {
    announceLike(userId, targetId, action).catch((err) => console.error('[like]', err.message));
  }
  return result;
}

/** Báo cho người được thích: realtime nếu đang mở app (cập nhật tab Lượt thích), không thì thông báo đẩy. */
async function announceLike(userId, targetId, action) {
  const superlike = action === 'superlike';
  const delivered = hub.sendToUser(targetId, 'like.new', { userId: Number(userId), superlike });
  if (!delivered) {
    await push.notify(targetId, {
      type: superlike ? 'super_like' : 'new_like',
      title: 'DateZone',
      body: superlike ? 'Có người vừa siêu thích bạn ⭐' : 'Có người vừa thích bạn ❤️',
      data: { fromUserId: Number(userId) },
    });
  }
}

/** Tương hợp mới: người quẹt nhận kết quả ngay trong response, người kia nhận sự kiện match.new / thông báo đẩy. */
async function announceMatch(userId, targetId, matchId) {
  const me = await usersService.getPublicProfile(targetId, userId); // hồ sơ của mình dưới góc nhìn người kia
  const delivered = hub.sendToUser(targetId, 'match.new', { matchId, user: me });
  if (!delivered) {
    await push.notify(targetId, {
      type: 'new_match',
      title: 'Tương hợp mới! 🎉',
      body: `Bạn và ${me.displayName} đã thích nhau. Gửi lời chào ngay!`,
      data: { matchId, userId: Number(userId) },
    });
  }
}

const VN_OFFSET_MS = 7 * 3600 * 1000;
const dayNumber = (date) => Math.floor((new Date(date).getTime() + VN_OFFSET_MS) / 86400000); // ngày theo giờ Việt Nam

/**
 * GET /home/likes - tab Lượt thích (MatchesActivity): những người đã thích mình mà mình CHƯA quẹt.
 * Thích lại / bỏ qua: POST /home/swipe { targetId, action: like | pass } (thích lại là thành tương hợp ngay).
 * Mỗi người có likedAt, superlike và group: today | yesterday | earlier (theo giờ Việt Nam) để chia nhóm.
 */
async function getLikes(userId, limit) {
  await loadViewer(userId); // chưa hoàn tất hồ sơ thì chưa xem được
  const base = `FROM swipes s
       JOIN users u    ON u.id = s.swiper_id
       JOIN profiles p ON p.user_id = u.id
       LEFT JOIN profiles me ON me.user_id = s.swiped_id
      WHERE s.swiped_id = ? AND s.action IN ('like','superlike')
        AND u.status = 'active' AND p.completed_at IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM swipes mine WHERE mine.swiper_id = s.swiped_id AND mine.swiped_id = u.id)
        AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id = s.swiped_id AND b.blocked_id = u.id)
                                                  OR (b.blocker_id = u.id AND b.blocked_id = s.swiped_id))`;
  const rows = await db.query(
    `SELECT u.id, ${usersService.PUBLIC_PROFILE_COLUMNS}, s.action, s.created_at AS liked_at,
            CASE WHEN p.latitude IS NULL OR me.latitude IS NULL THEN NULL
                 ELSE ST_Distance_Sphere(POINT(p.longitude, p.latitude), POINT(me.longitude, me.latitude)) / 1000
            END AS distance_km
       ${base}
      ORDER BY s.created_at DESC, u.id DESC
      LIMIT ?`,
    [userId, limit],
  );
  const [{ total }] = await db.query(`SELECT COUNT(*) AS total ${base}`, [userId]);

  const today = dayNumber(Date.now());
  const extras = await usersService.loadExtras(rows.map((r) => Number(r.id)));
  const items = rows.map((r) => {
    const diff = today - dayNumber(r.liked_at);
    return {
      ...usersService.toPublicProfile(r, extras),
      likedAt: r.liked_at,
      superlike: r.action === 'superlike',
      group: diff <= 0 ? 'today' : diff === 1 ? 'yesterday' : 'earlier',
    };
  });
  return { items, meta: { total: Number(total), today: items.filter((i) => i.group === 'today').length } };
}

/**
 * GET /home/matches - những người đã tương hợp, kèm tin nhắn cuối, số tin chưa đọc, độ thân thiết.
 *   sort=activity (mặc định): theo hoạt động gần nhất  |  sort=recent: mới tương hợp trước ("Tương hợp gần đây")
 *   newOnly=true: chỉ người chưa nhắn tin nào   |  limit
 */
async function getMatches(userId, { sort = 'activity', newOnly = false, limit = 100 } = {}) {
  let items = await chats.listConversations(userId);
  if (newOnly) items = items.filter((c) => !c.lastMessage);
  if (sort === 'recent') items = [...items].sort((a, b) => new Date(b.matchedAt) - new Date(a.matchedAt) || b.matchId - a.matchId);
  return items.slice(0, limit);
}

/** DELETE /home/matches/:matchId - huỷ tương hợp: cuộc trò chuyện biến mất ở cả 2 phía. */
async function unmatch(userId, matchId) {
  const [match] = await db.query(
    'SELECT user1_id, user2_id FROM matches WHERE id = ? AND (user1_id = ? OR user2_id = ?) AND unmatched_at IS NULL',
    [matchId, userId, userId],
  );
  if (!match) throw ApiError.notFound('Không tìm thấy match');
  const partnerId = Number(match.user1_id) === Number(userId) ? match.user2_id : match.user1_id;
  await safety.removeMatchBetween(userId, Number(partnerId));
}

module.exports = { getFeed, search, getTasteSummary, swipe, getLikes, getMatches, unmatch, findCandidates, loadViewer };
