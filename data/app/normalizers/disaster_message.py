from datetime import datetime, timedelta, timezone

from app.schemas.disaster_message import (
    DisasterMessage,
    DisasterMessageRaw,
)


KST = timezone(timedelta(hours=9))


def normalize_disaster_message(
    raw: DisasterMessageRaw,
) -> DisasterMessage:
    sent_at = datetime.strptime(
        raw.created_at,
        "%Y/%m/%d %H:%M:%S",
    ).replace(tzinfo=KST)

    regions = [
        region.strip()
        for region in raw.received_regions.split(",")
        if region.strip()
    ]

    return DisasterMessage(
        external_id=raw.serial_number,
        sent_at=sent_at,
        message=raw.message.strip(),
        regions=regions,
        emergency_level=raw.emergency_level.strip(),
        disaster_type=raw.disaster_type.strip(),
    )