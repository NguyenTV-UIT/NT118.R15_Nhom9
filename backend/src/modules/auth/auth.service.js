const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');

const env = require('../../config/env');
const db = require('../../config/db');
const ApiError = require('../../utils/ApiError');
const usersService = require('../users/users.service');
const otpService = require('../../services/otp.service');
const sessions = require('../sessions/sessions.service');

const sha256 = (s) => crypto.createHash('sha256').update(s).digest('hex');

function signAccessToken(user, sessionId) {
  return jwt.sign({ sub: String(user.id), role: user.role, sid: sessionId }, env.jwt.accessSecret, {
    expiresIn: env.jwt.accessExpiresIn,
  });
}

/** Hàm chạy SQL trả về rows / result cho cả db (pool) lẫn connection đang trong transaction. */
const runner = (conn) => (conn === db ? db.query : async (sql, params) => (await conn.query(sql, params))[0]);

/**
 * Cấp cặp token. Không truyền sessionId -> tạo phiên đăng nhập mới cho thiết bị `device`
 * (đăng nhập / đăng ký); có sessionId -> giữ nguyên phiên (refresh).
 */
async function issueTokens(user, { conn = db, sessionId = null, device = {} } = {}) {
  const run = runner(conn);
  const sid = sessionId || (await sessions.create(run, user.id, device));
  const refreshToken = crypto.randomBytes(48).toString('hex');
  const expiresAt = new Date(Date.now() + env.jwt.refreshTokenDays * 24 * 3600 * 1000);
  await run('INSERT INTO refresh_tokens (user_id, session_id, token_hash, expires_at) VALUES (?, ?, ?, ?)', [
    user.id,
    sid,
    sha256(refreshToken),
    expiresAt,
  ]);
  return {
    accessToken: signAccessToken(user, sid),
    refreshToken,
    tokenType: 'Bearer',
    expiresIn: env.jwt.accessExpiresIn,
    sessionId: sid,
  };
}

// ═════════════════════ EMAIL (đang dùng) ═════════════════════

/**
 * POST /auth/register/send-otp - (tuỳ chọn) gửi OTP xác thực email trước khi đăng ký.
 * Màn Register -> màn OTP (FLOW_REGISTER). Bắt buộc khi REGISTER_OTP_REQUIRED=true.
 */
async function sendRegisterOtp({ email }) {
  const [existing] = await db.query('SELECT id FROM users WHERE email = ?', [email]);
  if (existing) throw ApiError.conflict('Email đã được sử dụng');
  return otpService.send(email, 'register');
}

/**
 * POST /auth/register - chỉ cần email + mật khẩu (+ otp nếu bật REGISTER_OTP_REQUIRED hoặc app tự gửi kèm).
 * Tạo tài khoản + HỒ SƠ TRỐNG + tiêu chí mặc định, trả token để app đi tiếp các màn hình onboarding
 * (tên, ảnh, ngày sinh, giới tính... điền bằng PUT /users/me/profile, xong gọi POST /users/me/onboarding/complete).
 */
async function register({ email, password, otp }, device) {
  const [existing] = await db.query('SELECT id FROM users WHERE email = ?', [email]);
  if (existing) throw ApiError.conflict('Email đã được sử dụng');

  if (env.registerOtpRequired && !otp) {
    throw new ApiError(400, 'Vui lòng nhập mã OTP đã gửi tới email', 'OTP_REQUIRED', [
      { field: 'otp', message: 'Gọi POST /auth/register/send-otp để nhận mã' },
    ]);
  }
  if (otp) await otpService.verify(email, 'register', otp);

  const passwordHash = await bcrypt.hash(password, 10);
  const user = await db.transaction(async (conn) => {
    const [result] = await conn.query(
      'INSERT INTO users (email, email_verified_at, password_hash, password_changed_at) VALUES (?, IF(?, UTC_TIMESTAMP(), NULL), ?, UTC_TIMESTAMP())',
      [email, otp ? 1 : 0, passwordHash],
    );
    const userId = result.insertId;
    await conn.query('INSERT INTO profiles (user_id) VALUES (?)', [userId]);
    await conn.query('INSERT INTO preferences (user_id) VALUES (?)', [userId]);
    return { id: userId, role: 'user' };
  });

  const tokens = await issueTokens(user, { device });
  return { user: await usersService.getMe(user.id), tokens };
}

