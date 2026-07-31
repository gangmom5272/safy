# AI 서버 · 백엔드 연동 명세

## 1. 문서 상태

- 관련 이슈: `S15P11A503-96`
- 기준 커밋: `develop` (154번 머지 후)
- 상태: 구현 기준 명세. 코드에서 직접 확인한 내용이다.

이 문서는 백엔드가 AI 서버를 호출할 때 필요한 요청·응답 형식을 확정하기 위한
것이다. 추측이 아니라 `ai/app/api/`와 `ai/app/schemas/`의 현재 구현을 그대로
기술했다.

호출 주체와 시점(즉시 호출 / 배치 / 별도 워커)은 아직 결정되지 않았다. 이
문서는 "어떤 형식으로 주고받는가"만 다룬다.

### 오류 계약 실측 검증

문서 작성 시 실제로 요청을 보내 확인한 결과다.

| 요청 | HTTP | 응답 본문 |
|---|---|---|
| `analyze` · `text/plain` | 415 | `{"detail":"이미지 파일만 업로드할 수 있습니다."}` |
| `analyze` · 빈 파일 | 400 | `{"detail":"업로드된 이미지가 비어 있습니다."}` |
| `analyze` · 깨진 이미지 | **200** | `analysisStatus: INVALID_FRAME`, `qualityIssues: ["DECODE_FAILED"]` |
| `blur` · `text/plain` | 415 | `{"failureCodes":["UNSUPPORTED_MEDIA_TYPE"]}` |
| `blur` · 빈 파일 | 400 | `{"failureCodes":["EMPTY_IMAGE"]}` |
| `blur` · 깨진 이미지 | 422 | `{"failureCodes":["IMAGE_DECODE_FAILED"]}` |

## 2. 엔드포인트 목록

| 메서드 | 경로 | 용도 |
|---|---|---|
| `POST` | `/api/v1/analyze/frame` | 프레임 1장 분석 |
| `POST` | `/api/v1/privacy/blur` | 대표 이미지 전체 블러 |
| `GET` | `/health` | 생존 확인, P2PNet 자산 상태 |

기본 포트는 `8000`이다. Compose에서 `127.0.0.1:${AI_SERVER_PORT:-8000}`으로
바인딩하므로 호스트 외부에는 직접 노출되지 않는다.

## 3. 먼저 알아야 할 다섯 가지

구현을 읽지 않으면 놓치기 쉬운 항목이다.

### 3.1 분석 실패도 HTTP 200이다

`analysisStatus`가 `FAILED`인 경우에도 **HTTP 200**을 반환한다. 5xx가 아니다.
따라서 백엔드는 HTTP 상태 코드가 아니라 **응답 본문의 `analysisStatus`를
읽어야 한다.**

HTTP 상태가 200이 아닌 경우는 요청 자체가 잘못된 415와 400뿐이다.

### 3.2 오류 응답 형식이 두 엔드포인트에서 다르다

| 엔드포인트 | 4xx 본문 형식 |
|---|---|
| `/api/v1/analyze/frame` | `{"detail": "이미지 파일만 업로드할 수 있습니다."}` |
| `/api/v1/privacy/blur` | `{"failureCodes": ["UNSUPPORTED_MEDIA_TYPE"]}` |

분석 API는 FastAPI 기본 `HTTPException` 형식을 쓰고, 블러 API는 자체
`failureCodes` 형식을 쓴다. 백엔드가 두 형태를 모두 처리해야 한다.

이 불일치는 AI 쪽에서 통일할 수 있다. 어느 쪽으로 맞출지는 6장 결정 항목에
있다.

### 3.3 `processingTimeMs`는 YOLO 추론 시간만이다

요청 전체 소요 시간이 아니다. 품질 검사, 디코딩, 네트워크 시간은 포함하지
않는다. `INVALID_FRAME`과 초기 `FAILED`에서는 `0`이다.

백엔드 타임아웃 산정이나 SLA 판단에 이 값을 쓰면 안 된다.

### 3.4 인증이 없다

두 엔드포인트 모두 인증 의존성이 없다. 내부 네트워크 전용을 전제로 한다.
nginx에 AI 라우트를 추가하거나 외부에 노출하려면 인증을 먼저 논의해야 한다.

