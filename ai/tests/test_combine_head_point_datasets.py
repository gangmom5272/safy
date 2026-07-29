import hashlib
from pathlib import Path

from PIL import Image

from app.services.head_point_dataset import (
    combine_head_point_manifests,
    load_head_point_manifest,
)


def _create_manifest(
    *,
    root: Path,
    sample_id: str,
    source: str,
    consent_confirmed: bool,
) -> Path:
    root.mkdir(parents=True)

    image_path = root / f"{sample_id}.jpg"
    annotation_path = root / f"{sample_id}.points.csv"

    Image.new(
        "RGB",
        (640, 480),
    ).save(image_path)

    annotation_path.write_text(
        ("x,y\n100.0,120.0\n"),
        encoding="utf-8",
    )

    image_sha256 = hashlib.sha256(image_path.read_bytes()).hexdigest()

    manifest_path = root / "manifest.csv"
    manifest_path.write_text(
        (
            "sample_id,source,source_group,split,"
            "image_path,annotation_path,"
            "image_width,image_height,"
            "person_count,scenario,license,"
            "consent_confirmed,image_sha256,"
            "label_format_version\n"
            f"{sample_id},{source},{sample_id},"
            "TRAIN,"
            f"{image_path.name},"
            f"{annotation_path.name},"
            "640,480,1,SPARSE,internal,"
            f"{str(consent_confirmed).lower()},"
            f"{image_sha256},head-point-v1\n"
        ),
        encoding="utf-8",
    )

    return manifest_path


def test_combine_head_point_manifests_rebases_paths(
    tmp_path: Path,
) -> None:
    jhu_manifest_path = _create_manifest(
        root=tmp_path / "jhu",
        sample_id="jhu-0001",
        source="JHU-CROWD++ v2.0",
        consent_confirmed=False,
    )
    team_manifest_path = _create_manifest(
        root=tmp_path / "team",
        sample_id="team-0001",
        source="TEAM_CONSENTED",
        consent_confirmed=True,
    )

    output_path = tmp_path / "combined" / "manifest.csv"

    combined_path = combine_head_point_manifests(
        manifest_paths=[
            jhu_manifest_path,
            team_manifest_path,
        ],
        output_path=output_path,
    )

    assert combined_path == output_path

    samples = load_head_point_manifest(combined_path)

    assert [sample.sample_id for sample in samples] == [
        "jhu-0001",
        "team-0001",
    ]

    assert (
        samples[0].image_path.resolve() == (tmp_path / "jhu" / "jhu-0001.jpg").resolve()
    )

    assert (
        samples[1].annotation_path.resolve()
        == (tmp_path / "team" / "team-0001.points.csv").resolve()
    )
