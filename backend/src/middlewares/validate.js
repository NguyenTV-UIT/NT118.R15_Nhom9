const ApiError = require('../utils/ApiError');

/**
 * validate({ body: zodSchema, query: zodSchema, params: zodSchema })
 * Dữ liệu sau khi parse (đã ép kiểu, bỏ field thừa) được ghi đè lại vào req.
 */
module.exports = (schemas) => (req, res, next) => {
  const errors = [];
  for (const key of ['params', 'query', 'body']) {
    if (!schemas[key]) continue;
    const result = schemas[key].safeParse(req[key] ?? {});
    if (result.success) {
      req[key] = result.data;
    } else {
      for (const issue of result.error.issues) {
        errors.push({ field: issue.path.join('.') || key, message: issue.message });
      }
    }
  }
  if (errors.length) return next(ApiError.badRequest('Dữ liệu không hợp lệ', errors));
  next();
};
