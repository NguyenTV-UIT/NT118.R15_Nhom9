/**
 * THÔNG BÁO ĐẨY (push notification) tới điện thoại khi người nhận đang KHÔNG mở app
 * (đang mở app thì đã nhận qua WebSocket).
 *
 *  PUSH_PROVIDER=console (mặc định): chỉ ghi log "[push] ..." - chưa gửi thật.
 *  Gửi thật bằng Firebase Cloud Messaging: thêm nhánh 'fcm' trong deliver() dùng thư viện firebase-admin
 *  (cần file service-account của project Firebase), app gọi POST /users/me/device-token để đăng ký token.
 *
 * Tôn trọng công tắc "Thông báo đẩy" trong Cài đặt (users.push_enabled).
 */
const env = require('../config/env');
const db = require('../config/db');

async function deliver(tokens, notification) {
  switch (env.pushProvider) {
    case 'console':
      console.log(`[push] -> ${tokens.length} thiết bị | ${notification.title}: ${notification.body}`);
      return;
    default:
      throw new Error(`PUSH_PROVIDER "${env.pushProvider}" chưa được hỗ trợ (xem src/services/push.js)`);
  }
}

/**
 * Gửi thông báo cho 1 người. Không bao giờ ném lỗi (push hỏng không được làm hỏng API chính).
 * notification: { type, title, body, data }
 */
async function notify(userId, notification) {
  try {
    const rows = await db.query(
      `SELECT d.token FROM device_tokens d JOIN users u ON u.id = d.user_id
        WHERE d.user_id = ? AND u.push_enabled = 1`,
      [userId],
    );
    if (!rows.length) return 0;
    await deliver(rows.map((r) => r.token), notification);
    return rows.length;
  } catch (err) {
    console.error('[push]', err.message);
    return 0;
  }
}

module.exports = { notify };
