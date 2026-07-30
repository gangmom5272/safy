import asyncio
import hashlib
import json
import logging
import os
from collections.abc import Callable
from dataclasses import asdict, dataclass
from functools import lru_cache
from pathlib import Path
from threading import Lock
from typing import Protocol
from uuid import uuid4


logger = logging.getLogger(__name__)

AI_ROOT = Path(__file__).resolve().parents[2]

SHADOW_MODEL_NAME = "P2PNet"
SHADOW_MODEL_VERSION = "safy-p2pnet-v2-epoch3"
SHADOW_MODEL_WEIGHT_ID = "p2pnet-finetune-v2-balanced-search/epoch-003.pth"
SHADOW_SELECTION_STATUS = "YOLO_PRIMARY_P2PNET_SHADOW"
AUTOMATIC_FALLBACK_STATUS = "DISABLED"
SHADOW_FAILURE_CODE = "MODEL_INFERENCE_FAILED"


@dataclass(frozen=True)
class ModelAnalysisSnapshot:
    model_name: str
    model_version: str
    model_weight_id: str
    analysis_status: str
    person_count: int | None
    visual_congestion_score: float | None
    local_peak_congestion_score: float | None
    processing_time_ms: int
    failure_codes: list[str]
    low_confidence_signal: bool | None = None


@dataclass(frozen=True)
class ShadowAnalysisDifferences:
    person_count: int | None
    visual_congestion_score: float | None
    local_peak_congestion_score: float | None


@dataclass(frozen=True)
class ShadowAnalysisRecord:
    analysis_id: str
    frame_sha256: str
    selection_status: str
    automatic_fallback: str
    official: ModelAnalysisSnapshot
    shadow: ModelAnalysisSnapshot
    differences: ShadowAnalysisDifferences

    def to_dict(self) -> dict:
        return {
            "event": "p2pnet_shadow_analysis",
            "analysisId": self.analysis_id,
            "frameSha256": self.frame_sha256,
            "selectionStatus": self.selection_status,
            "automaticFallback": self.automatic_fallback,
            "official": _snapshot_to_dict(self.official),
            "shadow": _snapshot_to_dict(self.shadow),
            "differences": {
                "personCount": self.differences.person_count,
                "visualCongestionScore": (self.differences.visual_congestion_score),
                "localPeakCongestionScore": (
                    self.differences.local_peak_congestion_score
                ),
            },
        }


@dataclass(frozen=True)
class ShadowAnalysisConfig:
    enabled: bool
    p2pnet_root: Path
    weight_path: Path
    device: str
    threshold: float
    timeout_seconds: float
    model_version: str = SHADOW_MODEL_VERSION
    model_weight_id: str = SHADOW_MODEL_WEIGHT_ID

    @classmethod
    def from_environment(cls) -> "ShadowAnalysisConfig":
        enabled = _read_boolean_environment(
            "P2PNET_SHADOW_ENABLED",
            default=False,
        )
        threshold = float(
            os.getenv(
                "P2PNET_SHADOW_THRESHOLD",
                "0.5",
            )
        )
        timeout_seconds = float(
            os.getenv(
                "P2PNET_SHADOW_TIMEOUT_SECONDS",
                "5.0",
            )
        )

        if not 0.0 < threshold < 1.0:
            raise ValueError(
                "P2PNet shadow threshold must be between 0 and 1.",
            )

        if timeout_seconds <= 0.0:
            raise ValueError(
                "P2PNet shadow timeout must be greater than 0.",
            )

        return cls(
            enabled=enabled,
            p2pnet_root=_resolve_path(
                os.getenv(
                    "P2PNET_SHADOW_ROOT",
                    "/opt/p2pnet",
                )
            ),
            weight_path=_resolve_path(
                os.getenv(
                    "P2PNET_SHADOW_WEIGHT_PATH",
                    ("/var/cache/safy-ai/models/p2pnet/epoch-003.pth"),
                )
            ),
            device=os.getenv(
                "P2PNET_SHADOW_DEVICE",
                "cpu",
            ),
            threshold=threshold,
            timeout_seconds=timeout_seconds,
            model_version=os.getenv(
                "P2PNET_SHADOW_MODEL_VERSION",
                SHADOW_MODEL_VERSION,
            ),
            model_weight_id=os.getenv(
                "P2PNET_SHADOW_WEIGHT_ID",
                SHADOW_MODEL_WEIGHT_ID,
            ),
        )


class ShadowRunnerResult(Protocol):
    analysis_status: str
    person_count: int | None
    visual_congestion_score: float | None
    local_peak_congestion_score: float | None
    processing_time_ms: int
    failure_codes: list[str]
    low_confidence_signal: bool | None


ShadowRunner = Callable[[bytes], ShadowRunnerResult]
ShadowRunnerFactory = Callable[[ShadowAnalysisConfig], ShadowRunner]
ShadowRecordSink = Callable[[dict], None]


