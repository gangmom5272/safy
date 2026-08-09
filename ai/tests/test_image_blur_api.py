import cv2
import numpy as np
from app.api import privacy
from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


def encode_test_image() -> bytes:
    rows, columns = np.indices((240, 320))
    checkerboard = ((rows // 4 + columns // 4) % 2 * 255).astype(np.uint8)
    image = cv2.cvtColor(
        checkerboard,
        cv2.COLOR_GRAY2BGR,
    )

    success, encoded_image = cv2.imencode(
        ".jpg",
        image,
    )

    assert success is True
    return encoded_image.tobytes()


def test_blur_image_returns_jpeg_with_original_size() -> None:
    response = client.post(
        "/api/v1/privacy/blur",
        files={
            "image": (
                "representative.jpg",
                encode_test_image(),
                "image/jpeg",
            )
        },
    )

    assert response.status_code == 200
    assert response.headers["content-type"] == "image/jpeg"
    assert response.headers["x-blur-rule-version"] == ("full-image-blur-v0.1")
    assert response.headers["x-image-width"] == "320"
    assert response.headers["x-image-height"] == "240"
    assert int(response.headers["x-processing-time-ms"]) >= 0

    encoded_image = np.frombuffer(
        response.content,
        dtype=np.uint8,
    )
    blurred_image = cv2.imdecode(
        encoded_image,
        cv2.IMREAD_COLOR,
    )

    assert blurred_image is not None
    assert blurred_image.shape == (
        240,
        320,
        3,
    )


def test_blur_image_returns_safe_decode_failure() -> None:
    response = client.post(
        "/api/v1/privacy/blur",
        files={
            "image": (
                "broken.jpg",
                b"not-an-image",
                "image/jpeg",
            )
        },
    )

    assert response.status_code == 422
    assert response.json() == {
        "failureCodes": [
            "IMAGE_DECODE_FAILED",
        ],
    }
    assert b"not-an-image" not in response.content


def test_blur_image_rejects_empty_file() -> None:
    response = client.post(
        "/api/v1/privacy/blur",
        files={
            "image": (
                "empty.jpg",
                b"",
                "image/jpeg",
            )
        },
    )

    assert response.status_code == 400
    assert response.json() == {
        "failureCodes": [
            "EMPTY_IMAGE",
        ],
    }


def test_blur_image_rejects_non_image_content_type() -> None:
    response = client.post(
        "/api/v1/privacy/blur",
        files={
            "image": (
                "document.txt",
                b"plain-text",
                "text/plain",
            )
        },
    )

    assert response.status_code == 415
    assert response.json() == {
        "failureCodes": [
            "UNSUPPORTED_MEDIA_TYPE",
        ],
    }


def test_blur_image_hides_internal_processing_error(
    monkeypatch,
) -> None:
    def fail_blur(_: bytes) -> None:
        raise RuntimeError(
            "Sensitive internal blur error",
        )

    monkeypatch.setattr(
        privacy,
        "blur_full_image",
        fail_blur,
    )

    original_bytes = encode_test_image()

    response = client.post(
        "/api/v1/privacy/blur",
        files={
            "image": (
                "representative.jpg",
                original_bytes,
                "image/jpeg",
            )
        },
    )

    assert response.status_code == 500
    assert response.json() == {
        "failureCodes": [
            "IMAGE_BLUR_FAILED",
        ],
    }
    assert b"Sensitive internal blur error" not in (response.content)
    assert original_bytes != response.content
