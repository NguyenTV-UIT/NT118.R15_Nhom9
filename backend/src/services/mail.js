/**
 * GỬI EMAIL - dùng cho mã OTP (quên mật khẩu, sau này: xác thực / đổi email).
 *
 *  MAIL_PROVIDER=console (mặc định): in email ra log server (pm2 logs dating-api), không gửi thật.
 *  MAIL_PROVIDER=smtp: gửi thật qua SMTP. Với Gmail:
 *     1. Bật xác minh 2 bước cho tài khoản Gmail
 *     2. Google Account -> Security -> App passwords -> tạo mật khẩu ứng dụng (16 ký tự)
 *     3. .env: SMTP_USER=<gmail>, SMTP_PASS=<app password>, MAIL_FROM="DateZone <gmail>"
 *     4. pm2 restart dating-api  (Gmail miễn phí giới hạn ~500 email/ngày, đủ cho đồ án)
 */
const env = require('../config/env');

let transporter;
function getTransporter() {
  if (!transporter) {
    // eslint-disable-next-line global-require
    const nodemailer = require('nodemailer');
    transporter = nodemailer.createTransport({
      host: env.mail.host,
      port: env.mail.port,
      secure: env.mail.port === 465,
      auth: { user: env.mail.user, pass: env.mail.pass },
    });
  }
  return transporter;
}

async function send(to, subject, text) {
  switch (env.mail.provider) {
    case 'console':
      console.log(`[mail] -> ${to} | ${subject}\n${text}`);
      return;
    case 'smtp':
      await getTransporter().sendMail({ from: env.mail.from, to, subject, text });
      return;
    default:
      throw new Error(`MAIL_PROVIDER "${env.mail.provider}" chưa được hỗ trợ (console | smtp)`);
  }
}

/** an.nguyen@gmail.com -> an*****@gmail.com */
function maskEmail(email) {
  const [name, domain] = String(email).split('@');
  if (!domain) return email;
  return `${name.slice(0, 2)}${'*'.repeat(Math.max(1, name.length - 2))}@${domain}`;
}

module.exports = { send, maskEmail };
