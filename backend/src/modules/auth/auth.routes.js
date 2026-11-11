const router = require('express').Router();
const h = require('../../utils/asyncHandler');
const validate = require('../../middlewares/validate');
const rateLimit = require('../../middlewares/rateLimit');
const { requireAuth } = require('../../middlewares/auth');
const schema = require('./auth.validation');
const ctrl = require('./auth.controller');
const env = require('../../config/env');
const ApiError = require('../../utils/ApiError');

// Chống dò mật khẩu / spam SMS: tối đa N request / phút / IP
const loginLimit = rateLimit({ name: 'login', windowSec: 60, max: 30 });
const otpLimit = rateLimit({ name: 'otp', windowSec: 60, max: 5 });
const verifyLimit = rateLimit({ name: 'verify', windowSec: 60, max: 15 });

// ── Email (đang dùng) ──
router.post('/register', loginLimit, validate({ body: schema.register }), h(ctrl.register));
router.post('/login', loginLimit, validate({ body: schema.login }), h(ctrl.login));

// OTP cho đăng ký / đăng nhập (tuỳ chọn - bật bằng REGISTER_OTP_REQUIRED / EMAIL_LOGIN_OTP_REQUIRED)
router.post('/register/send-otp', otpLimit, validate({ body: schema.emailOnly }), h(ctrl.sendRegisterOtp));
router.post('/login/resend-otp', otpLimit, validate({ body: schema.emailOnly }), h(ctrl.resendLoginOtp));
router.post('/login/verify-otp', verifyLimit, validate({ body: schema.emailOtp }), h(ctrl.verifyLoginOtpEmail));

// Quên mật khẩu: email -> OTP -> resetToken -> mật khẩu mới
router.post('/password/forgot', otpLimit, validate({ body: schema.forgotPassword }), h(ctrl.forgotPassword));
router.post('/password/verify-otp', verifyLimit, validate({ body: schema.verifyResetOtpEmail }), h(ctrl.verifyResetOtpEmail));
router.post('/password/reset', verifyLimit, validate({ body: schema.passwordReset }), h(ctrl.resetPassword));

// ── Số điện thoại + OTP: TẠM NGỪNG (bật lại bằng PHONE_AUTH_ENABLED=true trong .env) ──
const phoneAuth = (req, res, next) =>
  env.phoneAuthEnabled
    ? next()
    : next(new ApiError(403, 'Đăng ký / đăng nhập bằng số điện thoại đang tạm ngừng, vui lòng dùng email', 'PHONE_AUTH_DISABLED'));

router.post('/otp/send', phoneAuth, otpLimit, validate({ body: schema.otpSend }), h(ctrl.sendOtp));
router.post('/register/phone', phoneAuth, verifyLimit, validate({ body: schema.registerPhone }), h(ctrl.registerPhone));
router.post('/login/phone', phoneAuth, loginLimit, validate({ body: schema.loginPhone }), h(ctrl.loginPhone));
router.post('/login/phone/verify', phoneAuth, verifyLimit, validate({ body: schema.loginPhoneVerify }), h(ctrl.loginPhoneVerify));
router.post('/otp/verify', phoneAuth, verifyLimit, validate({ body: schema.otpVerifyReset }), h(ctrl.verifyResetOtp));

router.post('/refresh', validate({ body: schema.refresh }), h(ctrl.refresh));
router.post('/logout', validate({ body: schema.refresh }), h(ctrl.logout));
router.put('/password', requireAuth, validate({ body: schema.changePassword }), h(ctrl.changePassword));

module.exports = router;
