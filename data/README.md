# SAFY Data Server

SAFY에서 사용하는 공공데이터를 수집·정제하고 내부 서비스에 제공하는 FastAPI 서버입니다.

## 역할

- 재난문자 등 외부 공공데이터 API 호출
- API 원본 응답 보존
- 지역, 발송시간, 재난유형 정규화
- 중복 데이터 제거
- Spring Boot가 사용할 내부 JSON 생성
- 외부 API 실패 및 응답 형식 변경 처리

## 개발환경

- Python 3.11.9
- FastAPI
- Uvicorn
- Pydantic Settings
- Pytest

## 프로젝트 구조

```text
data/
├─ app/
│  ├─ core/
│  │  ├─ __init__.py
│  │  └─ config.py
│  ├─ __init__.py
│  └─ main.py
├─ tests/
│  ├─ __init__.py
│  └─ test_health.py
├─ .env.example
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

### 2. 개발 의존성 설치

```bash
python -m pip install -r requirements-dev.txt
```

운영 의존성만 설치할 경우:

```bash
python -m pip install -r requirements.txt
```

### 3. 환경변수 설정

`.env.example`을 참고해 `data/.env` 파일을 생성합니다.

```dotenv
APP_ENV=local
DISASTER_API_BASE_URL=
DISASTER_API_KEY=
```

실제 인증키가 포함된 `.env` 파일은 Git에 커밋하지 않습니다.

### 4. 서버 실행

```bash
python -m uvicorn app.main:app --reload
```

- 상태 확인: `http://127.0.0.1:8000/health`
- Swagger UI: `http://127.0.0.1:8000/docs`

## 테스트

```bash
python -m pytest -q
```

## 현재 API

### Health Check

```http
GET /health
```

응답 예시:

```json
{
  "status": "ok",
  "service": "safy-data"
}
```

## 환경변수

| 이름 | 설명 | 필수 여부 |
| --- | --- | --- |
| `APP_ENV` | 실행환경 구분값 | 선택, 기본값 `local` |
| `DISASTER_API_BASE_URL` | 재난문자 API 기본 주소 | 수집 기능 구현 후 필수 |
| `DISASTER_API_KEY` | 재난문자 API 인증키 | 수집 기능 구현 후 필수 |

## 개발 원칙

- 외부 API 원본과 내부 정규화 모델을 분리합니다.
- API 인증키를 코드나 Git에 기록하지 않습니다.
- 외부 응답 형식이 변경되어도 원인을 확인할 수 있도록 오류를 구조화합니다.
- 수집 원본과 정제 결과 파일은 Git에 커밋하지 않습니다.
- Spring Boot와 공유하는 응답 형식은 명세를 먼저 합의한 뒤 변경합니다.

## 다음 구현 범위

1. 재난문자 API 원본 응답 수집
2. 내부 데이터 스키마 정의
3. 지역·시간·재난유형 정규화
4. 중복 제거
5. Spring Boot 연동 API 구현
