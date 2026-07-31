# SAFY Data Server

SAFY에서 사용하는 공공데이터를 수집·정제해 Spring Boot에 제공하는 FastAPI 서버입니다.

## 역할

세 가지 공공데이터를 수집합니다.

| 데이터 | 외부 API | 제공 기관 |
| --- | --- | --- |
| 재난문자 | `DSSP-IF-00247` | 행정안전부 재난안전데이터공유플랫폼 |
| 서울 실시간 혼잡도 | `citydata_ppltn` | 서울 열린데이터광장 |
| 서울 행사정보 | `citydata` | 서울 열린데이터광장 |

각 데이터마다 외부 API 호출 → 원본 검증 → 정규화 → 중복 제거 → 내부 JSON 생성까지
수행하고, 표준 수집 결과(`collection_run`)와 함께 반환합니다.

**이 서버의 책임이 아닌 것**: 호출 스케줄, DB 적재·갱신, 수집 이력 저장,
행사와 100m 격자의 공간 매핑, 최종 위험도 계산. 모두 Spring Boot가 담당합니다.
Spring Boot가 이 서버의 수집 API를 주기적으로 폴링합니다.

## 개발환경

- Python 3.11.9
- FastAPI / Uvicorn
- Pydantic v2 / pydantic-settings
- httpx2
- Pytest

## 프로젝트 구조

```text
data/
├─ app/
│  ├─ api/
│  │  ├─ routes/
│  │  │  └─ collections.py          수집 엔드포인트 3종
│  │  └─ dependencies.py            수집기·파이프라인 조립
│  ├─ collectors/                   외부 API 호출과 원본 검증
│  │  ├─ disaster_message.py
│  │  ├─ seoul_congestion.py
│  │  └─ seoul_event.py
│  ├─ normalizers/                  내부 표준 모델로 변환
│  │  ├─ disaster_message.py
│  │  ├─ seoul_congestion.py
│  │  └─ seoul_event.py
│  ├─ schemas/                      Raw·내부 모델과 요청/응답 계약
│  │  ├─ collection.py
│  │  ├─ disaster_message.py
│  │  ├─ seoul_congestion.py
│  │  └─ seoul_event.py
│  ├─ services/                     수집·정규화·중복 제거 연결
│  │  ├─ disaster_message_pipeline.py
│  │  ├─ seoul_congestion_pipeline.py
│  │  └─ seoul_event_pipeline.py
│  ├─ loaders/
│  │  └─ disaster_message_csv.py    CSV 표본 로더(참고용, 라우트에서 미사용)
│  ├─ core/
│  │  └─ config.py                  환경변수 설정
│  └─ main.py
├─ tests/                           17개 파일, 57개 테스트
├─ .dockerignore
├─ .env.example
├─ Dockerfile
├─ requirements.txt
├─ requirements-dev.txt
└─ README.md
```

## 로컬 실행

### 1. 가상환경 생성

Git Bash 기준:

```bash
cd data
python -m venv .venv
source .venv/Scripts/activate
```

### 2. 의존성 설치

```bash
python -m pip install -r requirements-dev.txt
```

`requirements-dev.txt`는 `requirements.txt`를 포함하고 pytest만 더합니다.
운영 의존성만 필요하면 `requirements.txt`를 설치합니다.

### 3. 환경변수 설정

`.env.example`을 참고해 `data/.env`를 만듭니다. 인증키가 담긴 `.env`는
Git에 커밋하지 않습니다.

### 4. 서버 실행

```bash
python -m uvicorn app.main:app --reload
```

- 상태 확인: `http://127.0.0.1:8000/health`
- Swagger UI: `http://127.0.0.1:8000/docs`

## 테스트

`data/` 디렉터리에서 실행해야 합니다. `conftest.py`와 `pytest.ini`가 없어
rootdir 기준으로 `app` 패키지를 찾습니다.

```bash
cd data
python -m pytest -q
```

CI에서는 `.gitlab-ci.yml`의 `data` 잡이 `data/**/*` 또는 `.gitlab-ci.yml`이
바뀔 때 같은 명령을 실행합니다.

## API

### 상태 확인

```http
GET /health
```

```json
{ "status": "ok", "service": "safy-data" }
```

수집기를 만들지 않으므로 API 키가 비어 있어도 200을 반환합니다.
즉 이 응답은 프로세스 기동만 보장하고, 수집 가능 여부는 보장하지 않습니다.

### 수집 엔드포인트

세 엔드포인트 모두 `POST`이고 항상 **HTTP 200**을 반환합니다.
성공·실패는 응답 본문의 `collection_run.status`로 구분합니다.

```http
POST /api/v1/collections/seoul-congestions
POST /api/v1/collections/seoul-events
POST /api/v1/collections/disaster-messages
```

#### 요청

