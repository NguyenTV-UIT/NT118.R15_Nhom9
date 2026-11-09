/**
 * Chuẩn hoá số điện thoại di động Việt Nam về dạng +84xxxxxxxxx (giống app Android).
 * Nhận: 0912345678, +84912345678, 84912345678, "091 234 5678", "(091) 234-5678"
 * Trả về null nếu không hợp lệ (đầu số di động 03, 05, 07, 08, 09).
 */
function normalizePhone(input) {
  if (input == null) return null;
  let p = String(input).trim().replace(/[\s().-]/g, '');
  if (p.startsWith('+84')) p = `0${p.slice(3)}`;
  else if (/^84\d{9}$/.test(p)) p = `0${p.slice(2)}`;
  if (!/^0[35789]\d{8}$/.test(p)) return null;
  return `+84${p.slice(1)}`;
}

/** +84912345678 -> *******678 (hiển thị / log) */
function maskPhone(phone) {
  return phone ? `${'*'.repeat(Math.max(0, phone.length - 3))}${phone.slice(-3)}` : '';
}

module.exports = { normalizePhone, maskPhone };
