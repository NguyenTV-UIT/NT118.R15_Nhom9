const { z } = require('zod');
const { birthday } = require('../auth/auth.validation');

const optionalText = (max) =>
  z
    .string()
    .trim()
    .max(max)
    .transform((v) => (v === '' ? null : v))
    .nullable()
    .optional();

const updateProfile = z
  .object({
    displayName: z.string().trim().min(1).max(100).optional(),
    birthday: birthday.optional(),
    gender: z.enum(['male', 'female', 'other']).optional(),
    bio: optionalText(500),
    jobTitle: optionalText(100),
    school: optionalText(150),
    city: optionalText(100),
    latitude: z.number().min(-90).max(90).nullable().optional(),
    longitude: z.number().min(-180).max(180).nullable().optional(),
  })
  .refine((d) => (d.latitude === undefined) === (d.longitude === undefined), {
    message: 'Phải gửi cả latitude và longitude',
    path: ['latitude'],
  });

const updatePreferences = z.object({
  interestedIn: z.enum(['male', 'female', 'everyone']).optional(),
  minAge: z.number().int().min(18).max(100).optional(),
  maxAge: z.number().int().min(18).max(100).optional(),
  maxDistanceKm: z.number().int().min(1).max(500).optional(),
});

const setInterests = z.object({
  interestIds: z.array(z.number().int().positive()).max(10, 'Tối đa 10 sở thích'),
});

const idParam = (name) => z.object({ [name]: z.coerce.number().int().positive() });

module.exports = { updateProfile, updatePreferences, setInterests, idParam };
