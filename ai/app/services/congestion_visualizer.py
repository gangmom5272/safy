from dataclasses import dataclass

import cv2
import numpy as np

from app.services.congestion import (
    GRID_SIZE,
    LOCAL_WINDOW_SIZE,
    CongestionGrid,
)


GRID_COLOR = (0, 200, 0)
PERSON_BOX_COLOR = (255, 0, 0)
PEAK_WINDOW_COLOR = (0, 0, 255)
PANEL_BACKGROUND_COLOR = (20, 20, 20)
PANEL_TEXT_COLOR = (255, 255, 255)
LABEL_BACKGROUND_COLOR = (0, 0, 0)
LABEL_TEXT_COLOR = (255, 255, 255)


@dataclass(frozen=True)
class DebugSummary:
    person_count: int
    visual_congestion_score: float
    local_peak_congestion_score: float


def build_debug_summary(
    grid: CongestionGrid,
) -> DebugSummary:
    person_count = sum(
        sum(row) for row in grid.cell_counts
    )
    visual_congestion_score = sum(
        sum(row) for row in grid.cell_scores
    ) / (GRID_SIZE * GRID_SIZE)

    return DebugSummary(
        person_count=person_count,
        visual_congestion_score=round(
            visual_congestion_score,
            2,
        ),
        local_peak_congestion_score=(
            grid.peak_window_score
        ),
    )


def render_congestion_debug_image(
    image_bytes: bytes,
    person_boxes: list[tuple[float, float, float, float]],
    grid: CongestionGrid,
) -> bytes:
    encoded_image = np.frombuffer(image_bytes, dtype=np.uint8)
    image = cv2.imdecode(encoded_image, cv2.IMREAD_COLOR)

    if image is None:
        raise ValueError("이미지를 디코딩할 수 없습니다.")

    rendered_image = image.copy()
    height, width = rendered_image.shape[:2]

    _draw_grid(rendered_image, width, height)
    _draw_person_boxes(
        rendered_image,
        person_boxes,
        width,
        height,
    )
    _draw_cell_values(
        rendered_image,
        grid,
        width,
        height,
    )
    _draw_peak_window(
        rendered_image,
        grid,
        width,
        height,
    )
    _draw_summary_panel(
        rendered_image,
        grid,
        width,
        height,
    )

    encoded_success, encoded_result = cv2.imencode(
        ".png",
        rendered_image,
    )

    if not encoded_success:
        raise ValueError("디버그 이미지를 인코딩할 수 없습니다.")

    return encoded_result.tobytes()


def _draw_grid(
    image: np.ndarray,
    width: int,
    height: int,
) -> None:
    for index in range(1, GRID_SIZE):
        x = round(index * width / GRID_SIZE)
        y = round(index * height / GRID_SIZE)

        cv2.line(
            image,
            (x, 0),
            (x, height - 1),
            GRID_COLOR,
            1,
        )
        cv2.line(
            image,
            (0, y),
            (width - 1, y),
            GRID_COLOR,
            1,
        )


def _draw_person_boxes(
    image: np.ndarray,
    person_boxes: list[tuple[float, float, float, float]],
    width: int,
    height: int,
) -> None:
    for left, top, right, bottom in person_boxes:
        left_pixel = _normalized_to_pixel(left, width)
        top_pixel = _normalized_to_pixel(top, height)
        right_pixel = _normalized_to_pixel(right, width)
        bottom_pixel = _normalized_to_pixel(bottom, height)

        cv2.rectangle(
            image,
            (left_pixel, top_pixel),
            (right_pixel, bottom_pixel),
            PERSON_BOX_COLOR,
            2,
        )


