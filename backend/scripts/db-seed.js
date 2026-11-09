/**
 * Thêm dữ liệu mẫu: 20 sở thích (như app) + tài khoản demo.
 * Đăng nhập bằng email, mật khẩu: 123456 (SĐT lưu sẵn cho khi bật lại đăng nhập bằng SĐT)
 *   npm run db:seed      (chạy lại nhiều lần không sao; tài khoản đã có sẽ được bổ sung SĐT nếu thiếu)
 */
const bcrypt = require('bcryptjs');
const db = require('../src/config/db');
const { APP_INTERESTS } = require('../src/modules/meta/options');

// Toạ độ quanh TP.HCM để test lọc theo khoảng cách
const DEMO_USERS = [
  { email: 'admin@datingapp.local', phone: '+84900000000', name: 'Admin', birthday: '2000-01-01', gender: 'other', role: 'admin', city: 'TP.HCM' },
  { email: 'an@demo.local', phone: '+84901000001', name: 'An', birthday: '2004-03-12', gender: 'male', city: 'Thủ Đức', lat: 10.8700, lng: 106.8031, interestedIn: 'female', bio: 'Sinh viên CNTT, thích cà phê và code dạo.', heightCm: 172, goal: 'dating', zodiac: 'pisces', smoking: 'never', drinking: 'sometimes', exercise: 'weekly', education: 'university_student' },
  { email: 'binh@demo.local', phone: '+84901000002', name: 'Bình', birthday: '2003-07-25', gender: 'female', city: 'Quận 1', lat: 10.7769, lng: 106.7009, interestedIn: 'male', bio: 'Mê du lịch bụi và chụp ảnh film.', heightCm: 160, goal: 'long_term', zodiac: 'leo', smoking: 'never', drinking: 'special_occasions', exercise: 'sometimes', education: 'bachelor' },
  { email: 'chi@demo.local', phone: '+84901000003', name: 'Chi', birthday: '2005-11-02', gender: 'female', city: 'Thủ Đức', lat: 10.8494, lng: 106.7537, interestedIn: 'male', bio: 'Thích đọc sách, nuôi 2 bé mèo.', heightCm: 158, goal: 'friendship', zodiac: 'scorpio', smoking: 'never', drinking: 'never', exercise: 'rarely', education: 'university_student' },
  { email: 'dung@demo.local', phone: '+84901000004', name: 'Dũng', birthday: '2002-05-18', gender: 'male', city: 'Bình Thạnh', lat: 10.8106, lng: 106.7091, interestedIn: 'female', bio: 'Gym, bóng đá cuối tuần.', heightCm: 178, goal: 'serious', zodiac: 'taurus', smoking: 'social', drinking: 'often', exercise: 'very_active', education: 'bachelor' },
  { email: 'giang@demo.local', phone: '+84901000005', name: 'Giang', birthday: '2004-09-09', gender: 'female', city: 'Quận 7', lat: 10.7340, lng: 106.7218, interestedIn: 'everyone', bio: 'Đi đâu cũng được, miễn có đồ ăn ngon.', heightCm: 163, goal: 'meet_new_people', zodiac: 'virgo', smoking: 'never', drinking: 'sometimes', exercise: 'sometimes', education: 'college' },
  { email: 'hoa@demo.local', phone: '+84901000006', name: 'Hoà', birthday: '2001-02-14', gender: 'other', city: 'Gò Vấp', lat: 10.8387, lng: 106.6653, interestedIn: 'everyone', bio: 'Vẽ tranh và nghe nhạc indie.', heightCm: 168, goal: 'not_sure', zodiac: 'aquarius', smoking: 'sometimes', drinking: 'sometimes', exercise: 'rarely', education: 'postgraduate' },
  { email: 'khoa@demo.local', phone: '+84901000007', name: 'Khoa', birthday: '2003-12-30', gender: 'male', city: 'Dĩ An', lat: 10.9065, lng: 106.7690, interestedIn: 'female', bio: 'Game thủ, đang tập chạy bộ.', heightCm: 175, goal: 'dating', zodiac: 'capricorn', smoking: 'never', drinking: 'never', exercise: 'weekly', education: 'university_student' },
];

