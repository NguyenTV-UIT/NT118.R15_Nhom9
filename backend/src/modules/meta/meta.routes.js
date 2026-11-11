const router = require('express').Router();
const h = require('../../utils/asyncHandler');
const db = require('../../config/db');
const { ok } = require('../../utils/response');
const env = require('../../config/env');
const options = require('./options');

/**
 * GET /api/meta/options - danh mục lựa chọn cho các màn hình onboarding / bộ lọc (không cần đăng nhập).
 * App dùng để hiển thị nhãn tiếng Việt và gửi mã lên server.
 */
router.get(
  '/options',
  h(async (req, res) => {
    const interests = await db.query('SELECT id, name FROM interests');
    const order = new Map(options.APP_INTERESTS.map((n, i) => [n, i]));
    interests.sort((a, b) => (order.get(a.name) ?? 999) - (order.get(b.name) ?? 999) || a.name.localeCompare(b.name, 'vi'));
    ok(res, {
      ...options.asJson(),
      interests: interests.map((r) => ({ id: Number(r.id), name: r.name, inApp: order.has(r.name) })),
      limits: options.LIMITS,
      // Cấu hình luồng đăng ký / đăng nhập để app biết có phải hiện màn nhập OTP không
      auth: {
        registerOtpRequired: env.registerOtpRequired,
        emailLoginOtpRequired: env.emailLoginOtpRequired,
        phoneAuthEnabled: env.phoneAuthEnabled,
        emailChangeVerifyNew: env.emailChangeVerifyNew,
      },
      // Mốc độ thân thiết trong chat
      chat: {
        intimacyMax: env.chat.intimacyCall,
        photoUnlockAt: env.chat.intimacyPhoto,
        callUnlockAt: env.chat.intimacyCall,
        maxMessageLength: env.chat.maxMessageLength,
        callRingSeconds: env.chat.callRingSeconds,
      },
      deleteConfirmWord: 'XÓA',
    });
  }),
);

module.exports = router;
