import logging
import logging.config

import pytest
from uvicorn.config import LOGGING_CONFIG

from app.logging_config import (
    DEFAULT_LOG_LEVEL,
    HANDLER_NAME,
    LOG_LEVEL_VARIABLE,
    configure_logging,
    resolve_log_level,
)


APP_LOGGER_NAME = "app.services.shadow_analysis"


@pytest.fixture(autouse=True)
def restore_root_logger():
    root_logger = logging.getLogger()
    original_handlers = list(root_logger.handlers)
    original_level = root_logger.level

    yield

    root_logger.handlers = original_handlers
    root_logger.setLevel(original_level)


def remove_safy_handlers() -> None:
    root_logger = logging.getLogger()
    root_logger.handlers = [
        handler
        for handler in root_logger.handlers
        if getattr(handler, "name", None) != HANDLER_NAME
    ]


def safy_handlers() -> list[logging.Handler]:
    return [
        handler
        for handler in logging.getLogger().handlers
        if getattr(handler, "name", None) == HANDLER_NAME
    ]


def test_app_info_logs_are_dropped_without_configuration() -> None:
    """수정 전 결함을 재현한다.

    uvicorn 기본 설정만 적용하면 root에 핸들러가 없어 앱 INFO 로그가
    logging.lastResort로 넘어가고, 그 레벨이 WARNING이므로 버려진다.
    이 테스트가 실패하면 uvicorn이 root를 설정하도록 바뀐 것이므로
    configure_logging의 전제를 다시 확인해야 한다.
    """
    remove_safy_handlers()
    logging.getLogger().handlers = []
    logging.getLogger().setLevel(logging.WARNING)
    logging.config.dictConfig(LOGGING_CONFIG)

    assert logging.getLogger().handlers == []
    assert logging.getLogger(APP_LOGGER_NAME).isEnabledFor(logging.INFO) is False


def test_configure_logging_enables_app_info_logs() -> None:
    remove_safy_handlers()

    configure_logging()

    assert len(safy_handlers()) == 1
    assert logging.getLogger(APP_LOGGER_NAME).isEnabledFor(logging.INFO) is True


def test_configuration_survives_uvicorn_dict_config() -> None:
    """실제 기동 순서를 재현한다.

    앱을 import할 때 configure_logging이 실행되고, 그 뒤 uvicorn이
    dictConfig를 적용한다. uvicorn 설정은 disable_existing_loggers=False이고
    root 설정을 포함하지 않으므로 핸들러가 유지되어야 한다.
    """
    remove_safy_handlers()
    configure_logging()

    logging.config.dictConfig(LOGGING_CONFIG)

    assert len(safy_handlers()) == 1
    assert logging.getLogger(APP_LOGGER_NAME).isEnabledFor(logging.INFO) is True


def test_uvicorn_loggers_do_not_duplicate_into_root() -> None:
    """uvicorn 로그가 root 핸들러로 중복 출력되지 않아야 한다."""
    logging.config.dictConfig(LOGGING_CONFIG)

    assert logging.getLogger("uvicorn").propagate is False
    assert logging.getLogger("uvicorn.access").propagate is False


def test_configure_logging_is_idempotent() -> None:
    remove_safy_handlers()

    configure_logging()
    configure_logging()
    configure_logging()

    assert len(safy_handlers()) == 1


def test_app_log_record_reaches_stdout(capsys: pytest.CaptureFixture[str]) -> None:
    remove_safy_handlers()
    configure_logging()

    logging.getLogger(APP_LOGGER_NAME).info(
        'p2pnet_shadow_analysis {"event":"probe"}',
    )

    captured = capsys.readouterr()

    assert "p2pnet_shadow_analysis" in captured.out
    assert "INFO" in captured.out
    assert APP_LOGGER_NAME in captured.out


def test_log_level_is_configurable(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv(LOG_LEVEL_VARIABLE, "warning")

    assert resolve_log_level() == logging.WARNING


def test_unknown_log_level_falls_back_to_default(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv(LOG_LEVEL_VARIABLE, "not-a-level")

    assert resolve_log_level() == logging.getLevelName(DEFAULT_LOG_LEVEL)


def test_default_log_level_is_info(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.delenv(LOG_LEVEL_VARIABLE, raising=False)

    assert resolve_log_level() == logging.INFO
