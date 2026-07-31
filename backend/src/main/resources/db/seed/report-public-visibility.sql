-- reports 테이블에 시민 공개 상태(public_visibility)와 대표 블러 이미지 컬럼을 추가하고
-- 기존 행을 백필합니다. 새 코드를 배포하기 "전"에 한 번 실행합니다.
--
-- 먼저 실행해야 하는 이유:
--   ddl-auto: update 는 기본값 없이 NOT NULL 컬럼을 추가하려 하는데, 기존 행이 있는
--   PostgreSQL 테이블에서는 이 ALTER 가 실패합니다. Hibernate 는 실패를 WARN 으로 삼키고
--   부팅을 계속하므로, 컬럼 없이 뜬 뒤 제보 조회 API 마다 500 이 납니다.
--   엔티티의 @ColumnDefault 로도 막고 있지만 기존 행 백필은 여기서만 할 수 있습니다.
--
-- 여러 번 실행해도 안전합니다.
-- ReportPublicVisibility enum 에 값을 추가하면 아래 CHECK 도 함께 갱신해야 합니다.

ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS public_visibility varchar(20) NOT NULL DEFAULT 'PENDING';

ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS representative_blur_asset_id bigint;

-- CLOSED 는 앞으로 담당자의 업무 종료(=채택)가 만드는 상태이므로 공개로 백필합니다.
-- 진행 중(RECEIVED/ASSIGNED/IN_PROGRESS)인 제보는 기본값 PENDING 으로 두어 시민 피드에서 빠집니다.
-- 아래 UPDATE 는 PENDING 인 행만 건드리므로, 재실행해도 담당자가 내린(WITHHELD) 결정을 되살리지 않습니다.
UPDATE reports SET public_visibility = 'PUBLISHED'
WHERE status = 'CLOSED' AND public_visibility = 'PENDING';

UPDATE reports SET public_visibility = 'WITHHELD'
WHERE status IN ('CANCELLED', 'REJECTED') AND public_visibility = 'PENDING';

-- ddl-auto: update 는 이미 만들어진 CHECK 제약을 갱신하지 않으므로 여기서 직접 관리합니다.
ALTER TABLE reports DROP CONSTRAINT IF EXISTS reports_public_visibility_check;
ALTER TABLE reports ADD CONSTRAINT reports_public_visibility_check
    CHECK (public_visibility IN ('PENDING', 'PUBLISHED', 'WITHHELD'));

CREATE INDEX IF NOT EXISTS idx_reports_public_feed
    ON reports (public_visibility, reported_at DESC, id DESC);
