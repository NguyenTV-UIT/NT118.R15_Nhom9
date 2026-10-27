const homeService = require('./home.service');
const { ok } = require('../../utils/response');

exports.getFeed = async (req, res) => {
  ok(res, await homeService.getFeed(req.user.id, req.query.limit));
};

exports.swipe = async (req, res) => {
  const result = await homeService.swipe(req.user.id, req.body.targetId, req.body.action);
  ok(res, result, result.matched ? "It's a match!" : 'Đã ghi nhận');
};

exports.getMatches = async (req, res) => {
  ok(res, await homeService.getMatches(req.user.id));
};

exports.unmatch = async (req, res) => {
  await homeService.unmatch(req.user.id, req.params.matchId);
  ok(res, null, 'Đã huỷ match');
};