async function main() {
  await db.query('INSERT IGNORE INTO interests (name) VALUES ?', [APP_INTERESTS.map((n) => [n])]);
  const all = await db.query('SELECT id, name FROM interests');
  const idOf = new Map(all.map((r) => [r.name, Number(r.id)]));
  const interestIds = APP_INTERESTS.map((n) => idOf.get(n));

  const passwordHash = await bcrypt.hash('123456', 10);
  let created = 0;
  let updated = 0;

  for (const [i, u] of DEMO_USERS.entries()) {
    const [exists] = await db.query('SELECT id, phone FROM users WHERE email = ?', [u.email]);
    if (exists) {
      // Tài khoản từ bản cũ: bổ sung SĐT + hồ sơ mở rộng
      if (!exists.phone) {
        await db.query('UPDATE users SET phone = ?, phone_verified_at = UTC_TIMESTAMP() WHERE id = ? AND phone IS NULL', [u.phone, exists.id]);
        await db.query(
          `UPDATE profiles SET height_cm = ?, relationship_goal = ?, zodiac = ?, smoking = ?, drinking = ?, exercise = ?, education = ?,
                  completed_at = COALESCE(completed_at, UTC_TIMESTAMP()) WHERE user_id = ?`,
          [u.heightCm ?? null, u.goal ?? null, u.zodiac ?? null, u.smoking ?? null, u.drinking ?? null, u.exercise ?? null, u.education ?? null, exists.id],
        );
        updated += 1;
      }
      continue;
    }

    await db.transaction(async (conn) => {
      const [res] = await conn.query(
        'INSERT INTO users (email, phone, phone_verified_at, password_hash, role) VALUES (?, ?, UTC_TIMESTAMP(), ?, ?)',
        [u.email, u.phone, passwordHash, u.role || 'user'],
      );
      const id = res.insertId;
      await conn.query(
        `INSERT INTO profiles (user_id, display_name, birthday, gender, bio, city, latitude, longitude,
                               height_cm, relationship_goal, zodiac, smoking, drinking, exercise, education, completed_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, UTC_TIMESTAMP())`,
        [id, u.name, u.birthday, u.gender, u.bio || null, u.city || null, u.lat ?? null, u.lng ?? null,
          u.heightCm ?? null, u.goal ?? null, u.zodiac ?? null, u.smoking ?? null, u.drinking ?? null, u.exercise ?? null, u.education ?? null],
      );
      await conn.query('INSERT INTO preferences (user_id, interested_in) VALUES (?, ?)', [id, u.interestedIn || 'everyone']);
      // 2 ảnh mẫu (avatar tạo từ tên) - app yêu cầu tối thiểu 2 ảnh
      await conn.query('INSERT INTO photos (user_id, url, position) VALUES ?', [
        [0, 1].map((pos) => [
          id,
          `https://ui-avatars.com/api/?size=512&name=${encodeURIComponent(u.name)}${pos ? '&background=random' : ''}`,
          pos,
        ]),
      ]);
      // 3 sở thích khác nhau cho mỗi người
      const picks = [0, 1, 2].map((k) => interestIds[(i * 3 + k) % interestIds.length]);
      await conn.query('INSERT INTO user_interests (user_id, interest_id) VALUES ?', [picks.map((iid) => [id, iid])]);
    });
    created += 1;
  }

  console.log(`[db:seed] ${APP_INTERESTS.length} sở thích, ${created} tài khoản demo mới, ${updated} tài khoản cũ được bổ sung SĐT`);
  console.log('[db:seed] Đăng nhập: an@demo.local, binh@demo.local ... khoa@demo.local, mật khẩu 123456');
  await db.pool.end();
}

main().catch(async (err) => {
  console.error('[db:seed] Lỗi:', err.message);
  await db.pool.end().catch(() => {});
  process.exit(1);
});
