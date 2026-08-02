# P2PNet 운영 자산 및 CPU 배포

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

약 82.39MB인 P2PNet 가중치는 Git에 커밋하지 않는다. 기존에 운영 중인
MinIO(`infra/env.example`의 `MINIO_ENDPOINT`/`MINIO_PUBLIC_ENDPOINT`)를
재사용해 다음 구조로 한 번 게시한다. 별도 저장소(GitLab Generic Package
Registry)를 새로 두지 않는 이유는 인증 정보를 하나 더 관리하지 않기
위해서다.

```text
bucket: safy-ai-models
object: p2pnet/safy-p2pnet-v2-epoch3/epoch-003.pth
```

경로에 배포 버전(`safy-p2pnet-v2-epoch3`)을 그대로 넣어 이후 버전이
추가돼도 기존 객체를 덮어쓰지 않고 나란히 보관하며, 롤백 시 이전 버전
경로를 그대로 다시 가리키면 된다.

게시 전 로컬 파일의 체크섬을 확인한다.

```bash
sha256sum \
  runs/p2pnet-finetune-v2-balanced-search/epoch-003.pth
```

출력은 반드시 다음 값이어야 한다.

```text
934989b1ec644660f3924fb916418cb7f757b11f35b470af46c324ac18204857
```

게시는 기존 MinIO 자격증명(`MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY`)으로
`mc`(MinIO Client)를 이용한다.

```bash
mc alias set safy-minio "${MINIO_ENDPOINT}" "${MINIO_ACCESS_KEY}" "${MINIO_SECRET_KEY}"
mc cp \
  runs/p2pnet-finetune-v2-balanced-search/epoch-003.pth \
  safy-minio/safy-ai-models/p2pnet/safy-p2pnet-v2-epoch3/epoch-003.pth
```

컨테이너 시작 스크립트는 MinIO SDK나 자격증명을 직접 다루지 않는다.
`prepare_p2pnet_assets()`는 URL과(선택적) 단일 인증 헤더만 받는 범용
다운로더이므로, 접근 가능한 URL만 넘기면 된다.

### 확정된 접근 방식: 내부 익명 + 외부 차단

인프라 담당자가 다음 방식으로 확정했다.

```text
ai-server  ->  http://minio:9000  (컨테이너 내부망, 익명 download)
외부 9200  ->  /safy-ai-models 를 404 로 차단
```

- `safy-ai-models` 버킷의 `p2pnet` prefix 에만 익명 download 정책을 부여한다.
- 외부에서 `9200` 으로 접근하면 nginx 가 `404` 를 반환한다. `/safy-reports`
  는 기존대로 `403` 을 유지한다.
- `P2PNET_ASSET_AUTH_HEADER_NAME`/`VALUE` 는 비워둔다.

인프라가 82.39MB 더미 파일로 배선을 선행 검증했다. 내부 익명 `GET` 이 `200`,
`86,390,702` 바이트, `0.08초`였고 외부 `404` 와 `/safy-reports` `403` 이
유지됐다. 검증 후 더미는 삭제하고 정책만 남겼다.

### presigned URL 을 쓰지 않는 이유

초기 설계는 presigned URL 을 권장했으나 채택하지 않았다. `mc share download`
의 만료 상한이 **7일**이어서(`--expire 169h` 를 넣으면 거부된다) 리허설이나
시연 직전에 재발급해야 하는 상황이 생긴다. 운영 중 URL 만료로 P2PNet 자산
준비가 실패하는 것을 피하기 위해 내부 익명 방식을 택했다.

`p2pnet_assets.py` 는 URL 과 단일 인증 헤더만 받는 범용 다운로더이므로 두
방식 모두 코드 변경 없이 지원한다. 나중에 presigned 로 전환하려면 `.env` 의
`P2PNET_SHADOW_WEIGHT_URL` 만 바꾸면 된다.

토큰, 서명 URL 또는 인증 헤더 값은 Git, Docker 이미지, 로그에 기록하지 않는다.

