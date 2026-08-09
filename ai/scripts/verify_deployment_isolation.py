"""배포 전 장애 격리 실사용 검증 (`S15P11A503-141`).

실제 uvicorn 서버를 시나리오별로 기동해 P2PNet 자산 준비를 의도적으로
실패시키고, 다음을 확인한다.

1. `/health`가 200과 `status=ok`를 유지한다(재시작 루프 방지).
2. `/health`의 `p2pnetShadow.status`가 실패 원인을 정확히 보고한다.
3. `POST /api/v1/analyze/frame`이 YOLO 공식 결과를 정상 반환한다.
4. 응답에 내부 예외 메시지·자격증명·다운로드 URL이 노출되지 않는다.

단위 테스트가 아니라 실제 프로세스를 띄워서 확인하는 것이 목적이다.
컨테이너 수준 항목(이미지 빌드, 볼륨 캐시, non-root)은 Docker가 필요하며
이 스크립트 범위에 포함하지 않는다.
"""

import argparse
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path


AI_ROOT = Path(__file__).resolve().parents[1]

SECRET_MARKERS = [
    "X-Amz-Signature",
    "verify-isolation-secret-token",
    "X-Verify-Isolation-Header",
    "minio.example.invalid",
]


def _build_valid_source_root(base: Path) -> Path:
    """소스 검증을 통과하는 최소 구조를 만든다(매니페스트 커밋 사용)."""
    manifest = json.loads(
        (AI_ROOT / "deploy" / "p2pnet-assets.json").read_text(encoding="utf-8")
    )
    commit = manifest["source"]["commit"]

    root = base / "valid-source"
    (root / "models").mkdir(parents=True, exist_ok=True)
    (root / "util").mkdir(parents=True, exist_ok=True)
    (root / "models" / "__init__.py").write_text("", encoding="utf-8")
    (root / "util" / "misc.py").write_text("", encoding="utf-8")
    (root / "LICENSE").write_text("test", encoding="utf-8")
    (root / ".safy-source-revision").write_text(commit, encoding="utf-8")

    return root


def build_scenarios(base: Path) -> list[dict]:
    valid_source = _build_valid_source_root(base)
    missing_source = base / "missing-source"
    missing_weight = base / "missing-weight.pth"

    corrupt_weight = base / "corrupt-weight.pth"
    corrupt_weight.write_bytes(b"not a real checkpoint")

    invalid_manifest = base / "invalid-manifest.json"
    invalid_manifest.write_text('{"schemaVersion": 99}', encoding="utf-8")

    return [
        {
            "name": "SHADOW_DISABLED",
            "description": "기본값(비활성화). P2PNet을 아예 로드하지 않는다.",
            "expectedStatus": "DISABLED",
            "env": {"P2PNET_SHADOW_ENABLED": "false"},
        },
        {
            "name": "SOURCE_MISSING",
            "description": "P2PNet 외부 소스 디렉터리가 없다.",
            "expectedStatus": "P2PNET_SOURCE_MISSING",
            "env": {
                "P2PNET_SHADOW_ENABLED": "true",
                "P2PNET_SHADOW_ROOT": str(missing_source),
                "P2PNET_SHADOW_WEIGHT_PATH": str(missing_weight),
            },
        },
        {
            "name": "SOURCE_VERSION_MISMATCH",
            "description": "소스가 있으나 고정 커밋과 다르다.",
            "expectedStatus": "P2PNET_SOURCE_VERSION_MISMATCH",
            "env": {
                "P2PNET_SHADOW_ENABLED": "true",
                "P2PNET_SHADOW_ROOT": str(_build_mismatched_source(base)),
                "P2PNET_SHADOW_WEIGHT_PATH": str(missing_weight),
            },
        },
        {
            "name": "WEIGHT_URL_MISSING",
            "description": "소스는 정상이나 가중치가 없고 다운로드 URL도 없다.",
            "expectedStatus": "P2PNET_WEIGHT_URL_MISSING",
            "env": {
                "P2PNET_SHADOW_ENABLED": "true",
                "P2PNET_SHADOW_ROOT": str(valid_source),
                "P2PNET_SHADOW_WEIGHT_PATH": str(missing_weight),
                "P2PNET_SHADOW_WEIGHT_URL": "",
            },
        },
        {
            "name": "WEIGHT_CHECKSUM_MISMATCH",
            "description": "가중치 파일이 있으나 SHA-256이 매니페스트와 다르다.",
            "expectedStatus": "P2PNET_WEIGHT_CHECKSUM_MISMATCH",
            "env": {
                "P2PNET_SHADOW_ENABLED": "true",
                "P2PNET_SHADOW_ROOT": str(valid_source),
                "P2PNET_SHADOW_WEIGHT_PATH": str(corrupt_weight),
                "P2PNET_SHADOW_WEIGHT_URL": "",
            },
        },
        {
            "name": "WEIGHT_DOWNLOAD_FAILED",
            "description": (
                "다운로드 URL과 인증 헤더가 있으나 접속에 실패한다. "
                "응답에 서명·토큰이 새지 않아야 한다."
            ),
            "expectedStatus": "P2PNET_WEIGHT_DOWNLOAD_FAILED",
            "env": {
                "P2PNET_SHADOW_ENABLED": "true",
                "P2PNET_SHADOW_ROOT": str(valid_source),
                "P2PNET_SHADOW_WEIGHT_PATH": str(base / "download-target.pth"),
                "P2PNET_SHADOW_WEIGHT_URL": (
                    "https://minio.example.invalid/safy-ai-models/"
                    "epoch-003.pth?X-Amz-Signature=deadbeef"
                ),
                "P2PNET_ASSET_AUTH_HEADER_NAME": ("X-Verify-Isolation-Header"),
                "P2PNET_ASSET_AUTH_HEADER_VALUE": ("verify-isolation-secret-token"),
                "P2PNET_ASSET_DOWNLOAD_TIMEOUT_SECONDS": "3",
            },
        },
        {
            "name": "MANIFEST_INVALID",
            "description": "자산 매니페스트 스키마 버전이 지원 범위를 벗어난다.",
            "expectedStatus": "P2PNET_ASSET_MANIFEST_INVALID",
            "env": {
                "P2PNET_SHADOW_ENABLED": "true",
                "P2PNET_ASSET_MANIFEST_PATH": str(invalid_manifest),
                "P2PNET_SHADOW_ROOT": str(valid_source),
                "P2PNET_SHADOW_WEIGHT_PATH": str(missing_weight),
            },
        },
    ]


