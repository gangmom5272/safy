import hashlib
import json
import logging
import os
import re
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from urllib.request import Request, urlopen
from uuid import uuid4


logger = logging.getLogger(__name__)

AI_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MANIFEST_PATH = AI_ROOT / "deploy" / "p2pnet-assets.json"
DEFAULT_SOURCE_ROOT = Path("/opt/p2pnet")
DEFAULT_WEIGHT_PATH = Path("/var/cache/safy-ai/models/p2pnet/epoch-003.pth")
SOURCE_REVISION_FILE = ".safy-source-revision"
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
GIT_COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")

ASSET_READY = "READY"
ASSET_DISABLED = "DISABLED"
ASSET_SOURCE_MISSING = "P2PNET_SOURCE_MISSING"
ASSET_SOURCE_VERSION_MISMATCH = "P2PNET_SOURCE_VERSION_MISMATCH"
ASSET_WEIGHT_URL_MISSING = "P2PNET_WEIGHT_URL_MISSING"
ASSET_WEIGHT_DOWNLOAD_FAILED = "P2PNET_WEIGHT_DOWNLOAD_FAILED"
ASSET_WEIGHT_CHECKSUM_MISMATCH = "P2PNET_WEIGHT_CHECKSUM_MISMATCH"
ASSET_MANIFEST_INVALID = "P2PNET_ASSET_MANIFEST_INVALID"
ASSET_CONFIGURATION_INVALID = "P2PNET_ASSET_CONFIGURATION_INVALID"


@dataclass(frozen=True)
class P2PNetAssetManifest:
    model_name: str
    deployment_version: str
    checkpoint_model_version: str
    weight_id: str
    weight_file_name: str
    weight_sha256: str
    source_repository: str
    source_commit: str
    source_license: str
    source_license_file: str


@dataclass(frozen=True)
class P2PNetAssetPreparation:
    ready: bool
    status: str
    model_version: str | None
    weight_id: str | None
    expected_weight_sha256: str | None
    actual_weight_sha256: str | None
    source_commit: str | None
    source_root: Path
    weight_path: Path

    def to_log_dict(self) -> dict:
        return {
            "event": "p2pnet_asset_startup",
            "ready": self.ready,
            "status": self.status,
            "modelVersion": self.model_version,
            "weightId": self.weight_id,
            "expectedWeightSha256": self.expected_weight_sha256,
            "actualWeightSha256": self.actual_weight_sha256,
            "sourceCommit": self.source_commit,
            "sourceRoot": str(self.source_root),
            "weightPath": str(self.weight_path),
        }


AssetDownloader = Callable[
    [str, Path, float, str | None, str | None],
    None,
]


def load_p2pnet_asset_manifest(
    manifest_path: Path = DEFAULT_MANIFEST_PATH,
) -> P2PNetAssetManifest:
    payload = json.loads(manifest_path.read_text(encoding="utf-8"))

    if payload.get("schemaVersion") != 1:
        raise ValueError(
            "P2PNet asset manifest schemaVersion must be 1.",
        )

    try:
        model = payload["model"]
        source = payload["source"]
        manifest = P2PNetAssetManifest(
            model_name=model["name"],
            deployment_version=model["deploymentVersion"],
            checkpoint_model_version=model["checkpointModelVersion"],
            weight_id=model["weightId"],
            weight_file_name=model["fileName"],
            weight_sha256=model["sha256"].lower(),
            source_repository=source["repository"],
            source_commit=source["commit"].lower(),
            source_license=source["license"],
            source_license_file=source["licenseFile"],
        )
    except (KeyError, AttributeError) as error:
        raise ValueError(
            "P2PNet asset manifest is missing a required field.",
        ) from error

    if not SHA256_PATTERN.fullmatch(manifest.weight_sha256):
        raise ValueError(
            "P2PNet weight sha256 must contain 64 hexadecimal characters.",
        )

    if not GIT_COMMIT_PATTERN.fullmatch(manifest.source_commit):
        raise ValueError(
            "P2PNet source commit must contain 40 hexadecimal characters.",
        )

    return manifest


def calculate_file_sha256(path: Path) -> str:
    digest = hashlib.sha256()

    with path.open("rb") as stream:
        for chunk in iter(
            lambda: stream.read(1024 * 1024),
            b"",
        ):
            digest.update(chunk)

    return digest.hexdigest()


