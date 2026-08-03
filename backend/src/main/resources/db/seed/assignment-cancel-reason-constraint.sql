-- report_operator_assignments.cancel_reason 체크 제약을 ReportAssignmentCancelReason enum 과
-- 일치시킵니다.
--
-- 왜 필요한가:
--   Hibernate 의 ddl-auto: update 는 컬럼·테이블은 추가해주지만 기존 CHECK 제약은 절대
--   수정하지 않습니다. dev/local 은 Hibernate 가 이 컬럼을 만들면서 그 시점의 enum 값으로
--   CHECK 를 함께 걸어두었기 때문에, enum 에 값이 추가되면 제약만 옛 상태로 남아
--   배정 회수 UPDATE 가 제약 위반으로 통째로 롤백됩니다.
--   realtime_events.event_type 에서 이미 같은 사고가 있었습니다.
--
--   prod 는 이 컬럼을 CHECK 없이 만들었으므로 원래 영향이 없지만,
--   두 환경의 스키마를 같은 모양으로 맞춰두기 위해 함께 실행합니다.
--
-- 증상:
--   자리비움·오프라인 전환이나 타임아웃 회수에서 원인 불명의 실패.
--   (배정이 회수되지 않고 담당자에게 그대로 남습니다.)
--
-- 실행:
--   docker exec -i backend-db-1 psql -U <DB_USER> -d <DB_NAME> \
--     < backend/src/main/resources/db/seed/assignment-cancel-reason-constraint.sql
--
-- 값을 넓히기만 하므로 기존 데이터는 안전하며, 여러 번 실행해도 됩니다.
-- enum 에 값을 추가할 때마다 이 파일도 함께 갱신하세요.

ALTER TABLE report_operator_assignments
    DROP CONSTRAINT IF EXISTS report_operator_assignments_cancel_reason_check;

ALTER TABLE report_operator_assignments
    ADD CONSTRAINT report_operator_assignments_cancel_reason_check
    CHECK (cancel_reason IS NULL OR cancel_reason IN (
        'REASSIGN_REQUESTED',
        'TRANSFERRED',
        'ASSIGNMENT_TIMEOUT',
        'OPERATOR_OFFLINE',
        'OPERATOR_AWAY',
        'REPORTER_CANCELLED',
        'REPORT_REJECTED'
    ));
