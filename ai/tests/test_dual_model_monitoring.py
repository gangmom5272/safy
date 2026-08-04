import json
from pathlib import Path


AI_ROOT = Path(__file__).resolve().parents[1]
REPOSITORY_ROOT = AI_ROOT.parent
DASHBOARD_PATH = (
    REPOSITORY_ROOT / "infra" / "grafana" / "dashboards" / "safy-ai-dual-model.json"
)
NGINX_CONFIG_PATH = REPOSITORY_ROOT / "infra" / "nginx" / "ai-failover.conf"


def test_dual_model_dashboard_is_valid_and_covers_operational_metrics() -> None:
    dashboard = json.loads(
        DASHBOARD_PATH.read_text(
            encoding="utf-8",
        )
    )

    assert dashboard["uid"] == "safy-ai-dual-model"
    assert dashboard["title"] == "SAFY AI 이중 모델 운영"
    assert dashboard["__inputs"][0]["pluginId"] == "loki"

    panel_titles = {panel["title"] for panel in dashboard["panels"]}
    assert {
        "이중 모델 분석 요청",
        "P2PNet 완료율",
        "P2PNet 보정 적용률",
        "오탐 게이트 발동률",
        "실패·타임아웃",
        "전체 왕복 p95",
        "YOLO·P2PNet 결과 차이",
        "모델별 처리시간",
        "GPU 주력·CPU 폴백 경로",
    } <= panel_titles

    expressions = "\n".join(
        target["expr"]
        for panel in dashboard["panels"]
        for target in panel.get(
            "targets",
            [],
        )
    )

    for field_name in (
        "p2pnetStatus",
        "congestionSource",
        "falsePositiveGateTriggered",
        "personCountDifference",
        "visualCongestionDifference",
        "localPeakDifference",
        "yoloProcessingTimeMs",
        "p2pnetProcessingTimeMs",
        "aiRoute",
    ):
        assert field_name in expressions


def test_ai_failover_nginx_logs_monitoring_headers() -> None:
    config = NGINX_CONFIG_PATH.read_text(
        encoding="utf-8",
    )

    assert "log_format safy_ai_operation escape=json" in config
    assert "access_log /dev/stdout safy_ai_operation" in config
    assert "map $upstream_addr $safy_ai_route" in config
    assert '"~.*,.*" "CPU_FALLBACK"' in config
    assert '"~.* : .*" "CPU_FALLBACK"' in config
    assert '"aiRoute":"$safy_ai_route"' in config

    for header_variable in (
        "$upstream_http_x_safy_ai_mode",
        "$upstream_http_x_safy_p2pnet_status",
        "$upstream_http_x_safy_congestion_source",
        "$upstream_http_x_safy_p2pnet_gate_triggered",
        "$upstream_http_x_safy_yolo_processing_time_ms",
        "$upstream_http_x_safy_p2pnet_processing_time_ms",
        "$upstream_http_x_safy_person_count_difference",
        "$upstream_http_x_safy_visual_congestion_difference",
        "$upstream_http_x_safy_local_peak_difference",
    ):
        assert header_variable in config
