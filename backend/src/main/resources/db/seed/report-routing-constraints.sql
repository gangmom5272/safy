-- Hibernate가 report_center_routes 테이블을 생성한 뒤 한 번 실행합니다.
-- 한 제보에 활성 관제센터 라우팅이 둘 이상 생성되는 것을 DB에서도 차단합니다.

CREATE UNIQUE INDEX IF NOT EXISTS uq_report_center_routes_active_report
ON report_center_routes (report_id)
WHERE status = 'ACTIVE';

-- ACCEPTED 는 세팅하는 코드가 없는 죽은 값이라 enum 에서 제거했습니다.
-- 이미 만들어진 인덱스는 CREATE ... IF NOT EXISTS 로는 갱신되지 않으므로 다시 만듭니다.
-- (해당 상태의 행이 존재한 적이 없어 동작은 동일하며, 재생성은 즉시 끝납니다.)
DROP INDEX IF EXISTS uq_report_operator_assignments_active_report;

CREATE UNIQUE INDEX IF NOT EXISTS uq_report_operator_assignments_active_report
ON report_operator_assignments (report_id)
WHERE status IN ('ASSIGNED', 'IN_PROGRESS');
