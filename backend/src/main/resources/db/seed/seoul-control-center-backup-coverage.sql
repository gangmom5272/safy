-- seoul-control-centers.sql 실행 후 수동 실행합니다.
--
-- 왜 필요한가:
-- seoul-control-centers.sql 은 구 코드와 센터 코드를 1:1 로 맞춰 PRIMARY 커버리지만 만든다.
-- 그러면 한 행정구역을 담당하는 센터가 정확히 하나뿐이라, 수동 이관 화면에서
-- "같은 행정구역을 지원하는 다른 센터" 후보를 찾을 수 없다.
-- 이 파일은 서울시 도시기본계획 5대 생활권 안에서 구끼리 서로를 BACKUP 으로 등록해
-- 수동 이관 선택지를 제공한다. 119 상호응원협정과 같은 구조다.
--
-- 라우팅 정렬은 PRIMARY 를 항상 먼저 보므로(JpaControlCenterAreaRepository.findRoutingCandidates)
-- 자동 라우팅과 자동 재배정은 관할 구 PRIMARY 상황실에만 머문다. BACKUP 은 관리자가
-- 명시적으로 이관할 때만 선택된다.
--
-- priority 는 생활권 안에서 모두 2 로 둔다. 구 경계(boundary_geo_json)가 아직 비어 있어
-- 인접 순서를 데이터로 정할 수 없기 때문이다. 같은 우선순위 안에서는 커버리지 id 순으로
-- 결정되며, 인접 순서를 반영하려면 해당 행의 priority 만 낮추면 된다.

WITH zone (area_code, zone_name) AS (
    VALUES
        ('1111000000', '도심권'),   -- 종로구
        ('1114000000', '도심권'),   -- 중구
        ('1117000000', '도심권'),   -- 용산구
        ('1120000000', '동북권'),   -- 성동구
        ('1121500000', '동북권'),   -- 광진구
        ('1123000000', '동북권'),   -- 동대문구
        ('1126000000', '동북권'),   -- 중랑구
        ('1129000000', '동북권'),   -- 성북구
        ('1130500000', '동북권'),   -- 강북구
        ('1132000000', '동북권'),   -- 도봉구
        ('1135000000', '동북권'),   -- 노원구
        ('1138000000', '서북권'),   -- 은평구
        ('1141000000', '서북권'),   -- 서대문구
        ('1144000000', '서북권'),   -- 마포구
        ('1147000000', '서남권'),   -- 양천구
        ('1150000000', '서남권'),   -- 강서구
        ('1153000000', '서남권'),   -- 구로구
        ('1154500000', '서남권'),   -- 금천구
        ('1156000000', '서남권'),   -- 영등포구
        ('1159000000', '서남권'),   -- 동작구
        ('1162000000', '서남권'),   -- 관악구
        ('1165000000', '동남권'),   -- 서초구
        ('1168000000', '동남권'),   -- 강남구
        ('1171000000', '동남권'),   -- 송파구
        ('1174000000', '동남권')    -- 강동구
)
INSERT INTO control_center_areas (
    control_center_id,
    administrative_area_id,
    coverage_role,
    priority,
    status,
    effective_from,
    effective_to,
    created_at,
    updated_at
)
SELECT backup_center.id,
       target_area.id,
       'BACKUP',
       2,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       NULL,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM zone target
JOIN zone backup
  ON backup.zone_name = target.zone_name
 AND backup.area_code <> target.area_code
JOIN administrative_areas target_area
  ON target_area.code = target.area_code
 AND target_area.area_type = 'SIGUNGU'
 AND target_area.status = 'ACTIVE'
JOIN control_centers backup_center
  ON backup_center.code = 'CC-' || backup.area_code
 AND backup_center.status = 'ACTIVE'
ON CONFLICT (control_center_id, administrative_area_id, coverage_role) DO UPDATE
SET priority = EXCLUDED.priority,
    status = 'ACTIVE',
    effective_to = NULL,
    updated_at = CURRENT_TIMESTAMP;