### 3.5 응답에 프레임 식별자가 없다

AI 서버는 상태를 저장하지 않는다. 제보 ID, 프레임 순번, 요청 ID를 받지도
반환하지도 않는다.

동기 호출이면 응답이 요청과 1:1이므로 문제가 없다. 비동기나 배치로 가면
백엔드가 스스로 대응 관계를 유지해야 한다. 필요하면 통과용 식별자 필드를
추가할 수 있다. 6장 결정 항목 참고.

## 4. POST /api/v1/analyze/frame

### 4.1 요청

```text
POST /api/v1/analyze/frame
Content-Type: multipart/form-data

image: <바이너리>   (필수)
```

- 필드명은 정확히 `image`여야 한다.
- `Content-Type`이 `image/`로 시작하지 않으면 415다. 파일 확장자가 아니라
  파트의 `Content-Type` 헤더를 본다.
- 본문이 비어 있으면 400이다.
- 크기 상한이 구현되어 있지 않다. 6장 참고.

```bash
curl -X POST "http://ai-server:8000/api/v1/analyze/frame" \
  -F "image=@frame.jpg"
```

### 4.2 요청 오류

| HTTP | 조건 | 본문 |
|---|---|---|
| 415 | `Content-Type`이 `image/`로 시작하지 않음 | `{"detail": "이미지 파일만 업로드할 수 있습니다."}` |
| 400 | 업로드 본문이 비어 있음 | `{"detail": "업로드된 이미지가 비어 있습니다."}` |

둘 다 재시도해도 같은 결과다. 재시도하지 않는다.

### 4.3 응답 필드

HTTP 200, `application/json`. 필드명은 camelCase다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `analysisStatus` | `"COMPLETED"` \| `"INVALID_FRAME"` \| `"FAILED"` | 항상 존재 |
| `personCount` | `int \| null` | 공식 사람 수. YOLO 결과 |
| `personConfidences` | `float[]` | 탐지별 신뢰도. **DB에 저장하지 않기로 합의** |
| `visualCongestionScore` | `float \| null` | 전체 혼잡도 C, 0~100 |
| `localPeakCongestionScore` | `float \| null` | 국소 최대 혼잡도 L, 0~100 |
| `congestionSource` | `"YOLO"` \| `"P2PNET_CORRECTED"` | C·L 값의 출처. 기본 `"YOLO"` |
| `densityLevel` | `null` | 항상 `null`. 백엔드 판단 영역 |
| `riskScore` | `null` | 항상 `null`. 백엔드 판단 영역 |
| `confidence` | `float \| null` | 전체 분석 신뢰도. 보정 근거가 없어 현재 항상 `null` |
| `qualityIssues` | `string[]` | 품질 문제 코드 |
| `failureCodes` | `string[]` | AI 내부 실패 코드 |
| `frameWidth` | `int \| null` | 디코딩된 픽셀 너비 |
| `frameHeight` | `int \| null` | 디코딩된 픽셀 높이 |
| `brightnessScore` | `float \| null` | 회색조 픽셀 평균 |
| `blurScore` | `float \| null` | 라플라시안 분산. 짧은 변 720px 기준 |
| `modelName` | `string` | 현재 `"YOLO"` |
| `modelVersion` | `string` | 현재 `"yolo11n"` |
| `modelWeightId` | `string` | 현재 `"yolo11n.pt"` |
| `congestionRuleVersion` | `string` | 현재 `"yolo-grid-v0.2"` |
| `frameQualityRuleVersion` | `string` | 현재 `"frame-quality-v0.3"` |
| `processingTimeMs` | `int` | YOLO 추론 시간만. 3.3 참고 |

`modelName`, `modelVersion`, `modelWeightId`, 두 규칙 버전,
`processingTimeMs`, `analysisStatus`는 **모든 상태에서 항상 존재한다.**
실패해도 빠지지 않는다.

### 4.4 상태별 필드 값

`FAILED`는 발생 지점에 따라 세 가지로 나뉜다.

