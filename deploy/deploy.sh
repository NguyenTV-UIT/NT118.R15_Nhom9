#!/usr/bin/env bash
# =====================================================================
#  DEPLOY CODE MỚI - chạy trên VPS mỗi khi có code mới trên GitHub
#    cd ~/NT118.R15_Nhom9 && bash deploy/deploy.sh            # branch hiện tại
#    bash deploy/deploy.sh main                                # đổi sang branch khác
#
#  Script làm: kéo code mới -> cài thư viện -> reload PM2 (không downtime)
#  -> kiểm tra /api/health, nếu lỗi thì tự quay về bản trước.
#  KHÔNG đụng vào database và thư mục uploads/.
# =====================================================================
set -euo pipefail

PM2_NAME=dating-api
log()  { echo -e "\n\033[1;32m==> $*\033[0m"; }
die()  { echo -e "\033[1;31m[x] $*\033[0m"; exit 1; }

[[ $EUID -ne 0 ]] || die "Không chạy bằng sudo/root, chạy bằng user thường: bash deploy/deploy.sh"

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$REPO_DIR/backend"
cd "$REPO_DIR"

BRANCH="${1:-$(git rev-parse --abbrev-ref HEAD)}"
[[ -f "$BACKEND_DIR/.env" ]] || die "Chưa có backend/.env -> chạy 'sudo bash deploy/setup-server.sh' trước"

if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  die "Có file bị sửa trực tiếp trên VPS (git status). Hãy sửa trên máy, push lên GitHub rồi deploy lại."
fi

PREV_COMMIT=$(git rev-parse HEAD)

log "Kéo code mới từ branch '$BRANCH'"
git fetch origin "$BRANCH"
git checkout -q "$BRANCH" 2>/dev/null || git checkout -q -b "$BRANCH" "origin/$BRANCH"
git reset -q --hard "origin/$BRANCH"
NEW_COMMIT=$(git rev-parse HEAD)

if [[ "$PREV_COMMIT" == "$NEW_COMMIT" ]]; then
  echo "Không có commit mới ($(git log -1 --format='%h %s'))"
else
  echo "Các commit mới:"
  git log --format='   %h %an: %s' "$PREV_COMMIT..$NEW_COMMIT"
fi

log "Cài thư viện"
cd "$BACKEND_DIR"
npm install --omit=dev --no-audit --no-fund

log "Reload app"
pm2 startOrReload ecosystem.config.js --env production
pm2 save >/dev/null

log "Kiểm tra /api/health"
for i in {1..10}; do
  if curl -fsS http://127.0.0.1:3000/api/health >/dev/null 2>&1; then
    log "Deploy thành công: $(git log -1 --format='%h %s')"
    exit 0
  fi
  sleep 2
done

echo -e "\033[1;31m[x] App không phản hồi sau khi deploy -> quay về $PREV_COMMIT\033[0m"
pm2 logs "$PM2_NAME" --lines 30 --nostream || true
cd "$REPO_DIR"
git reset -q --hard "$PREV_COMMIT"
cd "$BACKEND_DIR"
npm install --omit=dev --no-audit --no-fund
pm2 startOrReload ecosystem.config.js --env production
die "Đã rollback. Sửa lỗi ở trên rồi push lại."
