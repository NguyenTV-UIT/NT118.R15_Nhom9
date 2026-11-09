-- Backend v2: đăng nhập bằng số điện thoại + OTP, hồ sơ mở rộng cho luồng onboarding của app Android.
-- Chạy 1 lần (db-migrate ghi lại vào bảng schema_migrations).

-- 1. Tài khoản: email không còn bắt buộc, thêm số điện thoại
ALTER TABLE users
  MODIFY email VARCHAR(255) NULL,
  ADD COLUMN phone VARCHAR(20) NULL AFTER email,
  ADD COLUMN phone_verified_at DATETIME NULL AFTER phone,
  ADD UNIQUE KEY uq_users_phone (phone);

-- 2. Hồ sơ: điền dần qua onboarding -> birthday / gender cho phép NULL, thêm các trường mới
ALTER TABLE profiles
  MODIFY birthday DATE NULL,
  MODIFY gender ENUM('male','female','other') NULL,
  ADD COLUMN show_gender TINYINT(1) NOT NULL DEFAULT 1 AFTER gender,
  ADD COLUMN height_cm SMALLINT UNSIGNED NULL AFTER longitude,
  ADD COLUMN weight_kg SMALLINT UNSIGNED NULL AFTER height_cm,
  ADD COLUMN relationship_goal VARCHAR(32) NULL AFTER weight_kg,
  ADD COLUMN zodiac VARCHAR(16) NULL AFTER relationship_goal,
  ADD COLUMN smoking VARCHAR(32) NULL AFTER zodiac,
  ADD COLUMN drinking VARCHAR(32) NULL AFTER smoking,
  ADD COLUMN exercise VARCHAR(32) NULL AFTER drinking,
  ADD COLUMN education VARCHAR(32) NULL AFTER exercise,
  ADD COLUMN completed_at DATETIME NULL AFTER education;

-- Hồ sơ cũ (đăng ký bằng email, đã có ngày sinh + giới tính) coi như đã hoàn tất
UPDATE profiles SET completed_at = UTC_TIMESTAMP()
 WHERE completed_at IS NULL AND birthday IS NOT NULL AND gender IS NOT NULL;

-- 3. Tiêu chí: thêm lựa chọn "Người phi nhị nguyên"
ALTER TABLE preferences
  MODIFY interested_in ENUM('male','female','other','everyone') NOT NULL DEFAULT 'everyone';

-- 4. Mã OTP
CREATE TABLE IF NOT EXISTS otp_codes (
  id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  phone        VARCHAR(20)     NOT NULL,
  purpose      ENUM('register','login','reset_password') NOT NULL,
  code_hash    CHAR(64)        NOT NULL,
  attempts     TINYINT UNSIGNED NOT NULL DEFAULT 0,
  expires_at   DATETIME        NOT NULL,
  consumed_at  DATETIME        NULL,
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_otp_phone (phone, purpose, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. 20 sở thích đúng như màn hình Interests của app (sở thích cũ vẫn giữ để không mất dữ liệu)
INSERT IGNORE INTO interests (name) VALUES
  ('Du lịch'), ('Âm nhạc'), ('Xem phim và series'), ('Đọc sách'), ('Nấu ăn'),
  ('Chơi game'), ('Thể thao'), ('Tập gym'), ('Cà phê'), ('Ẩm thực'),
  ('Nhiếp ảnh'), ('Nghệ thuật'), ('Công nghệ'), ('Thú cưng'), ('Thiên nhiên và cắm trại'),
  ('Thời trang'), ('Khiêu vũ'), ('Khám phá địa điểm mới'), ('Hoạt động ngoài trời'), ('Tình nguyện');
