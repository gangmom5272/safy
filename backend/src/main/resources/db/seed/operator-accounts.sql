-- 개발/테스트용 담당자(OPERATOR) 계정 시드.
-- 관제센터 25곳마다 담당자 2명(A/B)씩, 총 50개 계정을 만듭니다.
--
-- 왜 필요한가:
--   관제 대시보드(GET /operator/dashboard/reports)는 report_operator_assignments 를 조회합니다.
--   담당자가 한 명도 없으면 제보가 관제센터까지 라우팅돼도 사람에게 배정되지 못해
--   대시보드가 빈 목록이 됩니다. 이 시드가 그 전제를 채웁니다.
--
-- 실행 전제:
--   seoul-control-centers.sql 이 먼저 적용되어 control_centers 가 존재해야 합니다.
--
-- 실행:
--   docker exec -i backend-db-1 psql -U <DB_USER> -d <DB_NAME> \
--     < backend/src/main/resources/db/seed/operator-accounts.sql
--
-- 계정 규칙:
--   로그인 ID : op{관제센터ID 2자리}{a|b}   예) 강남구(23) -> op23a, op23b
--   비밀번호  : Operator1234!               (전 계정 공통, 개발 전용)
--   표시 이름 : {자치구} 담당자 A / B
--
-- 주의: 운영 환경에서 실행하지 마세요. 비밀번호가 공개된 계정 50개가 생깁니다.
--
-- 세 구문 모두 NOT EXISTS 로 보호되어 있어 여러 번 실행해도 중복 생성되지 않습니다.

-- 1) 담당자 계정 생성
WITH seed AS (
    SELECT
        cc.id AS center_id,
        'op' || lpad(cc.id::text, 2, '0') || s.slug AS login_id,
        replace(cc.name, ' 재난안전상황실', '') || ' 담당자 ' || s.suffix AS display_name
    FROM control_centers cc
    CROSS JOIN (VALUES ('A', 'a'), ('B', 'b')) AS s(suffix, slug)
)
INSERT INTO users (created_at, updated_at, version, token_version, role, status, display_name)
SELECT now(), now(), 0, 0, 'OPERATOR', 'ACTIVE', s.display_name
FROM seed s
WHERE NOT EXISTS (
    SELECT 1 FROM users u WHERE u.display_name = s.display_name AND u.role = 'OPERATOR'
);

-- 2) 로그인 자격증명 생성 (BCrypt cost 12, 평문 Operator1234!)
WITH seed AS (
    SELECT
        cc.id AS center_id,
        'op' || lpad(cc.id::text, 2, '0') || s.slug AS login_id,
        replace(cc.name, ' 재난안전상황실', '') || ' 담당자 ' || s.suffix AS display_name
    FROM control_centers cc
    CROSS JOIN (VALUES ('A', 'a'), ('B', 'b')) AS s(suffix, slug)
)
INSERT INTO admin_credentials (
    user_id, login_id, password_hash,
    created_at, updated_at, version, failed_login_count, password_changed_at
)
SELECT
    u.id,
    s.login_id,
    '$2b$12$s.R7bCi.zuZM7oeqozGHA.XYgpHqHq8rXVKzLiki2fV/NCdmo2QUW',
    now(), now(), 0, 0, now()
FROM seed s
JOIN users u ON u.display_name = s.display_name AND u.role = 'OPERATOR'
WHERE NOT EXISTS (
    SELECT 1 FROM admin_credentials ac WHERE ac.user_id = u.id
);

-- 3) 관제센터 소속 부여
--    라이브 세션 조회 쿼리(findAccessibleForOperator)가 is_primary = true 이고
--    status = 'ACTIVE' 인 소속을 요구하므로 두 값이 반드시 이렇게 들어가야 합니다.
WITH seed AS (
    SELECT
        cc.id AS center_id,
        replace(cc.name, ' 재난안전상황실', '') || ' 담당자 ' || s.suffix AS display_name
    FROM control_centers cc
    CROSS JOIN (VALUES ('A', 'a'), ('B', 'b')) AS s(suffix, slug)
)
INSERT INTO admin_center_memberships (
    user_id, control_center_id, membership_role, status,
    is_primary, started_at, created_at, updated_at
)
SELECT u.id, s.center_id, 'OPERATOR', 'ACTIVE', true, now(), now(), now()
FROM seed s
JOIN users u ON u.display_name = s.display_name AND u.role = 'OPERATOR'
WHERE NOT EXISTS (
    SELECT 1 FROM admin_center_memberships m WHERE m.user_id = u.id
);
