import argparse
import csv
from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageOps

from app.services.head_point_dataset import (
    load_head_point_annotation,
)
from app.services.team_head_point_dataset import (
    approve_team_head_point_review,
)


WINDOW_NAME = "SAFY Head Point Review"
HEADER_HEIGHT = 90
MAX_DISPLAY_WIDTH = 1400
MAX_DISPLAY_HEIGHT = 850
DELETE_DISTANCE_PIXELS = 20.0


@dataclass(frozen=True)
class HeadPointReviewItem:
    sample_id: str
    scenario: str
    image_path: Path
    annotation_path: Path
    image_width: int
    image_height: int


def _resolve_manifest_path(
    *,
    manifest_path: Path,
    value: str,
) -> Path:
    path = Path(value)

    if path.is_absolute():
        return path

    return manifest_path.parent / path


def load_pending_review_items(
    *,
    review_manifest_path: Path,
    scenarios: set[str],
) -> list[HeadPointReviewItem]:
    items: list[HeadPointReviewItem] = []

    with review_manifest_path.open(
        encoding="utf-8",
        newline="",
    ) as stream:
        reader = csv.DictReader(stream)

        for record in reader:
            if record["review_status"] != "PENDING_REVIEW":
                continue

            if record["scenario"] not in scenarios:
                continue

            items.append(
                HeadPointReviewItem(
                    sample_id=record["sample_id"],
                    scenario=record["scenario"],
                    image_path=(
                        _resolve_manifest_path(
                            manifest_path=(review_manifest_path),
                            value=record["image_path"],
                        )
                    ),
                    annotation_path=(
                        _resolve_manifest_path(
                            manifest_path=(review_manifest_path),
                            value=record["annotation_path"],
                        )
                    ),
                    image_width=int(record["image_width"]),
                    image_height=int(record["image_height"]),
                )
            )

    return items


def _load_oriented_image(
    item: HeadPointReviewItem,
) -> np.ndarray:
    with Image.open(item.image_path) as source_image:
        oriented_image = (ImageOps.exif_transpose(source_image)).convert("RGB")

        if oriented_image.size != (
            item.image_width,
            item.image_height,
        ):
            raise ValueError(
                f"{item.sample_id} 이미지 크기가 manifest와 일치하지 않습니다."
            )

        rgb_image = np.array(oriented_image)

    return cv2.cvtColor(
        rgb_image,
        cv2.COLOR_RGB2BGR,
    )


def _calculate_display_size(
    *,
    image_width: int,
    image_height: int,
) -> tuple[int, int]:
    scale = min(
        1.0,
        MAX_DISPLAY_WIDTH / image_width,
        MAX_DISPLAY_HEIGHT / image_height,
    )

    return (
        max(
            1,
            round(image_width * scale),
        ),
        max(
            1,
            round(image_height * scale),
        ),
    )


def _render_review_canvas(
    *,
    image: np.ndarray,
    item: HeadPointReviewItem,
    points: list[tuple[float, float]],
    item_index: int,
    item_count: int,
) -> tuple[
    np.ndarray,
    float,
    float,
]:
    display_width, display_height = _calculate_display_size(
        image_width=item.image_width,
        image_height=item.image_height,
    )

    scale_x = display_width / item.image_width
    scale_y = display_height / item.image_height

    resized_image = cv2.resize(
        image,
        (
            display_width,
            display_height,
        ),
        interpolation=cv2.INTER_AREA,
    )

    canvas = np.zeros(
        (
            display_height + HEADER_HEIGHT,
            display_width,
            3,
        ),
        dtype=np.uint8,
    )
    canvas[
        HEADER_HEIGHT:,
        :,
    ] = resized_image

    title = (
        f"{item_index + 1}/{item_count}  "
        f"{item.sample_id}  "
        f"{item.scenario}  "
        f"points={len(points)}"
    )
    controls = "Left:add  Right:delete  C:clear  Z:undo  A:approve/next  S:skip  Q:quit"

    cv2.putText(
        canvas,
        title,
        (
            12,
            32,
        ),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.7,
        (
            255,
            255,
            255,
        ),
        2,
        cv2.LINE_AA,
    )
    cv2.putText(
        canvas,
        controls,
        (
            12,
            68,
        ),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.58,
        (
            190,
            230,
            255,
        ),
        1,
        cv2.LINE_AA,
    )

    for point_x, point_y in points:
        display_x = round(point_x * scale_x)
        display_y = round(point_y * scale_y) + HEADER_HEIGHT

        cv2.circle(
            canvas,
            (
                display_x,
                display_y,
            ),
            5,
            (
                0,
                0,
                255,
            ),
            -1,
            cv2.LINE_AA,
        )
        cv2.circle(
            canvas,
            (
                display_x,
                display_y,
            ),
            8,
            (
                255,
                255,
                255,
            ),
            1,
            cv2.LINE_AA,
        )

    return (
        canvas,
        scale_x,
        scale_y,
    )


