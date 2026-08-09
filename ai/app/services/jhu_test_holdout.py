import json
import random
from dataclasses import dataclass
from pathlib import Path

from app.services.head_point_dataset import (
    HeadPointSample,
    load_head_point_manifest,
    validate_head_point_dataset,
)
from app.services.p2pnet_finetune_split import save_p2pnet_finetune_manifest


TEST_SPLIT = "TEST"

TRAINING_SCRIPT_RELATIVE_PATHS = [
    "scripts/train_p2pnet_finetune.py",
    "scripts/compare_p2pnet_finetune_checkpoints.py",
]

DEFAULT_QUALITATIVE_SUBSET_COUNTS = {
    "CROWDED": 25,
    "LOCAL_DENSE": 10,
    "NORMAL": 10,
    "SPARSE": 20,
    "EMPTY": 4,
}

EXCLUDED_SCAN_PATH_PREFIXES = (
    "datasets/",
    "candidate-models/",
)


@dataclass(frozen=True)
class IndependenceReport:
    manifest_path: str
    total_test_samples: int
    scenario_counts: dict[str, int]
    structural_check_passed: bool
    structural_check_error: str | None
    training_script_test_split_usage: dict[str, bool]
    external_reference_matches: dict[str, list[str]]
    scanned_file_count: int

    @property
    def is_independent(self) -> bool:
        return (
            self.structural_check_passed
            and not any(self.training_script_test_split_usage.values())
            and not any(self.external_reference_matches.values())
        )


def load_test_split_samples(
    manifest_path: Path,
) -> list[HeadPointSample]:
    samples = load_head_point_manifest(manifest_path)

    return [sample for sample in samples if sample.split == TEST_SPLIT]


def check_structural_independence(
    manifest_path: Path,
) -> tuple[bool, str | None]:
    try:
        validate_head_point_dataset(manifest_path)
    except ValueError as error:
        return False, str(error)

    return True, None


def scan_training_scripts_for_test_split(
    *,
    repo_root: Path,
    script_relative_paths: list[str] = TRAINING_SCRIPT_RELATIVE_PATHS,
) -> dict[str, bool]:
    usage: dict[str, bool] = {}

    for relative_path in script_relative_paths:
        script_path = repo_root / relative_path
        text = script_path.read_text(encoding="utf-8")

        usage[relative_path] = 'split="TEST"' in text or "split='TEST'" in text

    return usage


def _is_excluded_scan_path(
    relative_path: str,
) -> bool:
    return any(
        relative_path.startswith(prefix) for prefix in EXCLUDED_SCAN_PATH_PREFIXES
    )


def collect_test_identifiers(
    test_samples: list[HeadPointSample],
) -> tuple[set[str], set[str]]:
    sample_ids = {sample.sample_id for sample in test_samples}
    image_sha256_hexes = {sample.image_sha256.lower() for sample in test_samples}

    return sample_ids, image_sha256_hexes


def scan_files_for_test_identifiers(
    *,
    runs_root: Path,
    sample_ids: set[str],
    image_sha256_hexes: set[str],
    excluded_paths: set[Path] = frozenset(),
) -> tuple[dict[str, list[str]], int]:
    matches: dict[str, list[str]] = {}
    scanned_file_count = 0

    candidate_files = sorted(runs_root.rglob("*.csv")) + sorted(
        runs_root.rglob("*.json")
    )

    for file_path in candidate_files:
        if file_path in excluded_paths:
            continue

        relative_path = file_path.relative_to(runs_root).as_posix()

        if _is_excluded_scan_path(relative_path):
            continue

        scanned_file_count += 1
        text = file_path.read_text(encoding="utf-8", errors="ignore").lower()

        file_matches = [
            identifier for identifier in sample_ids if identifier.lower() in text
        ] + [sha256_hex for sha256_hex in image_sha256_hexes if sha256_hex in text]

        if file_matches:
            matches[relative_path] = sorted(set(file_matches))

    return matches, scanned_file_count


def build_independence_report(
    *,
    manifest_path: Path,
    repo_root: Path,
    runs_root: Path,
    excluded_scan_paths: set[Path] = frozenset(),
) -> IndependenceReport:
    test_samples = load_test_split_samples(manifest_path)

    if not test_samples:
        raise ValueError("TEST 분할 표본이 한 건 이상 필요합니다.")

    structural_check_passed, structural_check_error = check_structural_independence(
        manifest_path,
    )

    training_script_usage = scan_training_scripts_for_test_split(
        repo_root=repo_root,
    )

    sample_ids, image_sha256_hexes = collect_test_identifiers(test_samples)

    external_reference_matches, scanned_file_count = scan_files_for_test_identifiers(
        runs_root=runs_root,
        sample_ids=sample_ids,
        image_sha256_hexes=image_sha256_hexes,
        excluded_paths=excluded_scan_paths,
    )

    scenario_counts: dict[str, int] = {}

    for sample in test_samples:
        scenario_counts[sample.scenario] = scenario_counts.get(sample.scenario, 0) + 1

    return IndependenceReport(
        manifest_path=manifest_path.as_posix(),
        total_test_samples=len(test_samples),
        scenario_counts=dict(sorted(scenario_counts.items())),
        structural_check_passed=structural_check_passed,
        structural_check_error=structural_check_error,
        training_script_test_split_usage=training_script_usage,
        external_reference_matches=external_reference_matches,
        scanned_file_count=scanned_file_count,
    )


def save_independence_report(
    report: IndependenceReport,
    output_path: Path,
) -> Path:
    output_path.parent.mkdir(parents=True, exist_ok=True)

    payload = {
        "manifestPath": report.manifest_path,
        "totalTestSamples": report.total_test_samples,
        "scenarioCounts": report.scenario_counts,
        "structuralCheckPassed": report.structural_check_passed,
        "structuralCheckError": report.structural_check_error,
        "trainingScriptTestSplitUsage": report.training_script_test_split_usage,
        "externalReferenceMatches": report.external_reference_matches,
        "scannedFileCount": report.scanned_file_count,
        "isIndependent": report.is_independent,
    }

    output_path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    return output_path


def build_test_holdout_manifest(
    *,
    source_manifest_path: Path,
    output_path: Path,
) -> Path:
    test_samples = load_test_split_samples(source_manifest_path)

    if not test_samples:
        raise ValueError("TEST 분할 표본이 한 건 이상 필요합니다.")

    return save_p2pnet_finetune_manifest(
        samples=test_samples,
        output_path=output_path,
    )


def select_balanced_qualitative_subset(
    samples: list[HeadPointSample],
    *,
    counts_per_scenario: dict[str, int] = DEFAULT_QUALITATIVE_SUBSET_COUNTS,
    random_seed: int = 42,
) -> list[HeadPointSample]:
    if not samples:
        raise ValueError("선택할 표본이 한 건 이상 필요합니다.")

    samples_by_scenario: dict[str, list[HeadPointSample]] = {}

    for sample in samples:
        samples_by_scenario.setdefault(sample.scenario, []).append(sample)

    random_generator = random.Random(random_seed)
    selected: list[HeadPointSample] = []

    for scenario in sorted(counts_per_scenario):
        requested_count = counts_per_scenario[scenario]
        scenario_samples = sorted(
            samples_by_scenario.get(scenario, []),
            key=lambda sample: sample.sample_id,
        )
        random_generator.shuffle(scenario_samples)

        selected.extend(scenario_samples[: min(requested_count, len(scenario_samples))])

    return selected