class P2PNetShadowAnalysisService:
    def __init__(
        self,
        *,
        config: ShadowAnalysisConfig,
        runner_factory: ShadowRunnerFactory | None = None,
        record_sink: ShadowRecordSink | None = None,
    ) -> None:
        self.config = config
        self._runner_factory = runner_factory or _build_default_runner
        self._record_sink = record_sink or _write_shadow_log
        self._runner: ShadowRunner | None = None
        self._runner_lock = Lock()
        self._inference_lock = Lock()
        self._available = config.enabled

    @property
    def enabled(self) -> bool:
        return self._available

    async def analyze_and_log(
        self,
        *,
        image_bytes: bytes,
        official: ModelAnalysisSnapshot,
        analysis_id: str | None = None,
    ) -> ShadowAnalysisRecord | None:
        if not self.enabled:
            return None

        try:
            shadow = await asyncio.wait_for(
                asyncio.to_thread(
                    self._run_shadow,
                    image_bytes,
                ),
                timeout=self.config.timeout_seconds,
            )
        except TimeoutError:
            logger.error(
                "P2PNet shadow analysis timed out after %.2f seconds",
                self.config.timeout_seconds,
            )
            shadow = self._failed_snapshot()
        except Exception:
            logger.exception(
                "P2PNet shadow analysis failed",
            )
            shadow = self._failed_snapshot()

        record = ShadowAnalysisRecord(
            analysis_id=analysis_id or str(uuid4()),
            frame_sha256=hashlib.sha256(image_bytes).hexdigest(),
            selection_status=SHADOW_SELECTION_STATUS,
            automatic_fallback=AUTOMATIC_FALLBACK_STATUS,
            official=official,
            shadow=shadow,
            differences=_calculate_differences(
                official,
                shadow,
            ),
        )
        self._record_sink(record.to_dict())
        return record

    def _run_shadow(
        self,
        image_bytes: bytes,
    ) -> ModelAnalysisSnapshot:
        with self._inference_lock:
            result = self._get_runner()(image_bytes)

        return ModelAnalysisSnapshot(
            model_name=SHADOW_MODEL_NAME,
            model_version=self.config.model_version,
            model_weight_id=self.config.model_weight_id,
            analysis_status=result.analysis_status,
            person_count=result.person_count,
            visual_congestion_score=(result.visual_congestion_score),
            local_peak_congestion_score=(result.local_peak_congestion_score),
            processing_time_ms=result.processing_time_ms,
            failure_codes=list(result.failure_codes),
            low_confidence_signal=result.low_confidence_signal,
        )

    def _get_runner(self) -> ShadowRunner:
        if self._runner is not None:
            return self._runner

        with self._runner_lock:
            if self._runner is None:
                try:
                    self._runner = self._runner_factory(
                        self.config,
                    )
                except Exception:
                    self._available = False
                    logger.exception(
                        "P2PNet shadow runner initialization failed; "
                        "shadow analysis is now disabled",
                    )
                    raise

        return self._runner

    def _failed_snapshot(self) -> ModelAnalysisSnapshot:
        return ModelAnalysisSnapshot(
            model_name=SHADOW_MODEL_NAME,
            model_version=self.config.model_version,
            model_weight_id=self.config.model_weight_id,
            analysis_status="FAILED",
            person_count=None,
            visual_congestion_score=None,
            local_peak_congestion_score=None,
            processing_time_ms=0,
            failure_codes=[SHADOW_FAILURE_CODE],
        )


@lru_cache(maxsize=1)
def get_shadow_analysis_service() -> P2PNetShadowAnalysisService:
    return P2PNetShadowAnalysisService(
        config=ShadowAnalysisConfig.from_environment(),
    )


def _build_default_runner(
    config: ShadowAnalysisConfig,
) -> ShadowRunner:
    from app.services.hybrid_model_comparison import (
        build_p2pnet_runner,
    )

    runner = build_p2pnet_runner(
        p2pnet_root=config.p2pnet_root,
        weight_path=config.weight_path,
        device=config.device,
        threshold=config.threshold,
        model_version=config.model_version,
    )
    return runner.run


def _calculate_differences(
    official: ModelAnalysisSnapshot,
    shadow: ModelAnalysisSnapshot,
) -> ShadowAnalysisDifferences:
    if (
        official.analysis_status != "COMPLETED"
        or shadow.analysis_status != "COMPLETED"
        or official.person_count is None
        or shadow.person_count is None
        or official.visual_congestion_score is None
        or shadow.visual_congestion_score is None
        or official.local_peak_congestion_score is None
        or shadow.local_peak_congestion_score is None
    ):
        return ShadowAnalysisDifferences(
            person_count=None,
            visual_congestion_score=None,
            local_peak_congestion_score=None,
        )

    return ShadowAnalysisDifferences(
        person_count=abs(
            official.person_count - shadow.person_count,
        ),
        visual_congestion_score=round(
            abs(official.visual_congestion_score - shadow.visual_congestion_score),
            2,
        ),
        local_peak_congestion_score=round(
            abs(
                official.local_peak_congestion_score
                - shadow.local_peak_congestion_score
            ),
            2,
        ),
    )


def _snapshot_to_dict(
    snapshot: ModelAnalysisSnapshot,
) -> dict:
    values = asdict(snapshot)
    return {
        "modelName": values["model_name"],
        "modelVersion": values["model_version"],
        "modelWeightId": values["model_weight_id"],
        "analysisStatus": values["analysis_status"],
        "personCount": values["person_count"],
        "visualCongestionScore": values["visual_congestion_score"],
        "localPeakCongestionScore": values["local_peak_congestion_score"],
        "processingTimeMs": values["processing_time_ms"],
        "failureCodes": values["failure_codes"],
        "lowConfidenceSignal": values["low_confidence_signal"],
    }


def _read_boolean_environment(
    name: str,
    *,
    default: bool,
) -> bool:
    raw_value = os.getenv(name)

    if raw_value is None:
        return default

    normalized_value = raw_value.strip().lower()

    if normalized_value in {"1", "true", "yes", "on"}:
        return True

    if normalized_value in {"0", "false", "no", "off"}:
        return False

    raise ValueError(
        f"{name} must be a boolean value.",
    )


def _resolve_path(raw_path: str) -> Path:
    path = Path(raw_path)

    if path.is_absolute():
        return path

    return AI_ROOT / path


def _write_shadow_log(record: dict) -> None:
    logger.info(
        "p2pnet_shadow_analysis %s",
        json.dumps(
            record,
            ensure_ascii=False,
            separators=(",", ":"),
        ),
    )
