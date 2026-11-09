/**
 * Chạy các file database/migrations/*.sql CHƯA chạy, theo thứ tự tên.
 * Đã chạy file nào thì ghi vào bảng schema_migrations -> lần sau bỏ qua.
 * KHÔNG xoá dữ liệu -> an toàn để chạy trên VPS mỗi lần deploy (deploy.sh tự gọi).
 *   npm run db:migrate
 *
 * Thêm thay đổi schema: tạo file NNN_ten.sql mới (VD 003_chat.sql), không sửa file đã chạy.
 * Nhớ sửa luôn database/schema.sql cho giống (schema.sql = toàn bộ schema mới nhất, dùng khi cài mới).
 */
const fs = require('fs');
const path = require('path');
const mysql = require('mysql2/promise');
const env = require('../src/config/env');

const DIR = path.join(__dirname, '..', 'database', 'migrations');

function listMigrations() {
  return fs.existsSync(DIR) ? fs.readdirSync(DIR).filter((f) => f.endsWith('.sql')).sort() : [];
}

async function ensureTable(conn) {
  await conn.query(`CREATE TABLE IF NOT EXISTS schema_migrations (
    name VARCHAR(255) PRIMARY KEY,
    applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`);
}

/** Đánh dấu mọi migration là đã chạy (db-init gọi sau khi tạo schema mới). */
async function markAllApplied(conn) {
  await ensureTable(conn);
  const files = listMigrations();
  if (files.length) await conn.query('INSERT IGNORE INTO schema_migrations (name) VALUES ?', [files.map((f) => [f])]);
}

async function main() {
  const conn = await mysql.createConnection({ ...env.db, multipleStatements: true, charset: 'utf8mb4' });
  await ensureTable(conn);
  const applied = new Set((await conn.query('SELECT name FROM schema_migrations'))[0].map((r) => r.name));
  const pending = listMigrations().filter((f) => !applied.has(f));

  for (const file of pending) {
    try {
      await conn.query(fs.readFileSync(path.join(DIR, file), 'utf8'));
    } catch (err) {
      await conn.end();
      throw new Error(`${file}: ${err.message}`);
    }
    await conn.query('INSERT INTO schema_migrations (name) VALUES (?)', [file]);
    console.log(`[db:migrate] ✓ ${file}`);
  }
  await conn.end();
  console.log(pending.length ? `[db:migrate] Xong (${pending.length} migration mới)` : '[db:migrate] Database đã mới nhất');
}

if (require.main === module) {
  main().catch((err) => {
    console.error('[db:migrate] Lỗi:', err.message);
    process.exit(1);
  });
}

module.exports = { markAllApplied };
