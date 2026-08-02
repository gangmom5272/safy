-- reports 테이블에 100m 격자 셀 코드 컬럼을 추가하고 기존 행을 백필합니다.
-- 여러 번 실행해도 안전합니다.
--
-- ddl-auto: update 가 컬럼과 인덱스는 만들어 주지만 기존 행의 값은 채우지 못합니다.
-- 컬럼이 nullable 이라 컬럼 추가 자체는 실패하지 않으므로, 이 스크립트는 배포 전후
-- 아무 때나 실행해도 됩니다. 다만 백필 전에는 기존 제보가 셀 집계에서 빠집니다.
--
-- ── 산식은 Java·TypeScript 와 반드시 같아야 합니다 ─────────────────────────────
-- GridCellCode: floor((latitude - 37.4) / 0.0009), floor((longitude - 126.7) / 0.00113)
--
-- latitude·longitude 는 numeric(10,7) 이라 그냥 나누면 PostgreSQL 이 정확한 십진
-- 연산을 합니다. Java·JS 는 IEEE-754 double 로 계산하므로, 셀 경계에 딱 걸리는
-- 좌표에서 결과가 갈릴 수 있습니다. 그래서 좌표와 상수를 모두 double precision 으로
-- 캐스팅해 같은 부동소수 연산을 재현합니다. 이 캐스팅을 지우면 안 됩니다.
--
-- PostgreSQL 16 에서 GridCellCodeTest 의 벡터 11개(셀 경계 정확히·직전, 음수 인덱스,
-- 서울 밖 포함)로 Java 결과와 일치함을 확인했습니다.
--
-- 음수 인덱스(원점 37.4/126.7 남·서쪽)는 r/c 접두사 덕분에 'S100-r-12-c-9' 처럼
-- 한 가지로만 읽힙니다.

ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS grid_cell_code varchar(24);

UPDATE reports
SET grid_cell_code =
        'S100-r'
            || floor(
                   (latitude::double precision - 37.4::double precision)
                       / 0.0009::double precision
               )::bigint
            || '-c'
            || floor(
                   (longitude::double precision - 126.7::double precision)
                       / 0.00113::double precision
               )::bigint
WHERE grid_cell_code IS NULL;

CREATE INDEX IF NOT EXISTS idx_reports_grid_cell_code
    ON reports (grid_cell_code);

-- 검증: 백필이 남은 행이 없어야 합니다.
--   SELECT count(*) FROM reports WHERE grid_cell_code IS NULL;
--
-- 검증: 같은 좌표가 같은 셀로 묶이는지 눈으로 확인할 때.
--   SELECT grid_cell_code, count(*)
--   FROM reports
--   GROUP BY grid_cell_code
--   ORDER BY count(*) DESC
--   LIMIT 10;
