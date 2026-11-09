-- =====================================================================
--  Dating App - Nhóm 9 (NT118.R15)
--  Database: MySQL 8.x  |  Charset: utf8mb4 (hỗ trợ tiếng Việt + emoji)
--  Chạy lại file này sẽ XOÁ toàn bộ dữ liệu cũ (chỉ dùng cho dev).
-- =====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS schema_migrations;
DROP TABLE IF EXISTS otp_codes;
DROP TABLE IF EXISTS user_taste;
DROP TABLE IF EXISTS device_tokens;
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS reports;
DROP TABLE IF EXISTS blocks;
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS matches;
DROP TABLE IF EXISTS swipes;
DROP TABLE IF EXISTS refresh_tokens;
DROP TABLE IF EXISTS user_interests;
DROP TABLE IF EXISTS interests;
DROP TABLE IF EXISTS photos;
DROP TABLE IF EXISTS preferences;
DROP TABLE IF EXISTS profiles;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------
-- 1. USER & AUTH
-- ---------------------------------------------------------------------
-- Đăng nhập bằng email (số điện thoại: đang tạm ngừng, giữ cột để bật lại sau)
CREATE TABLE users (
  id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  email              VARCHAR(255)  NULL,
  phone              VARCHAR(20)   NULL,              -- dạng +84xxxxxxxxx
  phone_verified_at  DATETIME      NULL,
  password_hash   VARCHAR(255)  NOT NULL,
  role            ENUM('user','admin')             NOT NULL DEFAULT 'user',
  status          ENUM('active','banned','deleted') NOT NULL DEFAULT 'active',
  last_active_at  DATETIME      NULL,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_users_email (email),
  UNIQUE KEY uq_users_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Mã OTP gửi qua email hoặc SMS (quên mật khẩu; đăng ký / đăng nhập bằng SĐT khi bật lại)
CREATE TABLE otp_codes (
  id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  target       VARCHAR(255)    NOT NULL,           -- email hoặc SĐT +84...
  purpose      ENUM('register','login','reset_password') NOT NULL,
  code_hash    CHAR(64)        NOT NULL,           -- SHA-256(target:purpose:code)
  attempts     TINYINT UNSIGNED NOT NULL DEFAULT 0,
  expires_at   DATETIME        NOT NULL,
  consumed_at  DATETIME        NULL,
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_otp_target (target, purpose, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE refresh_tokens (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT UNSIGNED NOT NULL,
  token_hash  CHAR(64)        NOT NULL,           -- SHA-256 của refresh token
  expires_at  DATETIME        NOT NULL,
  revoked_at  DATETIME        NULL,
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_refresh_token_hash (token_hash),
  KEY idx_refresh_user (user_id),
  CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 2. PROFILE
-- ---------------------------------------------------------------------
-- Đăng ký chỉ cần email + mật khẩu -> hồ sơ tạo trống, tên / ngày sinh / giới tính... điền dần qua onboarding.
-- completed_at: thời điểm hoàn tất hồ sơ; chỉ hồ sơ đã hoàn tất mới hiện trên trang Home.
-- Các cột lựa chọn (relationship_goal, zodiac, ...) lưu mã tiếng Anh, danh sách mã: GET /api/meta/options
CREATE TABLE profiles (
  user_id            BIGINT UNSIGNED PRIMARY KEY,
  display_name       VARCHAR(100)  NULL,                   -- điền ở màn EnterName sau khi đăng ký
  birthday           DATE          NULL,
  gender             ENUM('male','female','other') NULL,   -- other = phi nhị nguyên
  show_gender        TINYINT(1)    NOT NULL DEFAULT 1,
  bio                VARCHAR(500)  NULL,
  job_title          VARCHAR(100)  NULL,
  school             VARCHAR(150)  NULL,
  city               VARCHAR(100)  NULL,
  latitude           DECIMAL(9,6)  NULL,
  longitude          DECIMAL(9,6)  NULL,
  height_cm          SMALLINT UNSIGNED NULL,
  weight_kg          SMALLINT UNSIGNED NULL,
  relationship_goal  VARCHAR(32)   NULL,
  zodiac             VARCHAR(16)   NULL,
  smoking            VARCHAR(32)   NULL,
  drinking           VARCHAR(32)   NULL,
  exercise           VARCHAR(32)   NULL,
  education          VARCHAR(32)   NULL,
  completed_at       DATETIME      NULL,
  updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_profiles_gender_birthday (gender, birthday),
  CONSTRAINT fk_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tiêu chí tìm kiếm của người dùng (dùng cho trang Home)
CREATE TABLE preferences (
  user_id          BIGINT UNSIGNED PRIMARY KEY,
  interested_in    ENUM('male','female','other','everyone') NOT NULL DEFAULT 'everyone',
  min_age          TINYINT UNSIGNED NOT NULL DEFAULT 18,
  max_age          TINYINT UNSIGNED NOT NULL DEFAULT 35,
  max_distance_km  SMALLINT UNSIGNED NOT NULL DEFAULT 50,
  CONSTRAINT fk_pref_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE photos (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT UNSIGNED NOT NULL,
  url         VARCHAR(500)    NOT NULL,
  position    TINYINT UNSIGNED NOT NULL DEFAULT 0,   -- 0 = ảnh đại diện
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_photos_user (user_id, position),
  CONSTRAINT fk_photos_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE interests (
  id    INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  name  VARCHAR(50) NOT NULL,
  UNIQUE KEY uq_interests_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_interests (
  user_id      BIGINT UNSIGNED NOT NULL,
  interest_id  INT UNSIGNED    NOT NULL,
  PRIMARY KEY (user_id, interest_id),
  CONSTRAINT fk_ui_user     FOREIGN KEY (user_id)     REFERENCES users(id)     ON DELETE CASCADE,
  CONSTRAINT fk_ui_interest FOREIGN KEY (interest_id) REFERENCES interests(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3. MATCHING
-- ---------------------------------------------------------------------
CREATE TABLE swipes (
  swiper_id   BIGINT UNSIGNED NOT NULL,
  swiped_id   BIGINT UNSIGNED NOT NULL,
  action      ENUM('like','pass','superlike') NOT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (swiper_id, swiped_id),
  KEY idx_swipes_swiped (swiped_id, action),
  CONSTRAINT fk_swipes_swiper FOREIGN KEY (swiper_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_swipes_swiped FOREIGN KEY (swiped_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Luôn lưu user1_id < user2_id để mỗi cặp chỉ có 1 dòng (đảm bảo ở tầng code;
-- MySQL không cho CHECK trên cột có FK ON DELETE CASCADE)
CREATE TABLE matches (
  id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user1_id      BIGINT UNSIGNED NOT NULL,
  user2_id      BIGINT UNSIGNED NOT NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  unmatched_at  DATETIME NULL,
  UNIQUE KEY uq_matches_pair (user1_id, user2_id),
  KEY idx_matches_user2 (user2_id),
  CONSTRAINT fk_matches_u1 FOREIGN KEY (user1_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_matches_u2 FOREIGN KEY (user2_id) REFERENCES users(id) ON DELETE CASCADE
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 4. MESSAGE (dùng ở giai đoạn sau: chat realtime qua WebSocket)
-- ---------------------------------------------------------------------
CREATE TABLE messages (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  match_id    BIGINT UNSIGNED NOT NULL,
  sender_id   BIGINT UNSIGNED NOT NULL,
  type        ENUM('text','image') NOT NULL DEFAULT 'text',
  content     TEXT            NOT NULL,
  read_at     DATETIME        NULL,
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_messages_match (match_id, id),
  CONSTRAINT fk_messages_match  FOREIGN KEY (match_id)  REFERENCES matches(id) ON DELETE CASCADE,
  CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users(id)   ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 5. SAFETY & MODERATION
-- ---------------------------------------------------------------------
CREATE TABLE blocks (
  blocker_id  BIGINT UNSIGNED NOT NULL,
  blocked_id  BIGINT UNSIGNED NOT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (blocker_id, blocked_id),
  KEY idx_blocks_blocked (blocked_id),
  CONSTRAINT fk_blocks_blocker FOREIGN KEY (blocker_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_blocks_blocked FOREIGN KEY (blocked_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE reports (
  id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  reporter_id  BIGINT UNSIGNED NOT NULL,
  reported_id  BIGINT UNSIGNED NOT NULL,
  reason       ENUM('fake_profile','harassment','inappropriate_content','spam','other') NOT NULL,
  details      VARCHAR(1000)   NULL,
  status       ENUM('pending','reviewed','dismissed') NOT NULL DEFAULT 'pending',
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_reports_status (status, created_at),
  CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_reports_reported FOREIGN KEY (reported_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 6. NOTIFICATION
-- ---------------------------------------------------------------------
CREATE TABLE notifications (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT UNSIGNED NOT NULL,
  type        ENUM('new_match','new_message','super_like','system') NOT NULL,
  payload     JSON            NULL,
  read_at     DATETIME        NULL,
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_notifications_user (user_id, read_at),
  CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- FCM token của thiết bị Android (push notification)
CREATE TABLE device_tokens (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT UNSIGNED NOT NULL,
  token       VARCHAR(255)    NOT NULL,
  platform    ENUM('android','web') NOT NULL DEFAULT 'android',
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_device_token (token),
  CONSTRAINT fk_device_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 7. GỢI Ý CÁ NHÂN HOÁ
-- ---------------------------------------------------------------------
CREATE TABLE user_taste (
  user_id          BIGINT UNSIGNED PRIMARY KEY,
  swipe_count      INT UNSIGNED  NOT NULL DEFAULT 0,  -- số lượt quẹt lúc tính
  like_count       INT UNSIGNED  NOT NULL DEFAULT 0,
  age_mean         DECIMAL(5,2)  NULL,                -- tuổi trung bình của người đã like
  age_std          DECIMAL(5,2)  NULL,
  distance_mean    DECIMAL(7,2)  NULL,                -- khoảng cách trung bình (km) của người đã like
  interest_affinity JSON         NULL,                -- {"<interestId>": độ ưa thích -1..1}
  weights          JSON          NULL,                -- trọng số cá nhân {"interests":..,"distance":..,"age":..}
  updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_taste_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 8. MIGRATION ĐÃ CHẠY (db-init tự đánh dấu, db-migrate dùng để bỏ qua)
-- ---------------------------------------------------------------------
CREATE TABLE schema_migrations (
  name        VARCHAR(255) PRIMARY KEY,
  applied_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
