const authService = require('./auth.service');
const { ok, created } = require('../../utils/response');

exports.register = async (req, res) => {
  created(res, await authService.register(req.body), 'Đăng ký thành công');
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