/** POST /auth/password/forgot - gửi (hoặc gửi lại sau 30 giây) mã OTP 6 số tới email. */
async function forgotPassword({ email }) {
  const [user] = await db.query('SELECT id, status FROM users WHERE email = ?', [email]);
  if (!user) throw ApiError.notFound('Email chưa được đăng ký');
  if (user.status !== 'active') throw ApiError.forbidden('Tài khoản đã bị khoá');
  return otpService.send(email, 'reset_password');
}

/** POST /auth/password/verify-otp - đúng mã -> resetToken (10 phút) cho màn hình Đặt lại mật khẩu. */
async function verifyResetOtpByEmail({ email, otp }) {
  const [user] = await db.query('SELECT id, password_hash FROM users WHERE email = ?', [email]);
  if (!user) throw ApiError.notFound('Email chưa được đăng ký');
  await otpService.verify(email, 'reset_password', otp);
  // Nhập đúng mã gửi tới email = email này có thật và thuộc về người dùng
  await db.query('UPDATE users SET email_verified_at = COALESCE(email_verified_at, UTC_TIMESTAMP()) WHERE id = ?', [user.id]);
  return issueResetToken(user);
}

// ═════════════════════ ĐẶT LẠI MẬT KHẨU (dùng chung) ═════════════════════

/** Token đặt lại mật khẩu dùng secret riêng -> không thể dùng thay access token. */
const resetSecret = () => `${env.jwt.accessSecret}:reset_password`;
const passwordFingerprint = (hash) => sha256(hash).slice(0, 16);

function issueResetToken(user) {
  const resetToken = jwt.sign(
    { sub: String(user.id), pwf: passwordFingerprint(user.password_hash) },
    resetSecret(),
    { expiresIn: '10m' },
  );
  return { resetToken, expiresIn: 600 };
}

// ═════════════════════ SỐ ĐIỆN THOẠI + OTP (TẠM NGỪNG - bật bằng PHONE_AUTH_ENABLED=true) ═════════════════════

async function findByPhone(phone) {
  const [user] = await db.query('SELECT id, role, status, password_hash FROM users WHERE phone = ?', [phone]);
  return user || null;
}

/**
 * POST /auth/otp/send - gửi (hoặc gửi lại) OTP.
 *  register       : số chưa được đăng ký
 *  login          : chỉ GỬI LẠI được sau khi đã nhập đúng mật khẩu ở /auth/login/phone (trong 10 phút)
 *  reset_password : số đã đăng ký
 */
async function sendOtp({ phone, purpose }) {
  const user = await findByPhone(phone);
  if (purpose === 'register' && user) throw ApiError.conflict('Số điện thoại đã được đăng ký');
  if (purpose !== 'register' && !user) throw ApiError.notFound('Số điện thoại chưa được đăng ký');
  if (user && user.status !== 'active') throw ApiError.forbidden('Tài khoản đã bị khoá');
  if (purpose === 'login' && !(await otpService.hasRecentLoginChallenge(phone))) {
    throw ApiError.badRequest('Phiên đăng nhập đã hết hạn, vui lòng nhập lại mật khẩu');
  }
  return otpService.send(phone, purpose);
}

/**
 * POST /auth/register/phone - màn hình OTP (luồng đăng ký) gọi sau khi người dùng nhập mã.
 * Tạo tài khoản + hồ sơ trống (chỉ có tên), trả token để app đi tiếp các màn hình onboarding.
 */
async function registerByPhone({ phone, otp, password, displayName }, device) {
  if (await findByPhone(phone)) throw ApiError.conflict('Số điện thoại đã được đăng ký');
  await otpService.verify(phone, 'register', otp);

  const passwordHash = await bcrypt.hash(password, 10);
  const user = await db.transaction(async (conn) => {
    const [result] = await conn.query(
      'INSERT INTO users (phone, phone_verified_at, password_hash, password_changed_at) VALUES (?, UTC_TIMESTAMP(), ?, UTC_TIMESTAMP())',
      [phone, passwordHash],
    );
    const userId = result.insertId;
    await conn.query('INSERT INTO profiles (user_id, display_name) VALUES (?, ?)', [userId, displayName]);
    await conn.query('INSERT INTO preferences (user_id) VALUES (?)', [userId]);
    return { id: userId, role: 'user' };
  });

  const tokens = await issueTokens(user, { device });
  return { user: await usersService.getMe(user.id), tokens };
}

