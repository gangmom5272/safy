import csv
import json
from dataclasses import dataclass
from pathlib import Path

from app.services.frame_quality import (
    inspect_frame_quality,
)


@dataclass(frozen=True)
class FrameQualityEvaluationSample:
    sample_id: str
    scenario: str
    image_path: Path
    expected_issues: list[str]

    @property
    def expected_valid(self) -> bool:
        return not self.expected_issues


@dataclass(frozen=True)
class FrameQualityEvaluationRecord:
    sample_id: str
    scenario: str
    expected_valid: bool
    actual_valid: bool
    expected_issues: list[str]
    actual_issues: list[str]
    matches_expectation: bool
    width: int | None
    height: int | None
    brightness_score: float | None
    blur_score: float | None


def load_frame_quality_manifest(
    manifest_path: Path,
) -> list[FrameQualityEvaluationSample]:
    samples: list[FrameQualityEvaluationSample] = []
    sample_ids: set[str] = set()

    with manifest_path.open(
        "r",
        encoding="utf-8",
        newline="",
    ) as manifest_file:
        reader = csv.DictReader(manifest_file)

        for row in reader:
            sample_id = row["sample_id"]

            if sample_id in sample_ids:
                raise ValueError("sample_id는 중복될 수 없습니다")

            sample_ids.add(sample_id)

            expected_issues = [
                issue for issue in row["expected_issues"].split("|") if issue
            ]
            image_path = manifest_path.parent / row["image_path"]

            samples.append(
                FrameQualityEvaluationSample(
                    sample_id=sample_id,
                    scenario=row["scenario"],
                    image_path=image_path,
                    expected_issues=(expected_issues),
                )
            )

    return samples


def evaluate_frame_quality_sample(
    sample: FrameQualityEvaluationSample,
) -> FrameQualityEvaluationRecord:
    quality = inspect_frame_quality(sample.image_path.read_bytes())

    return FrameQualityEvaluationRecord(
        sample_id=sample.sample_id,
        scenario=sample.scenario,
        expected_valid=sample.expected_valid,
        actual_valid=quality.is_valid,
        expected_issues=sample.expected_issues,
        actual_issues=quality.issues,
        matches_expectation=(sample.expected_issues == quality.issues),
        width=quality.width,
        height=quality.height,
        brightness_score=(quality.brightness_score),
        blur_score=quality.blur_score,
    )


def _record_to_dict(
    record: FrameQualityEvaluationRecord,
) -> dict[str, object]:
    return {
        "sampleId": record.sample_id,
        "scenario": record.scenario,
        "expectedValid": record.expected_valid,
        "actualValid": record.actual_valid,
        "expectedIssues": record.expected_issues,
        "actualIssues": record.actual_issues,
        "matchesExpectation": (record.matches_expectation),
        "width": record.width,
        "height": record.height,
        "brightnessScore": (record.brightness_score),
        "blurScore": record.blur_score,
    }


def save_frame_quality_report(
    records: list[FrameQualityEvaluationRecord],
    output_path: Path,
) -> None:
    matched_count = sum(record.matches_expectation for record in records)

    report = {
        "summary": {
            "sampleCount": len(records),
            "matchedCount": matched_count,
            "mismatchedCount": (len(records) - matched_count),
        },
        "records": [_record_to_dict(record) for record in records],
    }

    output_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )
    output_path.write_text(
        json.dumps(
            report,
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