| 필드 | COMPLETED | INVALID_FRAME | FAILED<br>품질검사 예외 | FAILED<br>탐지 실패 | FAILED<br>혼잡도 실패 |
|---|---|---|---|---|---|
| `personCount` | `int` | `null` | `null` | `null` | `null` |
| `personConfidences` | 배열 | `[]` | `[]` | `[]` | `[]` |
| C · L | `float` | `null` | `null` | `null` | `null` |
| `congestionSource` | `YOLO` 또는 `P2PNET_CORRECTED` | `YOLO` | `YOLO` | `YOLO` | `YOLO` |
| `qualityIssues` | `[]` | 1개 이상 | `[]` | `[]` | `[]` |
| `failureCodes` | `[]` | `[]` | `INTERNAL_AI_ERROR` | `MODEL_INFERENCE_FAILED` | `CONGESTION_CALCULATION_FAILED` |
| `frameWidth` · `frameHeight` | `int` | `int` | **`null`** | `int` | `int` |
| `brightnessScore` · `blurScore` | `float` | `float` | **`null`** | `float` | `float`|
| `processingTimeMs` | YOLO 시간 | `0` | `0` | `0` | YOLO 시간 |

품질 검사 자체가 예외로 죽은 경우에만 크기·밝기·블러가 `null`이다. 이미지를
디코딩조차 못 했기 때문이다.

응답 스키마에 검증 규칙이 걸려 있다.

```text
COMPLETED       failureCodes 비어 있어야 함
INVALID_FRAME   failureCodes 비어 있고 qualityIssues 1개 이상
FAILED          failureCodes 1개 이상
```

### 4.5 코드 목록

품질 문제 코드 (`qualityIssues`):

```text
DECODE_FAILED
LOW_RESOLUTION
TOO_DARK
TOO_BRIGHT
TOO_BLURRY
```

여러 개가 동시에 나올 수 있다. 예를 들어 야간 프레임은
`TOO_DARK`와 `TOO_BLURRY`가 함께 나온다. 첫 번째만 반환하지 않는다.

AI 내부 실패 코드 (`failureCodes`):

```text
MODEL_INFERENCE_FAILED
CONGESTION_CALCULATION_FAILED
INTERNAL_AI_ERROR
```

백엔드가 자체 생성하는 통신 실패 코드. **AI 응답에는 나오지 않는다.**

```text
AI_CONNECTION_FAILED
AI_TIMEOUT
INVALID_AI_RESPONSE
```

### 4.6 재시도와 집계 규칙

| 상황 | 재시도 | 영상 단위 집계 |
|---|---|---|
| `COMPLETED` | 불필요 | 포함 |
| `INVALID_FRAME` | **하지 않음** | **제외** |
| `FAILED` | **1회** | 재시도도 실패하면 **제외** |
| 415 · 400 | 하지 않음 | 제외 |
| 연결 실패 · 타임아웃 | 1회 | 재시도도 실패하면 제외 |

**실패한 프레임을 사람 0명으로 계산하면 안 된다.** 사람이 없는 정상 프레임
(`COMPLETED`, `personCount = 0`)과 분석하지 못한 프레임은 반드시 구분한다.
전자는 집계에 포함하고 후자는 제외한다.

최종 위험도, 영상 단위 상태, 혼잡 지속성 P, 증가 추세 G는 백엔드 책임이다.
AI는 프레임 1장의 관측값만 반환한다.

### 4.7 응답 예시

`COMPLETED`

```json
{
  "analysisStatus": "COMPLETED",
  "personCount": 12,
  "personConfidences": [0.91, 0.88, 0.84],
  "visualCongestionScore": 5.62,
  "localPeakCongestionScore": 30.0,
  "congestionSource": "YOLO",
  "densityLevel": null,
  "riskScore": null,
  "confidence": null,
  "qualityIssues": [],
  "failureCodes": [],
  "frameWidth": 1440,
  "frameHeight": 810,
  "brightnessScore": 86.33,
  "blurScore": 2364.53,
  "modelName": "YOLO",
  "modelVersion": "yolo11n",
  "modelWeightId": "yolo11n.pt",
  "congestionRuleVersion": "yolo-grid-v0.2",
  "frameQualityRuleVersion": "frame-quality-v0.3",
  "processingTimeMs": 89
}
```

`INVALID_FRAME`

