import logging

from fastapi import (
    APIRouter,
    BackgroundTasks,
    File,
    HTTPException,
    UploadFile,
)

from app.schemas.analysis import FrameAnalysisResponse
from app.services.congestion import (
    CONGESTION_RULE_VERSION,
    calculate_congestion,
)
from app.services.dense_scene_escalation import (
    CONGESTION_SOURCE_OFFICIAL,
    correct_congestion_scores,
    is_congestion_correction_enabled,
)
from app.services.frame_quality import (
    FRAME_QUALITY_RULE_VERSION,
    inspect_frame_quality,
)
from app.services.model import (
    MODEL_NAME,
    MODEL_VERSION,
    MODEL_WEIGHT_ID,
)
from app.services.person_detector import detect_people
from app.services.shadow_analysis import (
    ModelAnalysisSnapshot,
    get_shadow_analysis_service,
)


logger = logging.getLogger(__name__)

router = APIRouter(
    prefix="/api/v1/analyze",
    tags=["analysis"],
)


@router.post(
    "/frame",
    response_model=FrameAnalysisResponse,
)
async def analyze_frame(
    background_tasks: BackgroundTasks,
    image: UploadFile = File(...),
) -> FrameAnalysisResponse:
    if image.content_type is None or not image.content_type.startswith("image/"):
        raise HTTPException(
            status_code=415,
            detail="이미지 파일만 업로드할 수 있습니다.",
        )

    image_bytes = await image.read()

    if not image_bytes:
        raise HTTPException(
            status_code=400,
            detail="업로드된 이미지가 비어 있습니다.",
        )

    try:
        quality_result = inspect_frame_quality(
            image_bytes,
        )
    except Exception:
        logger.exception(
            "Frame quality inspection failed",
        )

        return FrameAnalysisResponse(
            analysis_status="FAILED",
            person_count=None,
            person_confidences=[],
            visual_congestion_score=None,
            local_peak_congestion_score=None,
            density_level=None,
            risk_score=None,
            confidence=None,
            quality_issues=[],
            failure_codes=[
                "INTERNAL_AI_ERROR",
            ],
            frame_width=None,
            frame_height=None,
            brightness_score=None,
            blur_score=None,
            model_name=MODEL_NAME,
            model_version=MODEL_VERSION,
            model_weight_id=MODEL_WEIGHT_ID,
            congestion_rule_version=(CONGESTION_RULE_VERSION),
            frame_quality_rule_version=(FRAME_QUALITY_RULE_VERSION),
            processing_time_ms=0,
        )

    if not quality_result.is_valid:
        return FrameAnalysisResponse(
            analysis_status="INVALID_FRAME",
            person_count=None,
            person_confidences=[],
            visual_congestion_score=None,
            local_peak_congestion_score=None,
            density_level=None,
            risk_score=None,
            confidence=None,
            quality_issues=quality_result.issues,
            failure_codes=[],
            frame_width=quality_result.width,
            frame_height=quality_result.height,
            brightness_score=(quality_result.brightness_score),
            blur_score=quality_result.blur_score,
            model_name=MODEL_NAME,
            model_version=MODEL_VERSION,
            model_weight_id=MODEL_WEIGHT_ID,
            congestion_rule_version=(CONGESTION_RULE_VERSION),
            frame_quality_rule_version=(FRAME_QUALITY_RULE_VERSION),
            processing_time_ms=0,
        )

    try:
        detection_result = detect_people(image_bytes)
    except Exception:
        logger.exception("Person detection failed")

        return FrameAnalysisResponse(
            analysis_status="FAILED",
            person_count=None,
            person_confidences=[],
            visual_congestion_score=None,
            local_peak_congestion_score=None,
            density_level=None,
            risk_score=None,
            confidence=None,
            quality_issues=quality_result.issues,
            failure_codes=[
                "MODEL_INFERENCE_FAILED",
            ],
            frame_width=quality_result.width,
            frame_height=quality_result.height,
            brightness_score=(quality_result.brightness_score),
            blur_score=quality_result.blur_score,
            model_name=MODEL_NAME,
            model_version=MODEL_VERSION,
            model_weight_id=MODEL_WEIGHT_ID,
            congestion_rule_version=(CONGESTION_RULE_VERSION),
            frame_quality_rule_version=(FRAME_QUALITY_RULE_VERSION),
            processing_time_ms=0,
        )

    try:
        congestion_result = calculate_congestion(
            detection_result.person_centers,
        )
    except Exception:
        logger.exception(
            "Congestion calculation failed",
        )

        return FrameAnalysisResponse(
            analysis_status="FAILED",
            person_count=None,
            person_confidences=[],
            visual_congestion_score=None,
            local_peak_congestion_score=None,
            density_level=None,
            risk_score=None,
            confidence=None,
            quality_issues=quality_result.issues,
            failure_codes=[
                "CONGESTION_CALCULATION_FAILED",
            ],
            frame_width=quality_result.width,
            frame_height=quality_result.height,
            brightness_score=(quality_result.brightness_score),
            blur_score=quality_result.blur_score,
            model_name=MODEL_NAME,
            model_version=MODEL_VERSION,
            model_weight_id=MODEL_WEIGHT_ID,
            congestion_rule_version=(CONGESTION_RULE_VERSION),
            frame_quality_rule_version=(FRAME_QUALITY_RULE_VERSION),
            processing_time_ms=(detection_result.processing_time_ms),
        )

    official_snapshot = ModelAnalysisSnapshot(
        model_name=MODEL_NAME,
        model_version=MODEL_VERSION,
        model_weight_id=MODEL_WEIGHT_ID,
        analysis_status="COMPLETED",
        person_count=detection_result.person_count,
        visual_congestion_score=(congestion_result.visual_congestion_score),
        local_peak_congestion_score=(congestion_result.local_peak_congestion_score),
        processing_time_ms=(detection_result.processing_time_ms),
        failure_codes=[],
    )

    visual_congestion_score = congestion_result.visual_congestion_score
    local_peak_congestion_score = congestion_result.local_peak_congestion_score
    congestion_source = CONGESTION_SOURCE_OFFICIAL

    try:
        shadow_service = get_shadow_analysis_service()

        if shadow_service.enabled and is_congestion_correction_enabled():
            # 보정을 켜면 P2PNet이 공식 응답의 크리티컬 패스로 들어온다.
            # 지연시간이 크게 늘고 동시 요청이 직렬화되므로 서버 실측 후에만
            # 활성화한다.
            shadow_record = await shadow_service.analyze_and_log(
                image_bytes=image_bytes,
                official=official_snapshot,
            )

            if shadow_record is not None:
                correction = correct_congestion_scores(
                    official_visual_congestion_score=(
                        congestion_result.visual_congestion_score
                    ),
                    official_local_peak_congestion_score=(
                        congestion_result.local_peak_congestion_score
                    ),
                    shadow_analysis_status=(shadow_record.shadow.analysis_status),
                    shadow_visual_congestion_score=(
                        shadow_record.shadow.visual_congestion_score
                    ),
                    shadow_local_peak_congestion_score=(
                        shadow_record.shadow.local_peak_congestion_score
                    ),
                    shadow_low_confidence_signal=(
                        shadow_record.shadow.low_confidence_signal
                    ),
                )
                visual_congestion_score = correction.visual_congestion_score
                local_peak_congestion_score = correction.local_peak_congestion_score
                congestion_source = correction.source
        elif shadow_service.enabled:
            background_tasks.add_task(
                shadow_service.analyze_and_log,
                image_bytes=image_bytes,
                official=official_snapshot,
            )
    except Exception:
        logger.exception(
            "P2PNet shadow analysis scheduling failed",
        )

    return FrameAnalysisResponse(
        analysis_status="COMPLETED",
        person_count=detection_result.person_count,
        person_confidences=detection_result.confidences,
        visual_congestion_score=visual_congestion_score,
        local_peak_congestion_score=(local_peak_congestion_score),
        congestion_source=congestion_source,
        density_level=None,
        risk_score=None,
        confidence=None,
        quality_issues=quality_result.issues,
        failure_codes=[],
        frame_width=quality_result.width,
        frame_height=quality_result.height,
        brightness_score=(quality_result.brightness_score),
        blur_score=quality_result.blur_score,
        model_name=MODEL_NAME,
        model_version=MODEL_VERSION,
        model_weight_id=MODEL_WEIGHT_ID,
        congestion_rule_version=CONGESTION_RULE_VERSION,
        frame_quality_rule_version=(FRAME_QUALITY_RULE_VERSION),
        processing_time_ms=(detection_result.processing_time_ms),
    )