## 시작 시 다운로드와 캐시

배포 서버의 `infra/.env`에 다음 값을 설정한다. 인프라 담당자가 확정한 값이다.

```dotenv
P2PNET_SHADOW_ENABLED=true
P2PNET_SHADOW_WEIGHT_URL=http://minio:9000/safy-ai-models/p2pnet/safy-p2pnet-v2-epoch3/epoch-003.pth
P2PNET_ASSET_AUTH_HEADER_NAME=
P2PNET_ASSET_AUTH_HEADER_VALUE=
```

`minio:9000` 은 컨테이너 내부망 주소다. 외부 `9200` 으로는 이 경로에 접근할 수
없다.

첫 배포는 `P2PNET_SHADOW_ENABLED=false` 로 진행했다. `.env` 에 `P2PNET` 키가
하나도 없으면 Compose 기본값 `false` 가 적용된다. 안정을 확인한 뒤 `true` 로
전환하는 순서를 권한다.

컨테이너 시작 시 다음 순서로 처리한다.

1. `p2pnet_model_cache` 볼륨에서 가중치를 찾는다.
2. 기존 파일이 있으면 SHA-256을 계산한다.
3. 체크섬이 맞으면 다운로드하지 않고 재사용한다.
4. 파일이 없거나 체크섬이 다르면 임시 파일로 다시 다운로드한다.
5. 새 파일의 체크섬이 맞을 때만 캐시 경로로 원자적으로 교체한다.
6. 어느 단계든 실패하면 `P2PNET_SHADOW_ENABLED=false`로 전환한다.

인증 헤더 이름과 값, 다운로드 URL은 로그에 남기지 않는다.

## 학습·CI·운영 배포 의존성

CPU CI는 `requirements-ci.txt`를 설치한다. 여기서는 CPU용 PyTorch와
torchvision으로 전체 테스트와 배포 구성 검증을 수행한다.

운영 컨테이너는 `requirements-deploy.txt`를 설치한다. 배포 서버에는 GPU와
NVIDIA 런타임이 없으므로 CPU용 PyTorch만 포함한다. CUDA 13.0용
`requirements.txt`는 RTX 4050 노트북의 학습과 로컬 GPU 검증에만 사용한다.
세 requirements 파일의 공통 라이브러리를 변경할 때는 함께 갱신한다.

Docker 컨텍스트에서는 `runs/`, 로컬 `weights/`, `*.pth`를 제외한다.
따라서 학습 데이터, 개인 사진, 로컬 가중치와 대용량 결과가 이미지에
실수로 들어가지 않는다. 공식 YOLO11n 가중치는 고정 URL에서 빌드 중
다운로드하고 SHA-256을 검증한다.

## CPU 이미지 빌드와 기동

운영 서버는 4 vCPU, 메모리 15GB, swap 0 환경이며 GPU와 NVIDIA 런타임이
없다. 컨테이너는 CPU 추론만 사용하며 NVIDIA 장치 예약을 설정하지 않는다.

