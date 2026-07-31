"""위험 상향 신호로 인원수와 국소 최대 혼잡도 L 중 무엇을 쓸지 비교한다.

두 신호를 같은 홀드아웃 표본에 적용해 재현율·오경보를 비교하고, 게이트를
거부권으로 쓸 때와 쓰지 않을 때의 차이도 함께 측정한다.

새로 추론하지 않고 138번 평가 리포트의 값을 재사용한다.
"""

import argparse
import json
from pathlib import Path

from app.services.dense_scene_escalation import (
    DENSE_SCENE_LOCAL_PEAK_THRESHOLD,
)
from app.services.escalation_simulation import (
    ALL_STRATEGIES,
    FrameObservation,
    evaluate_strategy,
    find_new_false_alarms,
    find_newly_caught_samples,
    outcome_to_dict,
)


# 프로젝트 시나리오 분류의 CROWDED 경계(51명 이상). 인원수 기반 비교에만
# 사용하며, 운영 판정 기준은 dense_scene_escalation의 L 임계값이다.
PERSON_COUNT_THRESHOLD = 51.0

SIGNAL_PERSON_COUNT = "personCount"
SIGNAL_LOCAL_PEAK = "localPeakCongestionScore"


def _load_gate_signals(gate_signal_path: Path) -> dict[str, bool]:
    payload = json.loads(gate_signal_path.read_text(encoding="utf-8"))

    return {
        record["sampleId"]: record["lowConfidenceSignal"]
        for record in payload["records"]
    }


_GROUND_TRUTH_KEYS = {
    SIGNAL_PERSON_COUNT: "groundTruthCount",
    SIGNAL_LOCAL_PEAK: "groundTruthLocalPeakCongestionScore",
}

_PREDICTED_KEYS = {
    SIGNAL_PERSON_COUNT: "predictedCount",
    SIGNAL_LOCAL_PEAK: "predictedLocalPeakCongestionScore",
}


def build_observations(
    *,
    yolo_report_path: Path,
    p2pnet_report_path: Path,
    gate_signal_path: Path,
    signal_name: str,
    ground_truth_signal_name: str | None = None,
) -> list[FrameObservation]:
    """예측 신호와 정답 신호를 다르게 지정할 수 있다.

    같은 정답 기준으로 서로 다른 예측 신호를 비교하려면
    `ground_truth_signal_name`을 고정한다.
    """
    yolo_report = json.loads(yolo_report_path.read_text(encoding="utf-8"))
    p2pnet_report = json.loads(p2pnet_report_path.read_text(encoding="utf-8"))
    gate_signals = _load_gate_signals(gate_signal_path)

    ground_truth_signal_name = ground_truth_signal_name or signal_name

    if signal_name not in _PREDICTED_KEYS:
        raise ValueError(f"지원하지 않는 신호입니다: {signal_name}")

    if ground_truth_signal_name not in _GROUND_TRUTH_KEYS:
        raise ValueError(
            f"지원하지 않는 정답 신호입니다: {ground_truth_signal_name}",
        )

    ground_truth_key = _GROUND_TRUTH_KEYS[ground_truth_signal_name]
    predicted_key = _PREDICTED_KEYS[signal_name]

    yolo_values = {
        record["sampleId"]: record[predicted_key] for record in yolo_report["records"]
    }

    observations: list[FrameObservation] = []

    for record in p2pnet_report["records"]:
        sample_id = record["sampleId"]

        if sample_id not in yolo_values:
            raise ValueError(f"YOLO 보고서에 없는 표본입니다: {sample_id}")

        if sample_id not in gate_signals:
            raise ValueError(f"게이트 신호에 없는 표본입니다: {sample_id}")

        observations.append(
            FrameObservation(
                sample_id=sample_id,
                scenario=record["scenario"],
                ground_truth_value=record[ground_truth_key],
                official_value=yolo_values[sample_id],
                shadow_value=record[predicted_key],
                shadow_low_confidence_signal=(gate_signals[sample_id]),
            )
        )

    return observations


def _sample_summary(observation: FrameObservation) -> dict:
    return {
        "sampleId": observation.sample_id,
        "scenario": observation.scenario,
        "groundTruthValue": observation.ground_truth_value,
        "officialValue": observation.official_value,
        "shadowValue": observation.shadow_value,
        "lowConfidenceSignal": (observation.shadow_low_confidence_signal),
    }


