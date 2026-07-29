import csv
import hashlib
from pathlib import Path

from PIL import Image

from app.services.head_point_dataset import (
    load_head_point_manifest,
)

from app.services.p2pnet_benchmark_adapter import (
    P2PNetDetectionResult,
)
from app.services.team_head_point_dataset import (
    TeamHeadPointCandidate,
    convert_normalized_head_points,
    generate_team_head_point_preannotations,
    load_team_head_point_candidates,
    approve_team_head_point_review,
    reclassify_approved_team_reviews,
    export_approved_team_head_point_manifest,
)


def test_convert_normalized_head_points_to_original_pixels() -> None:
    points = [
        (0.0, 0.0),
        (0.5, 0.25),
        (1.0, 1.0),
    ]

    converted_points = convert_normalized_head_points(
        points=points,
        image_width=640,
        image_height=480,
    )

    assert converted_points == [
        (0.0, 0.0),
        (320.0, 120.0),
        (639.0, 479.0),
    ]


def test_load_team_head_point_candidates(
    tmp_path: Path,
) -> None:
    image_directory = tmp_path / "images"
    image_directory.mkdir()

    image_path = image_directory / "team-photo.jpg"
    image_path.write_bytes(b"team-image")

    manifest_path = tmp_path / "team-candidates.csv"
    manifest_path.write_text(
        (
            "source,consent_confirmed,sample_id,"
            "selected_path,sha256,manual_scenario,"
            "frame_width,frame_height,label_status\n"
            "TEAM_CONSENTED,True,team-0001,"
            "images/team-photo.jpg,abc123,"
            "LOCAL_DENSE,640,480,UNLABELED\n"
        ),
        encoding="utf-8",
    )

    candidates = load_team_head_point_candidates(manifest_path)

    assert candidates == [
        TeamHeadPointCandidate(
            sample_id="team-0001",
            image_path=image_path,
            image_width=640,
            image_height=480,
            image_sha256="abc123",
            scenario="LOCAL_DENSE",
            consent_confirmed=True,
            label_status="UNLABELED",
        )
    ]


def test_generate_team_head_point_preannotations(
    tmp_path: Path,
) -> None:
    image_path = tmp_path / "team-photo.jpg"

    Image.new(
        "RGB",
        (640, 480),
    ).save(image_path)

    image_sha256 = hashlib.sha256(image_path.read_bytes()).hexdigest()

    candidate = TeamHeadPointCandidate(
        sample_id="team-0001",
        image_path=image_path,
        image_width=640,
        image_height=480,
        image_sha256=image_sha256,
        scenario="LOCAL_DENSE",
        consent_confirmed=True,
        label_status="UNLABELED",
    )

    def fake_detector(
        image_bytes: bytes,
    ) -> P2PNetDetectionResult:
        assert image_bytes == (image_path.read_bytes())

        return P2PNetDetectionResult(
            person_count=2,
            confidences=[0.9, 0.8],
            person_centers=[
                (0.5, 0.25),
                (1.0, 1.0),
            ],
            processing_time_ms=10,
        )

    output_root = tmp_path / "output"

    review_manifest_path = generate_team_head_point_preannotations(
        candidates=[candidate],
        output_root=output_root,
        detector=fake_detector,
    )

    annotation_path = output_root / "annotations" / "team-0001.points.csv"

    assert annotation_path.read_text(
        encoding="utf-8",
    ) == ("x,y\n320.0,120.0\n639.0,479.0\n")

    with review_manifest_path.open(
        encoding="utf-8",
        newline="",
    ) as stream:
        records = list(csv.DictReader(stream))

    assert len(records) == 1

    record = records[0]

    assert record["sample_id"] == "team-0001"
    assert record["source"] == "TEAM_CONSENTED"
    assert record["source_group"] == "team-0001"
    assert record["split"] == "TRAIN"
    assert record["image_width"] == "640"
    assert record["image_height"] == "480"
    assert record["person_count"] == "2"
    assert record["scenario"] == "LOCAL_DENSE"
    assert record["license"] == ("team-consented-internal")
    assert record["consent_confirmed"] == "true"
    assert record["image_sha256"] == image_sha256
    assert record["label_format_version"] == ("head-point-v1")
    assert record["review_status"] == ("PENDING_REVIEW")
    assert record["preannotation_method"] == ("P2PNET")

    manifest_image_path = (review_manifest_path.parent / record["image_path"]).resolve()
    manifest_annotation_path = (
        review_manifest_path.parent / record["annotation_path"]
    ).resolve()

    assert manifest_image_path == (image_path.resolve())
    assert manifest_annotation_path == (annotation_path.resolve())


