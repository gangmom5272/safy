import logging
import os
import sys


LOG_LEVEL_VARIABLE = "AI_LOG_LEVEL"
DEFAULT_LOG_LEVEL = "INFO"
LOG_FORMAT = "%(asctime)s %(levelname)s %(name)s %(message)s"
LOG_DATE_FORMAT = "%Y-%m-%dT%H:%M:%S%z"
HANDLER_NAME = "safy-ai-stdout"


def resolve_log_level() -> int:
    raw_value = os.getenv(LOG_LEVEL_VARIABLE, DEFAULT_LOG_LEVEL)
    level = logging.getLevelName(raw_value.strip().upper())

    # getLevelName은 알 수 없는 이름에 대해 문자열을 돌려준다.
    if not isinstance(level, int):
        return logging.getLevelName(DEFAULT_LOG_LEVEL)

    return level


def configure_logging() -> None:
    """앱 로그를 stdout으로 내보낸다.

    uvicorn 기본 설정은 `uvicorn`, `uvicorn.error`, `uvicorn.access` 세 로거만
    다루고 root는 건드리지 않는다. root에 핸들러가 없으면 앱 로그는
    `logging.lastResort`가 처리하는데 그 레벨이 WARNING이므로 INFO가 버려진다.
    그래서 P2PNet 그림자 비교 로그와 자산 준비 로그가 운영에서 한 줄도 남지
    않았다.

    uvicorn의 `dictConfig`는 `disable_existing_loggers=False`이고 root 설정을
    포함하지 않으므로, 여기서 추가한 root 핸들러는 서버 기동 후에도 유지된다.
    `uvicorn`과 `uvicorn.access`는 `propagate=False`라 중복 출력되지 않는다.
    """
    root_logger = logging.getLogger()
    level = resolve_log_level()
    root_logger.setLevel(level)

    for handler in root_logger.handlers:
        if getattr(handler, "name", None) == HANDLER_NAME:
            handler.setLevel(level)
            return

    handler = logging.StreamHandler(sys.stdout)
    handler.name = HANDLER_NAME
    handler.setLevel(level)
    handler.setFormatter(
        logging.Formatter(
            fmt=LOG_FORMAT,
            datefmt=LOG_DATE_FORMAT,
        )
    )

    root_logger.addHandler(handler)
