const fs = require('fs/promises');
const path = require('path');

const env = require('../../config/env');
const db = require('../../config/db');
const ApiError = require('../../utils/ApiError');
const { ageFromBirthday } = require('../../utils/age');
const { UPLOAD_DIR } = require('../../middlewares/upload');
const options = require('../meta/options');

const MAX_PHOTOS = options.LIMITS.photos.max;

/** Cột hồ sơ công khai - dùng chung cho mọi câu SELECT trả về thẻ người dùng (feed, search, match...). */
const PUBLIC_PROFILE_COLUMNS = `p.display_name, p.birthday, p.gender, p.show_gender, p.bio, p.job_title, p.school, p.city,
            p.height_cm, p.relationship_goal, p.zodiac, p.smoking, p.drinking, p.exercise, p.education`;

// DB lưu đường dẫn tương đối (/uploads/abc.jpg); trả về URL đầy đủ để đổi IP/domain không phải sửa DB.
const toPhotoUrl = (url) => (/^https?:\/\//.test(url) ? url : `${env.publicBaseUrl}${url}`);

/** Lấy ảnh + sở thích cho nhiều user một lúc (tránh N+1 query). */
async function loadExtras(userIds) {
  const photos = new Map();
  const interests = new Map();
  if (!userIds.length) return { photos, interests };

  const photoRows = await db.query(
    'SELECT id, user_id, url, position FROM photos WHERE user_id IN (?) ORDER BY position, id',
    [userIds],
  );
  for (const p of photoRows) {
    const key = Number(p.user_id);
    if (!photos.has(key)) photos.set(key, []);
    photos.get(key).push({ id: Number(p.id), url: toPhotoUrl(p.url), position: p.position });
  }

  const interestRows = await db.query(
    `SELECT ui.user_id, i.id, i.name FROM user_interests ui
       JOIN interests i ON i.id = ui.interest_id
      WHERE ui.user_id IN (?) ORDER BY i.name`,
    [userIds],
  );
  for (const r of interestRows) {
    const key = Number(r.user_id);
    if (!interests.has(key)) interests.set(key, []);
    interests.get(key).push({ id: r.id, name: r.name });
  }
  return { photos, interests };
}

/**
 * Hồ sơ hiển thị cho người khác xem.
 * KHÔNG có: email, SĐT, ngày sinh, toạ độ, cân nặng. Giới tính ẩn nếu người dùng tắt "Hiển thị giới tính".
 * Các trường lựa chọn trả về MÃ (VD smoking: "never"), nhãn tiếng Việt lấy từ GET /api/meta/options.
 */
function toPublicProfile(row, extras) {
  const id = Number(row.id ?? row.user_id);
  const photos = extras.photos.get(id) || [];
  const showGender = row.show_gender == null ? true : Boolean(Number(row.show_gender));
  return {
    id,
    displayName: row.display_name,
    age: ageFromBirthday(row.birthday),
    gender: showGender ? row.gender ?? null : null,
    bio: row.bio ?? null,
    jobTitle: row.job_title ?? null,
    school: row.school ?? null,
    city: row.city ?? null,
    heightCm: row.height_cm == null ? null : Number(row.height_cm),
    relationshipGoal: row.relationship_goal ?? null,
    zodiac: row.zodiac ?? null,
    smoking: row.smoking ?? null,
    drinking: row.drinking ?? null,
    exercise: row.exercise ?? null,
    education: row.education ?? null,
    distanceKm: row.distance_km == null ? null : Math.max(1, Math.round(Number(row.distance_km))),
    avatarUrl: photos[0]?.url || null,
    photos,
    interests: extras.interests.get(id) || [],
  };
}

/** Còn thiếu gì để hoàn tất hồ sơ (khớp các bước bắt buộc trên app). */
function missingForCompletion(row, photoCount, interestCount) {
  const missing = [];
  if (!row.display_name) missing.push('displayName');
  if (!row.birthday) missing.push('birthday');
  if (!row.gender) missing.push('gender');
  if (photoCount < env.onboardingMinPhotos) missing.push('photos');
  if (interestCount < options.LIMITS.interests.min) missing.push('interests');
  return missing;
}

/** Hồ sơ đầy đủ của chính mình. */
async function getMe(userId) {
  const [row] = await db.query(
    `SELECT u.id, u.email, u.phone, u.role, u.created_at,
            ${PUBLIC_PROFILE_COLUMNS}, p.latitude, p.longitude, p.weight_kg, p.completed_at,
            pr.interested_in, pr.min_age, pr.max_age, pr.max_distance_km
       FROM users u
       JOIN profiles p ON p.user_id = u.id
       LEFT JOIN preferences pr ON pr.user_id = u.id
      WHERE u.id = ?`,
    [userId],
  );
  if (!row) throw ApiError.notFound('Không tìm thấy người dùng');

  const extras = await loadExtras([userId]);
  const photoCount = (extras.photos.get(Number(userId)) || []).length;
  const interestCount = (extras.interests.get(Number(userId)) || []).length;
  const missing = missingForCompletion(row, photoCount, interestCount);
  return {
    ...toPublicProfile(row, extras),
    gender: row.gender, // của chính mình thì luôn thấy
    showGender: Boolean(Number(row.show_gender)),
    weightKg: row.weight_kg == null ? null : Number(row.weight_kg),
    email: row.email,
    phone: row.phone,
    role: row.role,
    birthday: row.birthday,
    latitude: row.latitude == null ? null : Number(row.latitude),
    longitude: row.longitude == null ? null : Number(row.longitude),
    preferences: {
      interestedIn: row.interested_in,
      minAge: row.min_age,
      maxAge: row.max_age,
      maxDistanceKm: row.max_distance_km,
    },
    // App dùng sau khi đăng nhập: completed = false -> đưa về màn hình onboarding còn thiếu
    onboarding: {
      completed: Boolean(row.completed_at),
      completedAt: row.completed_at,
      missing,
      canComplete: missing.length === 0,
    },
    createdAt: row.created_at,
  };
}

async function isBlockedBetween(a, b) {
  const rows = await db.query(
    'SELECT 1 FROM blocks WHERE (blocker_id = ? AND blocked_id = ?) OR (blocker_id = ? AND blocked_id = ?) LIMIT 1',
    [a, b, b, a],
  );
  return rows.length > 0;
}

async function getPublicProfile(viewerId, targetId) {
  if (await isBlockedBetween(viewerId, targetId)) throw ApiError.notFound('Không tìm thấy người dùng');

  const [row] = await db.query(
    `SELECT u.id, ${PUBLIC_PROFILE_COLUMNS},
            CASE WHEN p.latitude IS NULL OR me.latitude IS NULL THEN NULL
                 ELSE ST_Distance_Sphere(POINT(p.longitude, p.latitude), POINT(me.longitude, me.latitude)) / 1000
            END AS distance_km
       FROM users u
       JOIN profiles p ON p.user_id = u.id
       LEFT JOIN profiles me ON me.user_id = ?
      WHERE u.id = ? AND u.status = 'active' AND p.completed_at IS NOT NULL`,
    [viewerId, targetId],
  );
  if (!row) throw ApiError.notFound('Không tìm thấy người dùng');
  return toPublicProfile(row, await loadExtras([targetId]));
}

// Map tên field camelCase (API) -> snake_case (DB)
const PROFILE_FIELDS = {
  displayName: 'display_name',
  birthday: 'birthday',
  gender: 'gender',
  bio: 'bio',
  jobTitle: 'job_title',
  school: 'school',
  city: 'city',
  latitude: 'latitude',
  longitude: 'longitude',
  showGender: 'show_gender',
  heightCm: 'height_cm',
  weightKg: 'weight_kg',
  relationshipGoal: 'relationship_goal',
  zodiac: 'zodiac',
  smoking: 'smoking',
  drinking: 'drinking',
  exercise: 'exercise',
  education: 'education',
};
const PREFERENCE_FIELDS = {
  interestedIn: 'interested_in',
  minAge: 'min_age',
  maxAge: 'max_age',
  maxDistanceKm: 'max_distance_km',
};

async function updateColumns(table, mapping, userId, data) {
  const sets = [];
  const values = [];
  for (const [key, column] of Object.entries(mapping)) {
    if (data[key] !== undefined) {
      sets.push(`${column} = ?`);
      values.push(data[key]);
    }
  }
  if (!sets.length) return;
  await db.query(`UPDATE ${table} SET ${sets.join(', ')} WHERE user_id = ?`, [...values, userId]);
}

async function updateProfile(userId, data) {
  await updateColumns('profiles', PROFILE_FIELDS, userId, data);
  return getMe(userId);
}

async function updatePreferences(userId, data) {
  const [current] = await db.query('SELECT min_age, max_age FROM preferences WHERE user_id = ?', [userId]);
  const minAge = data.minAge ?? current?.min_age;
  const maxAge = data.maxAge ?? current?.max_age;
  if (minAge > maxAge) throw ApiError.badRequest('minAge phải nhỏ hơn hoặc bằng maxAge');

  await db.query('INSERT IGNORE INTO preferences (user_id) VALUES (?)', [userId]);
  await updateColumns('preferences', PREFERENCE_FIELDS, userId, data);
  return getMe(userId);
}

async function listInterests() {
  return db.query('SELECT id, name FROM interests ORDER BY name');
}

/**
 * Đặt lại toàn bộ sở thích. Nhận interestIds (số) và/hoặc interestNames (tên đúng như chip trên app,
 * không phân biệt hoa thường) - app Android đang dùng tên nên gửi interestNames là tiện nhất.
 */
async function setInterests(userId, { interestIds = [], interestNames = [] }) {
  const ids = new Set(interestIds);
  if (interestNames.length) {
    const all = await db.query('SELECT id, name FROM interests');
    const byName = new Map(all.map((r) => [options.norm(r.name), Number(r.id)]));
    const unknown = [];
    for (const name of interestNames) {
      const id = byName.get(options.norm(name));
      if (id) ids.add(id);
      else unknown.push(name);
    }
    if (unknown.length) throw ApiError.badRequest(`Sở thích không tồn tại: ${unknown.join(', ')}`);
  }
  if (ids.size > options.LIMITS.interests.max) {
    throw ApiError.badRequest(`Tối đa ${options.LIMITS.interests.max} sở thích`);
  }
  if (ids.size) {
    const found = await db.query('SELECT id FROM interests WHERE id IN (?)', [[...ids]]);
    if (found.length !== ids.size) throw ApiError.badRequest('Có sở thích không tồn tại');
  }
  await db.transaction(async (conn) => {
    await conn.query('DELETE FROM user_interests WHERE user_id = ?', [userId]);
    if (ids.size) {
      await conn.query('INSERT INTO user_interests (user_id, interest_id) VALUES ?', [[...ids].map((id) => [userId, id])]);
    }
  });
  return getMe(userId);
}

async function addPhoto(userId, file) {
  const removeFile = () => fs.unlink(file.path).catch(() => {});
  const [{ total }] = await db.query('SELECT COUNT(*) AS total FROM photos WHERE user_id = ?', [userId]);
  if (total >= MAX_PHOTOS) {
    await removeFile();
    throw ApiError.badRequest(`Tối đa ${MAX_PHOTOS} ảnh`);
  }
  const [{ nextPos }] = await db.query(
    'SELECT COALESCE(MAX(position) + 1, 0) AS nextPos FROM photos WHERE user_id = ?',
    [userId],
  );
  const url = `/uploads/${file.filename}`;
  const result = await db.query('INSERT INTO photos (user_id, url, position) VALUES (?, ?, ?)', [userId, url, nextPos]);
  return { id: Number(result.insertId), url: toPhotoUrl(url), position: Number(nextPos) };
}

async function deletePhoto(userId, photoId) {
  const [photo] = await db.query('SELECT id, url FROM photos WHERE id = ? AND user_id = ?', [photoId, userId]);
  if (!photo) throw ApiError.notFound('Không tìm thấy ảnh');

  await db.query('DELETE FROM photos WHERE id = ?', [photoId]);
  if (photo.url.startsWith('/uploads/')) {
    await fs.unlink(path.join(UPLOAD_DIR, path.basename(photo.url))).catch(() => {});
  }
  // Đánh số lại thứ tự ảnh (0 = ảnh đại diện)
  const rest = await db.query('SELECT id FROM photos WHERE user_id = ? ORDER BY position, id', [userId]);
  await Promise.all(rest.map((p, i) => db.query('UPDATE photos SET position = ? WHERE id = ?', [i, p.id])));
}

/**
 * POST /users/me/onboarding/complete - màn hình "Hồ sơ đã sẵn sàng" gọi khi xong các bước.
 * Thiếu thông tin bắt buộc -> 400 kèm danh sách còn thiếu. Lần đầu hoàn tất: đặt độ tuổi tìm kiếm
 * mặc định quanh tuổi người dùng (±5) vì app không có màn hình chọn độ tuổi.
 */
async function completeOnboarding(userId) {
  const me = await getMe(userId);
  if (me.onboarding.missing.length) {
    throw new ApiError(
      400,
      'Hồ sơ chưa đủ thông tin',
      'PROFILE_INCOMPLETE',
      me.onboarding.missing.map((field) => ({ field, message: COMPLETION_MESSAGES[field] })),
    );
  }
  if (!me.onboarding.completed) {
    await db.query('UPDATE profiles SET completed_at = UTC_TIMESTAMP() WHERE user_id = ?', [userId]);
    const age = me.age;
    await db.query('UPDATE preferences SET min_age = ?, max_age = ? WHERE user_id = ?', [
      Math.max(18, age - 5),
      Math.min(100, Math.max(age + 5, 23)),
      userId,
    ]);
  }
  return getMe(userId);
}

const COMPLETION_MESSAGES = {
  displayName: 'Chưa nhập tên',
  birthday: 'Chưa nhập ngày sinh',
  gender: 'Chưa chọn giới tính',
  photos: `Cần ít nhất ${env.onboardingMinPhotos} ảnh`,
  interests: `Cần chọn ít nhất ${options.LIMITS.interests.min} sở thích`,
};

module.exports = {
  PUBLIC_PROFILE_COLUMNS,
  completeOnboarding,
  loadExtras,
  toPublicProfile,
  getMe,
  getPublicProfile,
  isBlockedBetween,
  updateProfile,
  updatePreferences,
  listInterests,
  setInterests,
  addPhoto,
  deletePhoto,
};
