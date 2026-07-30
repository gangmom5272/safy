# P2PNet 운영 자산 및 GPU 배포

## 문서 목적

이 문서는 이슈 `S15P11A503-137`에서 확정한 P2PNet 그림자 모델의 소스,
가중치, 체크섬, 배포 의존성과 롤백 절차를 설명한다.

운영 공식 결과는 계속 YOLO11n이 생성한다. 이 문서의 P2PNet 구성은
그림자 분석에만 사용하며 자동 모델 전환은 활성화하지 않는다.

## 고정 자산

자산의 기준 정보는 `deploy/p2pnet-assets.json`에 저장한다.

| 항목 | 값 |
|---|---|
| 운영 식별자 | `safy-p2pnet-v2-epoch3` |
| 체크포인트 내부 모델 버전 | `safy-p2pnet-v1` |
| 가중치 ID | `p2pnet-finetune-v2-balanced-search/epoch-003.pth` |
| 가중치 SHA-256 | `934989b1ec644660f3924fb916418cb7f757b11f35b470af46c324ac18204857` |
| 외부 저장소 | `TencentYoutuResearch/CrowdCounting-P2PNet` |
| 외부 소스 커밋 | `5c91a81ca062b1c7fd3db3ad1c55b1c21f0a7455` |
| 외부 소스 라이선스 | Tencent P2PNet academic research license |

체크포인트 내부 버전은 초기 학습 스크립트의 값이고, 운영 식별자는 두 번째
균형 탐색 실험의 epoch 3을 구분하기 위한 배포 버전이다. 두 값을 숨기지
않고 매니페스트에 함께 기록한다.

P2PNet 라이선스는 사용 범위를 학술 연구로 제한한다. SAFY 교육 프로젝트
밖에서 상용 또는 다른 목적으로 재사용하려면 라이선스를 다시 검토해야 한다.

## 소스 코드 고정

`Dockerfile`은 빌드할 때 공식 P2PNet 저장소를 복제하고 위 커밋으로
checkout한다. 현재 PyTorch·torchvision과의 호환을 위해
`deploy/p2pnet-compatibility.patch`를 적용한 뒤 원본 커밋을
`.safy-source-revision`에 기록한다.

컨테이너 시작 시 다음 파일과 커밋 표식을 확인한다.

- `/opt/p2pnet/models/__init__.py`
- `/opt/p2pnet/util/misc.py`
- `/opt/p2pnet/LICENSE`
- `/opt/p2pnet/.safy-source-revision`

표식이 매니페스트와 다르면 P2PNet 그림자 분석만 비활성화된다.

## 가중치 저장 정책

86MB P2PNet 가중치는 Git에 커밋하지 않는다. 비공개 GitLab Generic
Package Registry에 다음 구조로 한 번 게시한다.

```text
package: p2pnet
version: safy-p2pnet-v2-epoch3
file: epoch-003.pth
```

게시 전 로컬 파일의 체크섬을 확인한다.

```bash
sha256sum \
  runs/p2pnet-finetune-v2-balanced-search/epoch-003.pth
```

출력은 반드시 다음 값이어야 한다.

```text
934989b1ec644660f3924fb916418cb7f757b11f35b470af46c324ac18204857
```

GitLab Deploy Token은 `read_package_registry`와
`write_package_registry` 범위로 생성하고 환경 변수로만 전달한다.

```bash
export GITLAB_PROJECT_ID="1373906"
export GITLAB_DEPLOY_TOKEN="<read-write-package-registry-token>"

curl --fail-with-body \
  --header "DEPLOY-TOKEN: ${GITLAB_DEPLOY_TOKEN}" \
  --upload-file \
  runs/p2pnet-finetune-v2-balanced-search/epoch-003.pth \
  "https://lab.ssafy.com/api/v4/projects/${GITLAB_PROJECT_ID}/packages/generic/p2pnet/safy-p2pnet-v2-epoch3/epoch-003.pth"
```

토큰, 서명 URL 또는 인증 헤더 값은 Git, Docker 이미지, 로그에 기록하지
않는다.

## 시작 시 다운로드와 캐시

배포 서버의 `infra/.env`에 다음 값을 설정한다.

```dotenv
P2PNET_SHADOW_ENABLED=true
P2PNET_SHADOW_WEIGHT_URL=https://lab.ssafy.com/api/v4/projects/1373906/packages/generic/p2pnet/safy-p2pnet-v2-epoch3/epoch-003.pth
P2PNET_ASSET_AUTH_HEADER_NAME=DEPLOY-TOKEN
P2PNET_ASSET_AUTH_HEADER_VALUE=<read-package-registry-deploy-token>
```

컨테이너 시작 시 다음 순서로 처리한다.

