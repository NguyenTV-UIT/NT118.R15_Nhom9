# Backend - Dating App (Nhóm 9, NT118.R15)

REST API viết bằng **Node.js (Express)** + **MySQL 8**. Android app (Retrofit) và Web Admin cùng gọi vào server này.

## Cấu trúc thư mục

```
backend/
├── database/schema.sql      # Toàn bộ bảng (users, profiles, swipes, matches, messages, ...)
├── scripts/
│   ├── db-init.js           # Tạo DB + chạy schema.sql (XOÁ dữ liệu cũ)
│   └── db-seed.js           # Dữ liệu mẫu: sở thích + tài khoản demo
├── src/
│   ├── config/              # env.js (đọc .env), db.js (MySQL pool)
│   ├── middlewares/         # auth (JWT), validate (zod), upload (multer), errorHandler
│   ├── modules/
│   │   ├── auth/            # đăng ký, đăng nhập, refresh token, đổi mật khẩu
│   │   ├── users/           # hồ sơ, tiêu chí, sở thích, ảnh
│   │   └── home/            # feed gợi ý, swipe, match
│   ├── utils/
│   ├── app.js               # khai báo middleware + routes
│   └── server.js            # khởi động HTTP server
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

Tài khoản demo (mật khẩu `123456`): `an@demo.local`, `binh@demo.local`, `chi@demo.local`, `dung@demo.local`, `giang@demo.local`, `hoa@demo.local`, `khoa@demo.local`, `admin@datingapp.local` (role admin).

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

## Danh sách API (giữa kỳ)

| Method | Endpoint | Auth | Mô tả |
|---|---|---|---|
| GET | `/api/health` | | Kiểm tra server + DB |
| **Auth** | | | |
| POST | `/api/auth/register` | | Bắt buộc: `{email, password, displayName, birthday: "YYYY-MM-DD", gender: male\|female\|other}` (≥ 18 tuổi). Không bắt buộc: `interestedIn, city, latitude, longitude, interestIds` → dùng làm kết quả Home mặc định |
| POST | `/api/auth/login` | | `{email, password}` |
| POST | `/api/auth/refresh` | | `{refreshToken}` → token mới |
| POST | `/api/auth/logout` | | `{refreshToken}` → thu hồi token |
| PUT | `/api/auth/password` | ✔ | `{currentPassword, newPassword}` |
| **User Profile** | | | |
| GET | `/api/users/me` | ✔ | Hồ sơ đầy đủ của mình (kèm preferences, photos, interests) |
| PUT | `/api/users/me/profile` | ✔ | Sửa `displayName, birthday, gender, bio, jobTitle, school, city, latitude, longitude` (gửi field nào sửa field đó) |
| PUT | `/api/users/me/preferences` | ✔ | `{interestedIn: male\|female\|everyone, minAge, maxAge, maxDistanceKm}` |
| GET | `/api/users/interests` | ✔ | Danh sách sở thích để chọn |
| PUT | `/api/users/me/interests` | ✔ | `{interestIds: [1,2,3]}` (tối đa 10) |
| POST | `/api/users/me/photos` | ✔ | multipart, field `photo` (jpg/png/webp, ≤ 5MB, tối đa 6 ảnh; ảnh đầu tiên là avatar) |
| DELETE | `/api/users/me/photos/:photoId` | ✔ | Xoá ảnh |
| GET | `/api/users/:id` | ✔ | Hồ sơ công khai của người khác (có `age`, `distanceKm`, không lộ email/ngày sinh/toạ độ) |
| **Home** | | | |
| GET | `/api/home/feed?limit=20` | ✔ | **(1) Kết quả mặc định** (chưa filter): dựa trên thông tin đăng ký + tiêu chí đã lưu, sắp theo `matchScore` |
| GET | `/api/home/search?...` | ✔ | **(2) Kết quả khi thêm filter** (xem bảng filter bên dưới) |
| POST | `/api/home/swipe` | ✔ | `{targetId, action: like\|pass\|superlike}` → `{matched, matchId?, user?}` |
| GET | `/api/home/matches` | ✔ | Danh sách đã match + tin nhắn cuối |
| DELETE | `/api/home/matches/:matchId` | ✔ | Huỷ match |

Ví dụ gọi thử từng API có trong `api.http` và `postman/DatingApp.postman_collection.json`.

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

Mỗi người trong kết quả của cả 2 API có thêm `matchScore` (0–100) và `commonInterestCount` (số sở thích chung), FE có thể hiện kiểu "Hợp nhau 86%".

## Database

Sơ đồ bảng (xem chi tiết trong `database/schema.sql`):

- `users`, `refresh_tokens` — tài khoản, phiên đăng nhập
- `profiles`, `preferences`, `photos`, `interests`, `user_interests` — hồ sơ
- `swipes`, `matches` — quẹt & ghép đôi (`matches.user1_id < user2_id`)
- `messages` — chat (giai đoạn sau, qua WebSocket)
- `blocks`, `reports` — an toàn & kiểm duyệt (Web Admin)
- `notifications`, `device_tokens` — thông báo, FCM push

Đã tạo sẵn bảng cho các phần sau để không phải đổi schema giữa chừng; API của chúng sẽ thêm ở 3 tuần hoàn thiện.

## Deploy lên VPS

Xem hướng dẫn đầy đủ ở [`deploy/README.md`](../deploy/README.md).

### Lưu ý

- Đặt `NODE_ENV=production`, `PUBLIC_BASE_URL=http://<IP-VPS>` (dùng để tạo URL ảnh), `JWT_ACCESS_SECRET` là chuỗi ngẫu nhiên dài.
- Tạo user MySQL riêng cho app, không dùng `root`.
- `npm run db:init` **xoá toàn bộ dữ liệu**, chỉ chạy lần đầu.
- Thư mục `uploads/` chứa ảnh người dùng, cần giữ lại giữa các lần deploy.
