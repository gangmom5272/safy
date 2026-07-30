import csv
import hashlib
import json
from pathlib import Path

from PIL import Image

from app.services.jhu_test_holdout import (
    build_independence_report,
    build_test_holdout_manifest,
    check_structural_independence,
    load_test_split_samples,
    save_independence_report,
    scan_files_for_test_identifiers,
    scan_training_scripts_for_test_split,
    select_balanced_qualitative_subset,
)


MANIFEST_FIELDNAMES = [
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


def _write_image_and_annotation(
    tmp_path: Path,
    *,
    name: str,
    points: list[tuple[float, float]],
    image_width: int = 640,
    image_height: int = 480,
) -> tuple[Path, Path, str]:
    image_path = tmp_path / "images" / f"{name}.jpg"
    annotation_path = tmp_path / "annotations" / f"{name}.points.csv"
    image_path.parent.mkdir(parents=True, exist_ok=True)
    annotation_path.parent.mkdir(parents=True, exist_ok=True)

    unique_color = (
        int(hashlib.sha1(name.encode()).hexdigest()[:2], 16),
        int(hashlib.sha1(name.encode()).hexdigest()[2:4], 16),
        int(hashlib.sha1(name.encode()).hexdigest()[4:6], 16),
    )
    Image.new("RGB", (image_width, image_height), color=unique_color).save(image_path)

    with annotation_path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(["x", "y"])
        for point_x, point_y in points:
            writer.writerow([point_x, point_y])

    image_sha256 = hashlib.sha256(image_path.read_bytes()).hexdigest()

    return image_path, annotation_path, image_sha256


def _build_manifest(
    tmp_path: Path,
    rows: list[dict],
) -> Path:
    manifest_path = tmp_path / "manifest.csv"

    with manifest_path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=MANIFEST_FIELDNAMES)
        writer.writeheader()
        writer.writerows(rows)

    return manifest_path


def _sample_row(
    tmp_path: Path,
    *,
    sample_id: str,
    source: str,
    source_group: str,
    split: str,
    scenario: str,
    person_count: int,
) -> dict:
    points = [(10.0 + index, 10.0 + index) for index in range(person_count)]
    image_path, annotation_path, image_sha256 = _write_image_and_annotation(
        tmp_path,
        name=sample_id,
        points=points,
    )

    return {
        "sample_id": sample_id,
        "source": source,
        "source_group": source_group,
        "split": split,
        "image_path": image_path.relative_to(tmp_path).as_posix(),
        "annotation_path": annotation_path.relative_to(tmp_path).as_posix(),
        "image_width": 640,
        "image_height": 480,
        "person_count": person_count,
        "scenario": scenario,
        "license": "jhu-crowd-noncommercial",
        "consent_confirmed": "false",
        "image_sha256": image_sha256,
        "label_format_version": "head-point-v1",
    }


def _build_clean_manifest(tmp_path: Path) -> Path:
    rows = [
        _sample_row(
            tmp_path,
            sample_id="jhu-train-1",
            source="JHU-CROWD++ v2.0",
            source_group="jhu-train-1",
            split="TRAIN",
            scenario="NORMAL",
            person_count=8,
        ),
        _sample_row(
            tmp_path,
            sample_id="jhu-val-1",
            source="JHU-CROWD++ v2.0",
            source_group="jhu-val-1",
            split="VALIDATION",
            scenario="NORMAL",
            person_count=8,
        ),
        _sample_row(
            tmp_path,
            sample_id="jhu-test-1",
            source="JHU-CROWD++ v2.0",
            source_group="jhu-test-1",
            split="TEST",
            scenario="CROWDED",
            person_count=60,
        ),
        _sample_row(
            tmp_path,
            sample_id="jhu-test-2",
            source="JHU-CROWD++ v2.0",
            source_group="jhu-test-2",
            split="TEST",
            scenario="SPARSE",
            person_count=2,
        ),
    ]

    return _build_manifest(tmp_path, rows)


def test_load_test_split_samples_filters_by_split(tmp_path: Path) -> None:
    manifest_path = _build_clean_manifest(tmp_path)

    test_samples = load_test_split_samples(manifest_path)

    assert {sample.sample_id for sample in test_samples} == {
        "jhu-test-1",
        "jhu-test-2",
    }
    assert all(sample.split == "TEST" for sample in test_samples)


