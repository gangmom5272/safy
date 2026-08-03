# SAFY 위험단계 공통 표준

## 문서 상태

- 관련 이슈: `S15P11A503-102` (위험도 계산)
- 상태: 프론트엔드·백엔드·AI 문서에서 공통으로 사용하는 공식 기준
- 적용 대상: 관리자 프론트엔드, 백엔드 API·DB, AI 및 프로젝트 문서
- 기준 구현:
  - `frontend-admin/src/utils/adminRisk.ts`
  - `backend/src/main/java/com/ojimasam/safy/analysis/domain/risk/RiskLevel.java`

이 문서는 팀원이 위험단계 이름과 점수 경계를 서로 다르게 사용하는 문제를
막기 위한 공통 규칙이다. 새로운 API, DB 컬럼, 화면, 테스트와 문서에서는 이
기준을 사용한다.

## 공식 위험단계

위험점수는 `0.0` 이상 `100.0` 이하이며 다음 5단계로 분류한다.

| 위험점수 범위 | API 값 | 백엔드 Java enum | 화면 표시 | 대표 색상 |
|---:|---|---|---|---|
| `81.0 ~ 100.0` | `very_high` | `VERY_HIGH` | 매우 높음 | 빨강 |
| `61.0 ~ 80.9` | `high` | `HIGH` | 높음 | 주황 |
| `41.0 ~ 60.9` | `medium` | `MEDIUM` | 보통 | 노랑 |
| `21.0 ~ 40.9` | `low` | `LOW` | 낮음 | 초록 |
| `0.0 ~ 20.9` | `very_low` | `VERY_LOW` | 매우 낮음 | 파랑 |

경계값 계산은 다음과 같다.

```text
score >= 81.0  -> very_high
score >= 61.0  -> high
score >= 41.0  -> medium
score >= 21.0  -> low
score >= 0.0   -> very_low
```

예를 들어 `60.9`는 `medium`, `61.0`은 `high`다.

## 시스템별 표현 규칙

### API와 JSON

API의 `riskLevel`에는 소문자 snake case 값을 사용한다.

```json
{
  "riskScore": 76.4,
  "riskLevel": "high"
}
```

허용값은 다음 다섯 개뿐이다.

```text
very_low
low
medium
high
very_high
```

### 백엔드

Java에서는 다음 enum을 사용한다.

```text
VERY_LOW
LOW
MEDIUM
HIGH
VERY_HIGH
```

현재 `RiskLevel`은 JSON 직렬화 시 소문자로 변환한다. DB의 `risk_level`은
`EnumType.STRING`으로 저장하므로 실제 저장값은 Java enum과 같은 대문자다.
API 값과 DB 값의 대소문자는 다르지만 의미와 단계는 동일하다.

### 프론트엔드

프론트엔드는 API 값을 한국어로 변환해 표시한다.

```text
very_low  -> 매우 낮음
low       -> 낮음
medium    -> 보통
high      -> 높음
very_high -> 매우 높음
```

백엔드가 `riskLevel`을 제공하면 해당 값을 우선 사용한다. 값이 없거나 인식할 수
없고 유효한 `riskScore`가 있으면 점수 경계로 단계를 계산한다. 둘 다 없으면
`미분류` 또는 `정보 없음`으로 표시한다.

### AI 서버

AI 서버는 프레임 한 장의 관측값인 C와 L을 제공한다. 혼잡 지속성 P, 증가 추세
G, 최종 위험점수와 위험단계는 제보 단위 프레임을 모은 백엔드가 계산한다.

따라서 현재 AI 프레임 분석 응답의 `riskScore`와 `densityLevel`은 `null`이며,
AI가 임의로 위험단계를 만들어 반환하지 않는다.

## 위험단계 예외 규칙

기본 점수 등급을 계산한 뒤 다음 예외 규칙을 적용한다.

```text
L >= 90 AND P >= 70 -> 최소 very_high
L >= 80             -> 최소 high
```

예외 규칙은 위험단계를 올리는 방향으로만 적용하며 낮추지 않는다. 두 조건을 모두
만족할 수 있으므로 `very_high` 규칙을 먼저 검사한다.

예시:

- 최종 위험점수 기준 등급이 `medium`이고 `L = 85`이면 최종 단계는 `high`다.
- 최종 위험점수 기준 등급이 `high`이고 `L = 92`, `P = 75`이면 최종 단계는
  `very_high`다.
