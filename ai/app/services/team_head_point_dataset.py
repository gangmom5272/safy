import csv
import hashlib
import os
from dataclasses import dataclass
from pathlib import Path

from app.services.head_point_dataset import (
    HEAD_POINT_LABEL_VERSION,
    load_head_point_annotation,
    save_head_point_annotation,
)
from app.services.p2pnet_benchmark_adapter import (
    P2PNetDetector,
)
from app.services.congestion import (
    calculate_congestion,
)


TEAM_SOURCE_NAME = "TEAM_CONSENTED"
TEAM_LICENSE = "team-consented-internal"


@dataclass(frozen=True)
class TeamHeadPointCandidate:
    sample_id: str
    image_path: Path
    image_width: int
    image_height: int
    image_sha256: str
    scenario: str
    consent_confirmed: bool
    label_status: str


def _parse_boolean(
    value: str,
) -> bool:
    normalized_value = value.strip().lower()

    if normalized_value == "true":
        return True

    if normalized_value == "false":
        return False

    raise ValueError("동의 여부는 true 또는 false여야 합니다.")


def load_team_head_point_candidates(
    manifest_path: Path,
) -> list[TeamHeadPointCandidate]:
    candidates: list[TeamHeadPointCandidate] = []

    with manifest_path.open(
        encoding="utf-8-sig",
        newline="",
    ) as stream:
        reader = csv.DictReader(stream)

        for row in reader:
            image_path = Path(row["selected_path"])

            if not image_path.is_absolute():
                image_path = manifest_path.parent / image_path

            candidates.append(
                TeamHeadPointCandidate(
                    sample_id=row["sample_id"].strip(),
                    image_path=image_path,
                    image_width=int(row["frame_width"]),
                    image_height=int(row["frame_height"]),
                    image_sha256=row["sha256"].strip(),
                    scenario=row["manual_scenario"].strip(),
                    consent_confirmed=(_parse_boolean(row["consent_confirmed"])),
                    label_status=row["label_status"].strip(),
                )
            )

    return candidates


def convert_normalized_head_points(
    *,
    points: list[tuple[float, float]],
    image_width: int,
    image_height: int,
) -> list[tuple[float, float]]:
    if image_width <= 0 or image_height <= 0:
        raise ValueError("이미지 크기는 양수여야 합니다.")

    maximum_x = float(image_width - 1)
    maximum_y = float(image_height - 1)

    converted_points: list[tuple[float, float]] = []

    for normalized_x, normalized_y in points:
        point_x = min(
            max(
                normalized_x * image_width,
                0.0,
            ),
            maximum_x,
        )
        point_y = min(
            max(
                normalized_y * image_height,
                0.0,
            ),
            maximum_y,
        )

        converted_points.append(
            (
                round(point_x, 6),
                round(point_y, 6),
            )
        )

    return converted_points


