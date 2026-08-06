-- report_operator_assignments.release_reason 컬럼을 추가하고 기존 행을 백필합니다.
--
-- 왜 필요한가:
--   담당자가 재배정·이관·반려하며 입력한 사유는 report_status_histories.reason 에만
--   남아 있었습니다. 그 테이블은 제보 단위 상태 전이 기록이라 "어느 담당 구간의
--   사유인가"를 잇는 키가 없고, 재배정이 여러 번이면 구간과 사유를 짝지을 방법이
--   시각이 우연히 같다는 사실뿐입니다. 담당 이력 화면이 구간별로 사유를 읽어야 하므로
--   배정 행 자체에 보관합니다.
--
--   dev/local 은 ddl-auto: update 가 컬럼을 만들어 주지만 기존 행은 채우지 못하고,
--   prod 는 ddl-auto: validate 라 컬럼 자체가 없으면 기동에 실패합니다.
--
-- 실행:
--   docker exec -i backend-db-1 psql -U <DB_USER> -d <DB_NAME> \
--     < backend/src/main/resources/db/seed/assignment-release-reason.sql
--
-- 여러 번 실행해도 안전합니다.

ALTER TABLE report_operator_assignments
    ADD COLUMN IF NOT EXISTS release_reason varchar(500);

-- 백필: 배정 회수와 상태 이력이 같은 트랜잭션에서 같은 Instant 로 기록되므로
-- (담당자, 회수 시각) 이 정확히 일치하는 이력 행이 그 구간의 사유입니다.
-- 자동 회수(타임아웃·오프라인·자리비움)는 reason 자리에 enum 이름만 들어 있어
-- 옮길 값이 없으므로 제외합니다 — cancel_reason 이 이미 같은 정보를 담고 있습니다.
UPDATE report_operator_assignments assignment
SET release_reason = history.reason
FROM report_status_histories history
WHERE assignment.release_reason IS NULL
  AND assignment.cancel_reason IN ('REASSIGN_REQUESTED', 'TRANSFERRED', 'REPORT_REJECTED')
  AND history.report_id = assignment.report_id
  AND history.changed_by = assignment.operator_id
  AND history.changed_at = assignment.cancelled_at
  AND history.reason IS NOT NULL;
