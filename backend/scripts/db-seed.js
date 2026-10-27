/**
 * Thêm dữ liệu mẫu: danh sách sở thích + vài tài khoản demo (mật khẩu: 123456).
 *   npm run db:seed
 */
const bcrypt = require('bcryptjs');
const db = require('../src/config/db');

const INTERESTS = [
  'Âm nhạc', 'Phim ảnh', 'Du lịch', 'Đọc sách', 'Cà phê', 'Nấu ăn', 'Thể thao', 'Gym',
  'Chạy bộ', 'Bóng đá', 'Cầu lông', 'Game', 'Anime', 'Nhiếp ảnh', 'Vẽ', 'Thú cưng',
  'Công nghệ', 'Lập trình', 'Thời trang', 'Ẩm thực đường phố', 'Karaoke', 'Yoga', 'Leo núi', 'Tình nguyện',
];

// Toạ độ quanh TP.HCM để test lọc theo khoảng cách
const DEMO_USERS = [
  { email: 'admin@datingapp.local', name: 'Admin', birthday: '2000-01-01', gender: 'other', role: 'admin', city: 'TP.HCM' },
  { email: 'an@demo.local', name: 'An', birthday: '2004-03-12', gender: 'male', city: 'Thủ Đức', lat: 10.8700, lng: 106.8031, interestedIn: 'female', bio: 'Sinh viên CNTT, thích cà phê và code dạo.' },
  { email: 'binh@demo.local', name: 'Bình', birthday: '2003-07-25', gender: 'female', city: 'Quận 1', lat: 10.7769, lng: 106.7009, interestedIn: 'male', bio: 'Mê du lịch bụi và chụp ảnh film.' },
  { email: 'chi@demo.local', name: 'Chi', birthday: '2005-11-02', gender: 'female', city: 'Thủ Đức', lat: 10.8494, lng: 106.7537, interestedIn: 'male', bio: 'Thích đọc sách, nuôi 2 bé mèo.' },
  { email: 'dung@demo.local', name: 'Dũng', birthday: '2002-05-18', gender: 'male', city: 'Bình Thạnh', lat: 10.8106, lng: 106.7091, interestedIn: 'female', bio: 'Gym, bóng đá cuối tuần.' },
  { email: 'giang@demo.local', name: 'Giang', birthday: '2004-09-09', gender: 'female', city: 'Quận 7', lat: 10.7340, lng: 106.7218, interestedIn: 'everyone', bio: 'Đi đâu cũng được, miễn có đồ ăn ngon.' },
  { email: 'hoa@demo.local', name: 'Hoà', birthday: '2001-02-14', gender: 'other', city: 'Gò Vấp', lat: 10.8387, lng: 106.6653, interestedIn: 'everyone', bio: 'Vẽ tranh và nghe nhạc indie.' },
  { email: 'khoa@demo.local', name: 'Khoa', birthday: '2003-12-30', gender: 'male', city: 'Dĩ An', lat: 10.9065, lng: 106.7690, interestedIn: 'female', bio: 'Game thủ, đang tập chạy bộ.' },
];

async function main() {
  await db.query('INSERT IGNORE INTO interests (name) VALUES ?', [INTERESTS.map((n) => [n])]);
  const interestIds = (await db.query('SELECT id FROM interests')).map((r) => r.id);

  const passwordHash = await bcrypt.hash('123456', 10);
  let createdCount = 0;

  for (const [i, u] of DEMO_USERS.entries()) {
    const [exists] = await db.query('SELECT id FROM users WHERE email = ?', [u.email]);
    if (exists) continue;

    await db.transaction(async (conn) => {
      const [res] = await conn.query('INSERT INTO users (email, password_hash, role) VALUES (?, ?, ?)', [
        u.email,
        passwordHash,
        u.role || 'user',
      ]);
      const id = res.insertId;
      await conn.query(
        `INSERT INTO profiles (user_id, display_name, birthday, gender, bio, city, latitude, longitude)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
        [id, u.name, u.birthday, u.gender, u.bio || null, u.city || null, u.lat ?? null, u.lng ?? null],
      );
      await conn.query('INSERT INTO preferences (user_id, interested_in) VALUES (?, ?)', [id, u.interestedIn || 'everyone']);
      // Ảnh mẫu (avatar tạo từ tên)
      await conn.query('INSERT INTO photos (user_id, url, position) VALUES (?, ?, 0)', [
        id,
        `https://ui-avatars.com/api/?size=512&name=${encodeURIComponent(u.name)}`,
      ]);
      // Gán 3 sở thích khác nhau cho mỗi người
      const picks = [0, 1, 2].map((k) => interestIds[(i * 3 + k) % interestIds.length]);
      await conn.query('INSERT INTO user_interests (user_id, interest_id) VALUES ?', [picks.map((iid) => [id, iid])]);
    });
    createdCount += 1;
  }

  console.log(`[db:seed] ${INTERESTS.length} sở thích, ${createdCount} tài khoản demo mới (mật khẩu: 123456)`);
  await db.pool.end();
}

main().catch(async (err) => {
  console.error('[db:seed] Lỗi:', err.message);
  await db.pool.end().catch(() => {});
  process.exit(1);
});
