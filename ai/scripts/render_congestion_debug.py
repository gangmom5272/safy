from argparse import ArgumentParser
from dataclasses import dataclass
from pathlib import Path

from app.services.congestion import calculate_congestion
from app.services.congestion import build_congestion_grid
from app.services.congestion_visualizer import (
    render_congestion_debug_image,
)
from app.services.person_detector import detect_people


@dataclass(frozen=True)
class DebugRenderResult:
    person_count: int
    visual_congestion_score: float
    local_peak_congestion_score: float
    processing_time_ms: int
    output_path: Path


def render_file(
    input_path: Path,
    output_path: Path,
) -> DebugRenderResult:
    image_bytes = input_path.read_bytes()
    detection = detect_people(image_bytes)

    grid = build_congestion_grid(
        detection.person_centers,
    )
    congestion = calculate_congestion(
        detection.person_centers,
    )

    rendered_bytes = render_congestion_debug_image(
        image_bytes=image_bytes,
        person_boxes=detection.person_boxes,
        grid=grid,
    )

    output_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )
    output_path.write_bytes(rendered_bytes)

    return DebugRenderResult(
        person_count=detection.person_count,
        visual_congestion_score=(
            congestion.visual_congestion_score
        ),
        local_peak_congestion_score=(
            congestion.local_peak_congestion_score
        ),
        processing_time_ms=detection.processing_time_ms,
        output_path=output_path,
    )


def main() -> None:
    parser = ArgumentParser(
        description="혼잡도 계산 근거가 표시된 디버그 이미지를 생성합니다.",
    )
    parser.add_argument(
        "input_path",
        type=Path,
        help="분석할 원본 이미지 경로",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("runs/congestion-debug.png"),
        help="디버그 이미지 저장 경로",
    )

    arguments = parser.parse_args()

    result = render_file(
        input_path=arguments.input_path,
        output_path=arguments.output,
    )

    print(f"personCount: {result.person_count}")
    print(
        "visualCongestionScore: "
        f"{result.visual_congestion_score}"
    )
    print(
        "localPeakCongestionScore: "
        f"{result.local_peak_congestion_score}"
    )
    print(
        "processingTimeMs: "
        f"{result.processing_time_ms}"
    )
    print(f"output: {result.output_path}")


if __name__ == "__main__":
    main()