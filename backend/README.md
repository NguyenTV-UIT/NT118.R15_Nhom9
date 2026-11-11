# Backend - Dating App (Nhóm 9, NT118.R15)

REST API viết bằng **Node.js (Express)** + **MySQL 8**. Android app (Retrofit) và Web Admin cùng gọi vào server này.

## Cấu trúc thư mục

```
backend/
├── database/
│   ├── schema.sql           # Toàn bộ bảng (users, profiles, swipes, matches, messages, ...)
│   └── migrations/          # Thay đổi schema cho DB đang có dữ liệu (npm run db:migrate)
├── scripts/
│   ├── db-init.js           # Tạo DB + chạy schema.sql (XOÁ dữ liệu cũ)
│   ├── db-migrate.js        # Chạy migrations (không xoá dữ liệu)
│   ├── db-seed.js           # Dữ liệu mẫu: sở thích + tài khoản demo
│   ├── simulate-swipes.js   # Giả lập quẹt để chứng minh gợi ý cá nhân hoá
│   └── simulate-chat.js     # Giả lập nhắn tin để mở khoá gửi ảnh / cuộc gọi khi test
├── src/
│   ├── config/              # env.js (đọc .env), db.js (MySQL pool)
│   ├── middlewares/         # auth (JWT), validate (zod), upload (multer), errorHandler
│   ├── modules/
│   │   ├── auth/            # đăng ký / đăng nhập email, quên mật khẩu (OTP email), SĐT (tạm ngừng)
│   │   ├── sessions/        # phiên đăng nhập theo thiết bị
│   │   ├── users/           # hồ sơ, tiêu chí, sở thích, ảnh + routes cài đặt / chặn / báo cáo
│   │   ├── account/         # cài đặt, đổi email, xoá tài khoản, device token
│   │   ├── safety/          # chặn, báo cáo
│   │   ├── home/            # feed gợi ý, search, swipe, lượt thích, match
│   │   │   ├── matching.js  #   thuật toán chấm điểm matchScore
│   │   │   └── taste.js     #   học gu từ lịch sử quẹt (cá nhân hoá)
│   │   ├── chats/           # chat + độ thân thiết (chats.service.js), cuộc gọi (calls.service.js)
│   │   └── meta/            # danh mục lựa chọn (options.js: mã ↔ nhãn tiếng Việt)
│   ├── realtime/            # hub.js: WebSocket /ws
│   ├── services/            # otp.service.js (OTP), mail.js (email), sms.js (SMS), push.js (thông báo đẩy)
│   ├── utils/
│   ├── app.js               # khai báo middleware + routes
│   └── server.js            # khởi động HTTP server + WebSocket
├── uploads/                 # ảnh người dùng (không commit)
├── api.http                 # file test API
└── docker-compose.yml       # MySQL cho dev (tuỳ chọn)
```

Mỗi module chia 4 file: `routes` → `validation` → `controller` → `service` (SQL nằm ở service).
Thêm module mới (chat, report, admin...) thì copy theo đúng mẫu này rồi khai báo trong `src/app.js`.

## Chạy ở máy local

Yêu cầu: Node.js ≥ 20, MySQL 8 (hoặc MariaDB ≥ 10.4 của XAMPP).

```bash
cd backend
npm install
cp .env.example .env          # rồi sửa DB_USER, DB_PASSWORD, JWT_ACCESS_SECRET

# Có Docker thì bật MySQL bằng:  docker compose up -d
npm run db:reset              # tạo bảng + seed dữ liệu mẫu
npm run dev                   # chạy có auto-reload, mặc định http://localhost:3000
```

Kiểm tra: mở `http://localhost:3000/api/health` → `"database": "up"`.

Tài khoản demo (mật khẩu `123456`), đăng nhập bằng email (cột SĐT dùng khi bật lại đăng nhập bằng SĐT):

| Tên | Email | SĐT |
|---|---|---|
| An | `an@demo.local` | `0901000001` |
| Bình | `binh@demo.local` | `0901000002` |
| Chi | `chi@demo.local` | `0901000003` |
| Dũng | `dung@demo.local` | `0901000004` |
| Giang | `giang@demo.local` | `0901000005` |
| Hoà | `hoa@demo.local` | `0901000006` |
| Khoa | `khoa@demo.local` | `0901000007` |
| Admin (role admin) | `admin@datingapp.local` | `0900000000` |

> Android Emulator gọi máy host bằng `http://10.0.2.2:3000/api/`, điện thoại thật thì dùng IP LAN của máy chạy server.

## Định dạng response

```json
{ "success": true,  "message": "OK", "data": { ... } }
{ "success": false, "message": "Dữ liệu không hợp lệ", "code": "BAD_REQUEST",
  "errors": [{ "field": "email", "message": "Email không hợp lệ" }] }
```

Phía Android nên có class chung `BaseResponse<T>` với `success`, `message`, `data`.

## Xác thực

- Đăng nhập/đăng ký trả về `tokens.accessToken` (hạn 15 phút) và `tokens.refreshToken` (hạn 30 ngày).
- Gọi API cần đăng nhập: header `Authorization: Bearer <accessToken>`.
- Khi nhận **401**, gọi `POST /api/auth/refresh` với refreshToken để lấy cặp token mới
  (refresh token chỉ dùng được 1 lần). Trên Android làm việc này trong `okhttp3.Authenticator`.
- Mỗi lần đăng nhập tạo 1 **phiên theo thiết bị**. App nên gửi kèm header `X-Device-Name` (VD `Samsung Galaxy S23`,
  có dấu tiếng Việt thì URL-encode) và `X-Device-Platform: android` khi đăng nhập / đăng ký để màn
  "Thiết bị đang đăng nhập" hiện đúng tên; không gửi thì server đoán từ `User-Agent`.
- 401 kèm `code: "SESSION_REVOKED"` = thiết bị này đã bị đăng xuất từ xa (hoặc đổi mật khẩu ở máy khác)
  → **không** refresh nữa, đưa người dùng về màn đăng nhập.

## Danh sách API

