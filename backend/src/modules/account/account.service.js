/**
 * TÀI KHOẢN: cài đặt (màn Settings), đổi email, xoá tài khoản, đăng ký thiết bị nhận thông báo đẩy.
 */
const crypto = require('crypto');
const fs = require('fs/promises');
const path = require('path');
const jwt = require('jsonwebtoken');

const env = require('../../config/env');
const db = require('../../config/db');
const ApiError = require('../../utils/ApiError');
const otpService = require('../../services/otp.service');
const mail = require('../../services/mail');
const hub = require('../../realtime/hub');
const { UPLOAD_DIR } = require('../../middlewares/upload');

const sha256 = (s) => crypto.createHash('sha256').update(s).digest('hex');

// ═════════════════════ CÀI ĐẶT ═════════════════════

/** GET /users/me/settings - mọi thứ màn Cài đặt cần hiển thị (trừ danh sách thiết bị: GET /users/me/sessions). */
async function getSettings(userId) {
  const [row] = await db.query(
    `SELECT u.email, u.email_verified_at, u.password_changed_at, u.created_at, u.push_enabled,
            p.show_active_status, p.show_distance,
            (SELECT COUNT(*) FROM blocks b WHERE b.blocker_id = u.id) AS blocked_count,
            (SELECT COUNT(*) FROM sessions s WHERE s.user_id = u.id AND s.revoked_at IS NULL) AS device_count
       FROM users u JOIN profiles p ON p.user_id = u.id
      WHERE u.id = ?`,
    [userId],
  );
  if (!row) throw ApiError.notFound('Không tìm thấy người dùng');
  return {
    email: row.email,
    emailMasked: row.email ? mail.maskEmail(row.email) : null, // minh***@gmail.com
    emailVerified: Boolean(row.email_verified_at),
    passwordChangedAt: row.password_changed_at || row.created_at, // "Đổi lần cuối ..."
    pushEnabled: Boolean(Number(row.push_enabled)),
    showActiveStatus: Boolean(Number(row.show_active_status)),
    showDistance: Boolean(Number(row.show_distance)),
    blockedCount: Number(row.blocked_count),
    deviceCount: Number(row.device_count),
  };
}

/** PUT /users/me/settings - bật/tắt từng công tắc (gửi công tắc nào đổi công tắc đó). */
async function updateSettings(userId, { pushEnabled, showActiveStatus, showDistance }) {
  if (pushEnabled !== undefined) await db.query('UPDATE users SET push_enabled = ? WHERE id = ?', [pushEnabled, userId]);
  const sets = [];
  const values = [];
  if (showActiveStatus !== undefined) {
    sets.push('show_active_status = ?');
    values.push(showActiveStatus);
  }
  if (showDistance !== undefined) {
    sets.push('show_distance = ?');
    values.push(showDistance);
  }
  if (sets.length) await db.query(`UPDATE profiles SET ${sets.join(', ')} WHERE user_id = ?`, [...values, userId]);
  return getSettings(userId);
}

// ═════════════════════ ĐỔI EMAIL ═════════════════════
// VerifyCurrentEmail (nhập email hiện tại) -> OTP gửi tới email hiện tại -> ChangeEmail (nhập email mới) -> xong.
// Bật EMAIL_CHANGE_VERIFY_NEW=true thì thêm 1 bước OTP gửi tới email MỚI trước khi đổi (FLOW_CHANGE_EMAIL).

const changeSecret = () => `${env.jwt.accessSecret}:change_email`;

async function currentEmail(userId) {
  const [user] = await db.query('SELECT email FROM users WHERE id = ?', [userId]);
  if (!user?.email) throw ApiError.badRequest('Tài khoản chưa có email');
  return user.email;
}

/** POST /users/me/email/verify-current - bước 1: xác nhận email hiện tại, gửi (hoặc gửi lại) OTP tới email đó. */
async function sendCurrentEmailOtp(userId, { email }) {
  const current = await currentEmail(userId);
  if (email !== current.toLowerCase()) throw ApiError.badRequest('Email không khớp với email hiện tại của tài khoản');
  return otpService.send(current, 'change_email');
}

