const router = require('express').Router();
const h = require('../../utils/asyncHandler');
const validate = require('../../middlewares/validate');
const { requireAuth } = require('../../middlewares/auth');
const { uploadImage } = require('../../middlewares/upload');
const schema = require('./users.validation');
const ctrl = require('./users.controller');

router.use(requireAuth);

router.get('/me', h(ctrl.getMe));
router.put('/me/profile', validate({ body: schema.updateProfile }), h(ctrl.updateProfile));
router.put('/me/preferences', validate({ body: schema.updatePreferences }), h(ctrl.updatePreferences));
router.put('/me/interests', validate({ body: schema.setInterests }), h(ctrl.setInterests));
router.post('/me/photos', uploadImage.single('photo'), h(ctrl.addPhoto));
router.delete('/me/photos/:photoId', validate({ params: schema.idParam('photoId') }), h(ctrl.deletePhoto));

router.get('/interests', h(ctrl.listInterests));
router.get('/:id', validate({ params: schema.idParam('id') }), h(ctrl.getById));

module.exports = router;
