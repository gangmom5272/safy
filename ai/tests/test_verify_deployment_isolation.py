import json
from pathlib import Path

from scripts.verify_deployment_isolation import (
    SECRET_MARKERS,
    _build_mismatched_source,
    _build_valid_source_root,
    build_scenarios,
)


AI_ROOT = Path(__file__).resolve().parents[1]


def test_valid_source_root_satisfies_asset_validation(tmp_path: Path) -> None:
    manifest = json.loads(
        (AI_ROOT / "deploy" / "p2pnet-assets.json").read_text(encoding="utf-8")
    )

    root = _build_valid_source_root(tmp_path)

    assert (root / "models" / "__init__.py").is_file()
    assert (root / "util" / "misc.py").is_file()
    assert (root / "LICENSE").is_file()
    assert (root / ".safy-source-revision").read_text(encoding="utf-8") == manifest[
        "source"
    ]["commit"]


def test_mismatched_source_uses_a_different_commit(tmp_path: Path) -> None:
    manifest = json.loads(
        (AI_ROOT / "deploy" / "p2pnet-assets.json").read_text(encoding="utf-8")
    )

    root = _build_mismatched_source(tmp_path)
    revision = (root / ".safy-source-revision").read_text(encoding="utf-8")

    assert revision != manifest["source"]["commit"]
    assert len(revision) == 40


def test_build_scenarios_covers_every_documented_failure_status(
    tmp_path: Path,
) -> None:
    scenarios = build_scenarios(tmp_path)
    statuses = {scenario["expectedStatus"] for scenario in scenarios}

    assert statuses == {
        "DISABLED",
        "P2PNET_SOURCE_MISSING",
        "P2PNET_SOURCE_VERSION_MISMATCH",
        "P2PNET_WEIGHT_URL_MISSING",
        "P2PNET_WEIGHT_CHECKSUM_MISMATCH",
        "P2PNET_WEIGHT_DOWNLOAD_FAILED",
        "P2PNET_ASSET_MANIFEST_INVALID",
    }


def test_build_scenarios_names_are_unique(tmp_path: Path) -> None:
    scenarios = build_scenarios(tmp_path)
    names = [scenario["name"] for scenario in scenarios]

    assert len(names) == len(set(names))


def test_download_scenario_injects_secret_markers(tmp_path: Path) -> None:
    """누출 검사가 실제로 의미 있으려면 비밀값이 환경에 주입되어야 한다."""
    scenarios = build_scenarios(tmp_path)
    download = next(
        scenario
        for scenario in scenarios
        if scenario["name"] == "WEIGHT_DOWNLOAD_FAILED"
    )
    injected = json.dumps(download["env"])

    assert any(marker in injected for marker in SECRET_MARKERS)