def _build_mismatched_source(base: Path) -> Path:
    root = base / "mismatched-source"
    (root / "models").mkdir(parents=True, exist_ok=True)
    (root / "util").mkdir(parents=True, exist_ok=True)
    (root / "models" / "__init__.py").write_text("", encoding="utf-8")
    (root / "util" / "misc.py").write_text("", encoding="utf-8")
    (root / "LICENSE").write_text("test", encoding="utf-8")
    (root / ".safy-source-revision").write_text("0" * 40, encoding="utf-8")
    return root


def _wait_for_health(port: int, timeout_seconds: float) -> dict | None:
    deadline = time.monotonic() + timeout_seconds

    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(
                f"http://127.0.0.1:{port}/health",
                timeout=3,
            ) as response:
                return {
                    "statusCode": response.status,
                    "body": json.loads(response.read().decode("utf-8")),
                }
        except (urllib.error.URLError, ConnectionError, TimeoutError, OSError):
            time.sleep(0.4)

    return None


def _post_frame(port: int, image_path: Path) -> dict:
    boundary = "----safyIsolationBoundary"
    image_bytes = image_path.read_bytes()

    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="image"; '
        f'filename="{image_path.name}"\r\n'
        f"Content-Type: image/jpeg\r\n\r\n"
    ).encode("utf-8")
    body += image_bytes
    body += f"\r\n--{boundary}--\r\n".encode("utf-8")

    request = urllib.request.Request(
        f"http://127.0.0.1:{port}/api/v1/analyze/frame",
        data=body,
        method="POST",
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
    )

    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            raw = response.read().decode("utf-8")
            return {"statusCode": response.status, "raw": raw, "body": json.loads(raw)}
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8", errors="replace")
        return {"statusCode": error.code, "raw": raw, "body": None}


