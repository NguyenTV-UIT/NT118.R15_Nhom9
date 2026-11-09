-- Backend v3: đăng ký / đăng nhập bằng email (tạm ngừng số điện thoại).
-- Đăng ký chỉ cần email + mật khẩu -> hồ sơ được tạo trống, tên điền sau ở màn EnterName.
ALTER TABLE profiles MODIFY display_name VARCHAR(100) NULL;

-- OTP dùng chung cho email và số điện thoại: cột phone -> target
ALTER TABLE otp_codes
  CHANGE phone target VARCHAR(255) NOT NULL,
  RENAME INDEX idx_otp_phone TO idx_otp_target;