def _review_item(
    *,
    item: HeadPointReviewItem,
    item_index: int,
    item_count: int,
) -> tuple[
    str,
    list[tuple[float, float]],
]:
    image = _load_oriented_image(item)
    annotation = load_head_point_annotation(
        annotation_path=(item.annotation_path),
        image_width=item.image_width,
        image_height=item.image_height,
    )

    points = list(annotation.points)
    history: list[list[tuple[float, float]]] = []

    state = {
        "points": points,
        "scale_x": 1.0,
        "scale_y": 1.0,
    }

    def handle_mouse(
        event: int,
        mouse_x: int,
        mouse_y: int,
        flags: int,
        parameter,
    ) -> None:
        del flags
        del parameter

        if mouse_y < HEADER_HEIGHT:
            return

        scale_x = float(state["scale_x"])
        scale_y = float(state["scale_y"])
        current_points = state["points"]

        image_x = min(
            max(
                mouse_x / scale_x,
                0.0,
            ),
            item.image_width - 1.0,
        )
        image_y = min(
            max(
                (mouse_y - HEADER_HEIGHT) / scale_y,
                0.0,
            ),
            item.image_height - 1.0,
        )

        if event == cv2.EVENT_LBUTTONDOWN:
            history.append(list(current_points))
            current_points.append(
                (
                    round(image_x, 2),
                    round(image_y, 2),
                )
            )

        elif event == cv2.EVENT_RBUTTONDOWN and current_points:
            distances = [
                (
                    (point_x * scale_x - mouse_x) ** 2
                    + (point_y * scale_y + HEADER_HEIGHT - mouse_y) ** 2
                )
                ** 0.5
                for point_x, point_y in current_points
            ]
            nearest_index = int(np.argmin(distances))

            if distances[nearest_index] <= DELETE_DISTANCE_PIXELS:
                history.append(list(current_points))
                current_points.pop(nearest_index)

    cv2.namedWindow(
        WINDOW_NAME,
        cv2.WINDOW_NORMAL,
    )
    cv2.setMouseCallback(
        WINDOW_NAME,
        handle_mouse,
    )

    while True:
        (
            canvas,
            scale_x,
            scale_y,
        ) = _render_review_canvas(
            image=image,
            item=item,
            points=points,
            item_index=item_index,
            item_count=item_count,
        )

        state["scale_x"] = scale_x
        state["scale_y"] = scale_y

        cv2.imshow(
            WINDOW_NAME,
            canvas,
        )

        key = cv2.waitKey(20) & 0xFF

        if key in (
            ord("a"),
            ord("A"),
        ):
            return (
                "APPROVE",
                points,
            )

        if key in (
            ord("s"),
            ord("S"),
        ):
            return (
                "SKIP",
                points,
            )

        if key in (
            ord("c"),
            ord("C"),
        ):
            history.append(list(points))
            points.clear()

        if (
            key
            in (
                ord("z"),
                ord("Z"),
            )
            and history
        ):
            points[:] = history.pop()

        if key in (
            ord("q"),
            ord("Q"),
            27,
        ):
            return (
                "QUIT",
                points,
            )

        if (
            cv2.getWindowProperty(
                WINDOW_NAME,
                cv2.WND_PROP_VISIBLE,
            )
            < 1
        ):
            return (
                "QUIT",
                points,
            )


def run_review(
    *,
    review_manifest_path: Path,
    scenarios: set[str],
) -> None:
    items = load_pending_review_items(
        review_manifest_path=(review_manifest_path),
        scenarios=scenarios,
    )

    if not items:
        print("검수할 PENDING_REVIEW 표본이 없습니다.")
        return

    print(f"reviewItemCount: {len(items)}")

    try:
        for item_index, item in enumerate(items):
            action, points = _review_item(
                item=item,
                item_index=item_index,
                item_count=len(items),
            )

            if action == "QUIT":
                print("reviewStopped")
                break

            if action == "SKIP":
                print(f"skipped: {item.sample_id}")
                continue

            approve_team_head_point_review(
                review_manifest_path=(review_manifest_path),
                sample_id=item.sample_id,
                points=points,
            )
            print(f"approved: {item.sample_id} personCount={len(points)}")
    finally:
        cv2.destroyAllWindows()


def main() -> None:
    parser = argparse.ArgumentParser(
        description=("팀 사진의 머리 좌표를 클릭으로 검수합니다.")
    )
    parser.add_argument(
        "review_manifest_path",
        type=Path,
        help="검수 manifest 경로",
    )
    parser.add_argument(
        "--scenarios",
        nargs="+",
        default=[
            "EMPTY",
            "SPARSE",
        ],
        help="검수할 시나리오 목록",
    )

    arguments = parser.parse_args()

    run_review(
        review_manifest_path=(arguments.review_manifest_path),
        scenarios=set(arguments.scenarios),
    )


if __name__ == "__main__":
    main()
