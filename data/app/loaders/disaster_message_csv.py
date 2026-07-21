import csv
from pathlib import Path

from app.schemas.disaster_message import DisasterMessageRaw


def load_disaster_messages_csv(
    file_path: str | Path,
) -> list[DisasterMessageRaw]:
    messages: list[DisasterMessageRaw] = []

    with Path(file_path).open(
        mode="r",
        encoding="utf-8-sig",
        newline="",
    ) as csv_file:
        reader = csv.DictReader(csv_file)

        for row in reader:
            if row.get("SN") == "일련번호":
                continue

            messages.append(
                DisasterMessageRaw.model_validate(row)
            )

    return messages