/** Lỗi có HTTP status, được errorHandler chuyển thành JSON trả về client. */
class ApiError extends Error {
  constructor(status, message, code = undefined, details = undefined) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
  }

  static badRequest(message, details) {
    return new ApiError(400, message, 'BAD_REQUEST', details);
  }

  static unauthorized(message = 'Bạn chưa đăng nhập hoặc phiên đã hết hạn') {
    return new ApiError(401, message, 'UNAUTHORIZED');
  }

  static forbidden(message = 'Bạn không có quyền thực hiện thao tác này') {
    return new ApiError(403, message, 'FORBIDDEN');
  }

  static notFound(message = 'Không tìm thấy dữ liệu') {
    return new ApiError(404, message, 'NOT_FOUND');
  }

  static conflict(message) {
    return new ApiError(409, message, 'CONFLICT');
  }
}

module.exports = ApiError;
