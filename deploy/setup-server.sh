#!/usr/bin/env bash
# =====================================================================
#  CÀI ĐẶT VPS LẦN ĐẦU - Dating App Nhóm 9
#  Hệ điều hành: Ubuntu 22.04 / 24.04 (EC2 hoặc VPS bất kỳ)
#
#  Cách chạy (trên VPS):
#    git clone -b Backend_API_v1 https://github.com/NguyenTV-UIT/NT118.R15_Nhom9.git ~/NT118.R15_Nhom9
#    cd ~/NT118.R15_Nhom9 && sudo bash deploy/setup-server.sh
#
#  Script làm:
#    1. Cài Node.js 22 LTS, MySQL 8, Nginx, PM2, firewall (ufw)
#    2. Tạo database + user MySQL riêng cho app (mật khẩu ngẫu nhiên)
#    3. Tạo file backend/.env (JWT secret ngẫu nhiên, PUBLIC_BASE_URL = IP VPS)
#    4. Tạo bảng + dữ liệu mẫu, cấu hình Nginx, chạy app bằng PM2
#  Chạy lại nhiều lần không sao: bước nào đã làm rồi sẽ bỏ qua.
# =====================================================================
set -euo pipefail

# ---------- Cấu hình (có thể sửa) ----------
NODE_MAJOR=22
DB_NAME=dating_app
DB_USER=dating_app
APP_PORT=3000
PM2_NAME=dating-api
# --------------------------------------------

log()  { echo -e "\n\033[1;32m==> $*\033[0m"; }
warn() { echo -e "\033[1;33m[!] $*\033[0m"; }
die()  { echo -e "\033[1;31m[x] $*\033[0m"; exit 1; }

[[ $EUID -eq 0 ]] || die "Hãy chạy bằng sudo: sudo bash deploy/setup-server.sh"
command -v apt-get >/dev/null || die "Script này dành cho Ubuntu/Debian (cần apt-get)"

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$REPO_DIR/backend"
APP_USER="${SUDO_USER:-ubuntu}"
[[ -f "$BACKEND_DIR/package.json" ]] || die "Không tìm thấy $BACKEND_DIR/package.json"

as_app() { sudo -u "$APP_USER" -H bash -lc "cd '$BACKEND_DIR' && $*"; }

