const { z } = require('zod');
const v = require('../../utils/validators');

const email = z.string().trim().toLowerCase().email('Email không hợp lệ').max(255);
// Đổi mật khẩu trong Cài đặt: ≥ 6 ký tự (như màn ChangePassword)
const password = z.string().min(6, 'Mật khẩu tối thiểu 6 ký tự').max(72, 'Mật khẩu tối đa 72 ký tự');

// ── EMAIL (đang dùng, khớp app Android) ──

/** Đăng ký: CHỈ email + mật khẩu (≥ 8 ký tự như màn Register). Hồ sơ điền sau khi đăng ký. */
const register = z.object({
  email,
  password: v.password8,
});

const login = z.object({
  email,
  password: z.string().min(1, 'Vui lòng nhập mật khẩu'),
});

const forgotPassword = z.object({ email });

const verifyResetOtpEmail = z.object({ email, otp: v.otp });

// ── SỐ ĐIỆN THOẠI + OTP (tạm ngừng) ──
const otpSend = z.object({
  phone: v.phone,
  purpose: z.enum(['register', 'login', 'reset_password'], {
    errorMap: () => ({ message: 'purpose phải là register | login | reset_password' }),
  }),
});

const registerPhone = z.object({
  phone: v.phone,
  otp: v.otp,
  password: v.password8,
  displayName: v.displayName,
});

const loginPhone = z.object({
  phone: v.phone,
  password: z.string().min(1, 'Vui lòng nhập mật khẩu'),
});

const loginPhoneVerify = z.object({
  phone: v.phone,
  otp: v.otp,
});

const otpVerifyReset = z.object({
  phone: v.phone,
  otp: v.otp,
});

/** Mật khẩu mới: ≥ 8 ký tự và có ít nhất 1 chữ số (như màn ResetPassword) */
const passwordReset = z.object({
  resetToken: z.string().min(1, 'Thiếu resetToken'),
  newPassword: v.password8.regex(/\d/, 'Mật khẩu cần có ít nhất 1 chữ số'),
});

const refresh = z.object({
  refreshToken: z.string().min(1, 'Thiếu refreshToken'),
});

const changePassword = z.object({
  currentPassword: z.string().min(1),
  newPassword: password,
});

module.exports = {
  register,
  login,
  forgotPassword,
  verifyResetOtpEmail,
  otpSend,
  registerPhone,
  loginPhone,
  loginPhoneVerify,
  otpVerifyReset,
  passwordReset,
  refresh,
  changePassword,
};
