from app.normalizers.disaster_message import (
    normalize_disaster_message,
)
from app.schemas.disaster_message import DisasterMessageRaw


def test_normalize_disaster_message() -> None:
    raw = DisasterMessageRaw.model_validate(
        {
            "SN": "205355",
            "CRT_DT": "2023/09/19 12:22:17",
            "MSG_CN": "  테스트 메시지  ",
            "RCPTN_RGN_NM": (
                "경기도 김포시 , 서울특별시 강서구 "
            ),
            "EMRG_STEP_NM": " 안전안내 ",
            "DST_SE_NM": " 기타 ",
            "REG_YMD": "2023-09-19",
            "MDFCN_YMD": "2023-09-19",
        }
    )

    message = normalize_disaster_message(raw)

    assert message.external_id == "205355"
    assert message.sent_at.isoformat() == (
        "2023-09-19T12:22:17+09:00"
    )
    assert message.message == "테스트 메시지"
    assert message.regions == [
        "경기도 김포시",
        "서울특별시 강서구",
    ]
    assert message.emergency_level == "안전안내"
    assert message.disaster_type == "기타"
    assert message.source == "safetydata.go.kr"