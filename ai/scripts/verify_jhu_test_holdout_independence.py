import argparse
from pathlib import Path

from app.services.jhu_test_holdout import (
    build_independence_report,
    save_independence_report,
)


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "JHU-CROWD++ TEST 분할이 P2PNet 학습·검증 데이터와 "
            "겹치지 않는지 검증하고 보고서를 저장합니다."
        ),
    )
    parser.add_argument(
        "manifest_path",
        type=Path,
        help="safy-head-point-v1 manifest 경로",
    )
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
        help="ai/ 디렉터리 경로 (스크립트 소스 스캔 기준)",
    )
    parser.add_argument(
        "--runs-root",
        type=Path,
        required=True,
        help="기존 평가 manifest·report를 스캔할 runs/ 디렉터리 경로",
    )
    parser.add_argument(
        "--output",
        type=Path,
        required=True,
        help="검증 보고서 JSON 저장 경로",
    )
    arguments = parser.parse_args()

    output_path = arguments.output.resolve()

    report = build_independence_report(
        manifest_path=arguments.manifest_path,
        repo_root=arguments.repo_root,
        runs_root=arguments.runs_root,
        excluded_scan_paths={output_path},
    )

    save_independence_report(report, arguments.output)

    print(f"totalTestSamples: {report.total_test_samples}")
    print(f"scenarioCounts: {report.scenario_counts}")
    print(f"structuralCheckPassed: {report.structural_check_passed}")
    print(f"trainingScriptTestSplitUsage: {report.training_script_test_split_usage}")
    print(f"externalReferenceMatches: {report.external_reference_matches}")
    print(f"scannedFileCount: {report.scanned_file_count}")
    print(f"isIndependent: {report.is_independent}")
    print(f"output: {arguments.output}")


if __name__ == "__main__":
    main()
