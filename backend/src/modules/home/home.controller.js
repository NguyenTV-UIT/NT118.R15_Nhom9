const homeService = require('./home.service');
const { ok } = require('../../utils/response');

function feedMessage(items, meta) {
  if (meta.expanded) {
    const e = meta.expanded;
    const km = e.maxDistanceKm ? `${e.maxDistanceKm} km, ` : '';
    return `Ít người phù hợp, đã mở rộng tìm kiếm: ${km}${e.minAge}-${e.maxAge} tuổi`;
  }
  return items.length ? 'OK' : 'Không còn ai phù hợp, hãy thử thay đổi tiêu chí';
}

exports.getFeed = async (req, res) => {
  const { items, meta } = await homeService.getFeed(req.user.id, req.query.limit);
  ok(res, items, feedMessage(items, meta), 200, meta);
};

exports.search = async (req, res) => {
  const { items, meta } = await homeService.search(req.user.id, req.query);
  ok(res, items, `Tìm thấy ${items.length} người`, 200, meta);
};

exports.getTaste = async (req, res) => {
  ok(res, await homeService.getTasteSummary(req.user.id));
};

exports.swipe = async (req, res) => {
  const result = await homeService.swipe(req.user.id, req.body.targetId, req.body.action);
  ok(res, result, result.matched ? "It's a match!" : 'Đã ghi nhận');
};

exports.getLikes = async (req, res) => {
  const { items, meta } = await homeService.getLikes(req.user.id, req.query.limit);
  ok(res, items, items.length ? 'OK' : 'Chưa có ai thích bạn hôm nay', 200, meta);
};

exports.getMatches = async (req, res) => {
  ok(res, await homeService.getMatches(req.user.id, req.query));
};

exports.unmatch = async (req, res) => {
  await homeService.unmatch(req.user.id, req.params.matchId);
  ok(res, null, 'Đã huỷ match');
};
