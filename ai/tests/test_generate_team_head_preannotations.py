import hashlib
from pathlib import Path

from PIL import Image

from app.services.p2pnet_benchmark_adapter import (
    P2PNetDetectionResult,
)
from scripts import (
    generate_team_head_preannotations,
)


def test_run_generation_creates_review_manifest(
    tmp_path: Path,
    monkeypatch,
) -> None:
    image_path = tmp_path / "team-photo.jpg"

    Image.new(
        "RGB",
        (640, 480),
    ).save(image_path)

    image_sha256 = hashlib.sha256(image_path.read_bytes()).hexdigest()

    candidate_manifest_path = tmp_path / "team-candidates.csv"
    candidate_manifest_path.write_text(
        (
            "source,consent_confirmed,sample_id,"
            "selected_path,sha256,manual_scenario,"
            "frame_width,frame_height,label_status\n"
            "TEAM_CONSENTED,True,team-0001,"
            f"{image_path.as_posix()},"
            f"{image_sha256},"
            "LOCAL_DENSE,640,480,UNLABELED\n"
        ),
        encoding="utf-8",
    )

    def fake_detector(
        image_bytes: bytes,
    ) -> P2PNetDetectionResult:
        return P2PNetDetectionResult(
            person_count=1,
            confidences=[0.9],
            person_centers=[
                (0.5, 0.25),
            ],
            processing_time_ms=10,
        )

    monkeypatch.setattr(
        generate_team_head_preannotations,
        "load_p2pnet_detector",
        lambda **kwargs: fake_detector,
    )

    output_root = tmp_path / "output"

    review_manifest_path = generate_team_head_preannotations.run_generation(
        manifest_path=(candidate_manifest_path),
        p2pnet_root=(tmp_path / "p2pnet"),
        weight_path=(tmp_path / "weights.pth"),
        output_root=output_root,
        device="cpu",
        threshold=0.5,
    )

    assert review_manifest_path == (output_root / "review-manifest.csv")
    assert review_manifest_path.exists()

    assert (output_root / "annotations" / "team-0001.points.csv").read_text(
        encoding="utf-8",
    ) == ("x,y\n320.0,120.0\n")
