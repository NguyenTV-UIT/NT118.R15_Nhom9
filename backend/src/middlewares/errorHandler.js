const multer = require('multer');
const ApiError = require('../utils/ApiError');
const env = require('../config/env');

function notFound(req, res, next) {
  next(ApiError.notFound(`Không có API ${req.method} ${req.originalUrl}`));
}

// eslint-disable-next-line no-unused-vars
function errorHandler(err, req, res, next) {
  if (err instanceof multer.MulterError) {
    err = ApiError.badRequest(err.code === 'LIMIT_FILE_SIZE' ? 'Ảnh vượt quá dung lượng cho phép (5MB)' : err.message);
  }
  if (err.type === 'entity.parse.failed') {
    err = ApiError.badRequest('Body không phải JSON hợp lệ');
  }

  const status = err.status || 500;
  if (status >= 500) console.error(err);

  res.status(status).json({
    success: false,
    message: status >= 500 && env.isProduction ? 'Lỗi máy chủ' : err.message,
    code: err.code || (status >= 500 ? 'INTERNAL_ERROR' : undefined),
    errors: err.details,
  });
}

module.exports = { notFound, errorHandler };
