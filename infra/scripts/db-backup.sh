#!/bin/bash
# PostgreSQL 백업 스크립트. 서버 crontab 에서 주기 실행한다.
#
# 사용법:
#   bash db-backup.sh
#
# crontab 등록(매시 정각):
#   0 * * * * bash /home/ubuntu/S15P11A503/infra/scripts/db-backup.sh
#
# 복원:
#   docker exec -i infra-db-1 sh -c \
#     'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists' \
#     < ~/backups/db/safy-YYYYMMDD-HHMMSS.dump
#
# 저장 위치를 저장소 밖(~/backups/db)에 두는 이유: 배포의 rsync --delete 가
# infra/ 를 통째로 동기화하므로 저장소 안에 백업을 두면 배포 한 번에 사라진다.
#
# 비밀번호를 쓰지 않는다. docker exec 로 컨테이너 안에서 실행하면 로컬 소켓
# 인증이라 자격증명이 명령줄이나 로그에 남지 않는다.
#
# 실측(2026-08-04, DB 15MB / 테이블 31개): custom format 덤프 416KB, 0.32초,
# 실행 중 load 영향 없음. 매시 실행으로 7일 보관해도 약 70MB 라
# 디스크(265GB 여유)는 제약이 아니다.

set -euo pipefail

CONTAINER="${DB_BACKUP_CONTAINER:-infra-db-1}"
DEST_DIR="${DB_BACKUP_DIR:-$HOME/backups/db}"
LOG_FILE="${DB_BACKUP_LOG:-$HOME/backups/db-backup.log}"
RETENTION_DAYS="${DB_BACKUP_RETENTION_DAYS:-7}"
# 덤프가 이보다 작으면 실패로 본다. 정상 덤프는 400KB 이상이고 스키마만 남아도
# 이 값을 넘는다. 0바이트이거나 중간에 끊긴 파일을 거르는 용도다.
MIN_BYTES="${DB_BACKUP_MIN_BYTES:-50000}"

mkdir -p "$DEST_DIR" "$(dirname "$LOG_FILE")"

log() {
  echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) $*" >> "$LOG_FILE"
}

STAMP="$(date -u +%Y%m%d-%H%M%S)"
TMP_FILE="$DEST_DIR/.safy-$STAMP.dump.part"
OUT_FILE="$DEST_DIR/safy-$STAMP.dump"

# 임시 파일에 받아 검증한 뒤 rename 한다. 도중에 실패해도 반쪽짜리 덤프가
# 정상 백업처럼 남지 않는다.
if ! docker exec "$CONTAINER" sh -c \
    'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$TMP_FILE" 2>>"$LOG_FILE"; then
  log "FAIL pg_dump 실패 ($CONTAINER)"
  rm -f "$TMP_FILE"
  exit 1
fi

SIZE="$(stat -c %s "$TMP_FILE")"
if [ "$SIZE" -lt "$MIN_BYTES" ]; then
  log "FAIL 덤프 크기 비정상 ${SIZE}B (최소 ${MIN_BYTES}B)"
  rm -f "$TMP_FILE"
  exit 1
fi

mv "$TMP_FILE" "$OUT_FILE"
log "OK $(basename "$OUT_FILE") ${SIZE}B"

# 보존 기간이 지난 덤프와, 중단된 실행이 남긴 .part 잔해를 정리한다.
find "$DEST_DIR" -maxdepth 1 -name 'safy-*.dump' -mtime "+$RETENTION_DAYS" -delete
find "$DEST_DIR" -maxdepth 1 -name '.safy-*.dump.part' -mmin +120 -delete