def prepare_p2pnet_assets(
    *,
    manifest_path: Path = DEFAULT_MANIFEST_PATH,
    source_root: Path = DEFAULT_SOURCE_ROOT,
    weight_path: Path = DEFAULT_WEIGHT_PATH,
    download_url: str | None = None,
    download_timeout_seconds: float = 60.0,
    auth_header_name: str | None = None,
    auth_header_value: str | None = None,
    downloader: AssetDownloader | None = None,
) -> P2PNetAssetPreparation:
    try:
        manifest = load_p2pnet_asset_manifest(
            manifest_path,
        )
    except (OSError, ValueError, json.JSONDecodeError):
        logger.exception(
            "P2PNet asset manifest validation failed",
        )
        return _failed_preparation(
            status=ASSET_MANIFEST_INVALID,
            source_root=source_root,
            weight_path=weight_path,
        )

    try:
        source_status = _validate_source(
            source_root=source_root,
            expected_commit=manifest.source_commit,
        )
    except OSError:
        logger.exception(
            "P2PNet source validation failed",
        )
        source_status = ASSET_SOURCE_MISSING

    if source_status is not None:
        return _failed_preparation(
            status=source_status,
            source_root=source_root,
            weight_path=weight_path,
            manifest=manifest,
        )

    actual_sha256 = (
        calculate_file_sha256(weight_path) if weight_path.is_file() else None
    )

    if actual_sha256 == manifest.weight_sha256:
        return _ready_preparation(
            manifest=manifest,
            source_root=source_root,
            weight_path=weight_path,
            actual_sha256=actual_sha256,
        )

    if not download_url:
        status = (
            ASSET_WEIGHT_CHECKSUM_MISMATCH
            if actual_sha256 is not None
            else ASSET_WEIGHT_URL_MISSING
        )
        return _failed_preparation(
            status=status,
            source_root=source_root,
            weight_path=weight_path,
            manifest=manifest,
            actual_sha256=actual_sha256,
        )

    weight_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )
    temporary_path = weight_path.with_name(f".{weight_path.name}.{uuid4().hex}.part")
    asset_downloader = downloader or _download_asset

    try:
        asset_downloader(
            download_url,
            temporary_path,
            download_timeout_seconds,
            auth_header_name,
            auth_header_value,
        )
        downloaded_sha256 = calculate_file_sha256(
            temporary_path,
        )

        if downloaded_sha256 != manifest.weight_sha256:
            return _failed_preparation(
                status=ASSET_WEIGHT_CHECKSUM_MISMATCH,
                source_root=source_root,
                weight_path=weight_path,
                manifest=manifest,
                actual_sha256=downloaded_sha256,
            )

        temporary_path.replace(weight_path)
        return _ready_preparation(
            manifest=manifest,
            source_root=source_root,
            weight_path=weight_path,
            actual_sha256=downloaded_sha256,
        )
    except Exception:
        logger.exception(
            "P2PNet weight download failed",
        )
        return _failed_preparation(
            status=ASSET_WEIGHT_DOWNLOAD_FAILED,
            source_root=source_root,
            weight_path=weight_path,
            manifest=manifest,
        )
    finally:
        temporary_path.unlink(missing_ok=True)


