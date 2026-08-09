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
    # 재난문자는 요청이 1건뿐이라 라우트의 전체 제한시간과 HTTP 제한시간이
    # 같은 것을 잰다. collector 기본값 10초를 그대로 두면 그쪽이 항상 먼저
    # 끊겨 disaster_collection_timeout_seconds 설정이 동작하지 않는다.
    # (혼잡도·행사는 장소별 HTTP와 전체 배치를 따로 재므로 넘기지 않는다.)
    collector = DisasterMessageCollector(
        base_url=settings.disaster_api_base_url,
        api_key=settings.disaster_api_key,
        timeout_seconds=settings.disaster_collection_timeout_seconds,
    )
    return DisasterMessagePipeline(collector)
