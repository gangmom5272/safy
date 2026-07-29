-- realtime_events.event_type 체크 제약을 RealtimeEventType enum 과 일치시킵니다.
--
-- 왜 필요한가:
--   Hibernate 의 ddl-auto: update 는 컬럼·테이블은 추가해주지만 기존 CHECK 제약은 절대 수정하지 않습니다.
--   테이블이 처음 만들어진 뒤 enum 에 값이 추가되면 제약만 옛날 상태로 남아,
--   새 이벤트를 INSERT 하는 순간 제약 위반으로 트랜잭션이 통째로 롤백됩니다.
--   실제로 REPORT_CLOSED 가 막혀 업무 종료 API 가 500 을 반환한 적이 있습니다.
--
-- 증상:
--   업무 종료·반려·재배정·시민 취소·라이브 끊김 등에서 원인 불명의 500.
--   (알림을 남기는 모든 기능이 함께 막힙니다.)
--
-- 실행:
--   docker exec -i backend-db-1 psql -U <DB_USER> -d <DB_NAME> \
--     < backend/src/main/resources/db/seed/realtime-event-type-constraint.sql
--
-- 값을 넓히기만 하므로 기존 데이터는 안전하며, 여러 번 실행해도 됩니다.
-- enum 에 값을 추가할 때마다 이 파일도 함께 갱신하세요.

ALTER TABLE realtime_events DROP CONSTRAINT IF EXISTS realtime_events_event_type_check;

ALTER TABLE realtime_events ADD CONSTRAINT realtime_events_event_type_check
CHECK (event_type IN (
    'REPORT_ASSIGNED',
    'IMAGE_READY',
    'LIVE_READY',
    'LIVE_ACTIVE',
    'LIVE_DISCONNECTED',
    'LIVE_RECONNECTED',
    'LIVE_ENDED',
    'REPORT_CLOSED',
    'REPORT_REJECTED',
    'REPORT_CANCELLED',
    'REPORT_REASSIGNED',
    'REPORT_MESSAGE'
));
