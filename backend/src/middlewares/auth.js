const jwt = require('jsonwebtoken');
const env = require('../config/env');
const db = require('../config/db');
const ApiError = require('../utils/ApiError');

/**
 * Kiểm tra access token + phiên đăng nhập. Dùng chung cho REST (requireAuth) và WebSocket (hub).
 * Trả về { id, role, sessionId, exp } hoặc ném ApiError 401.
 */
async function authenticateToken(token) {
  if (!token) throw ApiError.unauthorized();
  let payload;
  try {
    payload = jwt.verify(token, env.jwt.accessSecret);
  } catch (e) {
    throw ApiError.unauthorized(e.name === 'TokenExpiredError' ? 'Access token đã hết hạn' : undefined);
  }

  const sid = Number(payload.sid) || 0;
  const [row] = await db.query(
    `SELECT u.id, u.role, u.status, s.id AS session_id, s.revoked_at AS session_revoked_at
       FROM users u LEFT JOIN sessions s ON s.id = ? AND s.user_id = u.id
      WHERE u.id = ?`,
    [sid, payload.sub],
  );
  if (!row || row.status !== 'active') throw ApiError.unauthorized('Tài khoản không tồn tại hoặc đã bị khoá');
  // Token có mã phiên mà phiên đã bị thu hồi (đăng xuất từ thiết bị khác / đổi mật khẩu) -> từ chối ngay
  if (sid && (!row.session_id || row.session_revoked_at)) {
    throw new ApiError(401, 'Phiên đăng nhập đã kết thúc, vui lòng đăng nhập lại', 'SESSION_REVOKED');
  }
  return { id: Number(row.id), role: row.role, sessionId: sid || null, exp: payload.exp };
}

/** Ghi nhận hoạt động (tối đa 1 lần / phút để không ghi DB ở mọi request). */
function touch(user) {
  db.query(
    `UPDATE users SET last_active_at = UTC_TIMESTAMP()
      WHERE id = ? AND (last_active_at IS NULL OR last_active_at < UTC_TIMESTAMP() - INTERVAL 1 MINUTE)`,
    [user.id],
  ).catch(() => {});
  if (user.sessionId) {
    db.query(
      'UPDATE sessions SET last_active_at = UTC_TIMESTAMP() WHERE id = ? AND last_active_at < UTC_TIMESTAMP() - INTERVAL 1 MINUTE',
      [user.sessionId],
    ).catch(() => {});
  }
}

/**
 * Yêu cầu header: Authorization: Bearer <accessToken>
 * Gắn req.user = { id, role, sessionId } nếu hợp lệ.
 */
async function requireAuth(req, res, next) {
  try {
    const [scheme, token] = (req.headers.authorization || '').split(' ');
    if (scheme !== 'Bearer' || !token) throw ApiError.unauthorized();
    req.user = await authenticateToken(token);
    touch(req.user);
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

module.exports = { requireAuth, requireRole, authenticateToken, touch };
