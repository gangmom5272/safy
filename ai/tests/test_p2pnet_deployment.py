import json
from pathlib import Path

import yaml


AI_ROOT = Path(__file__).resolve().parents[1]
REPOSITORY_ROOT = AI_ROOT.parent


def test_dockerfile_pins_p2pnet_source_and_cpu_dependencies() -> None:
    manifest = json.loads(
        (AI_ROOT / "deploy" / "p2pnet-assets.json").read_text(encoding="utf-8")
    )
    dockerfile = (AI_ROOT / "Dockerfile").read_text(encoding="utf-8")

    assert manifest["source"]["commit"] in dockerfile
    assert "requirements-deploy.txt" in dockerfile
    assert "requirements.txt" not in dockerfile
    assert "requirements-ci.txt" not in dockerfile
    assert "p2pnet-compatibility.patch" in dockerfile


def test_docker_context_excludes_local_large_assets() -> None:
    excluded_paths = set(
        (AI_ROOT / ".dockerignore").read_text(encoding="utf-8").splitlines()
    )

    assert "runs" in excluded_paths
    assert "weights" in excluded_paths
    assert "*.pth" in excluded_paths


def test_compose_uses_persistent_model_cache_and_cpu() -> None:
    compose = yaml.safe_load(
        (REPOSITORY_ROOT / "infra" / "docker-compose.yml").read_text(encoding="utf-8")
    )
    service = compose["services"]["ai-server"]
    environment = set(service["environment"])

    assert any(item.startswith("P2PNET_SHADOW_ENABLED=") for item in environment)
    assert any(item.startswith("P2PNET_SHADOW_WEIGHT_URL=") for item in environment)
    assert "p2pnet_model_cache:/var/cache/safy-ai/models/p2pnet" in service["volumes"]
    assert "P2PNET_SHADOW_DEVICE=cpu" in environment
    assert "deploy" not in service


def test_cpu_deployment_requirements_do_not_include_cuda_packages() -> None:
    requirements = (AI_ROOT / "requirements-deploy.txt").read_text(encoding="utf-8")

    assert "https://download.pytorch.org/whl/cpu" in requirements
    assert "+cu" not in requirements
    assert "nvidia-" not in requirements
