/** Bọc controller async để lỗi tự chuyển sang errorHandler (Express 4). */
module.exports = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
