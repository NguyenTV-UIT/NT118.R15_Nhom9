const { z } = require('zod');

const feedQuery = z.object({
  limit: z.coerce.number().int().min(1).max(50).default(20),
});

const swipe = z.object({
  targetId: z.number().int().positive(),
  action: z.enum(['like', 'pass', 'superlike']),
});

const matchParam = z.object({
  matchId: z.coerce.number().int().positive(),
});

module.exports = { feedQuery, swipe, matchParam };
