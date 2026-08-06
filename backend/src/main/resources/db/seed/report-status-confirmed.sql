-- 제보 승인(CONFIRMED) 상태 도입 마이그레이션.
--
-- 배경:
--   기존 '처리 완료(CLOSED)'는 종결과 시민 지도 공개(publish)를 한 번에 했습니다.
--   승인(CONFIRMED)이 공개를, 종료(CLOSED)가 종결을 맡도록 분리하면서
--   reports.status 에 CONFIRMED 값이 추가되고 confirmed_at 컬럼이 생겼습니다.
--
-- 왜 필요한가:
--   Hibernate 의 ddl-auto: update 는 confirmed_at 컬럼은 추가해주지만 기존 CHECK 제약은
--   절대 수정하지 않습니다. dev/local 은 Hibernate 가 status 컬럼을 만들면서 그 시점의
--   enum 값으로 CHECK 를 걸어두었기 때문에, CONFIRMED 로의 UPDATE 가 제약 위반으로
--   롤백됩니다. (realtime_events.event_type, cancel_reason 에서 있었던 사고와 같습니다.)
--
-- 실행:
--   docker exec -i backend-db-1 psql -U <DB_USER> -d <DB_NAME> \
--     < backend/src/main/resources/db/seed/report-status-confirmed.sql
--
-- 값을 넓히고 비어 있는 컬럼만 채우므로 기존 데이터는 안전하며, 여러 번 실행해도 됩니다.

ALTER TABLE reports
    DROP CONSTRAINT IF EXISTS reports_status_check;

ALTER TABLE reports
    ADD CONSTRAINT reports_status_check
    CHECK (status IN (
        'RECEIVED',
        'ASSIGNED',
        'IN_PROGRESS',
        'CONFIRMED',
        'CLOSED',
        'REJECTED',
        'CANCELLED'
    ));

-- 상태 이력도 같은 enum 을 쓴다. 여기를 빠뜨리면 reports 는 통과하고 이력 INSERT 만
-- 실패하는데, 같은 트랜잭션이라 승인 자체가 통째로 롤백된다.
-- report_operator_assignments.status 는 이름만 비슷한 다른 enum
-- (ReportOperatorAssignmentStatus) 이므로 건드리지 않는다.
ALTER TABLE report_status_histories
    DROP CONSTRAINT IF EXISTS report_status_histories_from_status_check;

ALTER TABLE report_status_histories
    ADD CONSTRAINT report_status_histories_from_status_check
    CHECK (from_status IS NULL OR from_status IN (
        'RECEIVED',
        'ASSIGNED',
        'IN_PROGRESS',
        'CONFIRMED',
        'CLOSED',
        'REJECTED',
        'CANCELLED'
    ));

ALTER TABLE report_status_histories
    DROP CONSTRAINT IF EXISTS report_status_histories_to_status_check;

ALTER TABLE report_status_histories
    ADD CONSTRAINT report_status_histories_to_status_check
    CHECK (to_status IN (
        'RECEIVED',
        'ASSIGNED',
        'IN_PROGRESS',
        'CONFIRMED',
        'CLOSED',
        'REJECTED',
        'CANCELLED'
    ));

-- ddl-auto: update 가 컬럼을 만들기 전에 실행해도 되도록 여기서도 추가해 둡니다.
ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS confirmed_at timestamp(6) with time zone;

-- 백필: 분리 전 CLOSED 는 공개(승인)를 겸했으므로, 이미 공개된 CLOSED 제보의
-- 승인 시각은 실질적으로 closed_at 입니다. 비어 있을 때만 채웁니다.
UPDATE reports
SET confirmed_at = closed_at
WHERE status = 'CLOSED'
  AND public_visibility = 'PUBLISHED'
  AND confirmed_at IS NULL
  AND closed_at IS NOT NULL;
