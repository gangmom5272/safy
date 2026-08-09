from pathlib import Path

from scripts import (
    build_jhu_head_point_dataset,
)


def test_run_build_returns_manifest_path(
    monkeypatch,
    capsys,
    tmp_path: Path,
) -> None:
    source_root = tmp_path / "source"
    output_root = tmp_path / "output"
    expected_manifest_path = output_root / "manifest.csv"

    monkeypatch.setattr(
        build_jhu_head_point_dataset,
        "build_jhu_head_point_dataset",
        lambda **_: expected_manifest_path,
    )

    manifest_path = build_jhu_head_point_dataset.run_build(
        source_root=source_root,
        output_root=output_root,
    )

    output = capsys.readouterr().out

    assert manifest_path == expected_manifest_path
    assert f"manifest: {expected_manifest_path}" in output
