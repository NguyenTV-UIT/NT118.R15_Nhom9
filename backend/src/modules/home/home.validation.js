const { z } = require('zod');
const { option } = require('../../utils/validators');
const options = require('../meta/options');
const { LIMITS } = options;

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

const ANY = new Set(['', 'any', 'all', 'khong quan trong', 'không quan trọng']);

/**
 * Bộ lọc chọn nhiều: "serious,marriage" hoặc lặp tham số (?zodiac=aries&zodiac=leo). Nhận mã hoặc nhãn tiếng Việt.
 * Không gửi / "any" / "Không quan trọng" = không lọc.
 */
const optionList = (key) =>
  z
    .union([z.string(), z.array(z.string())])
    .transform((v, ctx) => {
      const parts = (Array.isArray(v) ? v : v.split(',')).map((x) => x.trim()).filter((x) => !ANY.has(options.norm(x)));
      const codes = [];
      for (const part of parts) {
        const code = options.toCode(key, part);
        if (!code) {
          ctx.addIssue({ code: z.ZodIssueCode.custom, message: `"${part}" không hợp lệ. Chọn trong: ${options.codes(key).join(', ')}` });
          return z.NEVER;
        }
        if (!codes.includes(code)) codes.push(code);
      }
      return codes.length ? codes : undefined;
    })
    .optional();

const range = (limit) => z.coerce.number().int().min(limit.min).max(limit.max).optional();

const searchQuery = z
  .object({
    gender: option('interestedIn').optional(), // male | female | other | everyone (hoặc nhãn tiếng Việt)
    minAge: z.coerce.number().int().min(18).max(100).optional(),
    maxAge: z.coerce.number().int().min(18).max(100).optional(),
    maxDistanceKm: z.coerce.number().int().min(1).max(500).optional(),
    interestIds: idList.optional(),
    city: optionalTrimmed(100),
    q: optionalTrimmed(100),
    hasPhoto: boolString.optional(),
    // ── Bộ lọc nâng cao (FiltersActivity) ──
    relationshipGoal: optionList('relationshipGoal'),
    zodiac: optionList('zodiac'),
    smoking: optionList('smoking'),
    drinking: optionList('drinking'),
    exercise: optionList('exercise'),
    education: optionList('education'),
    minHeight: range(LIMITS.heightCm),
    maxHeight: range(LIMITS.heightCm),
    minWeight: range(LIMITS.weightKg),
    maxWeight: range(LIMITS.weightKg),
    sameCity: boolString.optional(),
    sameDistrict: boolString.optional(), // chưa hỗ trợ (hồ sơ chưa có quận/huyện) -> bỏ qua, báo trong meta.ignoredFilters
    orientation: optionalTrimmed(30), // chưa hỗ trợ (hồ sơ chưa có xu hướng tính dục) -> bỏ qua, báo trong meta.ignoredFilters
    sort: z.enum(['score', 'distance', 'newest', 'active']).default('score'),
    limit: z.coerce.number().int().min(1).max(50).default(20),
  })
  .refine((d) => d.minAge === undefined || d.maxAge === undefined || d.minAge <= d.maxAge, {
    message: 'minAge phải nhỏ hơn hoặc bằng maxAge',
    path: ['minAge'],
  })
  .refine((d) => d.minHeight === undefined || d.maxHeight === undefined || d.minHeight <= d.maxHeight, {
    message: 'minHeight phải nhỏ hơn hoặc bằng maxHeight',
    path: ['minHeight'],
  })
  .refine((d) => d.minWeight === undefined || d.maxWeight === undefined || d.minWeight <= d.maxWeight, {
    message: 'minWeight phải nhỏ hơn hoặc bằng maxWeight',
    path: ['minWeight'],
  });

const likesQuery = z.object({
  limit: z.coerce.number().int().min(1).max(100).default(50),
});

const matchesQuery = z.object({
  sort: z.enum(['activity', 'recent']).default('activity'),
  newOnly: boolString.optional(),
  limit: z.coerce.number().int().min(1).max(200).default(100),
});

const swipe = z.object({
  targetId: z.number().int().positive(),
  action: z.enum(['like', 'pass', 'superlike']),
});

const matchParam = z.object({
  matchId: z.coerce.number().int().positive(),
});

module.exports = { feedQuery, searchQuery, likesQuery, matchesQuery, swipe, matchParam };
