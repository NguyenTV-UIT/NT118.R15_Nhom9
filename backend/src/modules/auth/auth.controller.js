const authService = require('./auth.service');
const { ok, created } = require('../../utils/response');

exports.register = async (req, res) => {
  created(res, await authService.register(req.body), 'Đăng ký thành công, hãy hoàn tất hồ sơ');
};

exports.forgotPassword = async (req, res) => {
  ok(res, await authService.forgotPassword(req.body), 'Đã gửi mã OTP tới email của bạn');
};

exports.verifyResetOtpEmail = async (req, res) => {
  ok(res, await authService.verifyResetOtpByEmail(req.body), 'Xác thực thành công, hãy đặt mật khẩu mới');
};

exports.login = async (req, res) => {
  ok(res, await authService.login(req.body), 'Đăng nhập thành công');
};

exports.refresh = async (req, res) => {
  ok(res, await authService.refresh(req.body.refreshToken), 'Làm mới token thành công');
};

exports.logout = async (req, res) => {
  await authService.logout(req.body.refreshToken);
  ok(res, null, 'Đã đăng xuất');
};

exports.changePassword = async (req, res) => {
  await authService.changePassword(req.user.id, req.body);
  ok(res, null, 'Đổi mật khẩu thành công, vui lòng đăng nhập lại trên các thiết bị khác');
};

// ── Số điện thoại + OTP ──
const OTP_SENT = {
  register: 'Đã gửi mã OTP đăng ký',
  login: 'Đã gửi lại mã OTP đăng nhập',
  reset_password: 'Đã gửi mã OTP đặt lại mật khẩu',
};

exports.sendOtp = async (req, res) => {
  ok(res, await authService.sendOtp(req.body), OTP_SENT[req.body.purpose]);
};

exports.registerPhone = async (req, res) => {
  created(res, await authService.registerByPhone(req.body), 'Đăng ký thành công, hãy hoàn tất hồ sơ');
};

exports.loginPhone = async (req, res) => {
  const result = await authService.loginByPhone(req.body);
  ok(res, result, result.otpRequired ? 'Đã gửi mã OTP, vui lòng nhập để đăng nhập' : 'Đăng nhập thành công');
};

exports.loginPhoneVerify = async (req, res) => {
  ok(res, await authService.verifyLoginOtp(req.body), 'Đăng nhập thành công');
};

exports.verifyResetOtp = async (req, res) => {
  ok(res, await authService.verifyResetOtp(req.body), 'Xác thực thành công, hãy đặt mật khẩu mới');
};

exports.resetPassword = async (req, res) => {
  await authService.resetPassword(req.body);
  ok(res, null, 'Đặt lại mật khẩu thành công! Vui lòng đăng nhập lại.');
};
