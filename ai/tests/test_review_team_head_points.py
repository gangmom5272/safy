from pathlib import Path

from scripts.review_team_head_points import (
    load_pending_review_items,
)


def test_load_pending_review_items_selects_low_density_samples(
    tmp_path: Path,
) -> None:
    manifest_path = tmp_path / "review-manifest.csv"
    manifest_path.write_text(
        (
            "sample_id,scenario,image_path,"
            "annotation_path,image_width,"
            "image_height,review_status\n"
            "empty-1,EMPTY,images/empty.jpg,"
            "annotations/empty.points.csv,"
            "640,480,PENDING_REVIEW\n"
            "sparse-1,SPARSE,images/sparse.jpg,"
            "annotations/sparse.points.csv,"
            "640,480,PENDING_REVIEW\n"
            "normal-1,NORMAL,images/normal.jpg,"
            "annotations/normal.points.csv,"
            "640,480,PENDING_REVIEW\n"
            "sparse-approved,SPARSE,"
            "images/approved.jpg,"
            "annotations/approved.points.csv,"
            "640,480,APPROVED\n"
        ),
        encoding="utf-8",
    )

    items = load_pending_review_items(
        review_manifest_path=manifest_path,
        scenarios={
            "EMPTY",
            "SPARSE",
        },
    )

    assert [item.sample_id for item in items] == [
        "empty-1",
        "sparse-1",
    ]

    assert items[0].image_path == (tmp_path / "images" / "empty.jpg")
    assert items[0].annotation_path == (tmp_path / "annotations" / "empty.points.csv")
    assert items[0].image_width == 640
    assert items[0].image_height == 480
