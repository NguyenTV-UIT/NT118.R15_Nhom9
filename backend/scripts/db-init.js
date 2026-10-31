/**
 * Tạo database (nếu chưa có) và chạy database/schema.sql.
 * CẢNH BÁO: schema.sql DROP toàn bộ bảng -> mất dữ liệu. Chỉ dùng khi dev / lần đầu deploy.
 *   npm run db:init
 */
const fs = require('fs');
const path = require('path');
const mysql = require('mysql2/promise');
const env = require('../src/config/env');

async function main() {
  const conn = await mysql.createConnection({
    host: env.db.host,
    port: env.db.port,
    user: env.db.user,
    password: env.db.password,
    multipleStatements: true,
    charset: 'utf8mb4',
  });

  await conn.query(
    `CREATE DATABASE IF NOT EXISTS \`${env.db.database}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`,
  );
  await conn.query(`USE \`${env.db.database}\``);

  const sql = fs.readFileSync(path.join(__dirname, '..', 'database', 'schema.sql'), 'utf8');
  await conn.query(sql);
  await conn.end();
  console.log(`[db:init] Đã tạo schema cho database "${env.db.database}"`);
}

main().catch((err) => {
  console.error('[db:init] Lỗi:', err.message);
  process.exit(1);
});
