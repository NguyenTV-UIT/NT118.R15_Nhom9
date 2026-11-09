require('dotenv').config();

function bool(value, fallback) {
  if (value === undefined || value === '') return fallback;
  return ['1', 'true', 'yes', 'on'].includes(String(value).toLowerCase());
}

function required(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Thiếu biến môi trường ${name} (xem .env.example)`);
  }
  return value;
}

const env = {
  nodeEnv: process.env.NODE_ENV || 'development',
  port: Number(process.env.PORT || 3000),
  publicBaseUrl: (process.env.PUBLIC_BASE_URL || `http://localhost:${process.env.PORT || 3000}`).replace(/\/$/, ''),

  db: {
    host: process.env.DB_HOST || '127.0.0.1',
    port: Number(process.env.DB_PORT || 3306),
    user: required('DB_USER'),
    password: process.env.DB_PASSWORD || '',
    database: required('DB_NAME'),
  },

  jwt: {
    accessSecret: required('JWT_ACCESS_SECRET'),
    accessExpiresIn: process.env.JWT_ACCESS_EXPIRES_IN || '15m',
    refreshTokenDays: Number(process.env.REFRESH_TOKEN_DAYS || 30),
  },

  corsOrigins: (process.env.CORS_ORIGINS || '*').split(',').map((s) => s.trim()),

  // Đăng ký / đăng nhập bằng số điện thoại: TẠM NGỪNG (true để bật lại)
  phoneAuthEnabled: bool(process.env.PHONE_AUTH_ENABLED, false),

  mail: {
    // console = in email ra log server; smtp = gửi thật (VD Gmail + App Password). Xem src/services/mail.js
    provider: process.env.MAIL_PROVIDER || 'console',
    host: process.env.SMTP_HOST || 'smtp.gmail.com',
    port: Number(process.env.SMTP_PORT || 465),
    user: process.env.SMTP_USER || '',
    pass: process.env.SMTP_PASS || '',
    from: process.env.MAIL_FROM || process.env.SMTP_USER || 'DateZone <no-reply@datezone.local>',
  },

  otp: {
    // console = in mã OTP ra log server (chưa có nhà mạng SMS). Xem src/services/sms.js
    provider: process.env.SMS_PROVIDER || 'console',
    // true = trả mã OTP trong response (field devOtp) để test / demo. TẮT khi có SMS thật.
    devMode: bool(process.env.OTP_DEV_MODE, true),
    ttlSeconds: Number(process.env.OTP_TTL_SECONDS || 300),
    resendSeconds: Number(process.env.OTP_RESEND_SECONDS || 30), // khớp đồng hồ đếm ngược trên app
    maxAttempts: 5,
    maxPerHour: Number(process.env.OTP_MAX_PER_HOUR || 5),
  },
  // Đăng nhập bằng SĐT có cần bước OTP thứ 2 không (app hiện tại: có)
  loginOtpRequired: bool(process.env.LOGIN_OTP_REQUIRED, true),
  // Số ảnh tối thiểu để hoàn tất hồ sơ (app yêu cầu 2)
  onboardingMinPhotos: Number(process.env.ONBOARDING_MIN_PHOTOS ?? 2),
};

env.isProduction = env.nodeEnv === 'production';

module.exports = env;
