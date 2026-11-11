/**
 * KÊNH REALTIME (WebSocket) - ws://<host>/ws
 *
 * Kết nối: gửi access token bằng 1 trong 3 cách
 *   1. Header khi mở kết nối:  Authorization: Bearer <accessToken>     (OkHttp trên Android làm được - nên dùng)
 *   2. Query string:           ws://host/ws?token=<accessToken>
 *   3. Tin nhắn đầu tiên:      {"event":"auth","data":{"token":"<accessToken>"}}   (trong 10 giây)
 * Server trả {"event":"ready","data":{"userId":..}} khi xác thực xong.
 *
 * Mọi tin nhắn 2 chiều là JSON: { "event": "<tên>", "data": { ... } }
 *
 * Server -> app:  ready, pong, error, message.new, message.read, typing, presence, match.new, like.new,
 *                 match.removed, call.incoming, call.accepted, call.rejected, call.cancelled, call.ended,
 *                 call.missed, call.signal, session.revoked
 * App -> server:  ping, auth, typing, call.signal        (các thao tác khác dùng REST)
 *
 * Access token hết hạn sau 15 phút nhưng kết nối đang mở vẫn được giữ; khi phiên đăng nhập bị thu hồi
 * (đăng xuất thiết bị từ xa, đổi mật khẩu, xoá tài khoản) server chủ động đóng kết nối với mã 4401.
 */
const { EventEmitter } = require('events');
const { WebSocketServer } = require('ws');
const { authenticateToken, touch } = require('../middlewares/auth');

const CLOSE_UNAUTHORIZED = 4401;
const AUTH_TIMEOUT_MS = 10000;
const HEARTBEAT_MS = 30000;

const clients = new Map(); // userId -> Set<ws>
const handlers = new Map(); // event -> fn(user, data, ws)
const events = new EventEmitter(); // 'online' / 'offline' (userId)
let wss = null;
let heartbeat = null;

function send(ws, event, data) {
  if (ws.readyState === 1) ws.send(JSON.stringify({ event, data }));
}

/** Gửi sự kiện tới mọi thiết bị đang kết nối của 1 người. Trả về số kết nối đã gửi (0 = đang offline). */
function sendToUser(userId, event, data) {
  const set = clients.get(Number(userId));
  if (!set) return 0;
  for (const ws of set) send(ws, event, data);
  return set.size;
}

function isOnline(userId) {
  return clients.has(Number(userId));
}

/** Đăng ký xử lý sự kiện do app gửi lên. */
function on(event, fn) {
  handlers.set(event, fn);
}

function register(ws, user) {
  ws.user = user;
  let set = clients.get(user.id);
  const first = !set;
  if (!set) clients.set(user.id, (set = new Set()));
  set.add(ws);
  send(ws, 'ready', { userId: user.id });
  if (first) events.emit('online', user.id);
}

function unregister(ws) {
  const user = ws.user;
  if (!user) return;
  const set = clients.get(user.id);
  if (!set) return;
  set.delete(ws);
  if (!set.size) {
    clients.delete(user.id);
    events.emit('offline', user.id);
  }
}

async function authenticate(ws, token) {
  try {
    register(ws, await authenticateToken(token));
    clearTimeout(ws.authTimer);
  } catch (err) {
    send(ws, 'error', { code: err.code || 'UNAUTHORIZED', message: err.message });
    ws.close(CLOSE_UNAUTHORIZED, 'unauthorized');
  }
}

function onConnection(ws, req) {
  ws.isAlive = true;
  ws.on('pong', () => {
    ws.isAlive = true;
  });
  ws.on('close', () => unregister(ws));
  ws.on('error', () => {});

  ws.on('message', async (raw) => {
    let msg;
    try {
      msg = JSON.parse(raw.toString());
    } catch {
      return send(ws, 'error', { code: 'BAD_JSON', message: 'Tin nhắn phải là JSON {"event","data"}' });
    }
    const { event, data = {} } = msg || {};
    if (event === 'auth') return ws.user ? null : authenticate(ws, data.token);
    if (!ws.user) return send(ws, 'error', { code: 'UNAUTHORIZED', message: 'Chưa xác thực' });
    if (event === 'ping') {
      touch(ws.user);
      return send(ws, 'pong', { time: new Date().toISOString() });
    }
    const handler = handlers.get(event);
    if (!handler) return send(ws, 'error', { code: 'UNKNOWN_EVENT', message: `Không hỗ trợ sự kiện "${event}"` });
    try {
      await handler(ws.user, data, ws);
    } catch (err) {
      send(ws, 'error', { code: err.code || 'ERROR', message: err.status ? err.message : 'Lỗi máy chủ', event });
      if (!err.status) console.error('[ws]', err);
    }
  });

  const url = new URL(req.url, 'http://localhost');
  const [scheme, headerToken] = (req.headers.authorization || '').split(' ');
  const token = (scheme === 'Bearer' && headerToken) || url.searchParams.get('token');
  if (token) authenticate(ws, token);
  else ws.authTimer = setTimeout(() => !ws.user && ws.close(CLOSE_UNAUTHORIZED, 'auth timeout'), AUTH_TIMEOUT_MS);
}

/** Gắn WebSocket vào HTTP server (đường dẫn /ws). */
function attach(server) {
  wss = new WebSocketServer({ noServer: true, maxPayload: 256 * 1024 });
  wss.on('connection', onConnection);
  server.on('upgrade', (req, socket, head) => {
    const { pathname } = new URL(req.url, 'http://localhost');
    if (pathname !== '/ws' && pathname !== '/ws/') return socket.destroy();
    wss.handleUpgrade(req, socket, head, (ws) => wss.emit('connection', ws, req));
  });
  // Dọn kết nối chết (mất mạng không báo) để trạng thái online chính xác
  heartbeat = setInterval(() => {
    for (const ws of wss.clients) {
      if (!ws.isAlive) {
        ws.terminate();
        continue;
      }
      ws.isAlive = false;
      ws.ping();
    }
  }, HEARTBEAT_MS);
  heartbeat.unref();
  return wss;
}

/** Đóng mọi kết nối của 1 phiên đăng nhập (khi phiên bị thu hồi). */
function disconnectSession(sessionId) {
  for (const set of clients.values()) {
    for (const ws of set) {
      if (ws.user.sessionId === Number(sessionId)) {
        send(ws, 'session.revoked', { message: 'Thiết bị này đã bị đăng xuất' });
        ws.close(CLOSE_UNAUTHORIZED, 'session revoked');
      }
    }
  }
}

function disconnectUser(userId) {
  for (const ws of clients.get(Number(userId)) || []) ws.close(CLOSE_UNAUTHORIZED, 'account closed');
}

function close() {
  clearInterval(heartbeat);
  if (wss) for (const ws of wss.clients) ws.terminate();
}

module.exports = { attach, on, sendToUser, isOnline, disconnectSession, disconnectUser, close, events };