# Lấy IP public: EC2 metadata (IMDSv2) -> dịch vụ ngoài -> IP nội bộ
detect_ip() {
  local token="" ip=""
  token=$(curl -s -m 2 -X PUT "http://169.254.169.254/latest/api/token" \
    -H "X-aws-ec2-metadata-token-ttl-seconds: 60" 2>/dev/null || true)
  if [[ -n "$token" ]]; then
    ip=$(curl -s -m 2 -H "X-aws-ec2-metadata-token: $token" \
      http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null || true)
  fi
  [[ "$ip" =~ ^[0-9.]+$ ]] || ip=$(curl -s -m 5 https://api.ipify.org 2>/dev/null || true)
  [[ "$ip" =~ ^[0-9.]+$ ]] || ip=$(hostname -I | awk '{print $1}')
  echo "$ip"
}

# ---------------------------------------------------------------------
log "1/7 Cập nhật hệ thống & cài gói cơ bản"
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y curl ca-certificates gnupg git ufw openssl

# ---------------------------------------------------------------------
log "2/7 Cài Node.js $NODE_MAJOR LTS + PM2"
if ! command -v node >/dev/null || [[ "$(node -v | cut -d. -f1 | tr -d v)" -lt 20 ]]; then
  curl -fsSL "https://deb.nodesource.com/setup_${NODE_MAJOR}.x" | bash -
  apt-get install -y nodejs
fi
command -v pm2 >/dev/null || npm install -g pm2
echo "Node $(node -v), npm $(npm -v), PM2 $(pm2 -v)"

# ---------------------------------------------------------------------
log "3/7 Cài MySQL 8"
if ! command -v mysql >/dev/null; then
  apt-get install -y mysql-server
fi
systemctl enable --now mysql

# MySQL chỉ nghe ở localhost (không mở ra Internet)
MYSQL_CNF=/etc/mysql/mysql.conf.d/mysqld.cnf
if [[ -f $MYSQL_CNF ]] && ! grep -q "^bind-address\s*=\s*127.0.0.1" $MYSQL_CNF; then
  sed -i 's/^#\?\s*bind-address.*/bind-address = 127.0.0.1/' $MYSQL_CNF
  systemctl restart mysql
fi

# ---------------------------------------------------------------------
log "4/7 Tạo database & file .env"
ENV_FILE="$BACKEND_DIR/.env"
if [[ -f "$ENV_FILE" ]]; then
  warn "Đã có $ENV_FILE -> giữ nguyên (xoá file này nếu muốn tạo lại)"
  DB_PASSWORD=$(grep -E '^DB_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)
else
  DB_PASSWORD=$(openssl rand -hex 16)
  JWT_SECRET=$(openssl rand -hex 48)
  PUBLIC_IP=$(detect_ip)
  echo "IP public phát hiện được: $PUBLIC_IP"

  cat > "$ENV_FILE" <<EOF
NODE_ENV=production
PORT=$APP_PORT
PUBLIC_BASE_URL=http://$PUBLIC_IP

DB_HOST=127.0.0.1
DB_PORT=3306
DB_USER=$DB_USER
DB_PASSWORD=$DB_PASSWORD
DB_NAME=$DB_NAME

JWT_ACCESS_SECRET=$JWT_SECRET
JWT_ACCESS_EXPIRES_IN=15m
REFRESH_TOKEN_DAYS=30

CORS_ORIGINS=*
EOF
  chown "$APP_USER":"$APP_USER" "$ENV_FILE"
  chmod 600 "$ENV_FILE"
fi

# Ubuntu: root MySQL đăng nhập bằng auth_socket -> dùng `mysql` khi chạy sudo
mysql <<SQL
CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
ALTER USER '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USER'@'localhost';
FLUSH PRIVILEGES;
SQL

# ---------------------------------------------------------------------
log "5/7 Cài thư viện Node & tạo bảng"
chown -R "$APP_USER":"$APP_USER" "$REPO_DIR"
as_app "npm install --omit=dev --no-audit --no-fund"
mkdir -p "$BACKEND_DIR/uploads" && chown "$APP_USER":"$APP_USER" "$BACKEND_DIR/uploads"

TABLES=$(mysql -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME'")
if [[ "$TABLES" -eq 0 ]]; then
  as_app "npm run db:init && npm run db:seed"
else
  warn "Database đã có $TABLES bảng -> KHÔNG chạy lại schema (tránh mất dữ liệu)"
fi

# ---------------------------------------------------------------------
log "6/7 Cấu hình Nginx (port 80 -> Node :$APP_PORT)"
apt-get install -y nginx
sed "s/__APP_PORT__/$APP_PORT/g" "$REPO_DIR/deploy/nginx/dating-app.conf" > /etc/nginx/sites-available/dating-app
ln -sf /etc/nginx/sites-available/dating-app /etc/nginx/sites-enabled/dating-app
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl enable nginx
systemctl reload nginx

# Firewall: chỉ mở SSH + HTTP (MySQL & port 3000 không mở ra ngoài)
ufw allow OpenSSH >/dev/null
ufw allow 'Nginx HTTP' >/dev/null
ufw --force enable >/dev/null
ufw status | sed 's/^/   /'

# ---------------------------------------------------------------------
log "7/7 Chạy app bằng PM2 (tự khởi động lại khi reboot)"
as_app "pm2 startOrReload ecosystem.config.js --env production && pm2 save"
env PATH="$PATH:/usr/bin" pm2 startup systemd -u "$APP_USER" --hp "$(eval echo ~"$APP_USER")" >/dev/null

# ---------------------------------------------------------------------
sleep 3
PUBLIC_URL=$(grep -E '^PUBLIC_BASE_URL=' "$ENV_FILE" | cut -d= -f2-)
if curl -fsS "http://127.0.0.1/api/health" >/dev/null; then
  log "HOÀN TẤT! API đang chạy tại: $PUBLIC_URL/api/"
  echo "   Kiểm tra:    $PUBLIC_URL/api/health"
  echo "   Xem log:     pm2 logs $PM2_NAME"
  echo "   Deploy lại:  bash deploy/deploy.sh"
  echo
  warn "Nhớ mở port 80 (HTTP) trong Security Group của EC2 nếu chưa mở"
else
  die "App chưa phản hồi. Xem lỗi bằng: pm2 logs $PM2_NAME --lines 50"
fi
