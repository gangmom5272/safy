#!/bin/bash
# backend를 이전 배포로 되돌리는 스크립트. 서버에서 직접 실행한다.
#
# 사용법:
#   bash rollback.sh          # 배포 이력에서 바로 이전 태그로 자동 롤백
#   bash rollback.sh <태그>    # 특정 커밋 해시 태그로 롤백
#
# 주의: --build를 쓰지 않는다 — 이미 서버에 있는 이미지를 그대로 재사용해야
# "다시 빌드하지 않고 즉시 되돌리는" 롤백의 의미가 있다. 대상 태그의 이미지가
# 없으면(예: 디스크 정리로 지워졌으면) 이 스크립트로는 롤백할 수 없다.

set -euo pipefail

cd "$(dirname "$0")/.."   # infra/ 로 이동

LOG_FILE="../.deployed_tags"

if [ ! -f "$LOG_FILE" ]; then
  echo "배포 이력 파일이 없습니다: $LOG_FILE"
  exit 1
fi

TARGET_TAG="${1:-}"

if [ -z "$TARGET_TAG" ]; then
  # rollback-to: 기록은 제외하고, 실제 배포 이력 중 마지막에서 두 번째(=현재 이전) 것을 고른다.
  TARGET_TAG=$(grep -v '^rollback-to:' "$LOG_FILE" | tail -n 2 | head -n 1 | awk '{print $1}')
fi

if [ -z "$TARGET_TAG" ]; then
  echo "롤백할 태그를 찾을 수 없습니다. 배포 이력을 확인하세요: $LOG_FILE"
  exit 1
fi

if ! docker image inspect "safy-backend:$TARGET_TAG" > /dev/null 2>&1; then
  echo "safy-backend:$TARGET_TAG 이미지가 로컬에 없습니다. 롤백 불가."
  exit 1
fi

echo "롤백 대상: safy-backend:$TARGET_TAG"

DEPLOY_TAG="$TARGET_TAG" docker compose --project-directory .. up -d backend

echo "rollback-to:$TARGET_TAG $(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$LOG_FILE"

echo "롤백 완료: safy-backend:$TARGET_TAG"