def test_check_structural_independence_passes_for_clean_manifest(
    tmp_path: Path,
) -> None:
    manifest_path = _build_clean_manifest(tmp_path)

    passed, error = check_structural_independence(manifest_path)

    assert passed is True
    assert error is None


def test_check_structural_independence_detects_image_leakage(
    tmp_path: Path,
) -> None:
    manifest_path = _build_clean_manifest(tmp_path)
    rows = list(csv.DictReader(manifest_path.open(encoding="utf-8")))

    leaking_row = dict(rows[0])
    leaking_row["sample_id"] = "jhu-test-leak"
    leaking_row["split"] = "TEST"
    rows.append(leaking_row)

    leaking_manifest_path = tmp_path / "leaking-manifest.csv"
    with leaking_manifest_path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=MANIFEST_FIELDNAMES)
        writer.writeheader()
        writer.writerows(rows)

    passed, error = check_structural_independence(leaking_manifest_path)

    assert passed is False
    assert "여러 데이터 분할" in error


def test_scan_training_scripts_for_test_split_reports_false_for_real_scripts(
    tmp_path: Path,
) -> None:
    repo_root = Path(__file__).resolve().parents[1]

    usage = scan_training_scripts_for_test_split(repo_root=repo_root)

    assert usage == {
        "scripts/train_p2pnet_finetune.py": False,
        "scripts/compare_p2pnet_finetune_checkpoints.py": False,
    }


def test_scan_training_scripts_for_test_split_detects_test_usage(
    tmp_path: Path,
) -> None:
    script_path = tmp_path / "scripts" / "leaky_train.py"
    script_path.parent.mkdir(parents=True)
    script_path.write_text(
        'dataset = P2PNetManifestDataset(manifest_path=manifest_path, split="TEST")\n',
        encoding="utf-8",
    )

    usage = scan_training_scripts_for_test_split(
        repo_root=tmp_path,
        script_relative_paths=["scripts/leaky_train.py"],
    )

    assert usage == {"scripts/leaky_train.py": True}


def test_scan_files_for_test_identifiers_finds_matches(tmp_path: Path) -> None:
    runs_root = tmp_path / "runs"
    evaluation_dir = runs_root / "evaluation"
    evaluation_dir.mkdir(parents=True)

    leaking_report = evaluation_dir / "some-report.json"
    leaking_report.write_text(
        json.dumps({"sampleId": "jhu-test-1"}),
        encoding="utf-8",
    )

    clean_report = evaluation_dir / "unrelated-report.json"
    clean_report.write_text(
        json.dumps({"sampleId": "coco-000000000009"}),
        encoding="utf-8",
    )

    excluded_dataset_manifest = runs_root / "datasets" / "manifest.csv"
    excluded_dataset_manifest.parent.mkdir(parents=True)
    excluded_dataset_manifest.write_text("jhu-test-1\n", encoding="utf-8")

    matches, scanned_file_count = scan_files_for_test_identifiers(
        runs_root=runs_root,
        sample_ids={"jhu-test-1"},
        image_sha256_hexes=set(),
    )

    assert matches == {"evaluation/some-report.json": ["jhu-test-1"]}
    assert scanned_file_count == 2


def test_scan_files_for_test_identifiers_respects_excluded_paths(
    tmp_path: Path,
) -> None:
    runs_root = tmp_path / "runs"
    evaluation_dir = runs_root / "evaluation"
    evaluation_dir.mkdir(parents=True)

    holdout_manifest = evaluation_dir / "jhu-test-holdout-manifest.csv"
    holdout_manifest.write_text("jhu-test-1\n", encoding="utf-8")

    matches, scanned_file_count = scan_files_for_test_identifiers(
        runs_root=runs_root,
        sample_ids={"jhu-test-1"},
        image_sha256_hexes=set(),
        excluded_paths={holdout_manifest},
    )

    assert matches == {}
    assert scanned_file_count == 0


