-- Backend v4: phiên đăng nhập theo thiết bị, cài đặt, chặn/báo cáo, xoá tài khoản, chat + độ thân thiết, cuộc gọi.
-- Chạy 1 lần, không xoá dữ liệu.

-- 1. Tài khoản: đã xác thực email chưa, thời điểm đổi mật khẩu gần nhất (màn Cài đặt), bật/tắt thông báo đẩy
ALTER TABLE users
  ADD COLUMN email_verified_at DATETIME NULL AFTER email,
  ADD COLUMN password_changed_at DATETIME NULL AFTER password_hash,
  ADD COLUMN push_enabled TINYINT(1) NOT NULL DEFAULT 1 AFTER status;

-- 2. Quyền riêng tư: hiện khoảng cách / trạng thái hoạt động
ALTER TABLE profiles
  ADD COLUMN show_distance TINYINT(1) NOT NULL DEFAULT 1 AFTER show_gender,
  ADD COLUMN show_active_status TINYINT(1) NOT NULL DEFAULT 1 AFTER show_distance;

-- 3. Phiên đăng nhập theo thiết bị (màn "Thiết bị đang đăng nhập")
CREATE TABLE IF NOT EXISTS sessions (
  id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT UNSIGNED NOT NULL,
  device_name     VARCHAR(120)    NOT NULL DEFAULT 'Thiết bị không xác định',
  platform        VARCHAR(20)     NULL,
  location        VARCHAR(120)    NULL,
  ip              VARCHAR(45)     NULL,
  user_agent      VARCHAR(255)    NULL,
  created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_active_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  revoked_at      DATETIME        NULL,
  KEY idx_sessions_user (user_id, revoked_at),
  CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE refresh_tokens
  ADD COLUMN session_id BIGINT UNSIGNED NULL AFTER user_id,
  ADD KEY idx_refresh_session (session_id),
  ADD CONSTRAINT fk_refresh_session FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE;

-- 4. OTP có thêm mục đích (đổi email...), báo cáo có thêm lý do -> đổi ENUM thành VARCHAR
ALTER TABLE otp_codes MODIFY purpose VARCHAR(32) NOT NULL;
ALTER TABLE reports MODIFY reason VARCHAR(32) NOT NULL;
UPDATE reports SET reason = 'sexual_content' WHERE reason = 'inappropriate_content';
UPDATE reports SET reason = 'scam_spam' WHERE reason = 'spam';

-- 5. Chat: bộ đếm tin nhắn mỗi bên (độ thân thiết), loại tin nhắn mở rộng, chống gửi trùng
ALTER TABLE matches
  ADD COLUMN user1_msg_count INT UNSIGNED NOT NULL DEFAULT 0 AFTER user2_id,
  ADD COLUMN user2_msg_count INT UNSIGNED NOT NULL DEFAULT 0 AFTER user1_msg_count;

ALTER TABLE messages
  MODIFY type VARCHAR(16) NOT NULL DEFAULT 'text',
  ADD COLUMN client_id VARCHAR(64) NULL AFTER content,
  ADD UNIQUE KEY uq_messages_client (match_id, sender_id, client_id),
  ADD KEY idx_messages_unread (match_id, sender_id, read_at);

UPDATE matches m SET
  user1_msg_count = (SELECT COUNT(*) FROM messages x WHERE x.match_id = m.id AND x.sender_id = m.user1_id AND x.type IN ('text','image')),
  user2_msg_count = (SELECT COUNT(*) FROM messages x WHERE x.match_id = m.id AND x.sender_id = m.user2_id AND x.type IN ('text','image'));

-- 6. Cuộc gọi thoại / video
CREATE TABLE IF NOT EXISTS calls (
  id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  match_id     BIGINT UNSIGNED NOT NULL,
  caller_id    BIGINT UNSIGNED NOT NULL,
  callee_id    BIGINT UNSIGNED NOT NULL,
  type         VARCHAR(8)      NOT NULL,                      -- voice | video
  status       VARCHAR(16)     NOT NULL DEFAULT 'ringing',    -- ringing | accepted | rejected | cancelled | missed | ended
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  answered_at  DATETIME        NULL,
  ended_at     DATETIME        NULL,
  KEY idx_calls_match (match_id, id),
  KEY idx_calls_callee (callee_id, status),
  CONSTRAINT fk_calls_match  FOREIGN KEY (match_id)  REFERENCES matches(id) ON DELETE CASCADE,
  CONSTRAINT fk_calls_caller FOREIGN KEY (caller_id) REFERENCES users(id)   ON DELETE CASCADE,
  CONSTRAINT fk_calls_callee FOREIGN KEY (callee_id) REFERENCES users(id)   ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Lý do xoá tài khoản (thống kê ẩn danh - không lưu ai đã xoá)
CREATE TABLE IF NOT EXISTS account_deletions (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  reason      VARCHAR(32)   NULL,
  details     VARCHAR(500)  NULL,
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Thông báo: thêm loại "lượt thích mới", "cuộc gọi nhỡ"
ALTER TABLE notifications MODIFY type VARCHAR(32) NOT NULL;
