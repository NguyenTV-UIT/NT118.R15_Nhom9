const { z } = require('zod');
const { ageFromBirthday } = require('../../utils/age');

const email = z.string().trim().toLowerCase().email('Email không hợp lệ').max(255);
const password = z.string().min(6, 'Mật khẩu tối thiểu 6 ký tự').max(72, 'Mật khẩu tối đa 72 ký tự');

const birthday = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/, 'Ngày sinh phải có dạng YYYY-MM-DD')
  .refine((v) => !Number.isNaN(Date.parse(v)), 'Ngày sinh không hợp lệ')
  .refine((v) => ageFromBirthday(v) >= 18, 'Bạn phải đủ 18 tuổi')
  .refine((v) => ageFromBirthday(v) <= 100, 'Ngày sinh không hợp lệ');

const register = z.object({
  email,
  password,
  displayName: z.string().trim().min(1, 'Tên hiển thị không được trống').max(100),
  birthday,
  gender: z.enum(['male', 'female', 'other']),
});

const login = z.object({
  email,
  password: z.string().min(1, 'Vui lòng nhập mật khẩu'),
});

const refresh = z.object({
  refreshToken: z.string().min(1, 'Thiếu refreshToken'),
});

const changePassword = z.object({
  currentPassword: z.string().min(1),
  newPassword: password,
});

module.exports = { register, login, refresh, changePassword, birthday };
