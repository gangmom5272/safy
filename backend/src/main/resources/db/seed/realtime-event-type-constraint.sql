-- realtime_events.event_type 체크 제약을 RealtimeEventType enum 과 일치시킵니다.
--
-- 왜 필요한가:
--   Hibernate 의 ddl-auto: update 는 컬럼·테이블은 추가해주지만 기존 CHECK 제약은 절대
--   수정하지 않습니다. dev/local 은 Hibernate 가 이 컬럼을 만들면서 그 시점의 enum 값으로
--   CHECK 를 함께 걸어두었기 때문에, enum 에 값이 추가되면 제약만 옛 상태로 남아
--   이벤트 INSERT 가 제약 위반으로 통째로 롤백됩니다.
--   같은 사고가 report_operator_assignments.cancel_reason 에서도 있었습니다.
--   (assignment-cancel-reason-constraint.sql 참고)
--
-- 증상:
--   제보 접수(POST /api/v1/reports)나 배정·집계 알림이
--   realtime_events_event_type_check 위반으로 500 실패.
--   RealtimeEventService.record 가 같은 트랜잭션이라 제보 생성까지 함께 롤백됩니다.
--
-- 실행:
--   docker exec -i backend-db-1 psql -U <DB_USER> -d <DB_NAME> \
--     < backend/src/main/resources/db/seed/realtime-event-type-constraint.sql
--
-- 값을 넓히기만 하므로 기존 데이터는 안전하며, 여러 번 실행해도 됩니다.
-- enum 에 값을 추가할 때마다 이 파일도 함께 갱신하세요.

ALTER TABLE realtime_events
    DROP CONSTRAINT IF EXISTS realtime_events_event_type_check;

-- 구역 제보 요청은 report_id 대신 resource_id에 area_report_request_id를 저장한다.
ALTER TABLE realtime_events
    ALTER COLUMN report_id DROP NOT NULL;

ALTER TABLE realtime_events
    ADD CONSTRAINT realtime_events_event_type_check
    CHECK (event_type IN (
        'REPORT_RECEIVED',
        'REPORT_ASSIGNED',
        'REPORT_IN_PROGRESS',
        'IMAGE_READY',
        'LIVE_READY',
        'LIVE_ACTIVE',
        'LIVE_DISCONNECTED',
        'LIVE_RECONNECTED',
        'LIVE_ENDED',
        'REPORT_CONFIRMED',
        'REPORT_CLOSED',
        'REPORT_REJECTED',
        'REPORT_CANCELLED',
        'REPORT_REASSIGNED',
        'REPORT_MESSAGE',
        'RISK_ASSESSMENT_UPDATED',
        'REPORT_ASSIGNMENT_STALLED',
        'AREA_REPORT_REQUEST'
    ));