1. `p2pnet_model_cache` 볼륨에서 가중치를 찾는다.
2. 기존 파일이 있으면 SHA-256을 계산한다.
3. 체크섬이 맞으면 다운로드하지 않고 재사용한다.
4. 파일이 없거나 체크섬이 다르면 임시 파일로 다시 다운로드한다.
5. 새 파일의 체크섬이 맞을 때만 캐시 경로로 원자적으로 교체한다.
6. 어느 단계든 실패하면 `P2PNET_SHADOW_ENABLED=false`로 전환한다.

인증 헤더 이름과 값, 다운로드 URL은 로그에 남기지 않는다.

## CPU CI와 GPU 배포 의존성

CPU CI는 `requirements-ci.txt`를 설치한다. 여기서는 CPU용 PyTorch와
torchvision으로 전체 테스트와 배포 구성 검증을 수행한다.

GPU 컨테이너는 `requirements.txt`를 설치한다. 이 파일은 CUDA 13.0용
PyTorch wheel을 사용한다. 두 requirements 파일의 공통 라이브러리를
변경할 때는 항상 함께 갱신한다.

Docker 컨텍스트에서는 `runs/`, 로컬 `weights/`, `*.pth`를 제외한다.
따라서 학습 데이터, 개인 사진, 로컬 가중치와 대용량 결과가 이미지에
실수로 들어가지 않는다. 공식 YOLO11n 가중치는 고정 URL에서 빌드 중
다운로드하고 SHA-256을 검증한다.

## GPU 이미지 빌드와 기동

GPU 호스트에는 NVIDIA 드라이버와 NVIDIA Container Toolkit이 필요하다.
호스트 준비 여부를 먼저 확인한다.

```bash
nvidia-smi
docker run --rm --gpus all nvidia/cuda:13.0.0-base-ubuntu24.04 nvidia-smi
```

AI 서비스는 기본 compose 실행에 포함되지 않도록 `ai-gpu` 프로필에 둔다.
준비된 GPU 호스트에서만 다음 명령으로 빌드하고 기동한다.

```bash
cd infra
docker compose --profile ai-gpu build ai-server
docker compose --profile ai-gpu up -d ai-server
docker compose logs --tail=100 ai-server
```

시작 로그에서 다음을 확인한다.

```text
"event":"p2pnet_asset_startup"
"ready":true
"status":"READY"
"modelVersion":"safy-p2pnet-v2-epoch3"
"actualWeightSha256":"934989b1ec644660f3924fb916418cb7f757b11f35b470af46c324ac18204857"
"sourceCommit":"5c91a81ca062b1c7fd3db3ad1c55b1c21f0a7455"
```

헬스체크는 `http://127.0.0.1:8000/health`를 사용한다. P2PNet 준비에
실패해도 FastAPI와 YOLO 공식 분석은 계속 동작하므로 컨테이너 자체는
정상 상태를 유지한다.

현재 GitLab 자동 배포 명령에는 `ai-server`를 추가하지 않는다. 실제 배포
호스트의 GPU와 Container Toolkit 확인 및 백엔드 호출 연동이 끝난 뒤
`ai-gpu` 프로필을 명시해 활성화한다.

## 실패 상태

| 상태 | 처리 |
|---|---|
| `P2PNET_ASSET_MANIFEST_INVALID` | 그림자 분석 비활성화 |
| `P2PNET_ASSET_CONFIGURATION_INVALID` | 그림자 분석 비활성화 |
| `P2PNET_SOURCE_MISSING` | 그림자 분석 비활성화 |
| `P2PNET_SOURCE_VERSION_MISMATCH` | 그림자 분석 비활성화 |
| `P2PNET_WEIGHT_URL_MISSING` | 그림자 분석 비활성화 |
| `P2PNET_WEIGHT_DOWNLOAD_FAILED` | 그림자 분석 비활성화 |
| `P2PNET_WEIGHT_CHECKSUM_MISMATCH` | 잘못된 파일을 사용하지 않고 그림자 분석 비활성화 |

P2PNet 모델 객체의 첫 초기화가 실패한 경우에도 해당 서비스 인스턴스의
그림자 분석을 비활성화한다. 이후 프레임은 YOLO 공식 분석만 수행한다.

## 비활성화와 롤백

가장 빠른 비활성화 방법은 서버 `infra/.env`에서 다음 값을 바꾸고
AI 컨테이너를 재생성하는 것이다.

```dotenv
P2PNET_SHADOW_ENABLED=false
```

```bash
docker compose --profile ai-gpu up -d --force-recreate ai-server
```

이 설정은 P2PNet만 끄며 YOLO 공식 분석 API 계약을 변경하지 않는다.

이전 컨테이너 이미지로 롤백해야 한다면 정상 배포 커밋 태그를 지정한다.

```bash
DEPLOY_TAG=<previous-commit-sha> \
docker compose --profile ai-gpu up -d --no-build ai-server
```

가중치 캐시를 삭제하거나 교체하기 전에는 현재 파일과 체크섬을 별도로
보관한다. 삭제가 필요하면 정확한 `p2pnet_model_cache` 볼륨 대상을 확인한
뒤 인프라 담당자와 함께 수행한다.
