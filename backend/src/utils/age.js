/** Tính tuổi từ chuỗi 'YYYY-MM-DD'. */
function ageFromBirthday(birthday) {
  if (!birthday) return null;
  const [y, m, d] = String(birthday).slice(0, 10).split('-').map(Number);
  const now = new Date();
  let age = now.getUTCFullYear() - y;
  const beforeBirthday = now.getUTCMonth() + 1 < m || (now.getUTCMonth() + 1 === m && now.getUTCDate() < d);
  if (beforeBirthday) age -= 1;
  return age;
}

module.exports = { ageFromBirthday };
