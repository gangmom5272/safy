-- seoul-administrative-areas.sql 실행 후 수동 실행합니다.
-- 실제 주소와 대표 전화번호는 확인된 운영 정보만 추후 갱신하며 임의 값을 넣지 않습니다.

INSERT INTO control_centers (
    code,
    name,
    center_type,
    status,
    capacity_limit,
    address,
    phone_number,
    created_at,
    updated_at
)
VALUES
    ('CC-1111000000', '종로구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CC-1114000000', '중구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CC-1117000000', '용산구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CC-1120000000', '성동구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CC-1121500000', '광진구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CC-1123000000', '동대문구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CC-1126000000', '중랑구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CC-1129000000', '성북구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CC-1130500000', '강북구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CC-1132000000', '도봉구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CC-1135000000', '노원구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CC-1138000000', '은평구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CC-1141000000', '서대문구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CC-1144000000', '마포구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CC-1147000000', '양천구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CC-1150000000', '강서구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CC-1153000000', '구로구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CC-1154500000', '금천구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CC-1156000000', '영등포구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CC-1159000000', '동작구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CC-1162000000', '관악구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CC-1165000000', '서초구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CC-1168000000', '강남구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CC-1171000000', '송파구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CC-1174000000', '강동구 재난안전상황실', 'DISTRICT', 'ACTIVE', NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    center_type = EXCLUDED.center_type,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;

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
SELECT center.id,
       area.id,
       'PRIMARY',
       1,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       NULL,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM administrative_areas area
JOIN control_centers center ON center.code = 'CC-' || area.code
WHERE area.area_type = 'SIGUNGU'
  AND area.status = 'ACTIVE'
  AND area.parent_id = (
      SELECT id
      FROM administrative_areas
      WHERE code = '1100000000'
  )
ON CONFLICT (control_center_id, administrative_area_id, coverage_role) DO UPDATE
SET priority = EXCLUDED.priority,
    status = 'ACTIVE',
    effective_to = NULL,
    updated_at = CURRENT_TIMESTAMP;
