# Tích hợp Android với backend v4

## Các luồng đã nối

- Đăng nhập email: `POST /auth/login`, lưu `data.tokens` và `data.user` vào
  SessionStore. Không qua OTP. AuthFlow dùng `user.onboarding.completed` và
  `missing` để mở Home hoặc bước hồ sơ bắt buộc còn thiếu đầu tiên.
- Đăng ký: `POST /auth/register`, chỉ gửi email/password (8–72 ký tự), lưu
  token và chuyển sang điền hồ sơ. Tên được nhập ở bước hồ sơ sau đăng ký.
- Quên mật khẩu: `POST /auth/password/forgot` → `/auth/password/verify-otp` →
  `/auth/password/reset`. Chỉ chuyển sang đặt lại mật khẩu khi server cấp
  resetToken. Thời gian chờ gửi lại lấy từ response; sau reset quay về login.
- Đổi mật khẩu trong Settings: `PUT /auth/password` với currentPassword và
  newPassword (6–72 ký tự); sau thành công yêu cầu đăng nhập lại.
- Logout: xoá phiên local và gửi yêu cầu thu hồi refresh token qua `/auth/logout`.

Mỗi bước tên, ngày sinh, giới tính/showGender, chiều cao, cân nặng, bio và lựa
chọn cá nhân gọi `PUT /users/me/profile`. Backend nhận nhãn tiếng Việt trên
onboarding và trả mã. Ngày sinh được kiểm tra ngày có thật ở cả client/server.
Đối tượng hẹn hò và khoảng cách lưu bằng `PUT /users/me/preferences`.
Sở thích onboarding gửi `interestNames` đến `/users/me/interests` (ít nhất 3).

Màn ảnh tải danh sách hiện có từ `/users/me`; upload ngay khi chọn, multipart
field `photo`, JPG/PNG/WebP, tối đa 5 MB/ảnh và 6 ảnh. Chỉ tính ảnh server đã
chấp nhận; xoá theo photo ID. Mặc định cần ít nhất 2 ảnh để tiếp tục. Các bước
chỉ chuyển màn khi server chấp nhận. Màn Hồ sơ sẵn sàng gọi
`POST /users/me/onboarding/complete` trước khi mở Home; lỗi thiếu thông tin được
hiển thị để người dùng quay lại bổ sung.

Trang Khám phá gọi `/home/feed`, `/home/search`, `/users/interests` và
`/home/swipe`. Chỉ hiện match khi `data.matched = true`; lỗi quẹt giữ nguyên thẻ.
Hồ sơ chi tiết và album dùng ảnh URL thật. Chiều cao hiển thị khi có dữ liệu;
giới tính `null` được ẩn, không đổi thành giới tính khác. Các thông tin cá nhân
công khai dùng nhãn từ `/meta/options`, không hiển thị cân nặng. Trang Hồ sơ
của tôi đọc `/users/me` thay cho dữ liệu tài khoản demo.

## Kết nối server

API mặc định: `http://13.228.203.107/api/` trên EC2, dùng được từ Genymotion
và điện thoại có Internet. Đã kiểm tra `/api/health` trả HTTP 200 và
`database: up`. App truy cập qua cổng 80, không dùng trực tiếp cổng 3000.
Để chuyển sang server khác hoặc backend local, ghi đè khi build, ví dụ:

```powershell
.\gradlew.bat :app:assembleDebug -Pdatezone.apiBaseUrl=http://192.168.1.10:3000/api/
```

Có thể đặt `datezone.apiBaseUrl` trong Gradle properties riêng trên máy phát
triển. Debug cho phép HTTP; release cần HTTPS. Backend và MySQL phải đang
chạy. Đọc `../backend/README.md` để cấu hình môi trường, chạy migration và
khởi động đúng phiên bản backend v3. Không cần sửa AndroidManifest để đổi URL.

OTP email mặc định của backend ghi vào log khi `MAIL_PROVIDER=console`;
muốn gửi email thật cần cấu hình SMTP trên server. App không tự tạo/chấp nhận
mã OTP, không tự dùng tài khoản demo và không tự khởi động backend.

## Lớp dùng chung

