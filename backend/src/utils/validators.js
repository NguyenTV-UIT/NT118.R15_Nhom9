/** Các kiểu dữ liệu dùng chung khi validate (zod). */
const { z } = require('zod');
const { ageFromBirthday } = require('./age');
const { normalizePhone } = require('./phone');
const options = require('../modules/meta/options');

/** Số điện thoại VN -> +84xxxxxxxxx */
const phone = z
  .string({ required_error: 'Vui lòng nhập số điện thoại' })
  .transform((v, ctx) => {
    const p = normalizePhone(v);
    if (!p) ctx.addIssue({ code: z.ZodIssueCode.custom, message: 'Số điện thoại không hợp lệ' });
    return p;
  });

const otp = z.string({ required_error: 'Vui lòng nhập mã OTP' }).trim().regex(/^\d{6}$/, 'Mã OTP gồm 6 chữ số');

/** Mật khẩu cho luồng số điện thoại: khớp app (≥ 8 ký tự) */
const password8 = z
  .string({ required_error: 'Vui lòng nhập mật khẩu' })
  .min(options.LIMITS.password.min, `Mật khẩu cần ít nhất ${options.LIMITS.password.min} ký tự`)
  .max(options.LIMITS.password.max, `Mật khẩu tối đa ${options.LIMITS.password.max} ký tự`);

const displayName = z
  .string()
  .trim()
  .min(options.LIMITS.displayName.min, `Tên cần ít nhất ${options.LIMITS.displayName.min} ký tự`)
  .max(options.LIMITS.displayName.max);

/**
 * Ngày sinh: nhận "YYYY-MM-DD" hoặc "DD/MM/YYYY" (như màn hình Birthday của app), trả về "YYYY-MM-DD".
 * Kiểm tra ngày có thật (không nhận 31/02) và đủ 18 tuổi.
 */
const birthday = z.string().transform((v, ctx) => {
  const s = String(v).trim();
  let m = s.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/);
  let y, mo, d;
  if (m) [, y, mo, d] = m.map(Number);
  else if ((m = s.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/))) [, d, mo, y] = m.map(Number);
  else {
    ctx.addIssue({ code: z.ZodIssueCode.custom, message: 'Ngày sinh phải có dạng YYYY-MM-DD hoặc DD/MM/YYYY' });
    return z.NEVER;
  }
  const date = new Date(Date.UTC(y, mo - 1, d));
  if (date.getUTCFullYear() !== y || date.getUTCMonth() !== mo - 1 || date.getUTCDate() !== d) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, message: 'Ngày sinh không hợp lệ' });
    return z.NEVER;
  }
  const iso = `${y}-${String(mo).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
  const age = ageFromBirthday(iso);
  if (age < 18) ctx.addIssue({ code: z.ZodIssueCode.custom, message: 'Bạn phải đủ 18 tuổi' });
  else if (age > 100) ctx.addIssue({ code: z.ZodIssueCode.custom, message: 'Ngày sinh không hợp lệ' });
  return iso;
});

/**
 * Lựa chọn trong danh mục: nhận MÃ hoặc NHÃN tiếng Việt (VD "never" / "Không hút thuốc"), trả về mã.
 * key: gender | interestedIn | relationshipGoal | zodiac | smoking | drinking | exercise | education
 */
const option = (key) =>
  z.string().transform((v, ctx) => {
    const code = options.toCode(key, v);
    if (!code) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: `Giá trị không hợp lệ. Chọn một trong: ${options.codes(key).join(', ')}`,
      });
      return z.NEVER;
    }
    return code;
  });

/** Số nguyên trong khoảng (nhận cả chuỗi số "170") */
const intRange = (min, max, label) =>
  z.coerce
    .number({ invalid_type_error: `${label} phải là số` })
    .int(`${label} phải là số nguyên`)
    .min(min, `${label} tối thiểu ${min}`)
    .max(max, `${label} tối đa ${max}`);

module.exports = { phone, otp, password8, displayName, birthday, option, intRange };