/** POST /users/me/email/verify-otp - bước 2: đúng OTP -> emailChangeToken (10 phút) cho màn nhập email mới. */
async function verifyCurrentEmailOtp(userId, { otp }) {
  const current = await currentEmail(userId);
  await otpService.verify(current, 'change_email', otp);
  await db.query('UPDATE users SET email_verified_at = COALESCE(email_verified_at, UTC_TIMESTAMP()) WHERE id = ?', [userId]);
  const emailChangeToken = jwt.sign({ sub: String(userId), em: sha256(current) }, changeSecret(), { expiresIn: '10m' });
  return { emailChangeToken, expiresIn: 600 };
}

function readChangeToken(userId, token, current) {
  let payload;
  try {
    payload = jwt.verify(token, changeSecret());
  } catch {
    throw ApiError.badRequest('Phiên đổi email không hợp lệ hoặc đã hết hạn, vui lòng xác thực lại');
  }
  // Token chỉ dùng cho đúng tài khoản và đúng email lúc xác thực (đổi xong thì token cũ vô hiệu)
  if (Number(payload.sub) !== Number(userId) || payload.em !== sha256(current)) {
    throw ApiError.badRequest('Phiên đổi email đã được sử dụng, vui lòng xác thực lại');
  }
  return payload;
}

async function applyNewEmail(userId, oldEmail, newEmail, verified) {
  try {
    await db.query('UPDATE users SET email = ?, email_verified_at = IF(?, UTC_TIMESTAMP(), NULL) WHERE id = ?', [
      newEmail,
      verified ? 1 : 0,
      userId,
    ]);
  } catch (err) {
    if (err.code === 'ER_DUP_ENTRY') throw ApiError.conflict('Email đã được sử dụng');
    throw err;
  }
  // Báo cho email cũ biết (phòng trường hợp tài khoản bị người khác đổi email)
  mail
    .send(
      oldEmail,
      '[DateZone] Email tài khoản của bạn đã được thay đổi',
      `Xin chào,\n\nEmail đăng nhập tài khoản DateZone của bạn vừa được đổi thành ${mail.maskEmail(newEmail)}.\n` +
        'Nếu không phải bạn thực hiện, hãy đặt lại mật khẩu ngay.\n\nDateZone',
    )
    .catch((err) => console.error('[mail]', err.message));
  return { email: newEmail, emailMasked: mail.maskEmail(newEmail), emailVerified: Boolean(verified) };
}

/**
 * PUT /users/me/email - bước 3: đổi sang email mới.
 * Mặc định đổi ngay (giống app). EMAIL_CHANGE_VERIFY_NEW=true: gửi OTP tới email mới, trả
 * { otpRequired: true, pendingToken } -> app gọi POST /users/me/email/confirm.
 */
async function changeEmail(userId, { emailChangeToken, newEmail }) {
  const current = await currentEmail(userId);
  readChangeToken(userId, emailChangeToken, current);
  if (newEmail === current.toLowerCase()) throw ApiError.badRequest('Email mới phải khác email hiện tại');
  const [taken] = await db.query('SELECT id FROM users WHERE email = ?', [newEmail]);
  if (taken) throw ApiError.conflict('Email đã được sử dụng');

  if (env.emailChangeVerifyNew) {
    const sent = await otpService.send(newEmail, 'change_email_new');
    const pendingToken = jwt.sign({ sub: String(userId), em: sha256(current), ne: newEmail }, changeSecret(), { expiresIn: '10m' });
    return { otpRequired: true, pendingToken, ...sent };
  }
  return { otpRequired: false, ...(await applyNewEmail(userId, current, newEmail, false)) };
}

