import logging

from fastapi import (
    APIRouter,
    File,
    UploadFile,
)
from fastapi.responses import (
    JSONResponse,
    Response,
)

from app.services.image_blur import (
    IMAGE_BLUR_RULE_VERSION,
    ImageBlurError,
    blur_full_image,
)


logger = logging.getLogger(__name__)

router = APIRouter(
    prefix="/api/v1/privacy",
    tags=["privacy"],
)


def failure_response(
    status_code: int,
    failure_code: str,
) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content={
            "failureCodes": [
                failure_code,
            ],
        },
    )


@router.post(
    "/blur",
    response_class=Response,
)
async def blur_image(
    image: UploadFile = File(...),
) -> Response:
    if image.content_type is None or not image.content_type.startswith(
        "image/",
    ):
        return failure_response(
            status_code=415,
            failure_code="UNSUPPORTED_MEDIA_TYPE",
        )

    image_bytes = await image.read()

    if not image_bytes:
        return failure_response(
            status_code=400,
            failure_code="EMPTY_IMAGE",
        )

    try:
        result = blur_full_image(image_bytes)
    except ImageBlurError as error:
        if error.code == "IMAGE_DECODE_FAILED":
            status_code = 422
        else:
            status_code = 500
            logger.exception(
                "Image blur processing failed",
            )

        return failure_response(
            status_code=status_code,
            failure_code=error.code,
        )
    except Exception:
        logger.exception(
            "Unexpected image blur failure",
        )

        return failure_response(
            status_code=500,
            failure_code="IMAGE_BLUR_FAILED",
        )

    return Response(
        content=result.image_bytes,
        media_type="image/jpeg",
        headers={
            "X-Blur-Rule-Version": (IMAGE_BLUR_RULE_VERSION),
            "X-Image-Width": str(result.width),
            "X-Image-Height": str(result.height),
            "X-Processing-Time-Ms": str(
                result.processing_time_ms,
            ),
        },
    )
