from app.collectors.disaster_message import (
    DisasterMessageCollector,
)
from app.collectors.seoul_congestion import (
    SeoulCongestionCollector,
)
from app.collectors.seoul_event import SeoulEventCollector
from app.core.config import get_settings
from app.services.disaster_message_pipeline import (
    DisasterMessagePipeline,
)
from app.services.seoul_congestion_pipeline import (
    SeoulCongestionPipeline,
)
from app.services.seoul_event_pipeline import (
    SeoulEventPipeline,
)


def get_seoul_congestion_pipeline() -> (
    SeoulCongestionPipeline
):
    settings = get_settings()
    collector = SeoulCongestionCollector(
        base_url=settings.seoul_citydata_base_url,
        api_key=settings.seoul_citydata_api_key,
    )
    return SeoulCongestionPipeline(collector)


def get_seoul_event_pipeline() -> SeoulEventPipeline:
    settings = get_settings()
    collector = SeoulEventCollector(
        base_url=settings.seoul_citydata_base_url,
        api_key=settings.seoul_citydata_api_key,
    )
    return SeoulEventPipeline(collector)


def get_disaster_message_pipeline() -> (
    DisasterMessagePipeline
):
    settings = get_settings()
    collector = DisasterMessageCollector(
        base_url=settings.disaster_api_base_url,
        api_key=settings.disaster_api_key,
    )
    return DisasterMessagePipeline(collector)
