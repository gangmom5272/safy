import json
import platform
from pathlib import Path

import torch

from scripts.benchmark_frame_runtime import (
    run_benchmark,
)


class FakeResponse:
    def raise_for_status(self) -> None:
        return None

    def json(self) -> dict[str, object]:
        return {
            "analysisStatus": "COMPLETED",
        }


class FakeClient:
    def __init__(self) -> None:
        self.request_count = 0

    def post(
        self,
        url: str,
        *,
        files: dict[str, object],
    ) -> FakeResponse:
        self.request_count += 1
        return FakeResponse()


def test_run_benchmark_saves_single_and_continuous_results(
    tmp_path: Path,
) -> None:
    image_directory = tmp_path / "images"
    image_directory.mkdir()

    first_image = image_directory / "first.jpg"
    second_image = image_directory / "second.jpg"
    first_image.write_bytes(b"first-image")
    second_image.write_bytes(b"second-image")

    manifest_path = tmp_path / "manifest.csv"
    manifest_path.write_text(
        (
            "sample_id,scenario,image_path,"
            "ground_truth_count,source,license\n"
            "first,NORMAL,images/first.jpg,"
            "1,test,internal\n"
            "second,NORMAL,images/second.jpg,"
            "1,test,internal\n"
        ),
        encoding="utf-8",
    )

    output_path = tmp_path / "report.json"
    client = FakeClient()

    run_benchmark(
        manifest_path=manifest_path,
        output_path=output_path,
        client=client,
        limit=None,
        warmup_iterations=1,
        repetitions_per_frame=2,
        device="cpu",
    )

    report = json.loads(output_path.read_text(encoding="utf-8"))

    assert report["model"] == {
        "name": "YOLO",
        "version": "yolo11n",
    }
    assert report["environment"]["device"] == "cpu"
    assert report["environment"]["warmupIterations"] == 1
    assert report["environment"]["pythonVersion"] == platform.python_version()
    assert report["environment"]["torchVersion"] == str(torch.__version__)
    assert report["environment"]["cudaRuntime"] == torch.version.cuda
    assert report["environment"]["device"] == "cpu"
    assert report["environment"]["warmupIterations"] == 1
    assert report["environment"]["pythonVersion"] == platform.python_version()
    assert report["environment"]["torchVersion"] == str(torch.__version__)
    assert report["environment"]["cudaRuntime"] == torch.version.cuda
    assert report["environment"]["gpuName"] is None
    assert report["environment"]["gpuTotalMemoryMb"] == 0.0
    assert report["singleRequest"]["requestCount"] == 1
    assert report["continuousFrames"]["requestCount"] == 4
    assert report["continuousFrames"]["completedCount"] == 4

    assert client.request_count == 6
