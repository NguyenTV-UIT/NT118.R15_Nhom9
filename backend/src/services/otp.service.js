/**
 * MÃ OTP 6 SỐ gửi qua EMAIL hoặc SMS (target có "@" -> email, ngược lại -> SMS).
 * Mục đích: reset_password (quên mật khẩu qua email - đang dùng);
 *           register / login (đăng ký / đăng nhập bằng SĐT - đang tạm ngừng).
 *
 * - Mã lưu dạng băm SHA-256 (không lưu mã gốc), hết hạn sau OTP_TTL_SECONDS (mặc định 5 phút)
 * - Gửi lại sau OTP_RESEND_SECONDS (30 giây, khớp đồng hồ đếm ngược trên app)
 * - Tối đa OTP_MAX_PER_HOUR lần gửi / số / mục đích / giờ
 * - Sai quá 5 lần thì mã bị huỷ, phải gửi mã mới
 * - Gửi mã mới thì mã cũ (cùng mục đích) hết hiệu lực
 */
const crypto = require('crypto');
const env = require('../config/env');
const db = require('../config/db');
const ApiError = require('../utils/ApiError');
const sms = require('./sms');
const mail = require('./mail');

const isEmail = (target) => String(target).includes('@');

const PURPOSE_TEXT = {
  register: 'đăng ký tài khoản',
  login: 'đăng nhập',
  reset_password: 'đặt lại mật khẩu',
};

const hashCode = (target, purpose, code) => crypto.createHash('sha256').update(`${target}:${purpose}:${code}`).digest('hex');

/** Tạo & gửi OTP. Trả về thông tin cho app (kèm devOtp khi bật OTP_DEV_MODE). */
async function send(target, purpose) {
  const [last] = await db.query(
    `SELECT TIMESTAMPDIFF(SECOND, created_at, UTC_TIMESTAMP()) AS age
       FROM otp_codes WHERE target = ? AND purpose = ? ORDER BY id DESC LIMIT 1`,
    [target, purpose],
  );
  if (last && Number(last.age) < env.otp.resendSeconds) {
    const wait = env.otp.resendSeconds - Number(last.age);
    throw new ApiError(429, `Vui lòng đợi ${wait} giây trước khi gửi lại mã`, 'OTP_TOO_SOON', [{ field: 'retryAfter', message: String(wait) }]);
  }
  const [{ sent }] = await db.query(
    `SELECT COUNT(*) AS sent FROM otp_codes
      WHERE target = ? AND purpose = ? AND created_at > UTC_TIMESTAMP() - INTERVAL 1 HOUR`,
    [target, purpose],
  );
  if (Number(sent) >= env.otp.maxPerHour) {
    throw new ApiError(429, 'Bạn đã yêu cầu quá nhiều mã OTP, vui lòng thử lại sau 1 giờ', 'OTP_LIMIT');
  }

  const code = String(crypto.randomInt(0, 1000000)).padStart(6, '0');
  // Huỷ các mã cũ chưa dùng của cùng mục đích
  await db.query(
    'UPDATE otp_codes SET consumed_at = UTC_TIMESTAMP() WHERE target = ? AND purpose = ? AND consumed_at IS NULL',
    [target, purpose],
  );
  await db.query(
    `INSERT INTO otp_codes (target, purpose, code_hash, expires_at)
     VALUES (?, ?, ?, UTC_TIMESTAMP() + INTERVAL ? SECOND)`,
    [target, purpose, hashCode(target, purpose, code), env.otp.ttlSeconds],
  );
  const minutes = Math.round(env.otp.ttlSeconds / 60);
  if (isEmail(target)) {
    await mail.send(
      target,
      `[DateZone] Mã xác thực ${PURPOSE_TEXT[purpose]}: ${code}`,
      `Xin chào,\n\nMã xác thực ${PURPOSE_TEXT[purpose]} DateZone của bạn là: ${code}\n` +
        `Mã có hiệu lực trong ${minutes} phút. Không chia sẻ mã này cho bất kỳ ai.\n\n` +
        'Nếu bạn không yêu cầu, hãy bỏ qua email này.\n\nDateZone',
    );
  } else {
    await sms.send(target, `[DateZone] Ma xac thuc ${PURPOSE_TEXT[purpose]} cua ban la ${code}. Ma het han sau ${minutes} phut.`);
  }

  return {
    sentTo: isEmail(target) ? mail.maskEmail(target) : sms.maskPhone(target),
    expiresIn: env.otp.ttlSeconds,
    resendAfter: env.otp.resendSeconds,
    ...(env.otp.devMode ? { devOtp: code } : {}),
  };
}

/** Kiểm tra OTP; đúng thì đánh dấu đã dùng (mỗi mã chỉ dùng 1 lần). Sai thì ném lỗi 400. */
async function verify(target, purpose, code) {
  const [otp] = await db.query(
    `SELECT id, code_hash, attempts, (expires_at < UTC_TIMESTAMP()) AS expired
       FROM otp_codes WHERE target = ? AND purpose = ? AND consumed_at IS NULL
      ORDER BY id DESC LIMIT 1`,
    [target, purpose],
  );
  if (!otp) throw new ApiError(400, 'Mã OTP không tồn tại hoặc đã được sử dụng, vui lòng gửi lại mã', 'OTP_INVALID');
  if (Number(otp.expired)) throw new ApiError(400, 'Mã OTP đã hết hạn, vui lòng gửi lại mã', 'OTP_EXPIRED');

  const a = Buffer.from(otp.code_hash, 'hex');
  const b = Buffer.from(hashCode(target, purpose, String(code)), 'hex');
  if (!crypto.timingSafeEqual(a, b)) {
    const attempts = Number(otp.attempts) + 1;
    const left = env.otp.maxAttempts - attempts;
    await db.query(
      `UPDATE otp_codes SET attempts = ?, consumed_at = IF(? <= 0, UTC_TIMESTAMP(), NULL) WHERE id = ?`,
      [attempts, left, otp.id],
    );
    if (left <= 0) throw new ApiError(400, 'Nhập sai quá nhiều lần, vui lòng gửi lại mã mới', 'OTP_LOCKED');
    throw new ApiError(400, `Mã OTP không đúng, còn ${left} lần thử`, 'OTP_WRONG');
  }
  await db.query('UPDATE otp_codes SET consumed_at = UTC_TIMESTAMP() WHERE id = ?', [otp.id]);
}

/** Đã có OTP đăng nhập được cấp trong 10 phút gần đây chưa (tức đã nhập đúng mật khẩu) -> cho phép gửi lại. */
async function hasRecentLoginChallenge(target) {
  const rows = await db.query(
    `SELECT 1 FROM otp_codes WHERE target = ? AND purpose = 'login'
        AND created_at > UTC_TIMESTAMP() - INTERVAL 10 MINUTE LIMIT 1`,
    [target],
  );
  return rows.length > 0;
}

module.exports = { send, verify, hasRecentLoginChallenge };