ApiClient có `request` cho API cần token, `publicRequest` cho login/register/
reset/meta và `uploadPhoto` cho ảnh. Mọi request chạy ngoài main thread.
Request cần xác thực tự refresh khi nhận 401 rồi thử lại đúng một lần. Các
lần refresh được tuần tự hóa vì refresh token chỉ dùng được một lần. API công
khai trả 401 sẽ báo lỗi trực tiếp. Refresh cũ không thể khôi phục phiên sau
logout hoặc ghi đè token của tài khoản mới. SessionStore không được backup.

ApiUi ngăn gửi lặp form, hiện trạng thái đang xử lý và lỗi từ server, giữ lại
màn hình khi request thất bại. AuthFlow lưu token sau login/register và chọn
bước onboarding. SessionStore lưu bản hồ sơ gần nhất để hiển thị trên app.

## Phạm vi còn lại

Chỉnh sửa và xem trước hồ sơ đã đọc `/users/me`, không dùng thông tin mẫu hay
UserProfilePrefs cũ. Editor tải nhãn lựa chọn từ `/meta/options`, lưu các trường
đã đổi bằng `/users/me/profile`, lưu sở thích bằng `/users/me/interests` và
thêm/xoá ảnh qua API ảnh. Ảnh được lưu ngay khi thêm/xoá; các trường và sở thích
được lưu bằng nút Lưu. Tab Hồ sơ tải lại dữ liệu khi quay về từ editor.
Editor giữ bản nháp khi xoay màn hình, không ghi đè ngày sinh/showGender hay
trường khác không có trên form. Nếu đổi tên thành phố thủ công, xoá cặp toạ độ
cũ để tránh tìm kiếm theo vị trí không còn đúng. Lỗi request giữ form để thử lại;
nếu thông tin cơ bản đã lưu nhưng sở thích lỗi, app thông báo phần chưa lưu.

## Đợt nối API v4 (lượt thích, chat, cuộc gọi, cài đặt, an toàn)

Đợt này **chưa được build / chạy thử trên máy thật hay giả lập** (môi trường viết code không có Android SDK);
mới kiểm tra cú pháp Java và XML. Trước khi merge cần build bằng Android Studio và chạy qua các luồng dưới đây.

| Màn hình | API | Ghi chú |
|---|---|---|
| Lượt thích (`MatchesActivity`) | `GET /home/likes`, `POST /home/swipe` | Chia nhóm Hôm nay / Hôm qua / Trước đó theo `group`. Thích lại thành tương hợp → `MatchSuccessDialog` → mở chat thật |
| Khám phá (`HomeActivity`) | `POST /home/swipe` | "Gửi lời chào" sau khi tương hợp mở chat thật bằng `matchId` |
| Bộ lọc (`FiltersActivity`) | `GET /home/search` | Bật chiều cao, cân nặng, mục tiêu, cung hoàng đạo, hút thuốc, rượu bia, tập luyện, học vấn. Chỉ gửi mục người dùng đã chạm vào. Xu hướng tính dục, vị trí, mốc khoảng cách vẫn tắt |
| Tin nhắn (`ChatListActivity`) | `GET /chats` | Tự làm mới 5 giây / lần. Dòng mẫu trong layout được thay bằng `item_chat_conversation.xml` |
| Tìm kiếm chat (`ChatSearchActivity`) | `GET /chats` | Tải 1 lần, lọc theo tên trên máy (không phân biệt dấu) |
| Chat (`ChatDetailActivity`) | `GET /chats/:id`, `GET/POST /chats/:id/messages`, `POST /chats/:id/photos`, `POST /chats/:id/read` | Dùng dữ liệu thật khi mở bằng `EXTRA_MATCH_ID` (`ChatUi.chatIntent`). Tin mới lấy bằng cách hỏi lại 3 giây / lần (`?after=`) |
| Thân thiết (`ChatIntimacyActivity`) | `GET /chats/intimacy` | Thẻ dựng từ `item_intimacy_card.xml` |
| Cuộc gọi (`ChatCallActivity`) | `POST /chats/:id/calls`, `GET /calls/:id`, `POST /calls/:id/end`, `GET /calls/active`, `POST /calls/:id/accept\|reject` | Xem giới hạn bên dưới |
| Hồ sơ người khác (`ProfileObject2Activity`) | `POST /users/:id/block` | Hiện lại nút Chặn / Báo cáo cho hồ sơ từ máy chủ |
| Báo cáo (`ReportUserActivity`) | `POST /users/:id/report` | Lý do lấy theo radio đang chọn |
| Cài đặt (`SettingsActivity`) | `GET/PUT /users/me/settings`, `GET /users/me/sessions`, `DELETE /users/me/sessions[/:id]` | 3 công tắc lưu ngay; hiện thiết bị này + 1 thiết bị khác gần nhất |
| Đổi email | `POST /users/me/email/verify-current` → `POST /users/me/email/verify-otp` → `PUT /users/me/email` | `VerifyCurrentEmailActivity` → `OtpVerificationActivity` → `ChangeEmailActivity` |
| Xoá tài khoản (`DeleteAccountActivity`) | `DELETE /users/me` | Máy chủ kiểm tra chữ XÓA; thành công mới xoá phiên trên máy |