- `L = 92`여도 P가 없거나 70 미만이면 두 번째 규칙은 발동하지 않고 최소
  `high`만 보장한다.

## 레거시 4단계 처리

과거 목 데이터와 일부 문서에서 다음 명칭을 사용했다.

| 레거시 값 | 현재 변환값 | 비고 |
|---|---|---|
| `danger` | `very_high` | 호환 입력만 허용 |
| `warning` | `high` | 호환 입력만 허용 |
| `caution` | `medium` | 호환 입력만 허용 |
| `safe` | `low` | 호환 입력만 허용 |

프론트엔드는 기존 데이터 표시를 위해 이 값을 계속 인식할 수 있다. 하지만 신규
API 응답, DB 설계, 테스트 데이터와 문서에서는 `danger`, `warning`, `caution`,
`safe`를 생성하거나 공식 단계처럼 사용하지 않는다.

특히 `caution`은 현재 `medium`으로 변환된다. 따라서 과거 문서의
`L >= 80 -> 최소 CAUTION`은 최신 기준과 같지 않다. 해당 규칙은 반드시 다음과
같이 표현한다.

```text
L >= 80 -> 최소 high
```

## 값이 없는 경우

분석 가능한 정상 프레임이 없어 위험도를 계산할 수 없으면 `0`이나
`very_low`로 대신하지 않는다.

```json
{
  "riskScore": null,
  "riskLevel": null
}
```

`0점`은 계산 결과 실제 위험도가 가장 낮다는 뜻이고, `null`은 계산할 근거가
없다는 뜻이므로 반드시 구분한다.

## 용어 구분

다음 값은 서로 다른 의미이므로 혼용하지 않는다.

| 값 | 의미 |
|---|---|
| `personCount` | 프레임에서 탐지한 사람 수 |
| C (`visualCongestionScore`) | 프레임 전체의 시각적 혼잡도 |
| L (`localPeakCongestionScore`) | 프레임 내부 국소 최대 혼잡도 |
| P (`persistenceScore`) | 영상에서 높은 혼잡도가 지속된 정도 |
| G (`growthScore`) | 영상에서 혼잡도가 증가한 정도 |
| `riskScore` | C·L·P·G로 계산한 최종 위험점수 |
| `riskLevel` | 최종 위험점수를 5단계로 분류한 값 |

사람 수나 C·L을 곧바로 `riskLevel`이라고 부르지 않는다.

## 구현 및 문서 작성 체크리스트

- [ ] 위험단계가 공식 5단계 중 하나인가?
- [ ] API와 JSON에서 소문자 값을 사용하는가?
- [ ] Java enum에서 대문자 값을 사용하는가?
- [ ] 화면 표시가 한국어 명칭과 지정 색상을 따르는가?
- [ ] 점수 경계가 `21 / 41 / 61 / 81`과 일치하는가?
- [ ] 계산 불가를 `0` 또는 `very_low`로 치환하지 않았는가?
- [ ] 신규 코드나 문서에서 레거시 4단계 명칭을 생성하지 않는가?
- [ ] `L >= 80`, `L >= 90 AND P >= 70` 예외가 각각 `high`,
  `very_high`를 사용하고 있는가?

## 기준 문서 우선순위

위험단계 이름이나 경계가 다른 문서와 충돌하면 다음 순서로 판단한다.

1. 이 문서의 위험단계 이름과 점수 경계
2. `risk-calculation-spec.md`의 제보 단위 위험도 공식
3. `grid-risk-calculation-spec.md`의 격자 단위 집계 규칙
4. 실험·제안·검증 문서

실험 문서에 `CAUTION`, `DANGER` 같은 과거 명칭이 남아 있어도 신규 구현의
근거로 사용하지 않는다.

## 관련 문서

- `ai/docs/risk-calculation-spec.md` — 제보 단위 C·L·P·G 위험도 공식
- `ai/docs/grid-risk-calculation-spec.md` — 지도 격자 단위 위험도 명세
- `ai/docs/backend-integration-contract.md` — AI와 백엔드 응답 계약
- `ai/docs/congestion-scoring.md` — AI 프레임 단위 C·L 계산 규칙
- `frontend-admin/src/utils/adminRisk.ts` — 프론트 표시 기준 구현
- `backend/src/main/java/com/ojimasam/safy/analysis/domain/risk/RiskLevel.java`
  — 백엔드 위험단계 구현
