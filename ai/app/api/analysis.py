from fastapi import APIRouter, File, HTTPException, UploadFile

from app.schemas.analysis import FrameAnalysisResponse
from app.services.person_detector import detect_people
from app.services.frame_quality import inspect_frame_quality


router = APIRouter(prefix="/api/v1/analyze", tags=["analysis"])


@router.post("/frame", response_model=FrameAnalysisResponse)
async def analyze_frame(
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

    quality_result = inspect_frame_quality(image_bytes)

    if not quality_result.is_valid:
        return FrameAnalysisResponse(
            analysis_status="INVALID_FRAME",
            person_count=None,
            person_confidences=[],
            density_level=None,
            risk_score=None,
            confidence=None,
            quality_issues=quality_result.issues,
            frame_width=quality_result.width,
            frame_height=quality_result.height,
            brightness_score=quality_result.brightness_score,
            blur_score=quality_result.blur_score,
            model_name="YOLO",
            model_version="yolo11n",
            processing_time_ms=0,
        )

    try:
        result = detect_people(image_bytes)
    except ValueError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error
    except Exception:
        return FrameAnalysisResponse(
            analysis_status="FAILED",
            person_count=None,
            person_confidences=[],
            density_level=None,
            risk_score=None,
            confidence=None,
            quality_issues=quality_result.issues,
            frame_width=quality_result.width,
            frame_height=quality_result.height,
            brightness_score=quality_result.brightness_score,
            blur_score=quality_result.blur_score,
            model_name="YOLO",
            model_version="yolo11n",
            processing_time_ms=0,
        )

    return FrameAnalysisResponse(
        analysis_status="COMPLETED",
        person_count=result.person_count,
        person_confidences=result.confidences,
        density_level=None,
        risk_score=None,
        confidence=None,
        quality_issues=quality_result.issues,
        frame_width=quality_result.width,
        frame_height=quality_result.height,
        brightness_score=quality_result.brightness_score,
        blur_score=quality_result.blur_score,
        model_name="YOLO",
        model_version="yolo11n",
        processing_time_ms=result.processing_time_ms,
    )