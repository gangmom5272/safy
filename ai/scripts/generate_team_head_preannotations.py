import argparse
from pathlib import Path

from app.services.p2pnet_benchmark_adapter import (
    load_p2pnet_detector,
)
from app.services.team_head_point_dataset import (
    generate_team_head_point_preannotations,
    load_team_head_point_candidates,
)


def run_generation(
    *,
    manifest_path: Path,
    p2pnet_root: Path,
    weight_path: Path,
    output_root: Path,
    device: str,
    threshold: float,
) -> Path:
    candidates = load_team_head_point_candidates(manifest_path)

    detector = load_p2pnet_detector(
        p2pnet_root=p2pnet_root,
        weight_path=weight_path,
        device=device,
        threshold=threshold,
    )

    review_manifest_path = generate_team_head_point_preannotations(
        candidates=candidates,
        output_root=output_root,
        detector=detector,
    )

    print(f"sampleCount: {len(candidates)}")
    print(f"reviewManifest: {review_manifest_path}")

    return review_manifest_path


def main() -> None:
    parser = argparse.ArgumentParser(
        description=("팀 동의 사진의 P2PNet 초기 머리 좌표 라벨을 생성합니다.")
    )
    parser.add_argument(
        "manifest_path",
        type=Path,
        help="팀 사진 후보 manifest 경로",
    )
    parser.add_argument(
        "--p2pnet-root",
        type=Path,
        required=True,
        help="P2PNet 코드 디렉터리",
    )
    parser.add_argument(
        "--weight-path",
        type=Path,
        required=True,
        help="P2PNet 가중치 경로",
    )
    parser.add_argument(
        "--output",
        type=Path,
        required=True,
        help="초기 라벨 출력 디렉터리",
    )
    parser.add_argument(
        "--device",
        default="cuda",
        help="추론 장치",
    )
    parser.add_argument(
        "--threshold",
        type=float,
        default=0.5,
        help="P2PNet 탐지 임계값",
    )

    arguments = parser.parse_args()

    run_generation(
        manifest_path=(arguments.manifest_path),
        p2pnet_root=(arguments.p2pnet_root),
        weight_path=(arguments.weight_path),
        output_root=arguments.output,
        device=arguments.device,
        threshold=arguments.threshold,
    )


if __name__ == "__main__":
    main()
