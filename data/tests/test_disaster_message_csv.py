from pathlib import Path

from app.loaders.disaster_message_csv import (
    load_disaster_messages_csv,
)


def test_load_disaster_messages_csv(
    tmp_path: Path,
) -> None:
    csv_file = tmp_path / "disaster_messages.csv"
    csv_file.write_text(
        (
            '"SN","CRT_DT","MSG_CN","RCPTN_RGN_NM",'
            '"EMRG_STEP_NM","DST_SE_NM","REG_YMD","MDFCN_YMD"\n'
            '"일련번호","생성일시","메시지내용","수신지역명",'
            '"긴급단계명","재해구분명","등록일자","수정일자"\n'
            '"205355","2023/09/19 12:22:17","테스트 메시지",'
            '"경기도 김포시 ","안전안내","기타",'
            '"2023-09-19","2023-09-19"\n'
        ),
        encoding="utf-8-sig",
    )

    messages = load_disaster_messages_csv(csv_file)

    assert len(messages) == 1
    assert messages[0].serial_number == "205355"
    assert messages[0].created_at == "2023/09/19 12:22:17"
    assert messages[0].received_regions == "경기도 김포시 "
    assert messages[0].emergency_level == "안전안내"