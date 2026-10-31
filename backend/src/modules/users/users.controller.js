const usersService = require('./users.service');
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
  ok(res, await usersService.setInterests(req.user.id, req.body.interestIds), 'Cập nhật sở thích thành công');
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
