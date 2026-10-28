const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');

const env = require('../../config/env');
const db = require('../../config/db');
const ApiError = require('../../utils/ApiError');
const { ageFromBirthday } = require('../../utils/age');
const usersService = require('../users/users.service');

const sha256 = (s) => crypto.createHash('sha256').update(s).digest('hex');

function signAccessToken(user) {
  return jwt.sign({ sub: String(user.id), role: user.role }, env.jwt.accessSecret, {
    expiresIn: env.jwt.accessExpiresIn,
  });
}

async function issueTokens(user, conn = db) {
  const refreshToken = crypto.randomBytes(48).toString('hex');
  const expiresAt = new Date(Date.now() + env.jwt.refreshTokenDays * 24 * 3600 * 1000);
  await conn.query('INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES (?, ?, ?)', [
    user.id,
    sha256(refreshToken),
    expiresAt,
  ]);
  return {
    accessToken: signAccessToken(user),
    refreshToken,
    tokenType: 'Bearer',
    expiresIn: env.jwt.accessExpiresIn,
  };
}

/**
 * Tiêu chí tìm kiếm mặc định suy ra từ thông tin đăng ký:
 *  - interestedIn: người dùng chọn lúc đăng ký, không chọn thì 'everyone'
 *  - độ tuổi: quanh tuổi của người dùng (±5, tối thiểu 18)
 *  - khoảng cách: 50km
 */
function defaultPreferences(birthday, interestedIn) {
  const age = ageFromBirthday(birthday);
  return {
    interestedIn: interestedIn || 'everyone',
    minAge: Math.max(18, age - 5),
    maxAge: Math.min(100, Math.max(age + 5, 23)),
    maxDistanceKm: 50,
  };
}

async function register(data) {
  const { email, password, displayName, birthday, gender } = data;
  const [existing] = await db.query('SELECT id FROM users WHERE email = ?', [email]);
  if (existing) throw ApiError.conflict('Email đã được sử dụng');

  const passwordHash = await bcrypt.hash(password, 10);
  const pref = defaultPreferences(birthday, data.interestedIn);

  const user = await db.transaction(async (conn) => {
    const [result] = await conn.query('INSERT INTO users (email, password_hash) VALUES (?, ?)', [email, passwordHash]);
    const userId = result.insertId;
    await conn.query(
      `INSERT INTO profiles (user_id, display_name, birthday, gender, city, latitude, longitude)
       VALUES (?, ?, ?, ?, ?, ?, ?)`,
      [userId, displayName, birthday, gender, data.city || null, data.latitude ?? null, data.longitude ?? null],
    );
    await conn.query(
      'INSERT INTO preferences (user_id, interested_in, min_age, max_age, max_distance_km) VALUES (?, ?, ?, ?, ?)',
      [userId, pref.interestedIn, pref.minAge, pref.maxAge, pref.maxDistanceKm],
    );
    if (data.interestIds?.length) {
      // Bỏ qua id sở thích không tồn tại
      await conn.query(
        'INSERT IGNORE INTO user_interests (user_id, interest_id) SELECT ?, id FROM interests WHERE id IN (?)',
        [userId, [...new Set(data.interestIds)]],
      );
    }
    return { id: userId, role: 'user' };
  });

  const tokens = await issueTokens(user);
  return { user: await usersService.getMe(user.id), tokens };
}

async function login({ email, password }) {
  const [user] = await db.query('SELECT id, role, status, password_hash FROM users WHERE email = ?', [email]);
  const valid = user && (await bcrypt.compare(password, user.password_hash));
  if (!valid) throw ApiError.unauthorized('Email hoặc mật khẩu không đúng');
  if (user.status !== 'active') throw ApiError.forbidden('Tài khoản đã bị khoá');

  const tokens = await issueTokens(user);
  return { user: await usersService.getMe(user.id), tokens };
}

/** Đổi refresh token cũ lấy cặp token mới (refresh token xoay vòng, dùng 1 lần). */
async function refresh(refreshToken) {
  const hash = sha256(refreshToken);
  return db.transaction(async (conn) => {
    const [[row]] = await conn.query(
      `SELECT rt.id, rt.expires_at, rt.revoked_at, u.id AS user_id, u.role, u.status
         FROM refresh_tokens rt JOIN users u ON u.id = rt.user_id
        WHERE rt.token_hash = ? FOR UPDATE`,
      [hash],
    );
    if (!row || row.revoked_at || new Date(row.expires_at) < new Date()) {
      throw ApiError.unauthorized('Refresh token không hợp lệ hoặc đã hết hạn');
    }
    if (row.status !== 'active') throw ApiError.forbidden('Tài khoản đã bị khoá');

    await conn.query('UPDATE refresh_tokens SET revoked_at = UTC_TIMESTAMP() WHERE id = ?', [row.id]);
    return issueTokens({ id: row.user_id, role: row.role }, conn);
  });
}

async function logout(refreshToken) {
  await db.query('UPDATE refresh_tokens SET revoked_at = UTC_TIMESTAMP() WHERE token_hash = ? AND revoked_at IS NULL', [
    sha256(refreshToken),
  ]);
}

async function changePassword(userId, { currentPassword, newPassword }) {
  const [user] = await db.query('SELECT password_hash FROM users WHERE id = ?', [userId]);
  if (!user || !(await bcrypt.compare(currentPassword, user.password_hash))) {
    throw ApiError.badRequest('Mật khẩu hiện tại không đúng');
  }
  await db.query('UPDATE users SET password_hash = ? WHERE id = ?', [await bcrypt.hash(newPassword, 10), userId]);
  // Đăng xuất mọi thiết bị khác
  await db.query('UPDATE refresh_tokens SET revoked_at = UTC_TIMESTAMP() WHERE user_id = ? AND revoked_at IS NULL', [userId]);
}

module.exports = { register, login, refresh, logout, changePassword };