def generate_team_head_point_preannotations(
    *,
    candidates: list[TeamHeadPointCandidate],
    output_root: Path,
    detector: P2PNetDetector,
) -> Path:
    output_root.mkdir(
        parents=True,
        exist_ok=True,
    )

    review_manifest_path = output_root / "review-manifest.csv"
    records: list[dict[str, object]] = []

    for candidate in candidates:
        if not candidate.consent_confirmed:
            raise ValueError("팀 사진은 사용 동의가 필요합니다.")

        if candidate.label_status != "UNLABELED":
            raise ValueError("초기 라벨 생성 대상은 UNLABELED 상태여야 합니다.")

        image_bytes = candidate.image_path.read_bytes()
        actual_sha256 = hashlib.sha256(image_bytes).hexdigest()

        if actual_sha256 != candidate.image_sha256:
            raise ValueError("팀 사진 해시가 manifest와 일치하지 않습니다.")

        if candidate.scenario == "EMPTY":
            points: list[tuple[float, float]] = []
            preannotation_method = "EMPTY_BASELINE"

        elif candidate.scenario == "SPARSE":
            points = []
            preannotation_method = "MANUAL_REQUIRED"

        else:
            detection_result = detector(image_bytes)

            if detection_result.person_count != len(detection_result.person_centers):
                raise ValueError("P2PNet 인원수와 머리 좌표 수가 일치하지 않습니다.")

            points = convert_normalized_head_points(
                points=(detection_result.person_centers),
                image_width=(candidate.image_width),
                image_height=(candidate.image_height),
            )
            preannotation_method = "P2PNET"

        annotation_path = (
            output_root / "annotations" / (f"{candidate.sample_id}.points.csv")
        )

        save_head_point_annotation(
            points=points,
            annotation_path=annotation_path,
        )

        relative_image_path = Path(
            os.path.relpath(
                candidate.image_path.resolve(),
                output_root.resolve(),
            )
        ).as_posix()

        relative_annotation_path = Path(
            os.path.relpath(
                annotation_path.resolve(),
                output_root.resolve(),
            )
        ).as_posix()

        records.append(
            {
                "sample_id": (candidate.sample_id),
                "source": TEAM_SOURCE_NAME,
                "source_group": (candidate.sample_id),
                "split": "TRAIN",
                "image_path": (relative_image_path),
                "annotation_path": (relative_annotation_path),
                "image_width": (candidate.image_width),
                "image_height": (candidate.image_height),
                "person_count": len(points),
                "scenario": (candidate.scenario),
                "license": TEAM_LICENSE,
                "consent_confirmed": "true",
                "image_sha256": (candidate.image_sha256),
                "label_format_version": (HEAD_POINT_LABEL_VERSION),
                "preannotation_method": (preannotation_method),
                "review_status": ("PENDING_REVIEW"),
            }
        )

    fieldnames = [
        "sample_id",
        "source",
        "source_group",
        "split",
        "image_path",
        "annotation_path",
        "image_width",
        "image_height",
        "person_count",
        "scenario",
        "license",
        "consent_confirmed",
        "image_sha256",
        "label_format_version",
        "preannotation_method",
        "review_status",
    ]

    with review_manifest_path.open(
        mode="w",
        encoding="utf-8",
        newline="",
    ) as stream:
        writer = csv.DictWriter(
            stream,
            fieldnames=fieldnames,
            lineterminator="\n",
        )
        writer.writeheader()
        writer.writerows(records)

    return review_manifest_path


def approve_team_head_point_review(
    *,
    review_manifest_path: Path,
    sample_id: str,
    points: list[tuple[float, float]],
) -> None:
    with review_manifest_path.open(
        encoding="utf-8",
        newline="",
    ) as stream:
        reader = csv.DictReader(stream)
        fieldnames = reader.fieldnames
        records = list(reader)

    if fieldnames is None:
        raise ValueError("검수 manifest 헤더가 필요합니다.")

    matching_records = [
        record for record in records if record["sample_id"] == sample_id
    ]

    if len(matching_records) != 1:
        raise ValueError("검수할 sample_id를 정확히 하나 찾을 수 있어야 합니다.")

    record = matching_records[0]
    image_width = int(record["image_width"])
    image_height = int(record["image_height"])

    seen_points: set[tuple[float, float]] = set()

    for point_x, point_y in points:
        if not (0.0 <= point_x < image_width and 0.0 <= point_y < image_height):
            raise ValueError("머리 좌표는 이미지 범위 안이어야 합니다.")

        point = (
            float(point_x),
            float(point_y),
        )

        if point in seen_points:
            raise ValueError("머리 좌표는 중복될 수 없습니다.")

        seen_points.add(point)

    annotation_path = Path(record["annotation_path"])

    if not annotation_path.is_absolute():
        annotation_path = review_manifest_path.parent / annotation_path

    save_head_point_annotation(
        points=points,
        annotation_path=annotation_path,
    )

    record["person_count"] = str(len(points))
    record["review_status"] = "APPROVED"

    with review_manifest_path.open(
        mode="w",
        encoding="utf-8",
        newline="",
    ) as stream:
        writer = csv.DictWriter(
            stream,
            fieldnames=fieldnames,
            lineterminator="\n",
        )
        writer.writeheader()
        writer.writerows(records)


