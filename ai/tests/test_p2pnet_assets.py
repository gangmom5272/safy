import hashlib
import json
import os
from pathlib import Path

from app.services.p2pnet_assets import (
    ASSET_CONFIGURATION_INVALID,
    ASSET_READY,
    ASSET_SOURCE_VERSION_MISMATCH,
    ASSET_WEIGHT_CHECKSUM_MISMATCH,
    P2PNetAssetManifest,
    initialize_p2pnet_shadow_assets,
    load_p2pnet_asset_manifest,
    prepare_p2pnet_assets,
)
from app.services.shadow_analysis import (
    get_shadow_analysis_service,
)


def write_manifest(
    path: Path,
    *,
    weight_bytes: bytes,
    source_commit: str = "a" * 40,
) -> None:
    payload = {
        "schemaVersion": 1,
        "model": {
            "name": "P2PNet",
            "deploymentVersion": "test-v2",
            "checkpointModelVersion": "test-v1",
            "weightId": "test/epoch-003.pth",
            "fileName": "epoch-003.pth",
            "sha256": hashlib.sha256(weight_bytes).hexdigest(),
        },
        "source": {
            "repository": "https://example.com/p2pnet.git",
            "commit": source_commit,
            "license": "academic research",
            "licenseFile": "/opt/p2pnet/LICENSE",
        },
    }
    path.write_text(
        json.dumps(payload),
        encoding="utf-8",
    )


def write_source(
    root: Path,
    *,
    commit: str = "a" * 40,
) -> None:
    (root / "models").mkdir(
        parents=True,
    )
    (root / "util").mkdir()
    (root / "models" / "__init__.py").write_text(
        "",
        encoding="utf-8",
    )
    (root / "util" / "misc.py").write_text(
        "",
        encoding="utf-8",
    )
    (root / "LICENSE").write_text(
        "academic research",
        encoding="utf-8",
    )
    (root / ".safy-source-revision").write_text(
        commit,
        encoding="utf-8",
    )


def test_loads_deployment_asset_manifest(
    tmp_path: Path,
) -> None:
    manifest_path = tmp_path / "manifest.json"
    write_manifest(
        manifest_path,
        weight_bytes=b"weight",
    )

    manifest = load_p2pnet_asset_manifest(
        manifest_path,
    )

    assert isinstance(
        manifest,
        P2PNetAssetManifest,
    )
    assert manifest.deployment_version == "test-v2"
    assert manifest.checkpoint_model_version == "test-v1"
    assert manifest.source_commit == "a" * 40


def test_existing_assets_are_verified(
    tmp_path: Path,
) -> None:
    source_root = tmp_path / "p2pnet"
    weight_path = tmp_path / "epoch-003.pth"
    manifest_path = tmp_path / "manifest.json"
    write_source(source_root)
    weight_path.write_bytes(b"weight")
    write_manifest(
        manifest_path,
        weight_bytes=b"weight",
    )

    result = prepare_p2pnet_assets(
        manifest_path=manifest_path,
        source_root=source_root,
        weight_path=weight_path,
    )

    assert result.ready is True
    assert result.status == ASSET_READY
    assert result.actual_weight_sha256 == (hashlib.sha256(b"weight").hexdigest())


def test_missing_weight_is_downloaded_and_cached(
    tmp_path: Path,
) -> None:
    source_root = tmp_path / "p2pnet"
    weight_path = tmp_path / "cache" / "epoch-003.pth"
    manifest_path = tmp_path / "manifest.json"
    write_source(source_root)
    write_manifest(
        manifest_path,
        weight_bytes=b"downloaded-weight",
    )
    download_calls = []

    def downloader(
        url,
        output_path,
        timeout_seconds,
        auth_header_name,
        auth_header_value,
    ) -> None:
        download_calls.append(
            (
                url,
                timeout_seconds,
                auth_header_name,
                auth_header_value,
            )
        )
        output_path.write_bytes(b"downloaded-weight")

    result = prepare_p2pnet_assets(
        manifest_path=manifest_path,
        source_root=source_root,
        weight_path=weight_path,
        download_url="https://example.com/epoch-003.pth",
        auth_header_name="DEPLOY-TOKEN",
        auth_header_value="secret",
        downloader=downloader,
    )

    assert result.ready is True
    assert weight_path.read_bytes() == b"downloaded-weight"
    assert download_calls == [
        (
            "https://example.com/epoch-003.pth",
            60.0,
            "DEPLOY-TOKEN",
            "secret",
        )
    ]


def test_downloaded_checksum_mismatch_is_not_cached(
    tmp_path: Path,
) -> None:
    source_root = tmp_path / "p2pnet"
    weight_path = tmp_path / "epoch-003.pth"
    manifest_path = tmp_path / "manifest.json"
    write_source(source_root)
    write_manifest(
        manifest_path,
        weight_bytes=b"expected",
    )

    def downloader(
        _url,
        output_path,
        _timeout_seconds,
        _auth_header_name,
        _auth_header_value,
    ) -> None:
        output_path.write_bytes(b"wrong")

    result = prepare_p2pnet_assets(
        manifest_path=manifest_path,
        source_root=source_root,
        weight_path=weight_path,
        download_url="https://example.com/epoch-003.pth",
        downloader=downloader,
    )

    assert result.ready is False
    assert result.status == ASSET_WEIGHT_CHECKSUM_MISMATCH
    assert weight_path.exists() is False
    assert list(tmp_path.glob("*.part")) == []


def test_source_revision_must_match_manifest(
    tmp_path: Path,
) -> None:
    source_root = tmp_path / "p2pnet"
    manifest_path = tmp_path / "manifest.json"
    write_source(
        source_root,
        commit="b" * 40,
    )
    write_manifest(
        manifest_path,
        weight_bytes=b"weight",
    )

    result = prepare_p2pnet_assets(
        manifest_path=manifest_path,
        source_root=source_root,
        weight_path=tmp_path / "weight.pth",
    )

    assert result.ready is False
    assert result.status == ASSET_SOURCE_VERSION_MISMATCH


def test_startup_failure_disables_only_shadow(
    tmp_path: Path,
    monkeypatch,
) -> None:
    manifest_path = tmp_path / "manifest.json"
    write_manifest(
        manifest_path,
        weight_bytes=b"weight",
    )
    monkeypatch.setenv(
        "P2PNET_SHADOW_ENABLED",
        "true",
    )
    monkeypatch.setenv(
        "P2PNET_ASSET_MANIFEST_PATH",
        str(manifest_path),
    )
    monkeypatch.setenv(
        "P2PNET_SHADOW_ROOT",
        str(tmp_path / "missing-source"),
    )
    monkeypatch.setenv(
        "P2PNET_SHADOW_WEIGHT_PATH",
        str(tmp_path / "missing-weight.pth"),
    )
    get_shadow_analysis_service.cache_clear()

    result = initialize_p2pnet_shadow_assets()

    assert result.ready is False
    assert os.environ["P2PNET_SHADOW_ENABLED"] == "false"
    assert get_shadow_analysis_service().enabled is False


def test_invalid_startup_configuration_disables_shadow(
    monkeypatch,
) -> None:
    monkeypatch.setenv(
        "P2PNET_SHADOW_ENABLED",
        "true",
    )
    monkeypatch.setenv(
        "P2PNET_ASSET_DOWNLOAD_TIMEOUT_SECONDS",
        "not-a-number",
    )

    result = initialize_p2pnet_shadow_assets()

    assert result.ready is False
    assert result.status == ASSET_CONFIGURATION_INVALID
    assert os.environ["P2PNET_SHADOW_ENABLED"] == "false"
