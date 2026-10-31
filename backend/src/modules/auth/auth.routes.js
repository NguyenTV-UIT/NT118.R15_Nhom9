const router = require('express').Router();
const h = require('../../utils/asyncHandler');
const validate = require('../../middlewares/validate');
const { requireAuth } = require('../../middlewares/auth');
const schema = require('./auth.validation');
const ctrl = require('./auth.controller');

router.post('/register', validate({ body: schema.register }), h(ctrl.register));
router.post('/login', validate({ body: schema.login }), h(ctrl.login));
router.post('/refresh', validate({ body: schema.refresh }), h(ctrl.refresh));
router.post('/logout', validate({ body: schema.refresh }), h(ctrl.logout));
router.put('/password', requireAuth, validate({ body: schema.changePassword }), h(ctrl.changePassword));

module.exports = router;
