const router = require('express').Router();
const h = require('../../utils/asyncHandler');
const validate = require('../../middlewares/validate');
const { requireAuth } = require('../../middlewares/auth');
const schema = require('./chats.validation');
const ctrl = require('./chats.controller');

const call = validate({ params: schema.callParam });

router.use(requireAuth);

// /api/calls  (bắt đầu gọi: POST /api/chats/:matchId/calls)
router.get('/ice-servers', h(ctrl.iceServers));
router.get('/active', h(ctrl.activeCall));
router.get('/:callId', call, h(ctrl.getCall));
router.post('/:callId/accept', call, h(ctrl.acceptCall));
router.post('/:callId/reject', call, h(ctrl.rejectCall));
router.post('/:callId/cancel', call, h(ctrl.cancelCall));
router.post('/:callId/end', call, h(ctrl.endCall));

module.exports = router;