```bash
cd infra
docker compose build ai-server
docker compose up -d ai-server
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

`/health`는 P2PNet 그림자 자산 상태도 함께 보고한다. 운영 중 상태를
확인할 수단이 시작 로그뿐이었으므로 `S15P11A503-141`에서 추가했다.

```json
{
  "status": "ok",
  "p2pnetShadow": {
    "ready": false,
    "status": "P2PNET_WEIGHT_DOWNLOAD_FAILED",
    "modelVersion": "safy-p2pnet-v2-epoch3"
  }
}
```

`status`는 P2PNet 상태와 무관하게 항상 `ok`다. P2PNet 실패로 컨테이너를
unhealthy로 표시하면 Compose가 재시작 루프에 빠지기 때문이다. 자격증명,
인증 헤더와 다운로드 URL은 이 응답에 포함하지 않는다.

현재 GitLab 자동 배포 명령에는 `ai-server`를 추가하지 않는다. 실제 배포
서버에서 CPU 이미지와 메모리를 다시 측정하고, 백엔드 호출 연동과 모델
저장소 설정이 끝난 뒤 배포 목록에 추가한다.

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

## 장애 격리 실사용 검증 (`S15P11A503-141`)

단위 테스트가 아니라 실제 uvicorn 서버를 시나리오별로 기동해 검증했다.
각 시나리오에서 P2PNet 자산 준비를 의도적으로 실패시키고 실제 이미지를
`POST /api/v1/analyze/frame`에 전송했다.

```bash
python -m scripts.verify_deployment_isolation
```

결과: **7개 시나리오, 항목별 10개 체크 전부 통과(70/70)**

| 시나리오 | `p2pnetShadow.status` | YOLO 공식 분석 |
|---|---|---|
| 그림자 비활성화 | `DISABLED` | `COMPLETED` (4명) |
| 소스 디렉터리 없음 | `P2PNET_SOURCE_MISSING` | `COMPLETED` (4명) |
| 소스 커밋 불일치 | `P2PNET_SOURCE_VERSION_MISMATCH` | `COMPLETED` (4명) |
| 가중치·URL 모두 없음 | `P2PNET_WEIGHT_URL_MISSING` | `COMPLETED` (4명) |
| 가중치 체크섬 불일치 | `P2PNET_WEIGHT_CHECKSUM_MISMATCH` | `COMPLETED` (4명) |
| 다운로드 실패 | `P2PNET_WEIGHT_DOWNLOAD_FAILED` | `COMPLETED` (4명) |
| 매니페스트 무효 | `P2PNET_ASSET_MANIFEST_INVALID` | `COMPLETED` (4명) |

모든 시나리오에서 확인한 항목:

- `/health`가 200과 `status=ok`를 유지한다.
- `p2pnetShadow.status`가 실패 원인을 정확히 보고하고 `ready=false`다.
- 프레임 분석이 200 + `analysisStatus=COMPLETED`를 반환하고 `personCount`가
  존재하며 `failureCodes`가 비어 있다.
- 공식 응답의 `modelName`이 `YOLO`다.
- 가짜 presigned 서명(`X-Amz-Signature=...`), 인증 헤더 이름과 토큰 값이
  `/health`와 분석 응답 어디에도 노출되지 않는다.

검증 결과 원본은 `runs/deployment-isolation-report.json`에 저장되며 Git에는
포함하지 않는다.

### 실제 서버 검증 결과

위 검증은 로컬 uvicorn 기준이었다. 인프라 담당자가 배포 서버에서 임시 컨테이너로
자산 실패 3케이스를 재현해 격리를 확인했다. 운영 `ai-server` 는 건드리지 않았다.

| 주입한 실패 | `p2pnetShadow.status` | `/health` | YOLO 공식 분석 |
|---|---|---|---|
| URL 없음 | `P2PNET_WEIGHT_URL_MISSING` | `ok` 200 | `COMPLETED`, 4명, C=1.88 |
| 없는 URL | `P2PNET_WEIGHT_DOWNLOAD_FAILED` | `ok` 200 | `COMPLETED`, 4명, C=1.88 |
| 체크섬 불일치 | `P2PNET_WEIGHT_CHECKSUM_MISMATCH` | `ok` 200 | `COMPLETED`, 4명, C=1.88 |

세 경우 모두 `congestionSource=YOLO` 로 동일했다. **P2PNet 만 꺼지고 공식
경로는 영향이 없다.**

부수로 확인된 것이 있다. 세 케이스 모두 `modelVersion` 이
`safy-p2pnet-v2-epoch3` 로 채워졌다. 소스가 어긋나면 `P2PNET_SOURCE_MISSING`
이나 `P2PNET_SOURCE_VERSION_MISMATCH` 에서 먼저 걸리므로, **manifest 로드와
`/opt/p2pnet` 고정 커밋 대조가 통과했다는 뜻**이다. 이미지 안의 P2PNet 소스는
정상이다.

### 서버 런타임 실측 (`S15P11A503-106`)

같은 배포에서 측정했다. 왕복은 네트워크를 포함하고 `processingTimeMs` 는 YOLO
추론 시간만이다.

| 표본 | n | 왕복 mean / p95 | 추론 mean / p95 |
|---|---:|---|---|
| 1280×720 합성 | 30 | 83.9 / 86.4 ms | 60.5 / 63.0 ms |
| `bus.jpg` (4명) | 30 | 95.0 / 107.0 ms | 71.7 / 84.0 ms |
| 3840×2160 | 20 | 173.6 / 176.4 ms | 61.0 / 63.0 ms |

```text
컨테이너 RSS      410.5 MiB (유휴 202 MiB)
첫 요청           processingTimeMs 2151 (모델 로드, 배포 직후 1회)
```

읽을 점이 세 가지다.

- **사람이 탐지되면 추론이 약 11ms 늘어난다**(60.5 → 71.7).
- **4K 추론 시간이 720p 와 같다**(61ms). YOLO 가 내부에서 리사이즈하기
  때문이다. 왕복 차이 112ms 는 1.3MB 업로드와 디코드다. 큰 프레임에서는
  네트워크·디코드가 지배적이다. 이는 `frame-quality-v0.3` 수정의 가치를
  뒷받침한다. **4K 를 받아도 추론 비용이 같은데 v0.2 는 그 프레임을
  `TOO_BLURRY` 로 거부했으므로 순수한 손실이었다.**
- **서버 4 vCPU 가 노트북 CPU 추론보다 느리지 않다.** 노트북 추론 p95 는
  68.81ms 였고 서버는 63.0ms 로 약 8% 빠르다. 따라서 노트북 P2PNet CPU 측정값을
  서버 예상치로 거의 그대로 쓸 수 있다.

YOLO 추론 p95 63ms 는 5초 프레임 예산의 **1.3%** 다. **YOLO 기준으로 CPU 단독
배포는 충분하다.**

### 아직 검증하지 못한 항목

- P2PNet 실제 가중치 기준 지연시간과 RSS. 가중치 게시 후 측정한다. 이 값이
  C·L 보정 활성화 판단의 선행 조건이다.
- **P2PNet 동시 요청 시 대기열.** `shadow_analysis` 에 추론 락이 있어 P2PNet 은
  직렬화된다. 노트북 기준 고밀도 프레임이 1.73초이므로 동시 스트림 3개면 5.2초로
  5초 예산을 넘는다. 단일 요청 지연보다 이것이 보정 활성화 판단에 결정적이다.
- Egress 트랜스코딩과 겹쳤을 때 메모리. 라이브 세션이 없어 측정하지 못했다.
  `S15P11A503-95` 브라우저 라이브 제보 검증 때 함께 측정한다.
- `p2pnet_model_cache` 볼륨 재사용(2회 기동 시 재다운로드 생략).
- 비루트 사용자(uid 10001) 실행.
- Compose `healthcheck` 의 `start_period` 동작.

## 비활성화와 롤백

가장 빠른 비활성화 방법은 서버 `infra/.env`에서 다음 값을 바꾸고
AI 컨테이너를 재생성하는 것이다.

```dotenv
P2PNET_SHADOW_ENABLED=false
```

```bash
docker compose up -d --force-recreate ai-server
```

이 설정은 P2PNet만 끄며 YOLO 공식 분석 API 계약을 변경하지 않는다.

이전 컨테이너 이미지로 롤백해야 한다면 정상 배포 커밋 태그를 지정한다.

```bash
DEPLOY_TAG=<previous-commit-sha> \
docker compose up -d --no-build ai-server
```

가중치 캐시를 삭제하거나 교체하기 전에는 현재 파일과 체크섬을 별도로
보관한다. 삭제가 필요하면 정확한 `p2pnet_model_cache` 볼륨 대상을 확인한
뒤 인프라 담당자와 함께 수행한다.
