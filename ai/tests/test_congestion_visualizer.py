import cv2
import numpy as np

from app.services.congestion import build_congestion_grid
from app.services.congestion_visualizer import (
    build_debug_summary,
    render_congestion_debug_image,
)


def test_render_congestion_debug_image_draws_overlay() -> None:
    image = np.full((80, 80, 3), 255, dtype=np.uint8)
    encoded_success, encoded_image = cv2.imencode(".png", image)

    assert encoded_success is True

    person_centers = [(0.3, 0.3)]
    person_boxes = [(0.2, 0.15, 0.4, 0.55)]
    grid = build_congestion_grid(person_centers)

    rendered_bytes = render_congestion_debug_image(
        image_bytes=encoded_image.tobytes(),
        person_boxes=person_boxes,
        grid=grid,
    )

    rendered_array = np.frombuffer(rendered_bytes, dtype=np.uint8)
    rendered_image = cv2.imdecode(
        rendered_array,
        cv2.IMREAD_COLOR,
    )

    assert rendered_image is not None
    assert rendered_image.shape == image.shape
    assert np.any(rendered_image < 255)

    summary_panel = rendered_image[0:25, :]

    assert float(np.mean(summary_panel)) < 180.0


def test_build_debug_summary_returns_display_values() -> None:
    grid = build_congestion_grid([(0.3, 0.3)])

    summary = build_debug_summary(grid)

    assert summary.person_count == 1
    assert summary.visual_congestion_score == 0.47
    assert summary.local_peak_congestion_score == 7.5


def test_render_does_not_draw_peak_window_without_people() -> None:
    image = np.full((80, 80, 3), 255, dtype=np.uint8)
    encoded_success, encoded_image = cv2.imencode(
        ".png",
        image,
    )

    assert encoded_success is True

    grid = build_congestion_grid([])

    rendered_bytes = render_congestion_debug_image(
        image_bytes=encoded_image.tobytes(),
        person_boxes=[],
        grid=grid,
    )

    rendered_array = np.frombuffer(
        rendered_bytes,
        dtype=np.uint8,
    )
    rendered_image = cv2.imdecode(
        rendered_array,
        cv2.IMREAD_COLOR,
    )

    assert rendered_image is not None

    red_pixels = np.all(
        rendered_image == np.array([0, 0, 255]),
        axis=2,
    )

    assert not np.any(red_pixels)