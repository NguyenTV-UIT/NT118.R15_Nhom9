const { z } = require('zod');

const feedQuery = z.object({
  limit: z.coerce.number().int().min(1).max(50).default(20),
});

// Query string luôn là chuỗi -> ép kiểu. VD: ?gender=female&minAge=20&interestIds=1,3,5&hasPhoto=true
const boolString = z.enum(['true', 'false', '1', '0']).transform((v) => v === 'true' || v === '1');
const idList = z
  .string()
  .regex(/^\d+(,\d+)*$/, 'interestIds dạng 1,3,5')
  .transform((v) => [...new Set(v.split(',').map(Number))])
  .refine((a) => a.length <= 10, 'Tối đa 10 sở thích');
const optionalTrimmed = (max) =>
  z
    .string()
    .trim()
    .max(max)
    .transform((v) => v || undefined)
    .optional();

const searchQuery = z
  .object({
    gender: z.enum(['male', 'female', 'everyone']).optional(),
    minAge: z.coerce.number().int().min(18).max(100).optional(),
    maxAge: z.coerce.number().int().min(18).max(100).optional(),
    maxDistanceKm: z.coerce.number().int().min(1).max(500).optional(),
    interestIds: idList.optional(),
    city: optionalTrimmed(100),
    q: optionalTrimmed(100),
    hasPhoto: boolString.optional(),
    sort: z.enum(['score', 'distance', 'newest', 'active']).default('score'),
    limit: z.coerce.number().int().min(1).max(50).default(20),
  })
  .refine((d) => d.minAge === undefined || d.maxAge === undefined || d.minAge <= d.maxAge, {
    message: 'minAge phải nhỏ hơn hoặc bằng maxAge',
    path: ['minAge'],
  });

const swipe = z.object({
  targetId: z.number().int().positive(),
  action: z.enum(['like', 'pass', 'superlike']),
});

const matchParam = z.object({
  matchId: z.coerce.number().int().positive(),
});

module.exports = { feedQuery, searchQuery, swipe, matchParam };