def reclassify_approved_team_reviews(
    *,
    review_manifest_path: Path,
) -> int:
    with review_manifest_path.open(
        encoding="utf-8",
        newline="",
    ) as stream:
        reader = csv.DictReader(stream)
        fieldnames = reader.fieldnames
        records = list(reader)

    if fieldnames is None:
        raise ValueError("검수 manifest 헤더가 필요합니다.")

    updated_count = 0

    for record in records:
        if record["review_status"] != "APPROVED":
            continue

        image_width = int(record["image_width"])
        image_height = int(record["image_height"])

        annotation_path = Path(record["annotation_path"])

        if not annotation_path.is_absolute():
            annotation_path = review_manifest_path.parent / annotation_path

        annotation = load_head_point_annotation(
            annotation_path=(annotation_path),
            image_width=image_width,
            image_height=image_height,
        )
        person_count = annotation.person_count

        if person_count != int(record["person_count"]):
            raise ValueError(
                "검수 manifest의 인원수와 머리 좌표 수가 일치하지 않습니다."
            )

        if person_count == 0:
            scenario = "EMPTY"

        elif person_count <= 5:
            scenario = "SPARSE"

        elif person_count > 50:
            scenario = "CROWDED"

        else:
            normalized_points = [
                (
                    point_x / image_width,
                    point_y / image_height,
                )
                for point_x, point_y in annotation.points
            ]
            congestion = calculate_congestion(normalized_points)

            if congestion.local_peak_congestion_score >= 37.5:
                scenario = "LOCAL_DENSE"
            else:
                scenario = "NORMAL"

        if record["scenario"] != scenario:
            record["scenario"] = scenario
            updated_count += 1

    with review_manifest_path.open(
        mode="w",
        encoding="utf-8",
        newline="",
    ) as stream:
        writer = csv.DictWriter(
            stream,
            fieldnames=fieldnames,
            lineterminator="\n",
        )
        writer.writeheader()
        writer.writerows(records)

    return updated_count


def export_approved_team_head_point_manifest(
    *,
    review_manifest_path: Path,
    output_path: Path,
) -> Path:
    with review_manifest_path.open(
        encoding="utf-8",
        newline="",
    ) as stream:
        reader = csv.DictReader(stream)
        records = list(reader)

    approved_records = [
        record for record in records if record["review_status"] == "APPROVED"
    ]

    if not approved_records:
        raise ValueError("승인된 팀 머리 좌표 표본이 필요합니다.")

    output_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    exported_records: list[dict[str, object]] = []

    for record in approved_records:
        source_image_path = Path(record["image_path"])
        source_annotation_path = Path(record["annotation_path"])

        if not source_image_path.is_absolute():
            source_image_path = review_manifest_path.parent / source_image_path

        if not source_annotation_path.is_absolute():
            source_annotation_path = (
                review_manifest_path.parent / source_annotation_path
            )

        relative_image_path = Path(
            os.path.relpath(
                source_image_path.resolve(),
                output_path.parent.resolve(),
            )
        ).as_posix()

        relative_annotation_path = Path(
            os.path.relpath(
                source_annotation_path.resolve(),
                output_path.parent.resolve(),
            )
        ).as_posix()

        exported_records.append(
            {
                "sample_id": (record["sample_id"]),
                "source": record["source"],
                "source_group": (record["source_group"]),
                "split": record["split"],
                "image_path": (relative_image_path),
                "annotation_path": (relative_annotation_path),
                "image_width": (record["image_width"]),
                "image_height": (record["image_height"]),
                "person_count": (record["person_count"]),
                "scenario": (record["scenario"]),
                "license": (record["license"]),
                "consent_confirmed": (record["consent_confirmed"]),
                "image_sha256": (record["image_sha256"]),
                "label_format_version": (record["label_format_version"]),
            }
        )

    fieldnames = [
        "sample_id",
        "source",
        "source_group",
        "split",
        "image_path",
        "annotation_path",
        "image_width",
        "image_height",
        "person_count",
        "scenario",
        "license",
        "consent_confirmed",
        "image_sha256",
        "label_format_version",
    ]

    with output_path.open(
        mode="w",
        encoding="utf-8",
        newline="",
    ) as stream:
        writer = csv.DictWriter(
            stream,
            fieldnames=fieldnames,
            lineterminator="\n",
        )
        writer.writeheader()
        writer.writerows(exported_records)

    return output_path
