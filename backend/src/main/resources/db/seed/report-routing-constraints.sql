-- Hibernate가 report_center_routes 테이블을 생성한 뒤 한 번 실행합니다.
-- 한 제보에 활성 관제센터 라우팅이 둘 이상 생성되는 것을 DB에서도 차단합니다.

CREATE UNIQUE INDEX IF NOT EXISTS uq_report_center_routes_active_report
ON report_center_routes (report_id)
WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX IF NOT EXISTS uq_report_operator_assignments_active_report
ON report_operator_assignments (report_id)
WHERE status IN ('ASSIGNED', 'ACCEPTED', 'IN_PROGRESS');