def test_generate_team_preannotations_skips_p2pnet_for_empty_and_sparse(
    tmp_path: Path,
) -> None:
    candidates = []

    for (
        sample_id,
        scenario,
    ) in (
        (
            "team-empty",
            "EMPTY",
        ),
        (
            "team-sparse",
            "SPARSE",
        ),
    ):
        image_path = tmp_path / f"{sample_id}.jpg"

        Image.new(
            "RGB",
            (640, 480),
        ).save(image_path)

        image_sha256 = hashlib.sha256(image_path.read_bytes()).hexdigest()

        candidates.append(
            TeamHeadPointCandidate(
                sample_id=sample_id,
                image_path=image_path,
                image_width=640,
                image_height=480,
                image_sha256=image_sha256,
                scenario=scenario,
                consent_confirmed=True,
                label_status="UNLABELED",
            )
        )

    def unexpected_detector(
        image_bytes: bytes,
    ) -> P2PNetDetectionResult:
        raise AssertionError("EMPTY와 SPARSE에는 P2PNet을 실행하면 안 됩니다.")

    output_root = tmp_path / "output"

    review_manifest_path = generate_team_head_point_preannotations(
        candidates=candidates,
        output_root=output_root,
        detector=unexpected_detector,
    )

    assert (output_root / "annotations" / "team-empty.points.csv").read_text(
        encoding="utf-8",
    ) == "x,y\n"

    assert (output_root / "annotations" / "team-sparse.points.csv").read_text(
        encoding="utf-8",
    ) == "x,y\n"

    with review_manifest_path.open(
        encoding="utf-8",
        newline="",
    ) as stream:
        records = list(csv.DictReader(stream))

    assert [record["preannotation_method"] for record in records] == [
        "EMPTY_BASELINE",
        "MANUAL_REQUIRED",
    ]

    assert [record["person_count"] for record in records] == [
        "0",
        "0",
    ]


def test_approve_team_head_point_review_updates_annotation_and_manifest(
    tmp_path: Path,
) -> None:
    annotation_directory = tmp_path / "annotations"
    annotation_directory.mkdir()

    annotation_path = annotation_directory / "team-0001.points.csv"
    annotation_path.write_text(
        "x,y\n",
        encoding="utf-8",
    )

    review_manifest_path = tmp_path / "review-manifest.csv"
    review_manifest_path.write_text(
        (
            "sample_id,source,source_group,split,"
            "image_path,annotation_path,"
            "image_width,image_height,"
            "person_count,scenario,license,"
            "consent_confirmed,image_sha256,"
            "label_format_version,"
            "preannotation_method,"
            "review_status\n"
            "team-0001,TEAM_CONSENTED,"
            "team-0001,TRAIN,"
            "../team-photo.jpg,"
            "annotations/team-0001.points.csv,"
            "640,480,0,SPARSE,"
            "team-consented-internal,true,"
            "abc123,head-point-v1,"
            "MANUAL_REQUIRED,PENDING_REVIEW\n"
        ),
        encoding="utf-8",
    )

    approve_team_head_point_review(
        review_manifest_path=(review_manifest_path),
        sample_id="team-0001",
        points=[
            (100.0, 120.0),
            (320.0, 240.0),
        ],
    )

    assert annotation_path.read_text(
        encoding="utf-8",
    ) == ("x,y\n100.0,120.0\n320.0,240.0\n")

    with review_manifest_path.open(
        encoding="utf-8",
        newline="",
    ) as stream:
        records = list(csv.DictReader(stream))

    assert records[0]["person_count"] == "2"
    assert records[0]["review_status"] == ("APPROVED")


