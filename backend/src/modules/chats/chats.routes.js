const router = require('express').Router();
const h = require('../../utils/asyncHandler');
const validate = require('../../middlewares/validate');
const rateLimit = require('../../middlewares/rateLimit');
const { requireAuth } = require('../../middlewares/auth');
const { uploadImage } = require('../../middlewares/upload');
const schema = require('./chats.validation');
const ctrl = require('./chats.controller');

const sendLimit = rateLimit({ name: 'chat-send', windowSec: 60, max: 120 });
const callLimit = rateLimit({ name: 'call-start', windowSec: 60, max: 10 });
const match = validate({ params: schema.matchParam });

router.use(requireAuth);

// /api/chats
router.get('/', validate({ query: schema.listQuery }), h(ctrl.list));
router.get('/intimacy', h(ctrl.intimacy));
router.get('/:matchId', match, h(ctrl.get));
router.get('/:matchId/messages', validate({ params: schema.matchParam, query: schema.messagesQuery }), h(ctrl.messages));
router.post('/:matchId/messages', sendLimit, validate({ params: schema.matchParam, body: schema.sendMessage }), h(ctrl.send));
router.post('/:matchId/photos', sendLimit, match, h(ctrl.ensurePhotoUnlocked), uploadImage.single('photo'), h(ctrl.sendPhoto));
router.post('/:matchId/read', match, h(ctrl.read));
router.post('/:matchId/calls', callLimit, validate({ params: schema.matchParam, body: schema.startCall }), h(ctrl.startCall));

module.exports = router;
