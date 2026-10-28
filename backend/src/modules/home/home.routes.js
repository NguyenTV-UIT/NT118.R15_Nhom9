const router = require('express').Router();
const h = require('../../utils/asyncHandler');
const validate = require('../../middlewares/validate');
const { requireAuth } = require('../../middlewares/auth');
const schema = require('./home.validation');
const ctrl = require('./home.controller');

router.use(requireAuth);

router.get('/feed', validate({ query: schema.feedQuery }), h(ctrl.getFeed));
router.get('/search', validate({ query: schema.searchQuery }), h(ctrl.search));
router.post('/swipe', validate({ body: schema.swipe }), h(ctrl.swipe));
router.get('/matches', h(ctrl.getMatches));
router.delete('/matches/:matchId', validate({ params: schema.matchParam }), h(ctrl.unmatch));

module.exports = router;
