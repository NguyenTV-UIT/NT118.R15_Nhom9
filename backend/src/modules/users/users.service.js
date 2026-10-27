const fs = require('fs/promises');
const path = require('path');

const env = require('../../config/env');
const db = require('../../config/db');
const ApiError = require('../../utils/ApiError');
const { ageFromBirthday } = require('../../utils/age');
const { UPLOAD_DIR } = require('../../middlewares/upload');

const MAX_PHOTOS = 6;

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

/** Hồ sơ hiển thị cho người khác xem (không có email, ngày sinh, toạ độ). */
function toPublicProfile(row, extras) {
  const id = Number(row.id ?? row.user_id);
  const photos = extras.photos.get(id) || [];
  return {
    id,
    displayName: row.display_name,
    age: ageFromBirthday(row.birthday),
    gender: row.gender,
    bio: row.bio,
    jobTitle: row.job_title,
    school: row.school,
    city: row.city,
    distanceKm: row.distance_km == null ? null : Math.max(1, Math.round(Number(row.distance_km))),
    avatarUrl: photos[0]?.url || null,
    photos,
    interests: extras.interests.get(id) || [],
  };
}

/** Hồ sơ đầy đủ của chính mình. */
async function getMe(userId) {
  const [row] = await db.query(
    `SELECT u.id, u.email, u.role, u.created_at,
            p.display_name, p.birthday, p.gender, p.bio, p.job_title, p.school, p.city, p.latitude, p.longitude,
            pr.interested_in, pr.min_age, pr.max_age, pr.max_distance_km
       FROM users u
       JOIN profiles p ON p.user_id = u.id
       LEFT JOIN preferences pr ON pr.user_id = u.id
      WHERE u.id = ?`,
    [userId],
  );
  if (!row) throw ApiError.notFound('Không tìm thấy người dùng');

  const extras = await loadExtras([userId]);
  return {
    ...toPublicProfile(row, extras),
    email: row.email,
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
    `SELECT u.id, p.display_name, p.birthday, p.gender, p.bio, p.job_title, p.school, p.city,
            CASE WHEN p.latitude IS NULL OR me.latitude IS NULL THEN NULL
                 ELSE ST_Distance_Sphere(POINT(p.longitude, p.latitude), POINT(me.longitude, me.latitude)) / 1000
            END AS distance_km
       FROM users u
       JOIN profiles p ON p.user_id = u.id
       LEFT JOIN profiles me ON me.user_id = ?
      WHERE u.id = ? AND u.status = 'active'`,
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

async function setInterests(userId, interestIds) {
  const ids = [...new Set(interestIds)];
  if (ids.length) {
    const found = await db.query('SELECT id FROM interests WHERE id IN (?)', [ids]);
    if (found.length !== ids.length) throw ApiError.badRequest('Có sở thích không tồn tại');
  }
  await db.transaction(async (conn) => {
    await conn.query('DELETE FROM user_interests WHERE user_id = ?', [userId]);
    if (ids.length) {
      await conn.query('INSERT INTO user_interests (user_id, interest_id) VALUES ?', [ids.map((id) => [userId, id])]);
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

module.exports = {
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
