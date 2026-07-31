import argparse
import json
from pathlib import Path

from app.services.escalation_simulation import (
    ALL_STRATEGIES,
    DEFAULT_DENSE_THRESHOLD,
    FrameObservation,
    evaluate_strategy,
    find_new_false_alarms,
    find_newly_caught_samples,
    outcome_to_dict,
)


def build_observations(
    *,
    yolo_report_path: Path,
    gate_signal_path: Path,
) -> list[FrameObservation]:
    yolo_report = json.loads(yolo_report_path.read_text(encoding="utf-8"))
    gate_payload = json.loads(gate_signal_path.read_text(encoding="utf-8"))

    yolo_counts = {
        record["sampleId"]: record["predictedCount"]
        for record in yolo_report["records"]
    }

    observations: list[FrameObservation] = []

    for record in gate_payload["records"]:
        sample_id = record["sampleId"]

        if sample_id not in yolo_counts:
            raise ValueError(
                f"YOLO 보고서에 없는 표본입니다: {sample_id}",
            )

        observations.append(
            FrameObservation(
                sample_id=sample_id,
                scenario=record["scenario"],
                ground_truth_count=record["groundTruthCount"],
                yolo_count=yolo_counts[sample_id],
                p2pnet_count=record["p2pnetCount"],
                p2pnet_low_confidence_signal=(record["lowConfidenceSignal"]),
            )
        )

    return observations


def run_simulation(
    *,
    yolo_report_path: Path,
    gate_signal_path: Path,
    dense_thresholds: list[int],
    output_path: Path,
) -> dict:
    observations = build_observations(
        yolo_report_path=yolo_report_path,
        gate_signal_path=gate_signal_path,
    )

    results = []

    for dense_threshold in dense_thresholds:
        outcomes = {
            strategy: outcome_to_dict(
                evaluate_strategy(
                    observations,
                    strategy=strategy,
                    dense_threshold=dense_threshold,
                )
            )
            for strategy in ALL_STRATEGIES
        }

        newly_caught = find_newly_caught_samples(
            observations,
            dense_threshold=dense_threshold,
        )
        new_false_alarms = find_new_false_alarms(
            observations,
            dense_threshold=dense_threshold,
        )

        results.append(
            {
                "denseThreshold": dense_threshold,
                "strategies": outcomes,
                "newlyCaughtCount": len(newly_caught),
                "newFalseAlarmCount": len(new_false_alarms),
                "newlyCaughtSamples": [
                    {
                        "sampleId": item.sample_id,
                        "scenario": item.scenario,
                        "groundTruthCount": (item.ground_truth_count),
                        "yoloCount": item.yolo_count,
                        "p2pnetCount": item.p2pnet_count,
                    }
                    for item in newly_caught[:20]
                ],
                "newFalseAlarmSamples": [
                    {
                        "sampleId": item.sample_id,
                        "scenario": item.scenario,
                        "groundTruthCount": (item.ground_truth_count),
                        "yoloCount": item.yolo_count,
                        "p2pnetCount": item.p2pnet_count,
                    }
                    for item in new_false_alarms[:20]
                ],
            }
        )

    payload = {
        "yoloReportPath": yolo_report_path.as_posix(),
        "gateSignalPath": gate_signal_path.as_posix(),
        "sampleCount": len(observations),
        "results": results,
    }

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    return payload


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "YOLO 단독 판정과 P2PNet 게이트 기반 비대칭 위험 상향 규칙을 "
            "홀드아웃 표본에서 비교 시뮬레이션한다."
        ),
    )
    parser.add_argument("--yolo-report", type=Path, required=True)
    parser.add_argument("--gate-signals", type=Path, required=True)
    parser.add_argument(
        "--dense-threshold",
        type=int,
        nargs="+",
        default=[DEFAULT_DENSE_THRESHOLD],
    )
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()

    payload = run_simulation(
        yolo_report_path=arguments.yolo_report,
        gate_signal_path=arguments.gate_signals,
        dense_thresholds=arguments.dense_threshold,
        output_path=arguments.output,
    )

    print(f"sampleCount: {payload['sampleCount']}")

    for result in payload["results"]:
        print()
        print(f"=== denseThreshold: {result['denseThreshold']} ===")
        print(
            f"actuallyDense: {result['strategies']['YOLO_ONLY']['actuallyDenseCount']}"
        )

        for strategy, outcome in result["strategies"].items():
            print(
                f"{strategy}: "
                f"recall={outcome['recall']} "
                f"precision={outcome['precision']} "
                f"falseAlarmRate={outcome['falseAlarmRate']} "
                f"(TP={outcome['truePositive']} "
                f"FP={outcome['falsePositive']} "
                f"FN={outcome['falseNegative']})"
            )

        print(f"newlyCaught: {result['newlyCaughtCount']}")
        print(f"newFalseAlarms: {result['newFalseAlarmCount']}")

    print()
    print(f"output: {arguments.output}")


if __name__ == "__main__":
    main()
