const { z } = require('zod');
const v = require('../../utils/validators');
const { LIMITS } = require('../meta/options');

const optionalText = (max) =>
  z
    .string()
    .trim()
    .max(max, `Tối đa ${max} ký tự`)
    .transform((s) => (s === '' ? null : s))
    .nullable()
    .optional();

/** Lựa chọn trong danh mục, gửi null để xoá. Nhận mã hoặc nhãn tiếng Việt. */
const optionalOption = (key) => v.option(key).nullable().optional();

/**
 * PUT /users/me/profile - gửi field nào sửa field đó.
 * Mỗi màn hình onboarding của app gọi API này với đúng field của màn đó.
 */
const updateProfile = z
  .object({
    displayName: v.displayName.optional(),
    birthday: v.birthday.optional(),
    gender: v.option('gender').optional(),
    showGender: z.boolean().optional(),
    bio: optionalText(LIMITS.bio.max),
    jobTitle: optionalText(100),
    school: optionalText(150),
    city: optionalText(100),
    latitude: z.number().min(-90).max(90).nullable().optional(),
    longitude: z.number().min(-180).max(180).nullable().optional(),
    heightCm: v.intRange(LIMITS.heightCm.min, LIMITS.heightCm.max, 'Chiều cao').nullable().optional(),
    weightKg: v.intRange(LIMITS.weightKg.min, LIMITS.weightKg.max, 'Cân nặng').nullable().optional(),
    relationshipGoal: optionalOption('relationshipGoal'),
    zodiac: optionalOption('zodiac'),
    smoking: optionalOption('smoking'),
    drinking: optionalOption('drinking'),
    exercise: optionalOption('exercise'),
    education: optionalOption('education'),
  })
  .refine((d) => (d.latitude === undefined) === (d.longitude === undefined), {
    message: 'Phải gửi cả latitude và longitude',
    path: ['latitude'],
  });

const updatePreferences = z.object({
  interestedIn: v.option('interestedIn').optional(),
  minAge: v.intRange(LIMITS.age.min, LIMITS.age.max, 'Tuổi').optional(),
  maxAge: v.intRange(LIMITS.age.min, LIMITS.age.max, 'Tuổi').optional(),
  maxDistanceKm: v.intRange(1, 500, 'Khoảng cách').optional(),
});

/** Gửi interestIds (số) hoặc interestNames (tên như trên app), hoặc cả hai. Mảng rỗng = xoá hết. */
const setInterests = z
  .object({
    interestIds: z.array(z.number().int().positive()).max(LIMITS.interests.max).optional(),
    interestNames: z.array(z.string().trim().min(1)).max(LIMITS.interests.max).optional(),
  })
  .refine((d) => d.interestIds !== undefined || d.interestNames !== undefined, {
    message: 'Gửi interestIds hoặc interestNames',
    path: ['interestNames'],
  });

const idParam = (name) => z.object({ [name]: z.coerce.number().int().positive() });

// ── Cài đặt / tài khoản ──
const email = z.string().trim().toLowerCase().email('Email không hợp lệ').max(255);

const updateSettings = z
  .object({
    pushEnabled: z.boolean().optional(),
    showActiveStatus: z.boolean().optional(),
    showDistance: z.boolean().optional(),
  })
  .refine((d) => Object.values(d).some((x) => x !== undefined), { message: 'Gửi ít nhất 1 công tắc', path: ['pushEnabled'] });

const verifyCurrentEmail = z.object({ email });
const otpOnly = z.object({ otp: v.otp });
const changeEmail = z.object({ emailChangeToken: z.string().min(1, 'Thiếu emailChangeToken'), newEmail: email });
const confirmNewEmail = z.object({ pendingToken: z.string().min(1, 'Thiếu pendingToken'), otp: v.otp });

const deleteAccount = z.object({
  confirm: z.string({ required_error: 'Vui lòng nhập XÓA để xác nhận' }),
  reason: v.option('deleteReason').nullable().optional(),
  details: z.string().trim().max(500).optional(),
});

const deviceToken = z.object({
  token: z.string().trim().min(10, 'Token không hợp lệ').max(255),
  platform: z.enum(['android', 'web']).default('android'),
});
const deviceTokenRemove = z.object({ token: z.string().trim().min(1).max(255) });

// ── An toàn ──
const report = z.object({
  reason: v.option('reportReason'),
  details: z.string().trim().max(1000, 'Tối đa 1000 ký tự').transform((s) => s || undefined).optional(),
});

module.exports = {
  updateProfile,
  updatePreferences,
  setInterests,
  idParam,
  updateSettings,
  verifyCurrentEmail,
  otpOnly,
  changeEmail,
  confirmNewEmail,
  deleteAccount,
  deviceToken,
  deviceTokenRemove,
  report,
};