| 엔드포인트 | 필드 | 기본값 |
| --- | --- | --- |
| `seoul-congestions` | `area_codes` | 필수, 1~200개 |
| | `max_concurrency` | `15` (1~20) |
| `seoul-events` | `area_codes` | 필수, 1~200개 |
| | `max_concurrency` | `15` (1~20) |
| | `target_date` | 없으면 KST 오늘 |
| `disaster-messages` | `page_no` | `1` |
| | `num_of_rows` | `1000` (1~1000) |
| | `start_date` | 없으면 조회 기간 미지정 |
| | `region` | 없으면 전체 |

재난문자는 **본문 전체를 생략할 수 있습니다.** 다만 `start_date`를 주지 않으면
가장 오래된 구간이 반환되므로, Spring Boot가 조회 시작일을 실어 보냅니다.

```json
{ "start_date": "2026-07-30" }
```

#### 응답

```json
{
  "collection_run": {
    "external_run_id": "seoul-citydata-ppltn:0f2c...",
    "source": "seoul-citydata-ppltn",
    "started_at": "2026-07-31T05:00:00Z",
    "finished_at": "2026-07-31T05:00:01Z",
    "status": "PARTIAL_SUCCESS",
    "success_count": 120,
    "failure_count": 1,
    "error_code": null,
    "error_message": null,
    "error_details": {
      "failures": [
        { "area": "POI999", "error": "서울 실시간 인구 API 오류: code=INFO-200, ..." }
      ]
    }
  },
  "items": [],
  "failures": [
    { "area": "POI999", "error": "서울 실시간 인구 API 오류: code=INFO-200, ..." }
  ]
}
```

- `status`: `SUCCESS` / `PARTIAL_SUCCESS` / `FAILED`
- `error_details`는 JSON 문자열이 아니라 실제 JSON 객체입니다.
- 재난문자 응답에는 `failures` 필드가 없습니다.
- 전체 제한시간을 넘기면 `error_code`가 `COLLECTION_TIMEOUT`,
  그 밖의 실패는 `COLLECTION_FAILED`입니다.

## 환경변수

| 이름 | 설명 | 필수 여부 |
| --- | --- | --- |
| `APP_ENV` | 실행환경 구분값 | 선택, 기본 `local` (현재 코드에서 참조하지 않음) |
| `DISASTER_API_BASE_URL` | 재난문자 API 주소 | 재난문자 수집 시 필수 |
| `DISASTER_API_KEY` | 재난문자 API 인증키 | 재난문자 수집 시 필수 |
| `SEOUL_CITYDATA_BASE_URL` | 서울 열린데이터광장 주소 | 혼잡도·행사 수집 시 필수 |
| `SEOUL_CITYDATA_API_KEY` | 서울시 API 인증키 | 혼잡도·행사 수집 시 필수 |
| `COLLECTION_TIMEOUT_SECONDS` | 혼잡도·행사 전체 수집 제한시간(초) | 선택, 기본 `100.0` |
| `DISASTER_COLLECTION_TIMEOUT_SECONDS` | 재난문자 수집 제한시간(초) | 선택, 기본 `30.0` |

키가 비어 있으면 수집기 생성 단계에서 `ValueError`가 발생합니다.

## 배포

`Dockerfile`은 `python:3.11-slim` 기반이며 `requirements.txt`와 `app/`만 복사합니다.
`.env`는 이미지에 넣지 않고 compose의 `environment`로 주입합니다.

`infra/docker-compose.yml`의 `data` 서비스로 배포되며, 컨테이너 내부는 8000,
호스트에는 `127.0.0.1:8002`로만 게시됩니다(8000은 `ai-server`가 사용).
Spring Boot는 브리지 내부 이름 `http://data:8000`으로 접근합니다.

## 수집 주기와 외부 API 제약

호출 주기는 Spring Boot가 정하며 `infra/.env`로 조절합니다.

| 데이터 | 주기 | 하루 호출 수 |
| --- | --- | --- |
| 서울 혼잡도 | 3분 | 121곳 × 480회 = 58,080 |
| 재난문자 | 2분 | 720 |
| 서울 행사 | 04:00 하루 1회 | 121 |

외부 API 한도:

- **서울 열린데이터광장** — 호출 횟수 제한 없음. 1회 호출당 최대 1,000건.
  (1일 1,000회 제한은 실시간 지하철 API 전용이라 `citydata` 계열에는 해당하지 않습니다.)
- **재난안전데이터공유플랫폼** — 1일 1,000회. 2분 주기 720회가 72%를 쓰고,
  남는 280회가 재시도 예산입니다.

재난문자 API는 오래된 순으로 응답하므로 `num_of_rows` 상한에 걸리면
최신 건부터 잘립니다. 기본값 1000은 이 때문에 여유를 둔 값입니다.

## 개발 원칙

- 외부 API 원본과 내부 정규화 모델을 분리합니다.
- API 인증키를 코드나 Git에 기록하지 않습니다.
- 외부 응답 형식이 변경되어도 원인을 확인할 수 있도록 오류를 구조화합니다.
- 수집 원본과 정제 결과 파일(`raw/`)은 Git에 커밋하지 않습니다.
- Spring Boot와 공유하는 응답 형식은 명세를 먼저 합의한 뒤 변경합니다.
- 실패한 수집을 성공 0건으로 처리하지 않습니다.
