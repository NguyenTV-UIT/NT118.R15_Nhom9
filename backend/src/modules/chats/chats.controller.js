const chats = require('./chats.service');
const calls = require('./calls.service');
const schema = require('./chats.validation');
const env = require('../../config/env');
const ApiError = require('../../utils/ApiError');
const { ok, created } = require('../../utils/response');

const UNLOCK_MESSAGE = {
  photo: 'Đã mở khoá gửi ảnh!',
  call: 'Hai bạn đã thân thiết - mở khoá gọi thoại và video!',
};

// ── Cuộc trò chuyện ──
exports.list = async (req, res) => {
  const { items, meta } = await chats.listChats(req.user.id, req.query);
  ok(res, items, 'OK', 200, meta);
};

exports.intimacy = async (req, res) => {
  ok(res, await chats.getIntimacyOverview(req.user.id));
};

exports.get = async (req, res) => {
  ok(res, await chats.getChat(req.user.id, req.params.matchId));
};

exports.messages = async (req, res) => {
  const { items, meta } = await chats.getMessages(req.user.id, req.params.matchId, req.query);
  ok(res, items, 'OK', 200, meta);
};

function sent(res, result) {
  const message = UNLOCK_MESSAGE[result.unlocked] || 'Đã gửi';
  ok(res, result, message, result.duplicate ? 200 : 201);
}

exports.send = async (req, res) => {
  sent(res, await chats.sendText(req.user.id, req.params.matchId, req.body));
};

/** Chặn trước khi multer nhận file. */
exports.ensurePhotoUnlocked = async (req, res, next) => {
  await chats.assertPhotoUnlocked(req.user.id, req.params.matchId);
  next();
};

exports.sendPhoto = async (req, res) => {
  if (!req.file) throw ApiError.badRequest('Thiếu file ảnh (field "photo")');
  const parsed = schema.clientId.safeParse(req.body?.clientId || undefined);
  sent(res, await chats.sendPhoto(req.user.id, req.params.matchId, req.file, { clientId: parsed.success ? parsed.data : undefined }));
};

exports.read = async (req, res) => {
  ok(res, await chats.markRead(req.user.id, req.params.matchId), 'Đã đánh dấu đã đọc');
};

// ── Cuộc gọi ──
exports.startCall = async (req, res) => {
  created(res, await calls.start(req.user.id, req.params.matchId, req.body), 'Đang gọi...');
};

exports.activeCall = async (req, res) => {
  ok(res, await calls.getActive(req.user.id));
};

exports.iceServers = async (req, res) => {
  ok(res, { iceServers: env.iceServers });
};

exports.getCall = async (req, res) => {
  ok(res, await calls.get(req.user.id, req.params.callId));
};

exports.acceptCall = async (req, res) => {
  ok(res, await calls.accept(req.user.id, req.params.callId), 'Đã nhận cuộc gọi');
};

exports.rejectCall = async (req, res) => {
  ok(res, await calls.reject(req.user.id, req.params.callId), 'Đã từ chối cuộc gọi');
};

exports.cancelCall = async (req, res) => {
  ok(res, await calls.cancel(req.user.id, req.params.callId), 'Đã huỷ cuộc gọi');
};

exports.endCall = async (req, res) => {
  ok(res, await calls.end(req.user.id, req.params.callId), 'Đã kết thúc cuộc gọi');
};
