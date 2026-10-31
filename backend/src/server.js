const http = require('http');
const env = require('./config/env');
const db = require('./config/db');
const app = require('./app');

const server = http.createServer(app);
// Giai đoạn sau: gắn WebSocket (chat realtime, WebRTC signaling) vào `server` này.

async function start() {
  try {
    await db.query('SELECT 1');
    console.log(`[db] Đã kết nối MySQL ${env.db.host}:${env.db.port}/${env.db.database}`);
  } catch (err) {
    console.error('[db] Không kết nối được MySQL:', err.message);
    process.exit(1);
  }

  server.listen(env.port, '0.0.0.0', () => {
    console.log(`[api] Server chạy tại http://0.0.0.0:${env.port} (${env.nodeEnv})`);
  });
}

function shutdown(signal) {
  console.log(`[api] Nhận ${signal}, đang tắt server...`);
  server.close(async () => {
    await db.pool.end().catch(() => {});
    process.exit(0);
  });
  setTimeout(() => process.exit(1), 10000).unref();
}

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

start();