/** POST /users/me/email/confirm - (khi bật xác thực email mới) nhập OTP gửi tới email mới -> đổi email. */
async function confirmNewEmail(userId, { pendingToken, otp }) {
  const current = await currentEmail(userId);
  const payload = readChangeToken(userId, pendingToken, current);
  if (!payload.ne) throw ApiError.badRequest('Phiên đổi email không hợp lệ, vui lòng thực hiện lại');
  await otpService.verify(payload.ne, 'change_email_new', otp);
  return applyNewEmail(userId, current, payload.ne, true);
}

// ═════════════════════ XOÁ TÀI KHOẢN ═════════════════════

const CONFIRM_WORD = 'XÓA';
const normalizeConfirm = (s) => String(s || '').normalize('NFC').trim().toUpperCase();

/**
 * DELETE /users/me - xoá vĩnh viễn: hồ sơ, ảnh, lượt thích, tương hợp, tin nhắn (không khôi phục được).
 * Body: { confirm: "XÓA", reason?, details? } - người dùng phải gõ chữ XÓA như trên màn DeleteAccount.
 */
async function deleteAccount(userId, { confirm, reason, details }) {
  if (normalizeConfirm(confirm) !== CONFIRM_WORD) {
    throw ApiError.badRequest(`Vui lòng nhập ${CONFIRM_WORD} để xác nhận xoá tài khoản`);
  }
  // Gom file cần xoá trước khi dữ liệu biến mất: ảnh hồ sơ + ảnh trong mọi cuộc trò chuyện của người này
  const photos = await db.query('SELECT url FROM photos WHERE user_id = ?', [userId]);
  const chatImages = await db.query(
    `SELECT msg.content AS url FROM messages msg JOIN matches m ON m.id = msg.match_id
      WHERE msg.type = 'image' AND (m.user1_id = ? OR m.user2_id = ?)`,
    [userId, userId],
  );
  const partners = await db.query(
    `SELECT id, IF(user1_id = ?, user2_id, user1_id) AS partner_id FROM matches
      WHERE (user1_id = ? OR user2_id = ?) AND unmatched_at IS NULL`,
    [userId, userId, userId],
  );

  await db.transaction(async (conn) => {
    await conn.query('INSERT INTO account_deletions (reason, details) VALUES (?, ?)', [reason || null, details || null]);
    // ON DELETE CASCADE xoá toàn bộ dữ liệu liên quan: hồ sơ, ảnh, sở thích, lượt quẹt, tương hợp, tin nhắn,
    // cuộc gọi, chặn, báo cáo, phiên đăng nhập, token...
    await conn.query('DELETE FROM users WHERE id = ?', [userId]);
  });

  for (const { url } of [...photos, ...chatImages]) {
    if (typeof url === 'string' && url.startsWith('/uploads/')) {
      await fs.unlink(path.join(UPLOAD_DIR, url.slice('/uploads/'.length))).catch(() => {});
    }
  }
  for (const p of partners) hub.sendToUser(p.partner_id, 'match.removed', { matchId: Number(p.id), userId: Number(userId) });
  hub.disconnectUser(userId);
}

// ═════════════════════ THIẾT BỊ NHẬN THÔNG BÁO ĐẨY ═════════════════════

/** POST /users/me/device-token - app gửi FCM token sau khi đăng nhập / khi token đổi. */
async function registerDeviceToken(userId, { token, platform }) {
  await db.query(
    `INSERT INTO device_tokens (user_id, token, platform) VALUES (?, ?, ?)
     ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), platform = VALUES(platform)`,
    [userId, token, platform],
  );
}

/** DELETE /users/me/device-token - gọi khi đăng xuất để máy này không nhận thông báo của tài khoản nữa. */
async function removeDeviceToken(userId, { token }) {
  await db.query('DELETE FROM device_tokens WHERE user_id = ? AND token = ?', [userId, token]);
}

module.exports = {
  getSettings,
  updateSettings,
  sendCurrentEmailOtp,
  verifyCurrentEmailOtp,
  changeEmail,
  confirmNewEmail,
  deleteAccount,
  registerDeviceToken,
  removeDeviceToken,
};