/**
 * POST /auth/login/phone - bước 1: kiểm tra SĐT + mật khẩu.
 * LOGIN_OTP_REQUIRED=true (mặc định, giống app): gửi OTP, trả { otpRequired: true, ... } -> app chuyển màn OTP.
 * LOGIN_OTP_REQUIRED=false: trả luôn token.
 */
async function loginByPhone({ phone, password }, device) {
  const user = await findByPhone(phone);
  const valid = user && (await bcrypt.compare(password, user.password_hash));
  if (!valid) throw ApiError.unauthorized('Số điện thoại hoặc mật khẩu không đúng');
  if (user.status !== 'active') throw ApiError.forbidden('Tài khoản đã bị khoá');

  if (env.loginOtpRequired) {
    return { otpRequired: true, ...(await otpService.send(phone, 'login')) };
  }
  return { otpRequired: false, user: await usersService.getMe(user.id), tokens: await issueTokens(user, { device }) };
}

/** POST /auth/login/phone/verify - bước 2: nhập OTP -> token. */
async function verifyLoginOtp({ phone, otp }, device) {
  const user = await findByPhone(phone);
  if (!user) throw ApiError.notFound('Số điện thoại chưa được đăng ký');
  if (user.status !== 'active') throw ApiError.forbidden('Tài khoản đã bị khoá');
  await otpService.verify(phone, 'login', otp);
  return { user: await usersService.getMe(user.id), tokens: await issueTokens(user, { device }) };
}

/**
 * POST /auth/otp/verify - màn hình OTP (luồng quên mật khẩu): đúng mã -> resetToken (10 phút)
 * để màn hình Đặt lại mật khẩu gửi kèm mật khẩu mới.
 */
async function verifyResetOtp({ phone, otp }) {
  const user = await findByPhone(phone);
  if (!user) throw ApiError.notFound('Số điện thoại chưa được đăng ký');
  await otpService.verify(phone, 'reset_password', otp);
  return issueResetToken(user);
}

// ═════════════════════════════════════════════════════════════════

/** POST /auth/password/reset - đặt mật khẩu mới bằng resetToken (dùng 1 lần), đăng xuất mọi thiết bị. */
async function resetPassword({ resetToken, newPassword }) {
  let payload;
  try {
    payload = jwt.verify(resetToken, resetSecret());
  } catch {
    throw ApiError.badRequest('Phiên đặt lại mật khẩu không hợp lệ hoặc đã hết hạn, vui lòng thực hiện lại');
  }
  const [user] = await db.query('SELECT id, password_hash FROM users WHERE id = ?', [payload.sub]);
  // Mật khẩu đã đổi sau khi cấp token -> token đã được dùng
  if (!user || passwordFingerprint(user.password_hash) !== payload.pwf) {
    throw ApiError.badRequest('Phiên đặt lại mật khẩu đã được sử dụng, vui lòng thực hiện lại');
  }
  await db.query('UPDATE users SET password_hash = ?, password_changed_at = UTC_TIMESTAMP() WHERE id = ?', [
    await bcrypt.hash(newPassword, 10),
    user.id,
  ]);
  await sessions.revokeAll(user.id);
}

async function findByEmail(email) {
  const [user] = await db.query('SELECT id, role, status, password_hash FROM users WHERE email = ?', [email]);
  return user || null;
}

/**
 * POST /auth/login - email + mật khẩu -> token.
 * EMAIL_LOGIN_OTP_REQUIRED=true: gửi OTP tới email, trả { otpRequired: true } -> app chuyển màn OTP (FLOW_LOGIN)
 * rồi gọi POST /auth/login/verify-otp.
 */
async function login({ email, password }, device) {
  const user = await findByEmail(email);
  const valid = user && (await bcrypt.compare(password, user.password_hash));
  if (!valid) throw ApiError.unauthorized('Email hoặc mật khẩu không đúng');
  if (user.status !== 'active') throw ApiError.forbidden('Tài khoản đã bị khoá');

  if (env.emailLoginOtpRequired) {
    return { otpRequired: true, ...(await otpService.send(email, 'login')) };
  }
  const tokens = await issueTokens(user, { device });
  return { otpRequired: false, user: await usersService.getMe(user.id), tokens };
}

