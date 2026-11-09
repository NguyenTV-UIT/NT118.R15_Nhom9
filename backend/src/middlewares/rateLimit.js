/**
 * Giới hạn số request theo IP (lưu trong RAM - đủ cho 1 server PM2 instance).
 * Dùng cho đăng nhập / OTP để chống dò mật khẩu và spam SMS.
 *   router.post('/login', rateLimit({ windowSec: 60, max: 10 }), ...)
 */
const ApiError = require('../utils/ApiError');

module.exports = function rateLimit({ windowSec = 60, max = 10, name = '' } = {}) {
  const hits = new Map(); // key -> { count, resetAt }
  setInterval(() => {
    const now = Date.now();
    for (const [k, v] of hits) if (v.resetAt <= now) hits.delete(k);
  }, windowSec * 1000).unref();

  return (req, res, next) => {
    const key = `${name}:${req.ip}`;
    const now = Date.now();
    let entry = hits.get(key);
    if (!entry || entry.resetAt <= now) {
      entry = { count: 0, resetAt: now + windowSec * 1000 };
      hits.set(key, entry);
    }
    entry.count += 1;
    if (entry.count > max) {
      const wait = Math.ceil((entry.resetAt - now) / 1000);
      res.set('Retry-After', String(wait));
      return next(new ApiError(429, `Thao tác quá nhanh, vui lòng thử lại sau ${wait} giây`, 'TOO_MANY_REQUESTS'));
    }
    next();
  };
};