def test_build_independence_report_is_independent_for_clean_manifest(
    tmp_path: Path,
) -> None:
    manifest_path = _build_clean_manifest(tmp_path)
    runs_root = tmp_path / "runs"
    (runs_root / "evaluation").mkdir(parents=True)

    repo_root = Path(__file__).resolve().parents[1]

    report = build_independence_report(
        manifest_path=manifest_path,
        repo_root=repo_root,
        runs_root=runs_root,
    )

    assert report.total_test_samples == 2
    assert report.scenario_counts == {"CROWDED": 1, "SPARSE": 1}
    assert report.structural_check_passed is True
    assert report.is_independent is True


def test_save_independence_report_writes_json(tmp_path: Path) -> None:
    manifest_path = _build_clean_manifest(tmp_path)
    runs_root = tmp_path / "runs"
    (runs_root / "evaluation").mkdir(parents=True)
    repo_root = Path(__file__).resolve().parents[1]

    report = build_independence_report(
        manifest_path=manifest_path,
        repo_root=repo_root,
        runs_root=runs_root,
    )

    output_path = tmp_path / "report.json"
    save_independence_report(report, output_path)

    payload = json.loads(output_path.read_text(encoding="utf-8"))

    assert payload["isIndependent"] is True
    assert payload["totalTestSamples"] == 2


def test_build_test_holdout_manifest_contains_only_test_split(
    tmp_path: Path,
) -> None:
    manifest_path = _build_clean_manifest(tmp_path)
    output_path = tmp_path / "holdout" / "manifest.csv"

    build_test_holdout_manifest(
        source_manifest_path=manifest_path,
        output_path=output_path,
    )

    rows = list(csv.DictReader(output_path.open(encoding="utf-8")))

    assert {row["sample_id"] for row in rows} == {"jhu-test-1", "jhu-test-2"}
    assert all(row["split"] == "TEST" for row in rows)


def test_select_balanced_qualitative_subset_is_deterministic(
    tmp_path: Path,
) -> None:
    rows = [
        _sample_row(
            tmp_path,
            sample_id=f"jhu-test-crowded-{index}",
            source="JHU-CROWD++ v2.0",
            source_group=f"jhu-test-crowded-{index}",
            split="TEST",
            scenario="CROWDED",
            person_count=60 + index,
        )
        for index in range(5)
    ]
    manifest_path = _build_manifest(tmp_path, rows)
    samples = load_test_split_samples(manifest_path)

    first_selection = select_balanced_qualitative_subset(
        samples,
        counts_per_scenario={"CROWDED": 3},
        random_seed=42,
    )
    second_selection = select_balanced_qualitative_subset(
        samples,
        counts_per_scenario={"CROWDED": 3},
        random_seed=42,
    )

    assert [sample.sample_id for sample in first_selection] == [
        sample.sample_id for sample in second_selection
    ]
    assert len(first_selection) == 3


def test_select_balanced_qualitative_subset_caps_at_available_count(
    tmp_path: Path,
) -> None:
    rows = [
        _sample_row(
            tmp_path,
            sample_id="jhu-test-empty-1",
            source="JHU-CROWD++ v2.0",
            source_group="jhu-test-empty-1",
            split="TEST",
            scenario="EMPTY",
            person_count=0,
        ),
    ]
    manifest_path = _build_manifest(tmp_path, rows)
    samples = load_test_split_samples(manifest_path)

    selection = select_balanced_qualitative_subset(
        samples,
        counts_per_scenario={"EMPTY": 4},
        random_seed=42,
    )

    assert len(selection) == 1


def test_select_balanced_qualitative_subset_does_not_require_predictions(
    tmp_path: Path,
) -> None:
    rows = [
        _sample_row(
            tmp_path,
            sample_id="jhu-test-sparse-1",
            source="JHU-CROWD++ v2.0",
            source_group="jhu-test-sparse-1",
            split="TEST",
            scenario="SPARSE",
            person_count=2,
        ),
    ]
    manifest_path = _build_manifest(tmp_path, rows)
    samples = load_test_split_samples(manifest_path)

    selection = select_balanced_qualitative_subset(
        samples,
        counts_per_scenario={"SPARSE": 1},
        random_seed=42,
    )

    assert selection[0].sample_id == "jhu-test-sparse-1"