/** POST /auth/login/resend-otp - gửi lại OTP đăng nhập (chỉ khi vừa nhập đúng mật khẩu trong 10 phút). */
async function resendLoginOtp({ email }) {
  const user = await findByEmail(email);
  if (!user || !(await otpService.hasRecentLoginChallenge(email))) {
    throw ApiError.badRequest('Phiên đăng nhập đã hết hạn, vui lòng nhập lại mật khẩu');
  }
  return otpService.send(email, 'login');
}

/** POST /auth/login/verify-otp - bước 2 của đăng nhập có OTP. */
async function verifyEmailLoginOtp({ email, otp }, device) {
  const user = await findByEmail(email);
  if (!user) throw ApiError.unauthorized('Email hoặc mật khẩu không đúng');
  if (user.status !== 'active') throw ApiError.forbidden('Tài khoản đã bị khoá');
  await otpService.verify(email, 'login', otp);
  await db.query('UPDATE users SET email_verified_at = COALESCE(email_verified_at, UTC_TIMESTAMP()) WHERE id = ?', [user.id]);
  const tokens = await issueTokens(user, { device });
  return { user: await usersService.getMe(user.id), tokens };
}

/** Đổi refresh token cũ lấy cặp token mới (refresh token xoay vòng, dùng 1 lần, vẫn thuộc phiên cũ). */
async function refresh(refreshToken, device) {
  const hash = sha256(refreshToken);
  return db.transaction(async (conn) => {
    const [[row]] = await conn.query(
      `SELECT rt.id, rt.expires_at, rt.revoked_at, rt.session_id, s.revoked_at AS session_revoked_at,
              u.id AS user_id, u.role, u.status
         FROM refresh_tokens rt
         JOIN users u ON u.id = rt.user_id
         LEFT JOIN sessions s ON s.id = rt.session_id
        WHERE rt.token_hash = ? FOR UPDATE`,
      [hash],
    );
    if (!row || row.revoked_at || row.session_revoked_at || new Date(row.expires_at) < new Date()) {
      throw ApiError.unauthorized('Refresh token không hợp lệ hoặc đã hết hạn');
    }
    if (row.status !== 'active') throw ApiError.forbidden('Tài khoản đã bị khoá');

    await conn.query('UPDATE refresh_tokens SET revoked_at = UTC_TIMESTAMP() WHERE id = ?', [row.id]);
    if (row.session_id) {
      await conn.query('UPDATE sessions SET last_active_at = UTC_TIMESTAMP() WHERE id = ?', [row.session_id]);
    }
    // Token cấp trước bản v4 chưa có phiên -> tạo phiên cho thiết bị này
    return issueTokens({ id: row.user_id, role: row.role }, { conn, sessionId: row.session_id ? Number(row.session_id) : null, device });
  });
}

/** POST /auth/logout - thu hồi refresh token và đăng xuất phiên (thiết bị) tương ứng. */
async function logout(refreshToken) {
  const [row] = await db.query('SELECT id, user_id, session_id FROM refresh_tokens WHERE token_hash = ?', [sha256(refreshToken)]);
  if (!row) return;
  await db.query('UPDATE refresh_tokens SET revoked_at = UTC_TIMESTAMP() WHERE id = ? AND revoked_at IS NULL', [row.id]);
  if (row.session_id) await sessions.revoke(Number(row.user_id), Number(row.session_id)).catch(() => {});
}

/** PUT /auth/password - đổi mật khẩu; đăng xuất mọi thiết bị KHÁC, thiết bị đang dùng vẫn giữ đăng nhập. */
async function changePassword(user, { currentPassword, newPassword }) {
  const [row] = await db.query('SELECT password_hash FROM users WHERE id = ?', [user.id]);
  if (!row || !(await bcrypt.compare(currentPassword, row.password_hash))) {
    throw ApiError.badRequest('Mật khẩu hiện tại không đúng');
  }
  await db.query('UPDATE users SET password_hash = ?, password_changed_at = UTC_TIMESTAMP() WHERE id = ?', [
    await bcrypt.hash(newPassword, 10),
    user.id,
  ]);
  const loggedOut = await sessions.revokeAll(user.id, user.sessionId);
  return { loggedOutDevices: loggedOut };
}

module.exports = {
  issueTokens,
  sendRegisterOtp,
  register,
  login,
  resendLoginOtp,
  verifyEmailLoginOtp,
  forgotPassword,
  verifyResetOtpByEmail,
  sendOtp,
  registerByPhone,
  loginByPhone,
  verifyLoginOtp,
  verifyResetOtp,
  resetPassword,
  refresh,
  logout,
  changePassword,
};
