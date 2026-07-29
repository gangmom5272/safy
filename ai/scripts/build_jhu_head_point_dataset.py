import argparse
from pathlib import Path

from app.services.jhu_head_point_dataset import (
    build_jhu_head_point_dataset,
)


def run_build(
    source_root: Path,
    output_root: Path,
) -> Path:
    manifest_path = build_jhu_head_point_dataset(
        source_root=source_root,
        output_root=output_root,
    )

    print(f"manifest: {manifest_path}")

    return manifest_path


def main() -> None:
    parser = argparse.ArgumentParser(
        description=("JHU-CROWD++ 데이터를 SAFY head-point-v1 형식으로 변환합니다.")
    )
    parser.add_argument(
        "source_root",
        type=Path,
        help="JHU-CROWD++ v2.0 원본 폴더",
    )
    parser.add_argument(
        "--output",
        type=Path,
        required=True,
        help="변환된 라벨과 manifest 저장 폴더",
    )

    args = parser.parse_args()

    run_build(
        source_root=args.source_root,
        output_root=args.output,
    )


if __name__ == "__main__":
    main()