def initialize_p2pnet_shadow_assets() -> P2PNetAssetPreparation:
    from app.services.shadow_analysis import (
        get_shadow_analysis_service,
    )

    source_root = Path(
        os.getenv(
            "P2PNET_SHADOW_ROOT",
            str(DEFAULT_SOURCE_ROOT),
        )
    )
    weight_path = Path(
        os.getenv(
            "P2PNET_SHADOW_WEIGHT_PATH",
            str(DEFAULT_WEIGHT_PATH),
        )
    )

    if not _read_enabled_environment():
        preparation = P2PNetAssetPreparation(
            ready=False,
            status=ASSET_DISABLED,
            model_version=None,
            weight_id=None,
            expected_weight_sha256=None,
            actual_weight_sha256=None,
            source_commit=None,
            source_root=source_root,
            weight_path=weight_path,
        )
        _log_preparation(preparation)
        get_shadow_analysis_service.cache_clear()
        return preparation

    try:
        download_timeout_seconds = float(
            os.getenv(
                "P2PNET_ASSET_DOWNLOAD_TIMEOUT_SECONDS",
                "60.0",
            )
        )
        if download_timeout_seconds <= 0:
            raise ValueError(
                "P2PNet asset download timeout must be positive.",
            )

        preparation = prepare_p2pnet_assets(
            manifest_path=Path(
                os.getenv(
                    "P2PNET_ASSET_MANIFEST_PATH",
                    str(DEFAULT_MANIFEST_PATH),
                )
            ),
            source_root=source_root,
            weight_path=weight_path,
            download_url=os.getenv("P2PNET_SHADOW_WEIGHT_URL"),
            download_timeout_seconds=download_timeout_seconds,
            auth_header_name=os.getenv("P2PNET_ASSET_AUTH_HEADER_NAME"),
            auth_header_value=os.getenv("P2PNET_ASSET_AUTH_HEADER_VALUE"),
        )
    except Exception:
        logger.exception(
            "P2PNet asset startup configuration failed",
        )
        preparation = _failed_preparation(
            status=ASSET_CONFIGURATION_INVALID,
            source_root=source_root,
            weight_path=weight_path,
        )

    if preparation.ready:
        os.environ["P2PNET_SHADOW_ROOT"] = str(preparation.source_root)
        os.environ["P2PNET_SHADOW_WEIGHT_PATH"] = str(preparation.weight_path)
        if preparation.model_version is not None:
            os.environ["P2PNET_SHADOW_MODEL_VERSION"] = preparation.model_version
        if preparation.weight_id is not None:
            os.environ["P2PNET_SHADOW_WEIGHT_ID"] = preparation.weight_id
    else:
        os.environ["P2PNET_SHADOW_ENABLED"] = "false"

    get_shadow_analysis_service.cache_clear()
    _log_preparation(preparation)
    return preparation


def _validate_source(
    *,
    source_root: Path,
    expected_commit: str,
) -> str | None:
    required_paths = [
        source_root / "models" / "__init__.py",
        source_root / "util" / "misc.py",
        source_root / "LICENSE",
        source_root / SOURCE_REVISION_FILE,
    ]

    if any(not path.is_file() for path in required_paths):
        return ASSET_SOURCE_MISSING

    actual_commit = (
        source_root.joinpath(SOURCE_REVISION_FILE)
        .read_text(encoding="utf-8")
        .strip()
        .lower()
    )

    if actual_commit != expected_commit:
        return ASSET_SOURCE_VERSION_MISMATCH

    return None


def _download_asset(
    url: str,
    output_path: Path,
    timeout_seconds: float,
    auth_header_name: str | None,
    auth_header_value: str | None,
) -> None:
    request = Request(url)

    if auth_header_name and auth_header_value:
        request.add_header(
            auth_header_name,
            auth_header_value,
        )

    with (
        urlopen(
            request,
            timeout=timeout_seconds,
        ) as response,
        output_path.open("wb") as output_stream,
    ):
        while chunk := response.read(1024 * 1024):
            output_stream.write(chunk)


def _read_enabled_environment() -> bool:
    value = os.getenv(
        "P2PNET_SHADOW_ENABLED",
        "false",
    )
    return value.strip().lower() in {
        "1",
        "true",
        "yes",
        "on",
    }


def _ready_preparation(
    *,
    manifest: P2PNetAssetManifest,
    source_root: Path,
    weight_path: Path,
    actual_sha256: str,
) -> P2PNetAssetPreparation:
    return P2PNetAssetPreparation(
        ready=True,
        status=ASSET_READY,
        model_version=manifest.deployment_version,
        weight_id=manifest.weight_id,
        expected_weight_sha256=manifest.weight_sha256,
        actual_weight_sha256=actual_sha256,
        source_commit=manifest.source_commit,
        source_root=source_root,
        weight_path=weight_path,
    )


def _failed_preparation(
    *,
    status: str,
    source_root: Path,
    weight_path: Path,
    manifest: P2PNetAssetManifest | None = None,
    actual_sha256: str | None = None,
) -> P2PNetAssetPreparation:
    return P2PNetAssetPreparation(
        ready=False,
        status=status,
        model_version=(manifest.deployment_version if manifest is not None else None),
        weight_id=(manifest.weight_id if manifest is not None else None),
        expected_weight_sha256=(
            manifest.weight_sha256 if manifest is not None else None
        ),
        actual_weight_sha256=actual_sha256,
        source_commit=(manifest.source_commit if manifest is not None else None),
        source_root=source_root,
        weight_path=weight_path,
    )


def _log_preparation(
    preparation: P2PNetAssetPreparation,
) -> None:
    logger.info(
        "p2pnet_asset_startup %s",
        json.dumps(
            preparation.to_log_dict(),
            ensure_ascii=False,
            separators=(",", ":"),
        ),
    )