| Method | Endpoint | Auth | Mô tả |
|---|---|---|---|
| GET | `/api/health` | | Kiểm tra server + DB |
| GET | `/api/meta/options` | | Danh mục lựa chọn (mã ↔ nhãn / nhãn ngắn tiếng Việt), 20 sở thích, giới hạn chiều cao / cân nặng... |
| **Auth - email (app Android)** | | | Chi tiết ở phần [Nối API cho app Android](#nối-api-cho-app-android) |
| POST | `/api/auth/register` | | **Chỉ** `{email, password}` (mật khẩu ≥ 8) → tạo tài khoản + hồ sơ trống + token |
| POST | `/api/auth/login` | | `{email, password}` → token |
| POST | `/api/auth/register/send-otp` · `/login/verify-otp` · `/login/resend-otp` | | OTP email **tuỳ chọn**, mặc định tắt (xem `REGISTER_OTP_REQUIRED`, `EMAIL_LOGIN_OTP_REQUIRED` trong `.env.example`) |
| POST | `/api/auth/password/forgot` | | `{email}` → gửi / gửi lại OTP 6 số tới email |
| POST | `/api/auth/password/verify-otp` | | `{email, otp}` → `resetToken` |
| POST | `/api/auth/password/reset` | | `{resetToken, newPassword}` (≥ 8 ký tự, có chữ số) |
| **Auth - số điện thoại** | | | **Tạm ngừng** → 403 `PHONE_AUTH_DISABLED` (bật lại: `PHONE_AUTH_ENABLED=true`) |
| POST | `/api/auth/otp/send`, `/register/phone`, `/login/phone`, `/login/phone/verify`, `/otp/verify` | | Giữ code để bật lại sau |
| POST | `/api/auth/refresh` | | `{refreshToken}` → token mới |
| POST | `/api/auth/logout` | | `{refreshToken}` → đăng xuất thiết bị này (access token cũng mất hiệu lực ngay) |
| PUT | `/api/auth/password` | ✔ | `{currentPassword, newPassword}` → đổi mật khẩu + đăng xuất các thiết bị khác |
| **User Profile** | | | |
| GET | `/api/users/me` | ✔ | Hồ sơ đầy đủ của mình (kèm preferences, photos, interests) |
| PUT | `/api/users/me/profile` | ✔ | Gửi field nào sửa field đó: `displayName, birthday, gender, showGender, bio, jobTitle, school, city, latitude, longitude, heightCm, weightKg, relationshipGoal, zodiac, smoking, drinking, exercise, education` |
| PUT | `/api/users/me/preferences` | ✔ | `{interestedIn: male\|female\|other\|everyone, minAge, maxAge, maxDistanceKm}` |
| GET | `/api/users/interests` | ✔ | Danh sách sở thích để chọn |
| PUT | `/api/users/me/interests` | ✔ | `{interestNames: ["Du lịch", ...]}` hoặc `{interestIds: [1,2,3]}` (tối đa 20) |
| POST | `/api/users/me/onboarding/complete` | ✔ | Hoàn tất hồ sơ (cần tên, ngày sinh, giới tính, ≥ 2 ảnh, ≥ 3 sở thích) |
| POST | `/api/users/me/photos` | ✔ | multipart, field `photo` (jpg/png/webp, ≤ 5MB, tối đa 6 ảnh; ảnh đầu tiên là avatar) |
| DELETE | `/api/users/me/photos/:photoId` | ✔ | Xoá ảnh |
| GET | `/api/users/:id` | ✔ | Hồ sơ công khai của người khác (có `age`, `distanceKm`, không lộ email/ngày sinh/toạ độ) |
| **Home** | | | |
| GET | `/api/home/feed?limit=20` | ✔ | **(1) Kết quả mặc định** (chưa filter): dựa trên thông tin đăng ký + tiêu chí đã lưu, sắp theo `matchScore`, **cá nhân hoá theo gu**, tự nới filter khi ít người |
| GET | `/api/home/search?...` | ✔ | **(2) Kết quả khi thêm filter** (xem bảng filter bên dưới), cá nhân hoá thứ tự, không tự nới |
| GET | `/api/home/taste` | ✔ | Gu đã học từ lịch sử quẹt (tuổi / khoảng cách / sở thích hay like, trọng số riêng) |
| POST | `/api/home/swipe` | ✔ | `{targetId, action: like\|pass\|superlike}` → `{matched, matchId?, user?}` |
| GET | `/api/home/likes?limit=50` | ✔ | **Tab Lượt thích**: người đã thích mình mà mình chưa quẹt (`likedAt`, `superlike`, `group: today\|yesterday\|earlier`). Thích lại / bỏ qua bằng `/home/swipe` |
| GET | `/api/home/matches?sort=activity\|recent&newOnly=&limit=` | ✔ | Người đã tương hợp + tin nhắn cuối, `unreadCount`, `intimacy` (`sort=recent` = "Tương hợp gần đây") |
| DELETE | `/api/home/matches/:matchId` | ✔ | Huỷ tương hợp (cuộc trò chuyện biến mất ở cả 2 phía) |
| **Cài đặt & tài khoản (v4)** | | | Chi tiết ở phần [Cài đặt, an toàn](#cài-đặt-an-toàn-v4) |
| GET / PUT | `/api/users/me/settings` | ✔ | Công tắc `pushEnabled`, `showActiveStatus`, `showDistance` + email (che), ngày đổi mật khẩu, số người đã chặn, số thiết bị |
| GET | `/api/users/me/sessions` | ✔ | Thiết bị đang đăng nhập (thiết bị hiện tại đứng đầu, `current: true`) |
| DELETE | `/api/users/me/sessions/:sessionId` | ✔ | Đăng xuất 1 thiết bị (có hiệu lực ngay) |
| DELETE | `/api/users/me/sessions` | ✔ | Đăng xuất tất cả thiết bị khác |
| POST | `/api/users/me/email/verify-current` | ✔ | Đổi email bước 1: `{email}` (email hiện tại) → gửi / gửi lại OTP |
| POST | `/api/users/me/email/verify-otp` | ✔ | Bước 2: `{otp}` → `emailChangeToken` (10 phút) |
| PUT | `/api/users/me/email` | ✔ | Bước 3: `{emailChangeToken, newEmail}` → đổi email |
| POST | `/api/users/me/email/confirm` | ✔ | (chỉ khi `EMAIL_CHANGE_VERIFY_NEW=true`) `{pendingToken, otp}` |
| POST / DELETE | `/api/users/me/device-token` | ✔ | `{token, platform}` đăng ký / huỷ FCM token nhận thông báo đẩy |
| DELETE | `/api/users/me` | ✔ | **Xoá tài khoản vĩnh viễn**: `{confirm: "XÓA", reason?, details?}` |
| **An toàn (v4)** | | | |
| POST / DELETE | `/api/users/:id/block` | ✔ | Chặn / bỏ chặn (chặn = ẩn nhau khỏi feed, tìm kiếm, hồ sơ; huỷ tương hợp) |
| GET | `/api/users/me/blocks` | ✔ | Danh sách chặn |
| POST | `/api/users/:id/report` | ✔ | `{reason, details?}` → lưu báo cáo + chặn luôn ("Báo cáo và chặn") |
| **Chat (v4)** | | | Chi tiết ở phần [Chat, độ thân thiết, cuộc gọi](#chat-độ-thân-thiết-cuộc-gọi-v4) |
| GET | `/api/chats?q=&filter=all\|close\|unread\|new` | ✔ | Danh sách cuộc trò chuyện (tin cuối, `unreadCount`, `intimacy`); `meta.closeFriendsCount`, `meta.unreadMessages` |
| GET | `/api/chats/intimacy` | ✔ | Màn Thân thiết: `closeFriends` (20/20) + `inProgress` (kèm câu gợi ý) |
| GET | `/api/chats/:matchId` | ✔ | Đầu màn chat: người kia, `intimacy`, `unreadCount` |
| GET | `/api/chats/:matchId/messages?before=&after=&limit=30` | ✔ | Tin nhắn (cũ → mới), phân trang bằng `meta.nextBefore` |
| POST | `/api/chats/:matchId/messages` | ✔ | `{content, clientId?}` → `{message, intimacy, unlocked: null\|"photo"\|"call"}` |
| POST | `/api/chats/:matchId/photos` | ✔ | multipart, field `photo` — cần độ thân thiết ≥ 10, chưa đủ: 403 `PHOTO_LOCKED` |
| POST | `/api/chats/:matchId/read` | ✔ | Đánh dấu đã đọc |
| **Cuộc gọi (v4)** | | | |
| POST | `/api/chats/:matchId/calls` | ✔ | `{type: voice\|video}` bắt đầu gọi — cần độ thân thiết 20, chưa đủ: 403 `CALL_LOCKED` |
| POST | `/api/calls/:callId/accept` · `/reject` · `/cancel` · `/end` | ✔ | Nhấc máy / từ chối (người nhận) · huỷ (người gọi) · cúp máy (ai cũng được, dùng được mọi lúc) |
| GET | `/api/calls/active` · `/api/calls/:callId` · `/api/calls/ice-servers` | ✔ | Cuộc gọi đang diễn ra · trạng thái 1 cuộc gọi · máy chủ STUN/TURN cho WebRTC |
| **Realtime** | | | |
| WS | `ws://<host>/ws` | ✔ | WebSocket: tin nhắn mới, đã đọc, đang nhập, online, tương hợp / lượt thích mới, cuộc gọi |

Ví dụ gọi thử từng API có trong `api.http` và `postman/DatingApp.postman_collection.json`.

## Nối API cho app Android

App đăng ký / đăng nhập bằng **email + mật khẩu**, sau đó đi qua các màn hình onboarding để tạo hồ sơ. Bảng dưới là API cho từng màn hình (theo code trong `android-app/`). Đăng ký / đăng nhập bằng số điện thoại đang **tạm ngừng**.

### Đăng ký / đăng nhập / quên mật khẩu

| Màn hình | Khi bấm nút | API |
|---|---|---|
| `RegisterActivity` | Đăng ký | `POST /auth/register` `{email, password}` → lưu token → `EnterNameActivity` (tên nhập ở màn Register thì gửi luôn ở bước EnterName, hoặc điền sẵn vào ô tên) |
| `LoginActivity` | Đăng nhập | `POST /auth/login` `{email, password}` → lưu token. `user.onboarding.completed = false` thì đưa về màn onboarding còn thiếu, ngược lại vào Home |
| `ForgotPasswordActivity` | Gửi mã | `POST /auth/password/forgot` `{email}` → màn OTP |
| `OtpVerificationActivity` (FLOW_FORGOT_PASSWORD) | Xác nhận | `POST /auth/password/verify-otp` `{email, otp}` → truyền `resetToken` sang màn tiếp |
| `OtpVerificationActivity` (FLOW_FORGOT_PASSWORD) | Gửi lại mã (sau 30 giây) | `POST /auth/password/forgot` `{email}` |
| `ResetPasswordActivity` | Xác nhận | `POST /auth/password/reset` `{resetToken, newPassword}` → về Login |

- **Đăng ký và đăng nhập không cần OTP** → app bỏ qua màn `OtpVerificationActivity` ở `FLOW_REGISTER` và `FLOW_LOGIN`, chỉ dùng màn OTP cho quên mật khẩu.
- Đăng ký **chỉ nhận email + mật khẩu**; field khác gửi kèm sẽ bị bỏ qua. Hồ sơ được tạo trống ngay khi đăng ký, các thông tin (tên, ngày sinh, giới tính...) điền ở các màn onboarding bên dưới.
- Email không phân biệt hoa thường (`Lan@Gmail.com` = `lan@gmail.com`).
- OTP: 6 số, hết hạn sau 5 phút, gửi lại sau 30 giây, sai 5 lần phải gửi mã mới, tối đa 5 mã / email / giờ.
- **Gửi email**: mặc định `MAIL_PROVIDER=console` → email in ra log server (`pm2 logs dating-api`), mã OTP trả trong field `devOtp` (khi `OTP_DEV_MODE=true`) để test. Gửi thật bằng Gmail: xem `src/services/mail.js` (cần App Password), rồi tắt `OTP_DEV_MODE`.

Mã lỗi (field `code` trong response lỗi) để app hiện thông báo phù hợp:

| code | HTTP | Ý nghĩa |
|---|---|---|
| `CONFLICT` | 409 | Email đã được sử dụng (đăng ký) |
| `UNAUTHORIZED` | 401 | Sai email hoặc mật khẩu |
| `NOT_FOUND` | 404 | Email chưa đăng ký (quên mật khẩu) |
| `OTP_WRONG` | 400 | Sai mã, `message` có số lần thử còn lại |
| `OTP_EXPIRED` | 400 | Mã hết hạn |
| `OTP_LOCKED` / `OTP_INVALID` | 400 | Sai quá 5 lần / mã đã dùng → bấm gửi lại |
| `OTP_TOO_SOON` | 429 | Chưa đủ 30 giây để gửi lại |
| `OTP_LIMIT` | 429 | Quá 5 mã trong 1 giờ |
| `PHONE_AUTH_DISABLED` | 403 | Gọi API số điện thoại khi đang tạm ngừng |

### Onboarding (sau khi đăng ký)

Sau khi đăng ký, hồ sơ được **khởi tạo trống**. Mỗi màn hình gọi API ngay khi bấm "Tiếp tục" (không cần chuyền dữ liệu qua `Intent` tới màn cuối). Các lựa chọn gửi **nhãn tiếng Việt đúng như trên nút** (của bất kỳ màn nào) hoặc **mã** đều được, server luôn lưu và trả về mã.

| Màn hình | API | Body ví dụ |
|---|---|---|
| `EnterNameActivity` | `PUT /users/me/profile` | `{"displayName": "Lan"}` |
| `AddPhotosActivity` | `POST /users/me/photos` (mỗi ảnh 1 lần, multipart field `photo`) | ảnh đầu tiên = avatar, tối thiểu 2, tối đa 6 |
| `BirthdayActivity` | `PUT /users/me/profile` | `{"birthday": "12/03/2004"}` (hoặc `2004-03-12`) |
| `GenderActivity` | `PUT /users/me/profile` | `{"gender": "Nữ", "showGender": true}` |
| `DatingPreferenceActivity` | `PUT /users/me/preferences` | `{"interestedIn": "Nam"}` |
| `LocationDistanceActivity` | `PUT /users/me/profile` + `PUT /users/me/preferences` | `{"city": "TP. Hồ Chí Minh", "latitude": 10.87, "longitude": 106.80}` + `{"maxDistanceKm": 25}` |
| `HeightActivity` | `PUT /users/me/profile` | `{"heightCm": 160}` (140–200) |
| `WeightActivity` | `PUT /users/me/profile` | `{"weightKg": 48}` (35–120, **không** hiện cho người khác) |
| `InterestsActivity` | `PUT /users/me/interests` | `{"interestNames": ["Du lịch", "Cà phê", "Nhiếp ảnh"]}` (≥ 3) |
| `RelationshipGoalActivity` | `PUT /users/me/profile` | `{"relationshipGoal": "Hẹn hò và tìm hiểu"}` |
| `ZodiacActivity` | `PUT /users/me/profile` | `{"zodiac": "Song Ngư"}` |
| `SmokingActivity` | `PUT /users/me/profile` | `{"smoking": "Không hút thuốc"}` |
| `DrinkingActivity` | `PUT /users/me/profile` | `{"drinking": "Thỉnh thoảng uống"}` |
| `ExerciseActivity` | `PUT /users/me/profile` | `{"exercise": "Thỉnh thoảng tập luyện"}` |
| `EducationActivity` | `PUT /users/me/profile` | `{"education": "Đang học đại học"}` |
| `BioActivity` | `PUT /users/me/profile` | `{"bio": "..."}` (≤ 300 ký tự) |
| `ProfileReadyActivity` | `POST /users/me/onboarding/complete` | thiếu gì → 400 `PROFILE_INCOMPLETE`, `errors` liệt kê field còn thiếu |

### Nhãn lựa chọn (theo frontend)

App dùng **2 bộ nhãn** cho cùng một lựa chọn; server nhận cả hai:

| Mục | Màn Onboarding / Bộ lọc / Xem hồ sơ (`label`) | Màn Sửa hồ sơ / Xem trước (`shortLabel`) | Mã lưu DB |
|---|---|---|---|
| Hút thuốc | Không hút thuốc · Thỉnh thoảng hút thuốc · Hút thuốc thường xuyên | Không bao giờ · Thỉnh thoảng · Thường xuyên · **Đang cai** | `never` · `sometimes` · `regularly` · `quitting` |
| Rượu bia | Không uống · Thỉnh thoảng uống · Uống khá thường xuyên | Không bao giờ · Thỉnh thoảng · Thường xuyên · **Cuối tuần** | `never` · `sometimes` · `often` · `weekends` |
| Tập luyện | Tập luyện thường xuyên · Thỉnh thoảng tập luyện · Không tập luyện | **Mỗi ngày** · Thường xuyên · Thỉnh thoảng · Không tập | `daily` · `very_active` · `sometimes` · `never` |
| Học vấn | Tốt nghiệp THPT · Đại học · Sau đại học / Thạc sĩ · Tiến sĩ | Trung học · Đại học · Thạc sĩ · Tiến sĩ · **Khác** | `high_school` · `bachelor` · `postgraduate` · `phd` · `other` |
| Mục tiêu | Tìm hiểu để tiến tới mối quan hệ lâu dài · Hẹn hò và tìm hiểu · Kết hôn · Kết bạn · Chưa xác định... | Người yêu lâu dài · Bạn hẹn hò · Bạn đời · Bạn bè mới · Chưa rõ | `long_term` · `dating` · `marriage` · `friendship` · `not_sure` |

(Bảng chỉ liệt kê các lựa chọn có ở cả hai màn; **in đậm** là lựa chọn chỉ có ở màn Sửa hồ sơ.) Server luôn trả về **mã**; app hiển thị bằng `label` hoặc `shortLabel` lấy từ `GET /meta/options` (mỗi lựa chọn có `{ code, label, shortLabel }`). Thêm / sửa lựa chọn: chỉ sửa `src/modules/meta/options.js`.

- Nút "Bỏ qua" ở các màn không bắt buộc (cân nặng, cung hoàng đạo, hút thuốc, uống rượu, tập luyện, học vấn) → không gọi API.
- `GET /users/me` trả `onboarding: { completed, missing: [...] }` → sau khi đăng nhập app biết còn thiếu bước nào.
- Hồ sơ **chưa hoàn tất** thì không hiện trên Home của người khác, và tự xem Home sẽ bị 403 `PROFILE_INCOMPLETE`.
- Hồ sơ công khai (`/home/feed`, `/users/:id`) **không** có SĐT, email, ngày sinh, cân nặng; giới tính bị ẩn nếu `showGender = false`. (Thẻ trên app đang hiện cân nặng - nhóm chưa chốt, tạm giữ ẩn.)

### Gọi API từ Android (Retrofit)

- Base URL: `http://<IP-VPS>/api/` (thêm `android:usesCleartextTraffic="true"` và quyền `INTERNET` trong `AndroidManifest.xml`).
- Header: `Authorization: Bearer <accessToken>` cho mọi API có ✔.
- Access token hết hạn sau 15 phút → nhận 401 thì gọi `POST /auth/refresh` với `refreshToken`.
- Response chung: `{ success, message, data, meta? }`; lỗi: `{ success: false, message, code, errors? }` → hiện `message` cho người dùng.

## Cài đặt, an toàn (v4)

**Màn Settings** — `GET /api/users/me/settings` trả đủ dữ liệu cho cả màn:

```json
{ "email": "minhanh@gmail.com", "emailMasked": "mi*****@gmail.com", "emailVerified": false,
  "passwordChangedAt": "2026-11-01T08:00:00.000Z",
  "pushEnabled": true, "showActiveStatus": true, "showDistance": true,
  "blockedCount": 0, "deviceCount": 2 }
```

- Bật / tắt công tắc: `PUT /api/users/me/settings` gửi công tắc nào đổi công tắc đó, VD `{ "showDistance": false }`.
  - `showDistance = false` → người khác nhận `distanceKm: null` (feed, tìm kiếm, hồ sơ).
  - `showActiveStatus = false` → người khác nhận `isOnline: null`, `lastActiveAt: null`.
- **Thiết bị đang đăng nhập**: `GET /users/me/sessions` → `[{ id, deviceName, platform, location, current, active, lastActiveAt }]`.
  Dialog "Đăng xuất thiết bị" → `DELETE /users/me/sessions/:id`; "Đăng xuất tất cả thiết bị" → `DELETE /users/me/sessions`
  (giữ lại thiết bị đang dùng; muốn thoát luôn máy này thì gọi tiếp `POST /auth/logout`).
- **Đổi email** (VerifyCurrentEmail → OtpVerification → ChangeEmail → ChangeEmailSuccess):

  | Bước | API | Body | Trả về |
  |---|---|---|---|
  | 1 | `POST /users/me/email/verify-current` | `{email}` (email hiện tại; gọi lại = gửi lại mã) | `expiresIn, resendAfter, devOtp?` |
  | 2 | `POST /users/me/email/verify-otp` | `{otp}` | `emailChangeToken` |
  | 3 | `PUT /users/me/email` | `{emailChangeToken, newEmail}` | `{otpRequired: false, email}` → xong |

  Email cũ nhận 1 thư báo "email đã được thay đổi". Lần đăng nhập sau dùng email mới.
- **Xoá tài khoản** (DeleteAccount): `DELETE /users/me` với `{ "confirm": "XÓA", "reason": "taking_break" }`
  (`reason` nhận mã hoặc nhãn trong `deleteReason` của `/meta/options`). Xoá vĩnh viễn hồ sơ, ảnh, lượt thích, tương hợp, tin nhắn.
- **Chặn** (bottom sheet BlockUser): `POST /users/:id/block`. **Báo cáo** (ReportUser): `POST /users/:id/report`
  với `{ "reason": "harassment", "details": "..." }` — `reason` nhận mã hoặc đúng nhãn trên app (danh sách: `reportReason` trong `/meta/options`).
  Báo cáo xong server tự chặn + huỷ tương hợp. Người bị chặn không được thông báo, phía họ chỉ thấy cuộc trò chuyện biến mất.
- **Trang hồ sơ người khác**: `GET /users/:id` có thêm `relation: { matched, matchId, likedMe, myAction }` để biết hiện nút
  Nhắn tin (đã tương hợp → `matchId`), Thích lại (`likedMe`), hay Thích / Bỏ qua.
- **Thông báo đẩy**: sau khi đăng nhập app gửi FCM token `POST /users/me/device-token`. Server hiện ở chế độ
  `PUSH_PROVIDER=console` (chỉ ghi log, chưa gửi thật) — xem `src/services/push.js` để nối Firebase.

## Chat, độ thân thiết, cuộc gọi (v4)

Chỉ 2 người **đang tương hợp** mới nhắn tin / gọi được cho nhau; `matchId` chính là id cuộc trò chuyện.

### Độ thân thiết

Giống công thức trong `ChatDetailActivity`: `score = min(số tin mình gửi, số tin người kia gửi)`, tối đa 20.
Mọi API chat đều trả kèm:

```json
"intimacy": { "score": 12, "max": 20, "mine": 14, "partner": 12,
              "photoAt": 10, "callAt": 20,
              "photoUnlocked": true, "callUnlocked": false, "isCloseFriend": false }
```

| Mốc | Mở khoá | Chưa đủ mà gọi API |
|---|---|---|
| 10 | Gửi ảnh | 403 `PHOTO_LOCKED` |
| 20 | Gọi thoại / video, tính là "thân thiết" | 403 `CALL_LOCKED` |

Khi một tin nhắn vừa làm cặp đôi chạm mốc, response gửi tin (và sự kiện `message.new` của người kia) có
`unlocked: "photo"` hoặc `"call"` → app hiện `PhotoUnlockedDialog` / `IntimacyCompletedDialog`. Chỉ tin nhắn chữ và ảnh được tính.

Test nhanh không cần nhắn 40 tin: trên server chạy `npm run db:simulate-chat` (An ↔ Bình lên 20/20).

### Tin nhắn

```jsonc
// POST /api/chats/12/messages   { "content": "Chào bạn", "clientId": "uuid-do-app-sinh" }
{ "message": { "id": 345, "matchId": 12, "senderId": 2, "fromMe": true,
               "type": "text",            // text | image | call
               "content": "Chào bạn",     // image: URL ảnh (= imageUrl); call: null
               "preview": "Chào bạn",     // dòng xem trước ở danh sách chat ("📷 Hình ảnh", "Cuộc gọi nhỡ"...)
               "clientId": "uuid-do-app-sinh", "isRead": false, "readAt": null,
               "createdAt": "2026-11-11T09:00:00.000Z" },
  "intimacy": { "...": "..." }, "unlocked": null }
```

- `clientId` (không bắt buộc): gửi lại cùng `clientId` khi mạng chập chờn sẽ không tạo tin trùng (trả 200 + `duplicate: true`).
- Tin `type: "call"` là nhật ký cuộc gọi: `call: { callId, type: voice|video, status: ended|missed|cancelled|rejected, durationSeconds }`.
- Lịch sử: `GET /chats/:matchId/messages?limit=30` trả trang mới nhất theo thứ tự cũ → mới; kéo lên: `?before=<meta.nextBefore>`.
  Vừa kết nối lại: `?after=<id tin cuối đang có>`.
- Mở màn chat / đang xem thì gọi `POST /chats/:matchId/read`.

### Realtime (WebSocket)

Kết nối `ws://<IP-VPS>/ws` bằng OkHttp `WebSocket`, gửi access token theo 1 trong 3 cách: header `Authorization: Bearer <token>`
(nên dùng), query `?token=<token>`, hoặc tin đầu tiên `{"event":"auth","data":{"token":"..."}}`. Server trả `{"event":"ready"}`.
Mọi tin 2 chiều đều là JSON `{ "event": "...", "data": { ... } }`. Server đóng kết nối với mã **4401** khi phiên bị đăng xuất
→ app refresh token rồi kết nối lại (hoặc về màn đăng nhập nếu refresh thất bại). Mất mạng: tự kết nối lại và gọi `?after=` để lấy tin bị lỡ.

| Server → app | `data` | Khi nào |
|---|---|---|
| `message.new` | `{ message, intimacy, unlocked }` | Có tin nhắn mới (cả tin mình gửi từ thiết bị khác, cả nhật ký cuộc gọi) |
| `message.read` | `{ matchId, readerId, lastReadMessageId, readAt }` | Người kia đã xem |
| `typing` | `{ matchId, userId, isTyping }` | Người kia đang nhập |
| `presence` | `{ userId, isOnline, lastActiveAt }` | Người đã tương hợp vừa online / offline |
| `like.new` | `{ userId, superlike }` | Có người thích mình → tải lại tab Lượt thích |
| `match.new` | `{ matchId, user }` | Người mình thích vừa thích lại → hiện `MatchSuccessDialog` |
| `match.removed` | `{ matchId, userId }` | Bị huỷ tương hợp / chặn / người kia xoá tài khoản → gỡ cuộc trò chuyện |
| `call.incoming` | `{ call, from: {id, displayName, avatarUrl}, iceServers }` | Có cuộc gọi đến |
| `call.accepted` · `call.rejected` · `call.cancelled` · `call.ended` · `call.missed` | `{ call }` | Trạng thái cuộc gọi đổi |
| `call.signal` | `{ callId, fromUserId, signal }` | Gói WebRTC từ máy kia |
| `session.revoked` | `{ message }` | Thiết bị này vừa bị đăng xuất từ xa |
| `error` | `{ code, message }` | Tin gửi lên không hợp lệ |

| App → server | `data` | |
|---|---|---|
| `ping` | | Giữ kết nối + giữ trạng thái online (30–60 giây / lần) → `pong` |
| `typing` | `{ matchId, isTyping }` | Báo đang nhập |
| `call.signal` | `{ callId, signal }` | Gửi gói WebRTC cho máy kia (`signal` tuỳ app: offer / answer / candidate / tắt mic...) |

Gửi tin nhắn, đã đọc, bắt đầu / nhấc / cúp cuộc gọi đều dùng **REST**; WebSocket chỉ để nhận sự kiện (và `typing`, `call.signal`).
Người nhận không mở app thì server gửi thông báo đẩy thay cho WebSocket.

### Cuộc gọi

```
Người gọi                               Server                               Người nhận
POST /chats/:matchId/calls {type}  ──▶  tạo call (ringing)  ──▶  WS call.incoming (hoặc thông báo đẩy)
                                                            ◀──  POST /calls/:id/accept
WS call.accepted               ◀──
        ◀────────── WS call.signal (offer / answer / ICE candidate) ──────────▶
        ◀═══════════════ âm thanh / hình ảnh đi thẳng 2 máy (WebRTC) ═══════════▶
POST /calls/:id/end            ──▶  ended + ghi nhật ký vào chat  ──▶  WS call.ended
```

- Server chỉ làm **tổng đài** (đổ chuông, trạng thái, chuyển gói WebRTC). Âm thanh / hình ảnh là WebRTC giữa 2 máy
  (thư viện `io.getstream:stream-webrtc-android` hoặc tương đương), danh sách STUN/TURN lấy ở `GET /calls/ice-servers`.
- Không ai nhấc sau `CALL_RING_SECONDS` (45 giây) → `missed`. Người nhận từ chối → `rejected`. Người gọi cúp trước → `cancelled`.
- Nút cúp máy đỏ: luôn gọi `POST /calls/:id/end` (server tự hiểu là huỷ / từ chối / kết thúc tuỳ trạng thái).
- Đang có cuộc gọi khác: 409 `CALL_BUSY` (mình bận) / `CALLEE_BUSY` (người kia bận).
- `call` = `{ id, matchId, callerId, calleeId, type, status, isCaller, createdAt, answeredAt, endedAt, durationSeconds, ringSeconds }`.
- Mở app từ thông báo đẩy / hiện thanh "đang gọi" (`OngoingCallBarHelper`): `GET /calls/active`.
  Trả `{ call, partner: {id, displayName, avatarUrl}, iceServers }`; app chưa nối WebSocket có thể hỏi định kỳ API này để biết có cuộc gọi đến.

## Trang Home: kết quả mặc định & filter

### (1) Kết quả mặc định — `GET /api/home/feed`

Khi đăng ký, server tự tạo tiêu chí tìm kiếm từ thông tin người dùng cung cấp:

| Tiêu chí | Lấy từ |
|---|---|
| Giới tính muốn tìm | `interestedIn` lúc đăng ký (không chọn → `everyone`) |
| Độ tuổi | Quanh tuổi của người dùng: `tuổi - 5` đến `tuổi + 5` (tối thiểu 18) |
| Khoảng cách | 50 km quanh vị trí lúc đăng ký (`latitude`, `longitude`) |
| Sở thích | `interestIds` lúc đăng ký → dùng để chấm điểm |

Sau đó feed chạy 2 bước:
1. **Lọc** (SQL): giới tính phù hợp **2 chiều**, trong độ tuổi, trong bán kính (người chưa bật vị trí vẫn hiện), chưa quẹt, không chặn nhau.
2. **Chấm điểm** `matchScore` 0–100 (`src/modules/home/matching.js`) rồi sắp giảm dần:

| Yếu tố | Điểm tối đa | Cách tính |
|---|---|---|
| Sở thích chung | 35 | Tỉ lệ sở thích của mình mà người kia cũng có |
| Khoảng cách | 25 | Càng gần càng cao, về 0 ở rìa bán kính; chưa có vị trí = 10 |
| Mức độ hoạt động | 20 | Online ≤ 1 ngày: 20 · ≤ 7 ngày: 12 · ≤ 30 ngày: 5 |
| Tuổi gần nhau | 10 | Chênh 0 tuổi: 10 → chênh ≥ 10 tuổi: 0 |
| Hồ sơ đầy đủ | 10 | Có ảnh 4 · có bio 3 · có sở thích 3 |

Người **đã like mình** được ưu tiên ẩn +8 khi sắp xếp (không cộng vào `matchScore` trả về, để không lộ ai đã like).

Bảng trên là thuật toán **mặc định** cho người dùng mới. Khi đã quẹt đủ nhiều, điểm được cá nhân hoá - xem phần [Gợi ý cá nhân hoá](#gợi-ý-cá-nhân-hoá-dynamic-theo-từng-người-dùng).

### (2) Kết quả khi thêm filter — `GET /api/home/search`

Mọi filter đều không bắt buộc; filter nào không gửi thì dùng tiêu chí đã lưu. Filter chỉ áp dụng cho lần tìm đó, **không** ghi đè tiêu chí mặc định (muốn lưu thì gọi `PUT /api/users/me/preferences`).

| Query | Ví dụ | Ý nghĩa |
|---|---|---|
| `gender` | `female` | `male` \| `female` \| `everyone` |
| `minAge`, `maxAge` | `20`, `25` | 18–100 |
| `maxDistanceKm` | `10` | 1–500. Khi gửi filter này, người chưa bật vị trí bị loại |
| `interestIds` | `1,3,5` | Có ít nhất 1 sở thích trong danh sách (tối đa 10) |
| `city` | `Thủ Đức` | Tìm gần đúng theo thành phố |
| `q` | `Giang` | Tìm theo tên |
| `hasPhoto` | `true` | Chỉ người có ảnh |
| `sort` | `distance` | `score` (mặc định) \| `distance` \| `newest` \| `active` |
| `limit` | `20` | 1–50 |

Ví dụ: `GET /api/home/search?gender=female&minAge=20&maxAge=25&maxDistanceKm=10&interestIds=1,3&sort=distance`

Mỗi người trong kết quả của cả 2 API có thêm:
- `matchScore` (0–100) → FE hiện kiểu "Hợp nhau 86%"
- `commonInterestCount` (số sở thích chung)
- `reasons` (tối đa 3 lý do gợi ý), ví dụ `["Cùng thích Du lịch", "Hợp gu của bạn: Nhiếp ảnh", "Cách bạn 3 km"]`
- `outsidePreferences: true` (chỉ ở feed khi đã tự nới filter) → người này nằm ngoài tiêu chí gốc

**Bộ lọc nâng cao (màn Filters bước 2, v4)** — thêm vào cùng query string:

| Tham số | Ví dụ | Ghi chú |
|---|---|---|
| `relationshipGoal`, `zodiac`, `smoking`, `drinking`, `exercise`, `education` | `zodiac=leo,virgo` | Chọn nhiều, cách nhau dấu phẩy; nhận mã hoặc nhãn tiếng Việt. Không gửi / `any` / `Không quan trọng` = không lọc |
| `minHeight`, `maxHeight` | `minHeight=155&maxHeight=175` | cm. Người chưa khai chiều cao không lọt |
| `minWeight`, `maxWeight` | `minWeight=45&maxWeight=60` | kg. Chỉ dùng để lọc — cân nặng vẫn **không** trả về trong hồ sơ công khai |
| `sameCity` | `sameCity=true` | Cùng thành phố với mình (theo `city` trong hồ sơ) |
| `orientation`, `sameDistrict` | | App có hiển thị nhưng hồ sơ chưa có dữ liệu tương ứng → server **bỏ qua** và báo trong `meta.ignoredFilters` |

## Gợi ý cá nhân hoá (dynamic theo từng người dùng)

Thuật toán mặc định chấm điểm giống nhau cho mọi người. Phần này làm cho kết quả **khác nhau theo hành vi của từng người**: học từ lịch sử quẹt (`swipes`: like/superlike = thích, pass = không thích). Code: `src/modules/home/taste.js` (học gu) và `matching.js` (chấm điểm).

### Mức 1 — Học gu từ những người đã like

| Gu | Cách học | Dùng để |
|---|---|---|
| Tuổi hay like | Trung bình ± độ lệch chuẩn tuổi của người đã like | Ứng viên càng gần tuổi trung bình càng được điểm cao (phân phối chuẩn) |
| Khoảng cách hay like | Khoảng cách trung bình của người đã like | Trong khoảng đó = điểm tối đa, xa hơn giảm dần |
| Độ ưa thích từng sở thích (−1..1) | Tỉ lệ like khi người kia có sở thích X so với tỉ lệ like chung (có làm mượt, giảm độ tin khi còn ít like) | Ứng viên có sở thích mình hay like được cộng điểm, kể cả sở thích **mình không chọn** trong hồ sơ |

→ **Điểm gu** (0–100) = trộn 3 phần trên. Tỉ trọng 3 phần cũng học riêng: phần nào phân biệt người mình like / pass rõ nhất thì chiếm nhiều nhất.

### Mức 2 — Trọng số riêng cho từng người

Với mỗi yếu tố *sở thích chung, khoảng cách, tuổi*: so độ phù hợp trung bình của người đã like với người đã pass (chênh lệch `d` từ −1 đến 1).
Trọng số mới = trọng số mặc định × `(1 + 3d)` (giới hạn 0.3–2.5 lần), rồi chuẩn hoá để tổng vẫn = 100.
Ví dụ: người like người ở xa cũng nhiều như người ở gần → `d ≈ 0` → không tăng khoảng cách; người chỉ like người gần → khoảng cách tăng từ 25 lên ~40.

### Kết hợp & cold start

```
matchScore = (1 − α) · điểm cơ bản (trọng số đã cá nhân hoá) + α · điểm gu
```

| Số lượt quẹt | α (mức cá nhân hoá) |
|---|---|
| < 20 | 0 — dùng thuật toán mặc định (chưa đủ dữ liệu) |
| 20 → 50 | tăng dần 0.2 → 0.7 |
| ≥ 50 | 0.7 (luôn giữ 30% điểm cơ bản để không bị "đóng khung" một kiểu người) |

Gu được tính lại tự động **sau mỗi 10 lượt quẹt** (chạy nền, không làm chậm API swipe), lưu ở bảng `user_taste`, chỉ học từ 500 lượt quẹt gần nhất (gu có thể thay đổi theo thời gian).
Response của feed/search có `meta.personalization`: `{ level: α, swipeCount, minSwipes: 20, swipesUntilPersonalized }` → FE có thể hiện "Quẹt thêm 8 lượt để nhận gợi ý theo gu".

### Mức 3 — Tự nới filter khi ít người

Chỉ áp dụng cho **feed mặc định**. Feed có < 5 người thì tự mở rộng theo từng bước, dừng khi đủ người:

| Bước | Bán kính | Độ tuổi |
|---|---|---|
| 1 | × 2 | ± 2 tuổi |
| 2 | × 4 | ± 5 tuổi |
| 3 | × 10 (tối đa 500 km) | ± 10 tuổi |

Giới tính **không bao giờ** bị nới. Người ngoài tiêu chí gốc có `outsidePreferences: true`, xếp sau người trong tiêu chí; `meta.expanded` cho biết đã nới tới đâu và `message` có câu thông báo sẵn (VD: *"Ít người phù hợp, đã mở rộng tìm kiếm: 100 km, 18-35 tuổi"*).
`/home/search` **không** tự nới: filter người dùng tự chọn là ranh giới cứng, cá nhân hoá chỉ thay đổi thứ tự sắp xếp.

### Chứng minh bằng dữ liệu giả lập

```bash
npm run db:simulate            # tạo 100 user giả lập, cho An & Khoa quẹt 40 lượt theo 2 gu khác nhau
npm run db:simulate -- --clean # dọn dữ liệu giả lập
```

Script in top 10 feed **trước / sau** khi quẹt và gu học được:
- **An** — gu ẩn: thích người 20–23 tuổi hoặc thích "Nhiếp ảnh"
- **Khoa** — gu ẩn: chỉ thích người trong 10 km

Cùng tiêu chí khai báo (nữ, 18–35, 50 km) nhưng sau khi quẹt, feed mỗi người khác nhau và đúng gu. Kiểm tra lại qua Postman folder **6. Cá nhân hoá**.

**Hướng phát triển (chưa làm):** lọc cộng tác — "người có gu giống bạn cũng đã like X" (cần nhiều người dùng thật mới hiệu quả).

## Database

Sơ đồ bảng (xem chi tiết trong `database/schema.sql`):

- `users`, `sessions`, `refresh_tokens` — tài khoản, phiên đăng nhập theo thiết bị
- `profiles`, `preferences`, `photos`, `interests`, `user_interests` — hồ sơ
- `swipes`, `matches` — quẹt & ghép đôi (`matches.user1_id < user2_id`)
- `messages`, `calls` — chat (text / image / nhật ký cuộc gọi) và cuộc gọi; `matches.user1_msg_count / user2_msg_count` là bộ đếm độ thân thiết
- `blocks`, `reports`, `account_deletions` — an toàn & kiểm duyệt (Web Admin), lý do xoá tài khoản (ẩn danh)
- `notifications`, `device_tokens` — thông báo, FCM push
- `user_taste` — gu đã học từ lịch sử quẹt (gợi ý cá nhân hoá)
- `otp_codes` — mã OTP gửi qua email / SMS (lưu dạng băm)
- `schema_migrations` — migration đã chạy

Thay đổi schema sau khi đã có dữ liệu: thêm file mới `database/migrations/NNN_ten.sql` (không sửa file đã chạy), sửa luôn `schema.sql` cho giống, rồi chạy `npm run db:migrate`. Mỗi migration chỉ chạy 1 lần (ghi vào bảng `schema_migrations`), không xoá dữ liệu; `deploy.sh` tự chạy lệnh này mỗi lần deploy.

Bản v4 thêm migration `004_v4_chat_settings_sessions.sql` (phiên đăng nhập, cài đặt, chat, cuộc gọi).

## Deploy lên VPS

Xem hướng dẫn đầy đủ ở [`deploy/README.md`](../deploy/README.md).

### Lên bản v4 từ bản đang chạy

```bash
cd ~/NT118.R15_Nhom9
bash deploy/deploy.sh Backend_API_v4    # kéo code, npm install (thêm thư viện ws), chạy migration 004, reload PM2
```

Không cần sửa `.env` (các biến mới đều có mặc định) và không cần sửa Nginx (cấu hình sẵn có đã cho phép WebSocket).
Sau khi lên v4, token đăng nhập cũ vẫn refresh được; lần refresh đầu tiên sẽ gắn thiết bị vào 1 phiên mới.

### Lưu ý

- Đặt `NODE_ENV=production`, `PUBLIC_BASE_URL=http://<IP-VPS>` (dùng để tạo URL ảnh), `JWT_ACCESS_SECRET` là chuỗi ngẫu nhiên dài.
- Tạo user MySQL riêng cho app, không dùng `root`.
- `npm run db:init` **xoá toàn bộ dữ liệu**, chỉ chạy lần đầu.
- Thư mục `uploads/` chứa ảnh người dùng, cần giữ lại giữa các lần deploy.
