# Deploy backend lên VPS (EC2) - không dùng Vercel

```
Android app / Web Admin
        │  http://<IP-VPS>/api/...
        ▼
   Nginx :80  ──►  Node.js (PM2) :3000  ──►  MySQL :3306
  (mở ra ngoài)    (chỉ trong máy)           (chỉ trong máy)
```

| File | Chạy ở đâu | Khi nào |
|---|---|---|
| `setup-server.sh` | VPS, `sudo` | **1 lần** khi mới có VPS |
| `deploy.sh` | VPS, user thường | Mỗi lần có code mới |
| `remote-deploy.sh` | Máy cá nhân (Git Bash) | Thay cho `deploy.sh`, khỏi SSH thủ công |
| `nginx/dating-app.conf` | | Cấu hình reverse proxy (setup tự cài) |
| `../backend/ecosystem.config.js` | | Cấu hình PM2 |

## Bước 0: Security Group trên AWS

EC2 → Instances → chọn máy → tab **Security** → bấm vào Security Group → **Edit inbound rules**:

| Type | Port | Source |
|---|---|---|
| SSH | 22 | My IP (hoặc 0.0.0.0/0 nếu cả nhóm cùng SSH) |
| HTTP | 80 | 0.0.0.0/0 |

**Không** mở 3000 và 3306.

## Bước 1: SSH vào VPS

```bash
ssh -i nhom9.pem ubuntu@<IP-VPS>
```

Windows dùng Git Bash hoặc PowerShell. Nếu báo lỗi quyền file `.pem`, chạy trong Git Bash: `chmod 400 nhom9.pem`.

## Bước 2: Cài đặt lần đầu (khoảng 5 phút)

```bash
git clone -b Backend_API_v1 https://github.com/NguyenTV-UIT/NT118.R15_Nhom9.git ~/NT118.R15_Nhom9
cd ~/NT118.R15_Nhom9
sudo bash deploy/setup-server.sh
```

Script tự cài Node.js 22, MySQL 8, Nginx, PM2, firewall, tạo database với mật khẩu ngẫu nhiên, tạo `backend/.env`, seed dữ liệu mẫu rồi chạy app.
Chạy xong mở trình duyệt: `http://<IP-VPS>/api/health` → thấy `"database": "up"` là xong.

> Repo private thì `git clone` sẽ hỏi đăng nhập: dùng username GitHub + **Personal Access Token** (không phải mật khẩu).

## Bước 3: Deploy khi có code mới

Push code lên GitHub, rồi chọn 1 trong 2 cách:

```bash
# Cách 1: trên VPS
cd ~/NT118.R15_Nhom9 && bash deploy/deploy.sh

# Cách 2: từ máy cá nhân
bash deploy/remote-deploy.sh <IP-VPS> ~/nhom9.pem
```

Nếu app mới bị lỗi không khởi động được, script **tự quay về bản trước**.
Deploy không đụng tới database và ảnh đã upload.

## Lệnh hay dùng trên VPS

```bash
pm2 status                 # app có đang chạy không
pm2 logs dating-api        # xem log realtime (Ctrl+C để thoát)
pm2 restart dating-api     # khởi động lại
sudo mysql dating_app      # vào MySQL xem dữ liệu
sudo tail -f /var/log/nginx/dating-app.error.log
```

## Lưu ý

- **IP thay đổi khi Stop/Start EC2** (trừ khi dùng Elastic IP). Khi đổi IP: sửa `PUBLIC_BASE_URL` trong `backend/.env`, chạy `pm2 restart dating-api`, và báo FE đổi base URL. Nên gắn **Elastic IP** (miễn phí khi đang gắn vào máy đang chạy).
- `backend/.env` trên VPS chứa mật khẩu DB và JWT secret: **không commit**, không gửi lên nhóm chat.
- Reset dữ liệu về như mới (xoá hết): `cd backend && npm run db:reset && pm2 restart dating-api`.
- Không sửa code trực tiếp trên VPS, `deploy.sh` sẽ từ chối chạy. Sửa trên máy → push → deploy.
- Hiện chạy HTTP. Khi có domain, cài HTTPS bằng `sudo apt install certbot python3-certbot-nginx && sudo certbot --nginx`.

## Base URL cho Android

```java
public static final String BASE_URL = "http://<IP-VPS>/api/";
```

Android 9+ chặn HTTP mặc định → thêm vào `AndroidManifest.xml`, thẻ `<application>`:

```xml
android:usesCleartextTraffic="true"
```
