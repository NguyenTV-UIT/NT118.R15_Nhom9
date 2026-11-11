const usersService = require('./users.service');
const account = require('../account/account.service');
const safety = require('../safety/safety.service');
const sessions = require('../sessions/sessions.service');
const ApiError = require('../../utils/ApiError');
const { ok, created } = require('../../utils/response');

exports.getMe = async (req, res) => {
  ok(res, await usersService.getMe(req.user.id));
};

exports.updateProfile = async (req, res) => {
  ok(res, await usersService.updateProfile(req.user.id, req.body), 'Cập nhật hồ sơ thành công');
};

exports.updatePreferences = async (req, res) => {
  ok(res, await usersService.updatePreferences(req.user.id, req.body), 'Cập nhật tiêu chí thành công');
};

exports.listInterests = async (req, res) => {
  ok(res, await usersService.listInterests());
};

exports.setInterests = async (req, res) => {
  ok(res, await usersService.setInterests(req.user.id, req.body), 'Cập nhật sở thích thành công');
};

exports.addPhoto = async (req, res) => {
  if (!req.file) throw ApiError.badRequest('Thiếu file ảnh (field "photo")');
  created(res, await usersService.addPhoto(req.user.id, req.file), 'Tải ảnh lên thành công');
};

exports.deletePhoto = async (req, res) => {
  await usersService.deletePhoto(req.user.id, req.params.photoId);
  ok(res, null, 'Đã xoá ảnh');
};

exports.getById = async (req, res) => {
  ok(res, await usersService.getPublicProfile(req.user.id, req.params.id));
};

exports.completeOnboarding = async (req, res) => {
  ok(res, await usersService.completeOnboarding(req.user.id), 'Hồ sơ đã sẵn sàng!');
};

// ── Cài đặt ──
exports.getSettings = async (req, res) => {
  ok(res, await account.getSettings(req.user.id));
};

exports.updateSettings = async (req, res) => {
  ok(res, await account.updateSettings(req.user.id, req.body), 'Đã lưu cài đặt');
};

// ── Thiết bị đang đăng nhập ──
exports.listSessions = async (req, res) => {
  ok(res, await sessions.list(req.user.id, req.user.sessionId));
};

exports.revokeSession = async (req, res) => {
  await sessions.revoke(req.user.id, req.params.sessionId);
  ok(res, null, 'Đã đăng xuất thiết bị');
};

exports.revokeOtherSessions = async (req, res) => {
  const count = await sessions.revokeAll(req.user.id, req.user.sessionId);
  ok(res, { loggedOutDevices: count }, count ? `Đã đăng xuất ${count} thiết bị khác` : 'Không có thiết bị nào khác đang đăng nhập');
};

// ── Đổi email ──
exports.sendCurrentEmailOtp = async (req, res) => {
  ok(res, await account.sendCurrentEmailOtp(req.user.id, req.body), 'Đã gửi mã OTP tới email hiện tại');
};

exports.verifyCurrentEmailOtp = async (req, res) => {
  ok(res, await account.verifyCurrentEmailOtp(req.user.id, req.body), 'Xác thực thành công, hãy nhập email mới');
};

exports.changeEmail = async (req, res) => {
  const result = await account.changeEmail(req.user.id, req.body);
  ok(res, result, result.otpRequired ? 'Đã gửi mã OTP tới email mới' : 'Đổi email thành công');
};

exports.confirmNewEmail = async (req, res) => {
  ok(res, await account.confirmNewEmail(req.user.id, req.body), 'Đổi email thành công');
};

// ── Xoá tài khoản ──
exports.deleteAccount = async (req, res) => {
  await account.deleteAccount(req.user.id, req.body);
  ok(res, null, 'Đã xoá tài khoản vĩnh viễn');
};

// ── Thông báo đẩy ──
exports.registerDeviceToken = async (req, res) => {
  await account.registerDeviceToken(req.user.id, req.body);
  ok(res, null, 'Đã đăng ký thiết bị nhận thông báo');
};

exports.removeDeviceToken = async (req, res) => {
  await account.removeDeviceToken(req.user.id, req.body);
  ok(res, null, 'Đã huỷ đăng ký thiết bị');
};

// ── Chặn / báo cáo ──
exports.listBlocks = async (req, res) => {
  ok(res, await safety.listBlocks(req.user.id));
};

exports.block = async (req, res) => {
  ok(res, await safety.block(req.user.id, req.params.id), 'Đã chặn người này');
};

exports.unblock = async (req, res) => {
  await safety.unblock(req.user.id, req.params.id);
  ok(res, null, 'Đã bỏ chặn');
};

exports.report = async (req, res) => {
  created(res, await safety.report(req.user.id, req.params.id, req.body), 'Đã báo cáo và chặn');
};
