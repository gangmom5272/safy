from datetime import date, datetime

from app.collectors.disaster_message import (
    DisasterMessageCollector,
)
from app.schemas.disaster_message import (
    DisasterMessage,
    DisasterMessageRaw,
)
from app.services.disaster_message_pipeline import (
    DisasterMessagePipeline,
    build_internal_json,
    deduplicate_disaster_messages,
)

def create_message(
    external_id: str,
    message: str,
) -> DisasterMessage:
    return DisasterMessage(
        external_id=external_id,
        sent_at=datetime.fromisoformat(
            "2026-07-21T10:00:00+09:00"
        ),
        message=message,
        regions=["서울특별시"],
        emergency_level="안전안내",
        disaster_type="기타",
    )


def test_deduplicate_disaster_messages() -> None:
    messages = [
        create_message("100", "첫 번째 메시지"),
        create_message("200", "두 번째 메시지"),
        create_message("100", "중복 메시지"),
    ]

    result = deduplicate_disaster_messages(messages)

    assert [message.external_id for message in result] == [
        "100",
        "200",
    ]
    assert result[0].message == "첫 번째 메시지"


def test_build_internal_json() -> None:
    messages = [
        create_message("100", "테스트 메시지"),
    ]

    result = build_internal_json(messages)

    assert result == [
        {
            "external_id": "100",
            "sent_at": "2026-07-21T10:00:00+09:00",
            "message": "테스트 메시지",
            "regions": ["서울특별시"],
            "emergency_level": "안전안내",
            "disaster_type": "기타",
            "source": "safetydata.go.kr",
        }
    ]


def create_raw_message(
    serial_number: str,
    message: str,
) -> DisasterMessageRaw:
    return DisasterMessageRaw.model_validate(
        {
            "SN": serial_number,
            "CRT_DT": "2026/07/21 10:00:00",
            "MSG_CN": message,
            "RCPTN_RGN_NM": "서울특별시 ",
            "EMRG_STEP_NM": " 안전안내 ",
            "DST_SE_NM": " 기타 ",
            "REG_YMD": "2026-07-21",
            "MDFCN_YMD": "2026-07-21",
        }
    )


class FakeCollector(DisasterMessageCollector):
    def fetch(
        self,
        page_no: int = 1,
        num_of_rows: int = 10,
        start_date: date | None = None,
        region: str | None = None,
    ) -> list[DisasterMessageRaw]:
        return [
            create_raw_message("100", " 첫 번째 메시지 "),
            create_raw_message("200", " 두 번째 메시지 "),
            create_raw_message("100", " 중복 메시지 "),
        ]


def test_pipeline_runs_all_steps() -> None:
    fake_collector = FakeCollector(
    base_url="https://example.com/disaster-messages",
    api_key="test-api-key",
)
    pipeline = DisasterMessagePipeline(fake_collector)

    result = pipeline.run()

    assert len(result) == 2
    assert [item["external_id"] for item in result] == [
        "100",
        "200",
    ]
    assert result[0]["message"] == "첫 번째 메시지"
    assert result[0]["sent_at"] == (
        "2026-07-21T10:00:00+09:00"
    )
    assert result[0]["regions"] == ["서울특별시"]