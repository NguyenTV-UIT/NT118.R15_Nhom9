/**
 * GIẢ LẬP QUẸT để chứng minh thuật toán gợi ý cá nhân hoá.
 *
 *   npm run db:simulate              # chạy giả lập
 *   npm run db:simulate -- --clean   # xoá 100 user giả lập + lượt quẹt của An, Khoa
 *
 * Kịch bản:
 *  1. Tạo 100 người dùng giả lập (sim001..sim100@demo.local) quanh UIT, tuổi / sở thích ngẫu nhiên.
 *  2. Hai người dùng có "gu ẩn" khác nhau, cùng tiêu chí khai báo (tìm nữ, 18-35 tuổi, 50 km):
 *       - An   : chỉ like người 20-23 tuổi hoặc có sở thích "Nhiếp ảnh"
 *       - Khoa : chỉ like người trong bán kính 10 km
 *  3. In top 10 feed TRƯỚC khi quẹt (thuật toán mặc định -> 2 người gần như giống nhau).
 *  4. Mỗi người quẹt 40 lượt theo gu ẩn của mình.
 *  5. In top 10 feed SAU khi quẹt + gu đã học -> mỗi người một kết quả khác nhau, đúng gu.
 * Chạy lại được nhiều lần (tự reset lượt quẹt của An & Khoa). Kết quả cố định nhờ seed ngẫu nhiên.
 */
const bcrypt = require('bcryptjs');
const db = require('../src/config/db');
const home = require('../src/modules/home/home.service');
const taste = require('../src/modules/home/taste');
const { APP_INTERESTS } = require('../src/modules/meta/options');

const SIM_USERS = 100;
const SWIPES_PER_USER = 40;
const CENTER = { lat: 10.8700, lng: 106.8031 }; // UIT

// PRNG có seed -> lần nào chạy cũng ra cùng dữ liệu
let seed = 20261101;
const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
const pick = (arr) => arr[Math.floor(rnd() * arr.length)];
const shuffle = (arr) => arr.map((x) => [rnd(), x]).sort((a, b) => a[0] - b[0]).map((x) => x[1]);

const FEMALE = ['Lan', 'Mai', 'Hương', 'Linh', 'Trang', 'Ngọc', 'Thảo', 'Vy', 'My', 'Hân', 'Nhi', 'Quyên', 'Yến', 'Trâm', 'Thư'];
const MALE = ['Minh', 'Huy', 'Tuấn', 'Phúc', 'Long', 'Nam', 'Bảo', 'Đạt', 'Quân', 'Tài'];

function birthdayForAge(age) {
  const d = new Date();
  d.setUTCFullYear(d.getUTCFullYear() - age);
  d.setUTCDate(d.getUTCDate() - 1 - Math.floor(rnd() * 300));
  return d.toISOString().slice(0, 10);
}

async function ensureSimUsers(interestIds) {
  const [{ n }] = await db.query("SELECT COUNT(*) AS n FROM users WHERE email LIKE 'sim%@demo.local'");
  if (Number(n) >= SIM_USERS) return;

  const hash = await bcrypt.hash('123456', 10);
  for (let i = 1; i <= SIM_USERS; i++) {
    const email = `sim${String(i).padStart(3, '0')}@demo.local`;
    const female = rnd() < 0.75;
    const name = `${pick(female ? FEMALE : MALE)} ${String(i).padStart(3, '0')}`;
    const age = 18 + Math.floor(rnd() * 15); // 18-32
    const r = rnd() * 40; // 0-40 km quanh UIT
    const th = rnd() * 2 * Math.PI;
    const lat = CENTER.lat + (r * Math.cos(th)) / 111;
    const lng = CENTER.lng + (r * Math.sin(th)) / (111 * Math.cos((CENTER.lat * Math.PI) / 180));
    const interests = shuffle(interestIds).slice(0, 2 + Math.floor(rnd() * 3));
    const lastActive = new Date(Date.now() - rnd() * 10 * 24 * 3600 * 1000);

    const [exists] = await db.query('SELECT id FROM users WHERE email = ?', [email]);
    if (exists) continue;
    await db.transaction(async (conn) => {
      const [res] = await conn.query('INSERT INTO users (email, password_hash, last_active_at) VALUES (?, ?, ?)', [
        email,
        hash,
        lastActive,
      ]);
      const id = res.insertId;
      await conn.query(
        `INSERT INTO profiles (user_id, display_name, birthday, gender, bio, city, latitude, longitude, completed_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, UTC_TIMESTAMP())`,
        [id, name, birthdayForAge(age), female ? 'female' : 'male', rnd() < 0.7 ? 'Người dùng giả lập' : null, 'TP.HCM', lat.toFixed(6), lng.toFixed(6)],
      );
      await conn.query('INSERT INTO preferences (user_id, interested_in) VALUES (?, ?)', [id, rnd() < 0.6 ? 'male' : 'everyone']);
      await conn.query('INSERT INTO photos (user_id, url, position) VALUES (?, ?, 0)', [
        id,
        `https://ui-avatars.com/api/?size=512&name=${encodeURIComponent(name)}`,
      ]);
      await conn.query('INSERT INTO user_interests (user_id, interest_id) VALUES ?', [interests.map((x) => [id, x])]);
    });
  }
  console.log(`Đã tạo ${SIM_USERS} người dùng giả lập (mật khẩu 123456)`);
}

async function userId(email) {
  const [u] = await db.query('SELECT id FROM users WHERE email = ?', [email]);
  if (!u) throw new Error(`Không thấy ${email} - hãy chạy npm run db:seed trước`);
  return Number(u.id);
}