def test_reclassify_approved_team_reviews_uses_reviewed_points(
    tmp_path: Path,
) -> None:
    annotation_directory = tmp_path / "annotations"
    annotation_directory.mkdir()

    empty_annotation_path = annotation_directory / "empty.points.csv"
    empty_annotation_path.write_text(
        "x,y\n",
        encoding="utf-8",
    )

    dense_annotation_path = annotation_directory / "dense.points.csv"
    dense_annotation_path.write_text(
        (
            "x,y\n"
            "100.0,100.0\n"
            "105.0,105.0\n"
            "110.0,110.0\n"
            "115.0,115.0\n"
            "120.0,120.0\n"
            "125.0,125.0\n"
        ),
        encoding="utf-8",
    )

    manifest_path = tmp_path / "review-manifest.csv"
    manifest_path.write_text(
        (
            "sample_id,scenario,annotation_path,"
            "image_width,image_height,"
            "person_count,review_status\n"
            "team-empty,SPARSE,"
            "annotations/empty.points.csv,"
            "640,480,0,APPROVED\n"
            "team-dense,SPARSE,"
            "annotations/dense.points.csv,"
            "640,480,6,APPROVED\n"
        ),
        encoding="utf-8",
    )

    updated_count = reclassify_approved_team_reviews(
        review_manifest_path=(manifest_path)
    )

    assert updated_count == 2

    with manifest_path.open(
        encoding="utf-8",
        newline="",
    ) as stream:
        records = list(csv.DictReader(stream))

    assert [record["scenario"] for record in records] == [
        "EMPTY",
        "LOCAL_DENSE",
    ]


def test_export_approved_team_head_point_manifest_excludes_pending(
    tmp_path: Path,
) -> None:
    image_directory = tmp_path / "images"
    annotation_directory = tmp_path / "annotations"
    image_directory.mkdir()
    annotation_directory.mkdir()

    approved_image_path = image_directory / "approved.jpg"
    pending_image_path = image_directory / "pending.jpg"

    Image.new(
        "RGB",
        (640, 480),
    ).save(approved_image_path)
    Image.new(
        "RGB",
        (640, 480),
    ).save(pending_image_path)

    approved_hash = hashlib.sha256(approved_image_path.read_bytes()).hexdigest()
    pending_hash = hashlib.sha256(pending_image_path.read_bytes()).hexdigest()

    (annotation_directory / "approved.points.csv").write_text(
        ("x,y\n100.0,120.0\n"),
        encoding="utf-8",
    )
    (annotation_directory / "pending.points.csv").write_text(
        "x,y\n",
        encoding="utf-8",
    )

    review_manifest_path = tmp_path / "review-manifest.csv"
    review_manifest_path.write_text(
        (
            "sample_id,source,source_group,split,"
            "image_path,annotation_path,"
            "image_width,image_height,"
            "person_count,scenario,license,"
            "consent_confirmed,image_sha256,"
            "label_format_version,"
            "preannotation_method,"
            "review_status\n"
            "team-approved,TEAM_CONSENTED,"
            "team-approved,TRAIN,"
            "images/approved.jpg,"
            "annotations/approved.points.csv,"
            "640,480,1,SPARSE,"
            "team-consented-internal,true,"
            f"{approved_hash},head-point-v1,"
            "MANUAL_REQUIRED,APPROVED\n"
            "team-pending,TEAM_CONSENTED,"
            "team-pending,TRAIN,"
            "images/pending.jpg,"
            "annotations/pending.points.csv,"
            "640,480,0,EMPTY,"
            "team-consented-internal,true,"
            f"{pending_hash},head-point-v1,"
            "P2PNET,PENDING_REVIEW\n"
        ),
        encoding="utf-8",
    )

    output_path = tmp_path / "approved-manifest.csv"

    exported_path = export_approved_team_head_point_manifest(
        review_manifest_path=(review_manifest_path),
        output_path=output_path,
    )

    assert exported_path == output_path

    samples = load_head_point_manifest(output_path)

    assert len(samples) == 1
    assert samples[0].sample_id == ("team-approved")
    assert samples[0].person_count == 1
    assert samples[0].scenario == "SPARSE"
