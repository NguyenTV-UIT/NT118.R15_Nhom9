const router = require('express').Router();
const h = require('../../utils/asyncHandler');
const validate = require('../../middlewares/validate');
const rateLimit = require('../../middlewares/rateLimit');
const { requireAuth } = require('../../middlewares/auth');
const { uploadImage } = require('../../middlewares/upload');
const schema = require('./users.validation');
const ctrl = require('./users.controller');

const otpLimit = rateLimit({ name: 'email-otp', windowSec: 60, max: 5 });
const verifyLimit = rateLimit({ name: 'email-verify', windowSec: 60, max: 15 });
const reportLimit = rateLimit({ name: 'report', windowSec: 3600, max: 20 });

router.use(requireAuth);

// ── Hồ sơ của mình ──
router.get('/me', h(ctrl.getMe));
router.put('/me/profile', validate({ body: schema.updateProfile }), h(ctrl.updateProfile));
router.put('/me/preferences', validate({ body: schema.updatePreferences }), h(ctrl.updatePreferences));
router.put('/me/interests', validate({ body: schema.setInterests }), h(ctrl.setInterests));
router.post('/me/onboarding/complete', h(ctrl.completeOnboarding));
router.post('/me/photos', uploadImage.single('photo'), h(ctrl.addPhoto));
router.delete('/me/photos/:photoId', validate({ params: schema.idParam('photoId') }), h(ctrl.deletePhoto));

// ── Cài đặt (màn Settings) ──
router.get('/me/settings', h(ctrl.getSettings));
router.put('/me/settings', validate({ body: schema.updateSettings }), h(ctrl.updateSettings));
router.get('/me/sessions', h(ctrl.listSessions));
router.delete('/me/sessions', h(ctrl.revokeOtherSessions)); // đăng xuất tất cả thiết bị khác
router.delete('/me/sessions/:sessionId', validate({ params: schema.idParam('sessionId') }), h(ctrl.revokeSession));
router.get('/me/blocks', h(ctrl.listBlocks));

// ── Đổi email: xác thực email hiện tại -> OTP -> email mới ──
router.post('/me/email/verify-current', otpLimit, validate({ body: schema.verifyCurrentEmail }), h(ctrl.sendCurrentEmailOtp));
router.post('/me/email/verify-otp', verifyLimit, validate({ body: schema.otpOnly }), h(ctrl.verifyCurrentEmailOtp));
router.put('/me/email', verifyLimit, validate({ body: schema.changeEmail }), h(ctrl.changeEmail));
router.post('/me/email/confirm', verifyLimit, validate({ body: schema.confirmNewEmail }), h(ctrl.confirmNewEmail));

// ── Thông báo đẩy ──
router.post('/me/device-token', validate({ body: schema.deviceToken }), h(ctrl.registerDeviceToken));
router.delete('/me/device-token', validate({ body: schema.deviceTokenRemove }), h(ctrl.removeDeviceToken));

// ── Xoá tài khoản ──
router.delete('/me', validate({ body: schema.deleteAccount }), h(ctrl.deleteAccount));

router.get('/interests', h(ctrl.listInterests));

// ── Người khác ──
router.get('/:id', validate({ params: schema.idParam('id') }), h(ctrl.getById));
router.post('/:id/block', validate({ params: schema.idParam('id') }), h(ctrl.block));
router.delete('/:id/block', validate({ params: schema.idParam('id') }), h(ctrl.unblock));
router.post('/:id/report', reportLimit, validate({ params: schema.idParam('id'), body: schema.report }), h(ctrl.report));

module.exports = router;
