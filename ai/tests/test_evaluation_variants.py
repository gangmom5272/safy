import cv2
import numpy as np

from app.services.evaluation_variants import (
    create_blurry_variant,
    create_dark_variant,
)
from app.services.frame_quality import (
    inspect_frame_quality,
)


def test_quality_variants_trigger_expected_issues() -> None:
    random_generator = np.random.default_rng(42)
    image = random_generator.integers(
        0,
        256,
        size=(360, 640, 3),
        dtype=np.uint8,
    )

    encoded, encoded_image = cv2.imencode(
        ".jpg",
        image,
    )

    assert encoded is True

    image_bytes = encoded_image.tobytes()

    dark_image_bytes = create_dark_variant(image_bytes)
    blurry_image_bytes = create_blurry_variant(image_bytes)

    dark_quality = inspect_frame_quality(dark_image_bytes)
    blurry_quality = inspect_frame_quality(blurry_image_bytes)

    assert "TOO_DARK" in dark_quality.issues
    assert "TOO_BLURRY" in blurry_quality.issues