def run_scenario(
    *,
    scenario: dict,
    port: int,
    image_path: Path,
    startup_timeout_seconds: float,
) -> dict:
    environment = os.environ.copy()
    # 이전 시나리오 값이 남지 않도록 관련 변수를 먼저 제거한다.
    for key in [
        "P2PNET_SHADOW_ENABLED",
        "P2PNET_SHADOW_ROOT",
        "P2PNET_SHADOW_WEIGHT_PATH",
        "P2PNET_SHADOW_WEIGHT_URL",
        "P2PNET_ASSET_MANIFEST_PATH",
        "P2PNET_ASSET_AUTH_HEADER_NAME",
        "P2PNET_ASSET_AUTH_HEADER_VALUE",
        "P2PNET_ASSET_DOWNLOAD_TIMEOUT_SECONDS",
        "P2PNET_SHADOW_MODEL_VERSION",
        "P2PNET_SHADOW_WEIGHT_ID",
    ]:
        environment.pop(key, None)

    environment.update(scenario["env"])

    process = subprocess.Popen(
        [
            sys.executable,
            "-m",
            "uvicorn",
            "app.main:app",
            "--host",
            "127.0.0.1",
            "--port",
            str(port),
            "--log-level",
            "warning",
        ],
        cwd=str(AI_ROOT),
        env=environment,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
    )

    result: dict = {
        "name": scenario["name"],
        "description": scenario["description"],
        "expectedStatus": scenario["expectedStatus"],
    }

    try:
        health = _wait_for_health(port, startup_timeout_seconds)

        if health is None:
            result["serverStarted"] = False
            result["passed"] = False
            result["failureReason"] = "서버가 기동되지 않았습니다."
            return result

        result["serverStarted"] = True
        result["health"] = health
        shadow = health["body"].get("p2pnetShadow", {})
        result["actualStatus"] = shadow.get("status")
        result["shadowReady"] = shadow.get("ready")

        analysis = _post_frame(port, image_path)
        result["analysisStatusCode"] = analysis["statusCode"]

        body = analysis["body"] or {}
        result["analysisStatus"] = body.get("analysisStatus")
        result["personCount"] = body.get("personCount")
        result["modelName"] = body.get("modelName")

        leaked = [
            marker
            for marker in SECRET_MARKERS
            if marker in analysis["raw"] or marker in json.dumps(health["body"])
        ]
        result["leakedMarkers"] = leaked

        checks = {
            "healthIs200": health["statusCode"] == 200,
            "healthStatusOk": health["body"].get("status") == "ok",
            "shadowStatusMatches": (
                result["actualStatus"] == scenario["expectedStatus"]
            ),
            "shadowNotReady": shadow.get("ready") is False,
            "analysisIs200": analysis["statusCode"] == 200,
            "analysisCompleted": (body.get("analysisStatus") == "COMPLETED"),
            "officialModelIsYolo": body.get("modelName") == "YOLO",
            "personCountPresent": body.get("personCount") is not None,
            "noFailureCodes": not body.get("failureCodes"),
            "noSecretLeak": not leaked,
        }
        result["checks"] = checks
        result["passed"] = all(checks.values())

        return result
    finally:
        process.terminate()
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=10)


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "실제 uvicorn 서버로 P2PNet 자산 실패 시 YOLO 공식 분석이 "
            "유지되는지 검증한다."
        ),
    )
    parser.add_argument(
        "--image",
        type=Path,
        default=AI_ROOT / "runs" / "bus.jpg",
    )
    parser.add_argument("--port", type=int, default=8099)
    parser.add_argument(
        "--startup-timeout-seconds",
        type=float,
        default=90.0,
    )
    parser.add_argument(
        "--work-dir",
        type=Path,
        default=AI_ROOT / "runs" / "isolation-verification",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=(AI_ROOT / "runs" / "deployment-isolation-report.json"),
    )
    arguments = parser.parse_args()

    if not arguments.image.is_file():
        raise SystemExit(f"검증용 이미지가 없습니다: {arguments.image}")

    arguments.work_dir.mkdir(parents=True, exist_ok=True)

    scenarios = build_scenarios(arguments.work_dir)
    results = []

    for scenario in scenarios:
        print(f"[실행] {scenario['name']} ...", flush=True)
        result = run_scenario(
            scenario=scenario,
            port=arguments.port,
            image_path=arguments.image,
            startup_timeout_seconds=(arguments.startup_timeout_seconds),
        )
        results.append(result)

        mark = "PASS" if result.get("passed") else "FAIL"
        print(
            f"  {mark} shadowStatus={result.get('actualStatus')} "
            f"analysis={result.get('analysisStatus')} "
            f"personCount={result.get('personCount')}",
            flush=True,
        )

        if not result.get("passed"):
            failed_checks = [
                key for key, value in (result.get("checks") or {}).items() if not value
            ]
            if failed_checks:
                print(f"  실패 항목: {failed_checks}", flush=True)
            if result.get("failureReason"):
                print(f"  사유: {result['failureReason']}", flush=True)

    payload = {
        "scenarioCount": len(results),
        "passedCount": sum(1 for item in results if item.get("passed")),
        "image": arguments.image.as_posix(),
        "results": results,
    }

    arguments.output.parent.mkdir(parents=True, exist_ok=True)
    arguments.output.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    print()
    print(f"통과: {payload['passedCount']}/{payload['scenarioCount']}")
    print(f"output: {arguments.output}")

    if payload["passedCount"] != payload["scenarioCount"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
