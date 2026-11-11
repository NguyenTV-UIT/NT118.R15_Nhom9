/**
 * GIẢ LẬP CHAT để test nhanh các tính năng bị khoá theo độ thân thiết (gửi ảnh: 10 tin, gọi thoại/video: 20 tin mỗi bên).
 *
 *   npm run db:simulate-chat                                   # An <-> Bình, mỗi người 20 tin
 *   npm run db:simulate-chat -- an@demo.local chi@demo.local 10    # 2 tài khoản bất kỳ, mỗi người 10 tin
 *
 * Việc làm: cho 2 người thích nhau (tạo tương hợp nếu chưa có) rồi nhắn qua lại bằng đúng service của API,
 * nên bộ đếm, mốc mở khoá và tin nhắn giống hệt như khi nhắn từ app.
 */
const db = require('../src/config/db');
const home = require('../src/modules/home/home.service');
const chats = require('../src/modules/chats/chats.service');

const LINES = ['Chào bạn 👋', 'Hôm nay của bạn thế nào?', 'Mình vừa đi cà phê về', 'Cuối tuần bạn hay làm gì?', 'Nghe vui đó!', 'Bạn thích nghe nhạc gì?', 'Haha đúng rồi', 'Hôm nào đi ăn thử nhé', 'Ok luôn 😄', 'Chốt nha!'];

async function userId(email) {
  const [u] = await db.query('SELECT id FROM users WHERE email = ?', [email]);
  if (!u) throw new Error(`Không tìm thấy tài khoản ${email} (chạy npm run db:seed trước)`);
  return Number(u.id);
}

async function main() {
  const [emailA = 'an@demo.local', emailB = 'binh@demo.local', countArg = '20'] = process.argv.slice(2);
  const count = Math.max(1, Math.min(100, Number(countArg) || 20));
  const a = await userId(emailA);
  const b = await userId(emailB);

  await home.swipe(a, b, 'like');
  const { matched, matchId } = await home.swipe(b, a, 'like');
  if (!matched) throw new Error('Không tạo được tương hợp (2 tài khoản có đang chặn nhau không?)');

  let last;
  for (let i = 0; i < count; i++) {
    await chats.sendText(a, matchId, { content: LINES[(2 * i) % LINES.length] });
    last = await chats.sendText(b, matchId, { content: LINES[(2 * i + 1) % LINES.length] });
  }
  const it = last.intimacy;
  console.log(`[db:simulate-chat] ${emailA} <-> ${emailB} | matchId = ${matchId} | mỗi người gửi thêm ${count} tin`);
  console.log(`[db:simulate-chat] Độ thân thiết: ${it.score}/${it.max} | gửi ảnh: ${it.photoUnlocked ? 'ĐÃ MỞ' : 'khoá'} | gọi thoại/video: ${it.callUnlocked ? 'ĐÃ MỞ' : 'khoá'}`);
}

main()
  .catch((err) => {
    console.error('[db:simulate-chat] Lỗi:', err.message);
    process.exitCode = 1;
  })
  .finally(() => setTimeout(() => db.pool.end().finally(() => process.exit()), 300)); // chờ việc chạy nền (cập nhật gu) xong