```json
{
  "analysisStatus": "INVALID_FRAME",
  "personCount": null,
  "personConfidences": [],
  "visualCongestionScore": null,
  "localPeakCongestionScore": null,
  "congestionSource": "YOLO",
  "densityLevel": null,
  "riskScore": null,
  "confidence": null,
  "qualityIssues": ["TOO_DARK", "TOO_BLURRY"],
  "failureCodes": [],
  "frameWidth": 1280,
  "frameHeight": 720,
  "brightnessScore": 18.48,
  "blurScore": 15.79,
  "modelName": "YOLO",
  "modelVersion": "yolo11n",
  "modelWeightId": "yolo11n.pt",
  "congestionRuleVersion": "yolo-grid-v0.2",
  "frameQualityRuleVersion": "frame-quality-v0.3",
  "processingTimeMs": 0
}
```

`FAILED`

```json
{
  "analysisStatus": "FAILED",
  "personCount": null,
  "personConfidences": [],
  "visualCongestionScore": null,
  "localPeakCongestionScore": null,
  "congestionSource": "YOLO",
  "densityLevel": null,
  "riskScore": null,
  "confidence": null,
  "qualityIssues": [],
  "failureCodes": ["MODEL_INFERENCE_FAILED"],
  "frameWidth": 1280,
  "frameHeight": 720,
  "brightnessScore": 109.21,
  "blurScore": 953.91,
  "modelName": "YOLO",
  "modelVersion": "yolo11n",
  "modelWeightId": "yolo11n.pt",
  "congestionRuleVersion": "yolo-grid-v0.2",
  "frameQualityRuleVersion": "frame-quality-v0.3",
  "processingTimeMs": 0
}
```

## 5. POST /api/v1/privacy/blur

### 5.1 요청

```text
POST /api/v1/privacy/blur
Content-Type: multipart/form-data

image: <바이너리>   (필수)
```

대표 프레임 선정은 백엔드 책임이다. AI는 받은 이미지 **전체**에 블러를
적용한다. 사람이나 얼굴 영역만 찾지 않는다.

### 5.2 성공 응답

HTTP 200. 본문은 **JSON이 아니라 JPEG 바이너리**다.

```text
Content-Type: image/jpeg
X-Blur-Rule-Version: full-image-blur-v0.1
X-Image-Width: 1280
X-Image-Height: 720
X-Processing-Time-Ms: 24
```

메타데이터는 본문이 아니라 응답 헤더로 전달된다.

### 5.3 실패 응답

본문 형식은 `{"failureCodes": ["CODE"]}`다.

| HTTP | 코드 | 조건 |
|---|---|---|
| 415 | `UNSUPPORTED_MEDIA_TYPE` | `Content-Type`이 `image/`로 시작하지 않음 |
| 400 | `EMPTY_IMAGE` | 업로드 본문이 비어 있음 |
| 422 | `IMAGE_DECODE_FAILED` | 이미지를 디코딩할 수 없음 |
| 500 | `IMAGE_ENCODING_FAILED` | JPEG 인코딩 실패 |
| 500 | `IMAGE_BLUR_FAILED` | 그 외 블러 처리 실패 |

원본 이미지는 프론트에 노출하지 않는다. 블러 결과만 사용한다.

## 6. GET /health

HTTP 200을 항상 반환한다.

```json
{
  "status": "ok",
  "p2pnetShadow": {
    "ready": true,
    "status": "READY",
    "modelVersion": "safy-p2pnet-v2-epoch3"
  }
}
```

`status`는 P2PNet 상태와 무관하게 항상 `"ok"`다. P2PNet은 보조 모델이므로
준비에 실패해도 YOLO 공식 분석은 정상 동작한다. 이때 컨테이너를 unhealthy로
표시하면 재시작 루프에 빠지기 때문에 의도적으로 분리했다.

`p2pnetShadow`는 진단용이다. 자격증명이나 다운로드 URL은 포함하지 않는다.

`p2pnetShadow.status` 가능한 값:

```text
READY
DISABLED
NOT_INITIALIZED
P2PNET_SOURCE_MISSING
P2PNET_SOURCE_VERSION_MISMATCH
P2PNET_WEIGHT_URL_MISSING
P2PNET_WEIGHT_DOWNLOAD_FAILED
P2PNET_WEIGHT_CHECKSUM_MISMATCH
P2PNET_ASSET_MANIFEST_INVALID
P2PNET_ASSET_CONFIGURATION_INVALID
```

