/**
 * PHIÊN ĐĂNG NHẬP THEO THIẾT BỊ (màn Cài đặt > "Thiết bị đang đăng nhập")
 *
 * Mỗi lần đăng nhập / đăng ký tạo 1 phiên (session). Access token mang mã phiên (sid);
 * middleware kiểm tra phiên còn hiệu lực ở MỖI request -> "Đăng xuất thiết bị" có tác dụng ngay,
 * không phải chờ access token hết hạn. Refresh token xoay vòng nhưng vẫn thuộc cùng 1 phiên.
 */
const db = require('../../config/db');
const ApiError = require('../../utils/ApiError');

const header = (req, name) => {
  const v = req.headers?.[name];
  if (!v) return null;
  try {
    return decodeURIComponent(String(v)).trim().slice(0, 120) || null; // app có thể URL-encode tên tiếng Việt
  } catch {
    return String(v).trim().slice(0, 120) || null;
  }
};

/** Đoán tên thiết bị từ User-Agent khi app không gửi X-Device-Name. */
function deviceFromUserAgent(ua = '') {
  let m = /Android\s+([\d.]+);\s*([^;)]+?)(?:\s+Build|\)|;)/.exec(ua); // Dalvik/2.1.0 (Linux; U; Android 14; SM-S911B Build/...)
  if (m && !/Chrome|Firefox/.test(ua)) return { deviceName: `${m[2].trim()} (Android ${m[1]})`, platform: 'android' };
  if (/okhttp/i.test(ua)) return { deviceName: 'Thiết bị Android', platform: 'android' };
  if (/PostmanRuntime/i.test(ua)) return { deviceName: 'Postman', platform: 'web' };
  const browser = /Edg\//.test(ua) ? 'Edge' : /OPR\//.test(ua) ? 'Opera' : /Firefox\//.test(ua) ? 'Firefox'
    : /Chrome\//.test(ua) ? 'Chrome' : /Safari\//.test(ua) ? 'Safari' : null;
  const os = /Windows/.test(ua) ? 'Windows' : /iPhone/.test(ua) ? 'iPhone' : /iPad/.test(ua) ? 'iPad'
    : /Android/.test(ua) ? 'Android' : /Mac OS X/.test(ua) ? 'macOS' : /Linux/.test(ua) ? 'Linux' : null;
  if (browser && os) return { deviceName: `${browser} trên ${os}`, platform: 'web' };
  return { deviceName: 'Thiết bị không xác định', platform: null };
}

/**
 * Thông tin thiết bị của request. App nên gửi header:
 *   X-Device-Name: "Samsung Galaxy S23"   X-Device-Platform: android   X-Device-Location: "TP. Hồ Chí Minh"
 * (không gửi thì server đoán từ User-Agent; vị trí bỏ trống sẽ lấy thành phố trong hồ sơ).
 */
function describeDevice(req) {
  const ua = String(req.headers?.['user-agent'] || '').slice(0, 255);
  const guessed = deviceFromUserAgent(ua);
  return {
    deviceName: header(req, 'x-device-name') || guessed.deviceName,
    platform: (header(req, 'x-device-platform') || guessed.platform || '').toLowerCase().slice(0, 20) || null,
    location: header(req, 'x-device-location'),
    ip: String(req.ip || req.socket?.remoteAddress || '').replace(/^::ffff:/, '').slice(0, 45) || null,
    userAgent: ua || null,
  };
}

/**
 * Tạo phiên mới. `run(sql, params)` là hàm chạy SQL trả về rows / result
 * (db.query, hoặc bản bọc của connection khi đang trong transaction).
 */
async function create(run, userId, device = {}) {
  let location = device.location || null;
  if (!location) {
    const rows = await run('SELECT city FROM profiles WHERE user_id = ?', [userId]);
    location = rows[0]?.city || null;
  }
  const result = await run(
    'INSERT INTO sessions (user_id, device_name, platform, location, ip, user_agent) VALUES (?, ?, ?, ?, ?, ?)',
    [userId, device.deviceName || 'Thiết bị không xác định', device.platform || null, location, device.ip || null, device.userAgent || null],
  );
  return Number(result.insertId);
}

/** Danh sách thiết bị đang đăng nhập, thiết bị hiện tại đứng đầu. */
async function list(userId, currentSessionId) {
  const rows = await db.query(
    `SELECT id, device_name, platform, location, created_at, last_active_at
       FROM sessions WHERE user_id = ? AND revoked_at IS NULL
      ORDER BY (id = ?) DESC, last_active_at DESC`,
    [userId, currentSessionId || 0],
  );
  return rows.map((s) => {
    const current = Number(s.id) === Number(currentSessionId);
    return {
      id: Number(s.id),
      deviceName: s.device_name,
      platform: s.platform,
      location: s.location,
      current,
      // Coi là "đang hoạt động" nếu có request trong 5 phút gần đây
      active: current || Date.now() - new Date(s.last_active_at).getTime() < 5 * 60 * 1000,
      lastActiveAt: s.last_active_at,
      createdAt: s.created_at,
    };
  });
}

function closeSockets(sessionIds) {
  // eslint-disable-next-line global-require
  const hub = require('../../realtime/hub'); // require muộn để tránh vòng lặp phụ thuộc
  for (const id of sessionIds) hub.disconnectSession(Number(id));
}

async function revokeIds(userId, ids) {
  if (!ids.length) return 0;
  await db.query('UPDATE sessions SET revoked_at = UTC_TIMESTAMP() WHERE user_id = ? AND id IN (?) AND revoked_at IS NULL', [userId, ids]);
  await db.query('UPDATE refresh_tokens SET revoked_at = UTC_TIMESTAMP() WHERE user_id = ? AND session_id IN (?) AND revoked_at IS NULL', [userId, ids]);
  closeSockets(ids);
  return ids.length;
}

/** Đăng xuất 1 thiết bị. */
async function revoke(userId, sessionId) {
  const [s] = await db.query('SELECT id FROM sessions WHERE id = ? AND user_id = ? AND revoked_at IS NULL', [sessionId, userId]);
  if (!s) throw ApiError.notFound('Không tìm thấy thiết bị (có thể đã đăng xuất)');
  await revokeIds(userId, [Number(s.id)]);
}

/** Đăng xuất tất cả thiết bị, trừ exceptSessionId (nếu có). Trả về số thiết bị đã đăng xuất. */
async function revokeAll(userId, exceptSessionId = null) {
  const rows = await db.query('SELECT id FROM sessions WHERE user_id = ? AND revoked_at IS NULL AND id <> ?', [userId, exceptSessionId || 0]);
  const count = await revokeIds(userId, rows.map((r) => Number(r.id)));
  // Refresh token cũ chưa gắn phiên (tạo trước bản v4)
  await db.query('UPDATE refresh_tokens SET revoked_at = UTC_TIMESTAMP() WHERE user_id = ? AND session_id IS NULL AND revoked_at IS NULL', [userId]);
  return count;
}

module.exports = { describeDevice, deviceFromUserAgent, create, list, revoke, revokeAll };
