const mysql = require('mysql2/promise');
const env = require('./env');

const pool = mysql.createPool({
  ...env.db,
  waitForConnections: true,
  connectionLimit: 10,
  charset: 'utf8mb4',
  timezone: 'Z', // lưu & đọc DATETIME theo UTC
  dateStrings: ['DATE'], // trả về DATE dạng 'YYYY-MM-DD' thay vì Date object
});

/** Chạy 1 câu SQL, trả về rows (SELECT) hoặc result (INSERT/UPDATE). */
async function query(sql, params = []) {
  const [rows] = await pool.query(sql, params);
  return rows;
}

/** Chạy nhiều câu SQL trong 1 transaction. fn nhận vào connection. */
async function transaction(fn) {
  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();
    const result = await fn(conn);
    await conn.commit();
    return result;
  } catch (err) {
    await conn.rollback();
    throw err;
  } finally {
    conn.release();
  }
}

module.exports = { pool, query, transaction };
