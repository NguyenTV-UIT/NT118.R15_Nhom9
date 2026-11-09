/**
 * GỬI SMS - tách riêng để sau này đổi nhà cung cấp mà không sửa code OTP.
 *
 * Hiện có: provider "console" = in tin nhắn ra log server (pm2 logs dating-api).
 * Muốn gửi SMS thật: thêm 1 nhánh trong send() gọi API của nhà cung cấp
 * (VD eSMS.vn, SpeedSMS, Twilio - đều tính phí theo tin nhắn), rồi đặt SMS_PROVIDER trong .env.
 */
const env = require('../config/env');
const { maskPhone } = require('../utils/phone');

async function send(phone, message) {
  switch (env.otp.provider) {
    case 'console':
      console.log(`[sms] -> ${phone}: ${message}`);
      return;
    default:
      throw new Error(`SMS_PROVIDER "${env.otp.provider}" chưa được hỗ trợ (xem src/services/sms.js)`);
  }
}

module.exports = { send, maskPhone };
