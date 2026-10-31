#!/usr/bin/env bash
# =====================================================================
#  DEPLOY TỪ MÁY CÁ NHÂN (Git Bash / Linux / macOS) - không cần mở SSH thủ công
#    bash deploy/remote-deploy.sh <IP-VPS> <đường-dẫn-file-.pem> [branch]
#  Ví dụ:
#    bash deploy/remote-deploy.sh 13.229.10.20 ~/keys/nhom9.pem Backend_API_v1
#
#  Có thể đặt sẵn biến để khỏi gõ: export VPS_IP=... VPS_KEY=... VPS_USER=ubuntu
# =====================================================================
set -euo pipefail

VPS_IP="${1:-${VPS_IP:-}}"
VPS_KEY="${2:-${VPS_KEY:-}}"
BRANCH="${3:-}"
VPS_USER="${VPS_USER:-ubuntu}"
REMOTE_DIR="${REMOTE_DIR:-~/NT118.R15_Nhom9}"

if [[ -z "$VPS_IP" || -z "$VPS_KEY" ]]; then
  echo "Cách dùng: bash deploy/remote-deploy.sh <IP-VPS> <file-.pem> [branch]"
  exit 1
fi
[[ -f "$VPS_KEY" ]] || { echo "Không thấy file key: $VPS_KEY"; exit 1; }
chmod 400 "$VPS_KEY" 2>/dev/null || true

echo "==> Deploy lên $VPS_USER@$VPS_IP ..."
ssh -i "$VPS_KEY" -o StrictHostKeyChecking=accept-new "$VPS_USER@$VPS_IP" \
  "cd $REMOTE_DIR && bash deploy/deploy.sh $BRANCH"

echo "==> Kiểm tra từ ngoài Internet:"
curl -fsS "http://$VPS_IP/api/health" && echo
