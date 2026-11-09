# Tích hợp Android với backend v3

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

Danh sách lượt thích/tương hợp, chat, cuộc gọi, màn chỉnh sửa hồ sơ cũ, đổi
email, xoá tài khoản và báo cáo/chặn chưa nối trong đợt này. Các bộ lọc chưa
có trong hợp đồng tìm kiếm vẫn vô hiệu hóa. Search hỗ trợ giới tính, tuổi,
khoảng cách, sở thích (tối đa 10); không ghi đè preferences đã lưu trên server.
OTP cho đổi email chưa có API nên không mô phỏng xác thực thành công.

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

Đã chạy 15 instrumentation test thành công. Bản backend local chưa có `.env`
và `node_modules`. Server EC2 đã được kiểm tra health qua API công khai;
`GET /api/meta/options` và `POST /api/auth/password/forgot` đang trả 404.
Gửi `{}` tới `/api/auth/register` còn báo bắt buộc displayName/birthday/gender,
khác hợp đồng v3 chỉ yêu cầu email/password. Cần kiểm tra nhánh/tiến trình
backend đang phục vụ cổng 80 và restart sau khi deploy. Chưa kiểm thử toàn bộ
luồng tài khoản/SMTP với một tài khoản thật.