def run_comparison(
    *,
    yolo_report_path: Path,
    p2pnet_report_path: Path,
    gate_signal_path: Path,
    output_path: Path,
) -> dict:
    # 앞의 두 항목은 각 신호를 자기 정답 기준으로 평가한다.
    # 세 번째는 두 신호를 같은 정답 기준(L>=80)으로 비교하기 위한 것이며,
    # 이때 정답 임계값(80)과 예측 임계값(51)이 달라야 한다.
    comparisons = [
        (SIGNAL_PERSON_COUNT, PERSON_COUNT_THRESHOLD, None, None),
        (SIGNAL_LOCAL_PEAK, DENSE_SCENE_LOCAL_PEAK_THRESHOLD, None, None),
        (
            SIGNAL_PERSON_COUNT,
            PERSON_COUNT_THRESHOLD,
            SIGNAL_LOCAL_PEAK,
            DENSE_SCENE_LOCAL_PEAK_THRESHOLD,
        ),
    ]

    results = []

    for (
        signal_name,
        threshold,
        ground_truth_signal_name,
        ground_truth_threshold,
    ) in comparisons:
        observations = build_observations(
            yolo_report_path=yolo_report_path,
            p2pnet_report_path=p2pnet_report_path,
            gate_signal_path=gate_signal_path,
            signal_name=signal_name,
            ground_truth_signal_name=(ground_truth_signal_name),
        )

        outcomes = {
            strategy: outcome_to_dict(
                evaluate_strategy(
                    observations,
                    strategy=strategy,
                    threshold=threshold,
                    ground_truth_threshold=(ground_truth_threshold),
                    signal_name=signal_name,
                )
            )
            for strategy in ALL_STRATEGIES
        }

        newly_caught = find_newly_caught_samples(
            observations,
            threshold=threshold,
            ground_truth_threshold=(ground_truth_threshold),
        )
        new_false_alarms = find_new_false_alarms(
            observations,
            threshold=threshold,
            ground_truth_threshold=(ground_truth_threshold),
        )

        results.append(
            {
                "signalName": signal_name,
                "groundTruthSignalName": (ground_truth_signal_name or signal_name),
                "threshold": threshold,
                "strategies": outcomes,
                "newlyCaughtCount": len(newly_caught),
                "newFalseAlarmCount": len(new_false_alarms),
                "newlyCaughtSamples": [
                    _sample_summary(item) for item in newly_caught[:20]
                ],
                "newFalseAlarmSamples": [
                    _sample_summary(item) for item in new_false_alarms[:20]
                ],
            }
        )

    payload = {
        "yoloReportPath": yolo_report_path.as_posix(),
        "p2pnetReportPath": p2pnet_report_path.as_posix(),
        "gateSignalPath": gate_signal_path.as_posix(),
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
            "인원수 기반과 L 기반 위험 상향 신호를 같은 홀드아웃에서 비교한다."
        ),
    )
    parser.add_argument("--yolo-report", type=Path, required=True)
    parser.add_argument("--p2pnet-report", type=Path, required=True)
    parser.add_argument("--gate-signals", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()

    payload = run_comparison(
        yolo_report_path=arguments.yolo_report,
        p2pnet_report_path=arguments.p2pnet_report,
        gate_signal_path=arguments.gate_signals,
        output_path=arguments.output,
    )

    for result in payload["results"]:
        print()
        print(
            f"=== 예측신호: {result['signalName']} (임계 {result['threshold']}) "
            f"/ 정답기준: {result['groundTruthSignalName']} ==="
        )
        official = result["strategies"]["OFFICIAL_ONLY"]
        print(f"실제 고밀도: {official['actuallyDenseCount']}장")

        for strategy, outcome in result["strategies"].items():
            print(
                f"  {strategy:<20} "
                f"recall={outcome['recall']:.3f} "
                f"놓침={outcome['falseNegative']:>4} "
                f"오경보={outcome['falsePositive']:>4} "
                f"정밀도={outcome['precision']:.3f}"
            )

    print()
    print(f"output: {arguments.output}")


if __name__ == "__main__":
    main()
