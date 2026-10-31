const jwt = require('jsonwebtoken');
const env = require('../config/env');
const db = require('../config/db');
const ApiError = require('../utils/ApiError');

/**
 * Yêu cầu header: Authorization: Bearer <accessToken>
 * Gắn req.user = { id, role } nếu hợp lệ.
 */
async function requireAuth(req, res, next) {
  try {
    const header = req.headers.authorization || '';
    const [scheme, token] = header.split(' ');
    if (scheme !== 'Bearer' || !token) throw ApiError.unauthorized();

    let payload;
    try {
      payload = jwt.verify(token, env.jwt.accessSecret);
    } catch (e) {
      throw ApiError.unauthorized(e.name === 'TokenExpiredError' ? 'Access token đã hết hạn' : undefined);
    }

    const [user] = await db.query('SELECT id, role, status FROM users WHERE id = ?', [payload.sub]);
    if (!user || user.status !== 'active') throw ApiError.unauthorized('Tài khoản không tồn tại hoặc đã bị khoá');

    req.user = { id: Number(user.id), role: user.role };

    // Cập nhật thời gian hoạt động, không chờ kết quả
    db.query('UPDATE users SET last_active_at = UTC_TIMESTAMP() WHERE id = ?', [user.id]).catch(() => {});
    next();
  } catch (err) {
    next(err);
  }
}

function requireRole(...roles) {
  return (req, res, next) => {
    if (!req.user || !roles.includes(req.user.role)) return next(ApiError.forbidden());
    next();
  };
}

module.exports = { requireAuth, requireRole };