def _draw_cell_values(
    image: np.ndarray,
    grid: CongestionGrid,
    width: int,
    height: int,
) -> None:
    cell_height = height / GRID_SIZE
    font_scale = max(
        0.3,
        min(width, height) / 1800,
    )

    for row in range(GRID_SIZE):
        for column in range(GRID_SIZE):
            count = grid.cell_counts[row][column]
            score = grid.cell_scores[row][column]

            if count == 0:
                continue

            x = round(column * width / GRID_SIZE) + 4
            y = (
                round(row * height / GRID_SIZE)
                + max(10, round(cell_height * 0.18))
            )
            label = f"{count}/{score:g}"

            _draw_text_with_background(
                image=image,
                text=label,
                origin=(x, y),
                font_scale=font_scale,
            )


def _draw_peak_window(
    image: np.ndarray,
    grid: CongestionGrid,
    width: int,
    height: int,
) -> None:
    if grid.peak_window_score <= 0:
        return

    left = round(
        grid.peak_window_column * width / GRID_SIZE,
    )
    top = round(
        grid.peak_window_row * height / GRID_SIZE,
    )
    right = round(
        (
            grid.peak_window_column
            + LOCAL_WINDOW_SIZE
        )
        * width
        / GRID_SIZE,
    )
    bottom = round(
        (
            grid.peak_window_row
            + LOCAL_WINDOW_SIZE
        )
        * height
        / GRID_SIZE,
    )

    cv2.rectangle(
        image,
        (left, top),
        (
            min(right, width - 1),
            min(bottom, height - 1),
        ),
        PEAK_WINDOW_COLOR,
        3,
    )


def _draw_summary_panel(
    image: np.ndarray,
    grid: CongestionGrid,
    width: int,
    height: int,
) -> None:
    summary = build_debug_summary(grid)
    panel_height = min(
        height - 1,
        max(42, round(height * 0.07)),
    )

    cv2.rectangle(
        image,
        (0, 0),
        (width - 1, panel_height),
        PANEL_BACKGROUND_COLOR,
        -1,
    )

    summary_text = (
        f"People: {summary.person_count}"
        f" | C: {summary.visual_congestion_score:.2f}"
        f" | L: {summary.local_peak_congestion_score:.2f}"
    )
    legend_text = (
        "Green: Grid | Blue: Person | Red: Peak"
    )

    summary_scale = max(
        0.4,
        min(0.75, width / 1200),
    )
    legend_scale = max(
        0.35,
        min(0.55, width / 1600),
    )

    cv2.putText(
        image,
        summary_text,
        (10, max(18, round(panel_height * 0.42))),
        cv2.FONT_HERSHEY_SIMPLEX,
        summary_scale,
        PANEL_TEXT_COLOR,
        2,
        cv2.LINE_AA,
    )
    cv2.putText(
        image,
        legend_text,
        (10, panel_height - 8),
        cv2.FONT_HERSHEY_SIMPLEX,
        legend_scale,
        PANEL_TEXT_COLOR,
        1,
        cv2.LINE_AA,
    )


def _draw_text_with_background(
    image: np.ndarray,
    text: str,
    origin: tuple[int, int],
    font_scale: float,
) -> None:
    font = cv2.FONT_HERSHEY_SIMPLEX
    thickness = 1
    padding = 3

    (text_width, text_height), baseline = cv2.getTextSize(
        text,
        font,
        font_scale,
        thickness,
    )

    x, y = origin
    image_height, image_width = image.shape[:2]

    left = max(x - padding, 0)
    top = max(y - text_height - padding, 0)
    right = min(
        x + text_width + padding,
        image_width - 1,
    )
    bottom = min(
        y + baseline + padding,
        image_height - 1,
    )

    cv2.rectangle(
        image,
        (left, top),
        (right, bottom),
        LABEL_BACKGROUND_COLOR,
        -1,
    )
    cv2.putText(
        image,
        text,
        (x, y),
        font,
        font_scale,
        LABEL_TEXT_COLOR,
        thickness,
        cv2.LINE_AA,
    )


def _normalized_to_pixel(
    coordinate: float,
    length: int,
) -> int:
    bounded_coordinate = min(max(coordinate, 0.0), 1.0)
    return round(bounded_coordinate * (length - 1))