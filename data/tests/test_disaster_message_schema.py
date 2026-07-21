from app.schemas.disaster_message import DisasterMessageRaw


def test_parse_disaster_message_raw() -> None:
    raw_data = {
        "SN": 123456,
        "CRT_DT": "2026/07/20 14:30:00",
        "MSG_CN": "행사로 인한 인파 밀집이 예상되오니 우회 바랍니다.",
        "RCPTN_RGN_NM": "서울특별시 종로구",
        "EMRG_STEP_NM": "안전안내",
        "DST_SE_NM": "교통통제",
        "REG_YMD": "20260720",
        "MDFCN_YMD": "20260720",
    }

    message = DisasterMessageRaw.model_validate(raw_data)

    assert message.serial_number == "123456"
    assert message.message == raw_data["MSG_CN"]
    assert message.received_regions == "서울특별시 종로구"
    assert message.emergency_level == "안전안내"
    assert message.disaster_type == "교통통제"