const { z } = require('zod');
const env = require('../../config/env');

const id = z.coerce.number().int().positive();
const clientId = z.string().trim().min(1).max(64).optional();

const matchParam = z.object({ matchId: id });
const callParam = z.object({ callId: id });

// GET /chats?q=&filter=all|close|unread|new
const listQuery = z.object({
  q: z.string().trim().max(100).transform((v) => v || undefined).optional(),
  filter: z.enum(['all', 'close', 'unread', 'new']).default('all'),
  limit: z.coerce.number().int().min(1).max(200).default(100),
});

// GET /chats/:matchId/messages?before=&after=&limit=
const messagesQuery = z
  .object({
    before: id.optional(),
    after: id.optional(),
    limit: z.coerce.number().int().min(1).max(100).default(30),
  })
  .refine((d) => !(d.before && d.after), { message: 'Chỉ gửi before hoặc after', path: ['before'] });

const sendMessage = z.object({
  type: z.literal('text').default('text'), // ảnh gửi qua POST /chats/:matchId/photos
  content: z
    .string({ required_error: 'Vui lòng nhập nội dung tin nhắn' })
    .trim()
    .min(1, 'Tin nhắn không được để trống')
    .max(env.chat.maxMessageLength, `Tin nhắn tối đa ${env.chat.maxMessageLength} ký tự`),
  clientId,
});

const startCall = z.object({
  type: z.enum(['voice', 'video'], { errorMap: () => ({ message: 'type phải là voice hoặc video' }) }).default('voice'),
});

module.exports = { matchParam, callParam, listQuery, messagesQuery, sendMessage, startCall, clientId };