async function resetUser(id) {
  await db.query('DELETE FROM swipes WHERE swiper_id = ?', [id]);
  await db.query('DELETE FROM user_taste WHERE user_id = ?', [id]);
  await db.query(
    "UPDATE preferences SET interested_in = 'female', min_age = 18, max_age = 35, max_distance_km = 50 WHERE user_id = ?",
    [id],
  );
}

function printFeed(title, items, rule) {
  const hits = items.filter(rule).length;
  console.log(`\n  ${title}  →  ${hits}/${items.length} người đúng gu`);
  for (const u of items) {
    const interests = u.interests.map((i) => i.name).join(', ');
    console.log(
      `   ${rule(u) ? '✓' : ' '} ${String(u.matchScore).padStart(3)}đ  ${u.displayName.padEnd(10)} ${String(u.age).padStart(2)}t ` +
        `${String(u.distanceKm ?? '-').padStart(3)}km  [${interests}]`,
    );
  }
  return hits;
}

async function simulate(label, email, rule) {
  const id = await userId(email);
  await resetUser(id);
  console.log(`\n══════════ ${label} ══════════`);

  const before = await home.getFeed(id, 10);
  const hitsBefore = printFeed('TRƯỚC (thuật toán mặc định)', before.items, rule);

  // Quẹt 40 lượt theo gu ẩn
  const viewer = await home.loadViewer(id);
  const pool = shuffle(await home.findCandidates(viewer, { ...viewer.prefs, sort: 'newest', limit: 300 }));
  let likes = 0;
  for (const u of pool.slice(0, SWIPES_PER_USER)) {
    const like = rule(u);
    likes += like;
    await home.swipe(id, u.id, like ? 'like' : 'pass');
  }
  console.log(`\n  ... đã quẹt ${Math.min(SWIPES_PER_USER, pool.length)} lượt (${likes} like)`);
  await new Promise((r) => setTimeout(r, 500)); // chờ cập nhật gu chạy nền xong

  const summary = await home.getTasteSummary(id);
  const after = await home.getFeed(id, 10);
  const hitsAfter = printFeed(`SAU (cá nhân hoá, α = ${summary.personalization.level})`, after.items, rule);

  console.log('\n  Gu đã học:');
  if (summary.preferredAge) console.log(`   - Tuổi hay like: ${summary.preferredAge.mean} ± ${summary.preferredAge.std}`);
  if (summary.preferredDistanceKm != null) console.log(`   - Khoảng cách hay like: ~${summary.preferredDistanceKm} km`);
  if (summary.likedInterests.length) {
    console.log(`   - Sở thích ưa thích: ${summary.likedInterests.slice(0, 3).map((x) => `${x.name} (+${x.affinity})`).join(', ')}`);
  }
  const w = summary.weights;
  if (w.personal) {
    console.log(
      `   - Trọng số: sở thích ${w.default.interests}→${w.personal.interests}, khoảng cách ${w.default.distance}→${w.personal.distance}, tuổi ${w.default.age}→${w.personal.age}`,
    );
    console.log(`   - Tỉ trọng điểm gu: tuổi ${w.tasteMix.age}, khoảng cách ${w.tasteMix.distance}, sở thích ${w.tasteMix.interests}`);
  }
  return { label, before: hitsBefore, after: hitsAfter, total: after.items.length };
}

async function clean() {
  const ids = (await db.query("SELECT id FROM users WHERE email LIKE 'sim%@demo.local'")).map((r) => r.id);
  if (ids.length) await db.query('DELETE FROM users WHERE id IN (?)', [ids]); // CASCADE xoá hồ sơ, ảnh, quẹt...
  for (const email of ['an@demo.local', 'khoa@demo.local']) {
    const [u] = await db.query('SELECT id FROM users WHERE email = ?', [email]);
    if (u) await resetUser(Number(u.id));
  }
  console.log(`Đã xoá ${ids.length} người dùng giả lập và reset lượt quẹt của An, Khoa`);
}

async function main() {
  if (process.argv.includes('--clean')) return clean();

  // Chỉ dùng 20 sở thích có trên app
  const interests = (await db.query('SELECT id, name FROM interests')).filter((r) => APP_INTERESTS.includes(r.name));
  if (!interests.length) throw new Error('Chưa có sở thích - hãy chạy npm run db:seed trước');
  await ensureSimUsers(interests.map((r) => Number(r.id)));

  const has = (u, name) => u.interests.some((i) => i.name === name);
  const results = [
    await simulate('AN: thích người 20-23 tuổi hoặc thích "Nhiếp ảnh"', 'an@demo.local', (u) => (u.age >= 20 && u.age <= 23) || has(u, 'Nhiếp ảnh')),
    await simulate('KHOA: chỉ thích người trong bán kính 10 km', 'khoa@demo.local', (u) => u.distanceKm != null && u.distanceKm <= 10),
  ];

  console.log('\n══════════ TỔNG KẾT ══════════');
  for (const r of results) console.log(`  ${r.label.split(':')[0].padEnd(5)} đúng gu trong top ${r.total}: ${r.before} → ${r.after}`);
  console.log('\nXem gu qua API: GET /api/home/taste (đăng nhập an@demo.local hoặc khoa@demo.local)');
}

main()
  .catch((err) => {
    console.error('[simulate] Lỗi:', err.message);
    process.exitCode = 1;
  })
  .finally(() => db.pool.end());