Lớp dùng chung mới: `api/ChatUi` (avatar, giờ, mở chat), `api/IncomingCalls` (hỏi Nghe / Từ chối khi có cuộc gọi đến),
`ApiClient.uploadPhotoTo(path, uri)`. Mọi request gửi kèm `X-Device-Name` / `X-Device-Platform` để màn
"Thiết bị đang đăng nhập" hiện đúng tên máy.

### Giới hạn còn lại

- **Chưa có WebSocket**: app chưa có thư viện WebSocket nên chat và danh sách chat dùng cách hỏi lại định kỳ.
  Tin nhắn tới chậm tối đa ~3 giây; chưa có "đang nhập" và trạng thái online tức thời.
- **Cuộc gọi chưa có âm thanh / hình ảnh thật**: app mới đăng ký cuộc gọi với máy chủ (đổ chuông, nhấc máy,
  từ chối, kết thúc, gọi nhỡ, nhật ký trong chat). Muốn nghe / thấy nhau cần thêm thư viện WebRTC và trao đổi
  gói qua WebSocket (`call.signal`). Cuộc gọi đến chỉ được phát hiện khi đang mở màn Tin nhắn hoặc màn chat.
- **Thông báo đẩy** (FCM) chưa nối: chưa gọi `POST /users/me/device-token`.
- Màn Cài đặt chỉ hiện 1 thiết bị khác (layout có sẵn 1 dòng); các thiết bị còn lại ghi dạng "+N thiết bị khác".
- Mở lại màn Bộ lọc không khôi phục các lựa chọn nâng cao đã áp dụng lần trước.
- Đổi email khi máy chủ bật `EMAIL_CHANGE_VERIFY_NEW=true` (OTP gửi tới email mới) chưa có màn tương ứng.
- Hồ sơ mở từ màn chat dùng `ProfileObject2Activity` ở chế độ chỉ xem (ẩn nút Thích / Bỏ qua).

## Kiểm tra

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -Pdatezone.apiBaseUrl=http://127.0.0.1:38127/api/ --offline
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.datezone.app.api.ApiContractTest com.datezone.app.test/androidx.test.runner.AndroidJUnitRunner
```

Các test HTTP dùng server giả lập ngay trên thiết bị, preferences riêng,
không gọi backend thật. Bao phủ JSON nullable/ẩn giới tính, login/register,
resetToken, định tuyến onboarding, thiếu session, refresh/thử lại request,
token thu hồi, logout trong lúc refresh, lỗi validation, multipart ảnh và
giới hạn 5 MB. Các test HTTP chỉ chạy khi URL đúng địa chỉ test ở trên.
Sau test phải build/cài lại không có tham số test để dùng URL API bình thường.

Đã chạy 21 instrumentation test thành công, gồm mở editor với dữ liệu server
khác dữ liệu local, sửa tên/chiều cao, kiểm tra request PUT và dữ liệu cache sau
lưu. Các test bổ sung kiểm tra số đo, xoá giá trị tuỳ chọn, mã lựa chọn và giữ
nguyên ngày sinh/showGender khi không chỉnh sửa.

Backend v3 trên EC2 đã được kiểm tra đăng ký, đăng nhập và lấy hồ sơ thật.
Người dùng đã xác nhận nhận được OTP email sau khi cấu hình SMTP. Các thay đổi
editor được kiểm thử bằng server giả lập; không sửa hồ sơ tài khoản thật trong
bài kiểm tra tự động. Backend local chưa có `.env` và `node_modules`.
