from app.schemas.seoul_congestion import (
    SeoulCongestionRaw,
)


def test_parse_seoul_congestion_raw() -> None:
    raw_data = {
        "AREA_NM": "광화문·덕수궁",
        "AREA_CD": "POI009",
        "AREA_CONGEST_LVL": "약간 붐빔",
        "AREA_CONGEST_MSG": "사람이 다소 몰려 있습니다.",
        "AREA_PPLTN_MIN": "22000",
        "AREA_PPLTN_MAX": "24000",
        "REPLACE_YN": "N",
        "PPLTN_TIME": "2026-07-30 14:20",
    }

    congestion = SeoulCongestionRaw.model_validate(
        raw_data
    )

    assert congestion.area_name == "광화문·덕수궁"
    assert congestion.area_code == "POI009"
    assert congestion.congestion_level == "약간 붐빔"
    assert congestion.population_min == "22000"
    assert congestion.population_max == "24000"
    assert congestion.replacement_yn == "N"
    assert congestion.observed_at == (
        "2026-07-30 14:20"
    )