백엔드는 `/health`의 `p2pnetShadow`를 볼 필요가 없다. 운영 진단용이다.
백엔드가 확인할 것은 HTTP 200 응답 여부뿐이다.

## 7. 백엔드와 결정할 항목

### 7.1 호출 주체와 시점

가장 중요한 미결 사항이다. 세 안이 있다.

```text
A. 프레임 생성 이벤트마다 백엔드가 즉시 AI 호출
B. 세션 종료 후 백엔드가 MinIO 프레임을 순차 배치 호출
C. 별도 워커가 MinIO 프레임을 소비하여 AI 호출
```

AI 쪽 제약은 다음과 같다.

- AI는 상태를 저장하지 않으므로 세 안 모두 코드 변경 없이 가능하다.
- P2PNet 그림자는 기본 비활성화이므로 응답 지연에 영향이 없다.
- 보정(`P2PNET_CONGESTION_CORRECTION_ENABLED`)을 켜면 P2PNet이 응답
  크리티컬 패스에 들어와 지연시간이 크게 늘고, 추론 락 때문에 동시 요청이
  직렬화된다. 서버 실측(`S15P11A503-106`) 전까지 켜지 않는다.

실시간에 가까운 결과가 필요하면 A, 구현 단순성과 안정성이 우선이면 B가
현실적이다.

### 7.2 타임아웃 값

현재 근거로 제시할 수 있는 수치는 노트북 CPU 측정값이다.

```text
YOLO 대표 표본 평균   57.31ms
YOLO 대표 표본 P95    68.81ms
YOLO 고밀도 P95       78.46ms
```

운영 서버는 4 vCPU이고 측정 노트북보다 느리므로 이 값을 그대로 쓸 수 없다.
서버 실측(`S15P11A503-106`) 후 확정한다. 그 전까지는 넉넉하게 잡고,
타임아웃 발생 시 `AI_TIMEOUT`으로 1회 재시도한다.

### 7.3 프레임 식별자 통과 필요 여부

현재 응답에는 프레임을 식별할 값이 없다. 비동기나 배치 호출로 가면 백엔드가
대응 관계를 유지해야 한다.

필요하면 요청에 선택적 `frameId`를 받아 응답에 그대로 되돌려주는 필드를
추가할 수 있다. AI 로직에는 영향이 없다. 백엔드가 필요하다고 판단하면
AI 쪽에서 구현한다.

### 7.4 오류 응답 형식 통일

3.2의 불일치를 어느 쪽으로 맞출지 결정한다. 분석 API의 4xx를 블러 API처럼
`failureCodes` 형식으로 바꾸는 것이 일관성 면에서는 낫다. 다만 응답 형식
변경이므로 백엔드 구현 시점과 맞춰야 한다.

### 7.5 업로드 크기 상한

현재 두 엔드포인트 모두 크기 검사가 없다. 운영 서버는 메모리 15GB이고 swap이
0이므로, 매우 큰 이미지가 들어오면 디코딩 단계에서 메모리를 크게 쓴다.

nginx에 AI 라우트를 추가할 때 `client_max_body_size`를 함께 정하고, AI 쪽에도
픽셀 수 상한을 둘지 결정한다.

### 7.6 이미 전달된 변경 사항

`frameQualityRuleVersion`이 `frame-quality-v0.2`에서 `frame-quality-v0.3`으로
변경됐다(`S15P11A503-154`). 이 값을 저장하거나 비교하는 로직이 있으면 확인이
필요하다. 응답 필드명, 타입, 상태 코드는 변경되지 않았다.

## 8. 관련 문서

- `ai/docs/video-frame-analysis.md` — 프레임 상태와 실패 처리 기준
- `ai/docs/analysis-confidence-versioning.md` — 모델·규칙 버전 기록 기준
- `ai/docs/congestion-scoring.md` — C·L 계산 규칙
- `ai/docs/frame-quality-validation.md` — 품질 판정 기준과 보정 근거
- `ai/docs/image-blur.md` — 대표 이미지 블러 처리
- `ai/docs/p2pnet-shadow-analysis.md` — 그림자 분석과 보정 설정
