# 격자 단위 위험도 계산 구현 명세

## 0. 이 문서를 읽는 사람에게

이 문서는 백엔드에서 **격자 단위 위험도**(`grid_risk_snapshots`)를 구현하기
위한 명세다. 코드를 그대로 옮겨 쓸 수 있는 수준으로 작성했다.

- 관련 이슈: `S15P11A503-102` (위험도 계산)
- 선행 문서: `ai/docs/risk-calculation-spec.md` (제보 단위 위험도)
- 작성: AI 담당 (위험도 공식 설계자)

### 위험도는 두 층이다

```
제보 단위 위험도   영상 1건    C·L·P·G (프레임 분석)   risk-calculation-spec.md
격자 단위 위험도   지도 셀     제보들 + 121곳 혼잡도    이 문서
```

**제보 단위 위험도가 이 문서의 입력 중 하나다.** 먼저 그 문서를 읽어야 한다.

### 격자가 두 가지라 헷갈리기 쉽다

```
지도 격자 (grid_cells)   위경도 기반, 지역을 나눔       <- 이 문서
이미지 8×8 격자          사진 한 장을 64칸으로 나눔     <- AI 내부 C·L 계산
```

AI 응답의 `visualCongestionScore`(C)와 `localPeakCongestionScore`(L)는 **사진 한
장 안에서** 사람이 어디에 뭉쳤는지를 재는 값이라 지도와 무관하다. 이 문서의
격자는 `grid_cells` 테이블 쪽이다.

### 테스트 벡터가 진실의 기준이다

10장의 기대값은 검증된 참조 구현으로 계산한 값이다. 이 문서의 Java 코드는
컴파일 검증을 하지 못했다(작성 환경에 JDK가 없다).

**테스트가 실패하면 벡터가 아니라 Java 코드를 고쳐라.**

---

## 1. 구현 순서 체크리스트

```
[ ] 1. 백엔드 담당자와 패키지 위치 합의 (2장)
[ ] 2. 제보 단위 위험도 구현 완료 확인 (risk-calculation-spec.md)
[ ] 3. GridObservation, GridRiskInput, GridRiskSnapshot 작성 (6장)
[ ] 4. PublicCongestionScore 작성 (5장)
[ ] 5. GridRiskAssessor 작성 (7장)
[ ] 6. GridRiskAssessorTest 작성 (10장)
[ ] 7. ./gradlew test 통과 확인 (11장)
[ ] 8. 스케줄러와 연결 (12장)
```

---

## 2. 패키지 위치

제보 단위 명세와 같은 위치를 가정한다. `S15P11A503-21`에서 정한 위치가 있으면
그쪽을 따른다.

```
backend/src/main/java/com/ojimasam/safy/report/domain/
  GridObservation.java
  GridRiskInput.java
  GridRiskSnapshot.java
  PublicCongestionScore.java
  GridRiskAssessor.java
```

`RiskLevel`은 제보 단위 명세에서 만든 것을 재사용한다. 새로 만들지 않는다.

---

## 3. 무엇을 계산하는가

한 격자에서 **최근 15분 동안 모인 근거**를 하나의 위험도로 요약한다.

```
근거 1   그 격자에 들어온 제보들의 제보 단위 위험도
근거 2   그 격자에 매핑된 121곳 장소의 혼잡도 관측
```

문화행사는 **계산에 들어가지 않는다.** 4장 참고.

결과는 `grid_risk_snapshots` 한 행이다.

---

## 4. 문화행사는 왜 계산에 넣지 않는가

행사 데이터는 **왜 이렇게 되었는지 설명하는 용도**다. 위험도 입력이 아니다.

```
강남에 제보가 몰림 + 121곳 혼잡도 높음  ->  격자 위험도 high
그 격자의 행사를 조회                    ->  "○○ 축제로 인파가 몰린 것으로 보입니다"
```

계산에서 빼는 이유는 세 가지다.

**1. 인원 정보가 없다.** `events` 테이블에 `title`, `venue_name`, `start_date`,
`end_date`, 위경도만 있고 예상 인원이 없다. 가산값을 정할 근거가 없다.

**2. 이중 계산이 된다.** 행사로 사람이 몰리면 그 결과가 **이미 제보와 121곳
혼잡도에 나타난다.** 거기에 행사 가산을 더하면 같은 현상을 두 번 센다.

**3. 반대 방향 오류가 생긴다.** 행사가 열려도 사람이 안 모일 수 있다. 가산을
넣으면 실제로는 한산한 지역의 위험도가 올라간다.

`event_grid_mappings`는 위험도 계산이 아니라 **설명 문구를 만들 때 그 격자의
행사를 조회하는 용도**다.

---

## 5. 121곳 혼잡도를 점수로 바꾸는 규칙

### 5.1 변환표

```
여유         10
보통         30
약간 붐빔     45
붐빔         60
```

### 5.2 왜 붐빔이 60인가 — 공공데이터만으로는 medium 이 상한이다

이것이 이 문서에서 가장 중요한 결정이다.

**`high`(61점) 이상은 실제 현장 증거(제보)가 있어야 도달한다.** 붐빔 60은 정확히
그 직전이다.

근거는 서울시 척도가 실제로 재는 것이다. 실제 API 응답을 보면 이렇다.

```text
광화문·덕수궁, "약간 붐빔", 22,000~24,000명
AREA_CONGEST_MSG: "사람이 다소 몰려 있습니다."
```

**광화문에 2만 4천 명인데 '약간 붐빔'이다.** 서울시 4단계는 **보행 편의성**
척도이지 위험도 척도가 아니다. 최고 등급 '붐빔'도 "도보 이동이 어렵다" 수준이고,
압사 위험 구간은 이 척도의 범위 밖이다.

그리고 강남역·홍대 같은 곳은 **평일 저녁마다 '붐빔'이 뜬다.** 일상적인 값이다.

붐빔을 80으로 두면 이런 일이 생긴다.

- 금요일 저녁마다 강남·홍대 격자가 `high`로 켜진다.
- 매일 켜지는 경고는 관제사가 무시하게 된다.
- 그러면 진짜 위험할 때도 보지 않는다.

7장의 `max()` 구조에서 **121곳 값이 격자 위험도의 바닥선을 정한다.** 서울 주요
121곳이 상시 `high`면 지도가 늘 주황색이 된다.

### 5.3 감수하는 것

121곳 지역에서 실제로 위험한 군중이 형성됐는데 **아무도 제보하지 않으면
`medium`에 머문다.**

받아들일 만하다고 본다. 3분 주기로 넓은 지역을 관측한 값이고 서울시 척도 자체가
위험 구간을 구분하지 못한다. 없는 정보를 점수로 만들어내는 것보다 낫다. SAFY는
기존 안전관리 프로세스를 보조하는 것이지 대체하지 않으며, 121곳은 서울시가
자체 모니터링한다.

### 5.4 추정 인원을 쓰지 않는 이유

`CityCongestionObservation`에 `populationMin`/`populationMax`가 있어 실제 숫자를
쓰고 싶어진다. 쓰지 않는다.

**장소마다 면적이 다르다.** 광화문광장 1만 명과 좁은 상권 1만 명은 위험도가
전혀 다르다. 서울시가 이미 면적을 고려해 4단계로 정규화한 값이니 라벨을 쓰는
것이 맞다. 인원은 화면 표시용으로만 쓴다.

이는 제보 단위에서 `personCount`를 보조값으로 둔 것과 같은 이유다.

### 5.5 코드

```java
package com.ojimasam.safy.report.domain;

import com.ojimasam.safy.place.domain.type.CongestionLevel;

/**
 * 서울시 실시간 도시데이터의 인구 혼잡도 4단계를 SAFY 위험 점수로 바꾼다.
 *
 * 서울시 척도는 보행 편의성 기준이며 위험도 척도가 아니다. 최고 등급 '붐빔'도
 * "도보 이동이 어렵다" 수준이므로, 공공데이터만으로는 MEDIUM 을 넘지 않도록
 * 붐빔을 60(HIGH 시작인 61 직전)에 둔다.
 */
public final class PublicCongestionScore {

	public static final double RELAXED_SCORE = 10.0;
	public static final double NORMAL_SCORE = 30.0;
	public static final double SLIGHTLY_CROWDED_SCORE = 45.0;
	public static final double CROWDED_SCORE = 60.0;

	/** 공공데이터 단독 관측의 신뢰도. 정기 관측이지만 넓은 지역 평균이다. */
	public static final double PUBLIC_DATA_CONFIDENCE = 0.5;

	private PublicCongestionScore() {
	}

	public static double of(CongestionLevel level) {
		return switch (level) {
			case RELAXED -> RELAXED_SCORE;
			case NORMAL -> NORMAL_SCORE;
			case SLIGHTLY_CROWDED -> SLIGHTLY_CROWDED_SCORE;
			case CROWDED -> CROWDED_SCORE;
		};
	}
}
```

---

## 6. 입력 계약

### 6.1 제보 관측

```java
package com.ojimasam.safy.report.domain;

/**
 * 시간 창 안에 그 격자로 들어온 제보 1건의 위험도 결과.
 *
 * finalRisk 가 null 인 제보(분석 성공 프레임이 없어 위험도를 낼 수 없던 경우)는
 * 계산에서 제외한다.
 */
public record GridObservation(
		Long reportId,
		Long reporterUserId,
		Double finalRisk,
		double riskConfidence
) {
}
```

`reports` 테이블에 `grid_cell_id`가 `NOT NULL`로 있으므로 제보는 생성 시점에
격자가 정해진다. 별도 매핑 로직이 필요 없다.

### 6.2 계산 입력

```java
package com.ojimasam.safy.report.domain;

import com.ojimasam.safy.place.domain.type.CongestionLevel;
import java.util.List;

/**
 * 격자 1개의 위험도를 계산하기 위한 입력.
 *
 * reportObservations  시간 창 안에 이 격자로 들어온 제보들
 * publicCongestion    이 격자에 매핑된 121곳 장소의 최신 혼잡도. 없으면 null
 */
public record GridRiskInput(
		Long gridCellId,
		List<GridObservation> reportObservations,
		CongestionLevel publicCongestion
) {
}
```

한 격자에 여러 장소가 매핑될 수 있다(`place_grid_mappings`). 그 경우 **가장 높은
혼잡도 등급**을 넘긴다. 최댓값을 쓰는 이유는 7장과 같다.

### 6.3 출력

```java
package com.ojimasam.safy.report.domain;

/**
 * grid_risk_snapshots 한 행에 대응한다.
 *
 * score 가 null 이면 근거가 없어 위험도를 낼 수 없는 격자다.
 * 0 으로 채우면 안 된다. 7.4 참고.
 */
public record GridRiskSnapshot(
		Long gridCellId,
		Double score,
		RiskLevel congestionLevel,
		GridObservationStatus observationStatus,
		int evidenceCount,
		int distinctReporterCount,
		double confidenceScore,
		String calculationVersion
) {
}
```

```java
package com.ojimasam.safy.report.domain;

/** 격자 위험도 점수가 어떤 근거에서 나왔는지 나타낸다. */
public enum GridObservationStatus {
	REPORTED,
	PUBLIC_DATA,
	NO_DATA
}
```

---

## 7. 계산 규칙

### 7.1 구조

```
reportRisk = 시간 창 안 제보들의 finalRisk 최댓값
publicRisk = 121곳 혼잡도 라벨 -> 점수 변환

score = max(reportRisk, publicRisk)
```

### 7.2 왜 가중평균이 아니라 최댓값인가

**커버리지가 서로 다르기 때문이다.** 제보만 있는 격자, 121곳만 있는 격자, 둘 다
있는 격자, 아무것도 없는 격자가 섞인다. 가중평균이면 한쪽이 없을 때마다
재정규화해야 하고, 그것은 제보 단위에서 `P`·`G`가 `null`일 때 겪은 문제와 같다.

최댓값은 **"둘 중 더 위험한 신호를 따른다"**라 규칙이 단순하고, 위험을 낮추는
방향으로 작동하지 않는다. 이는 문서 전체에 일관된 원칙이다.

제보가 여러 건일 때도 최댓값을 쓴다. **가장 위험한 관측이 그 격자를 대표한다.**
제보 단위에서 C·L을 평활 후 최댓값으로 집계한 것과 같은 논리다.

### 7.3 시간 창 — 최근 15분

```
window_started_at = 계산 시각 - 15분
window_ended_at   = 계산 시각
```

121곳이 3분 주기로 수집되므로 15분이면 5개 표본이 들어온다. 제보 영상 길이(수십
초에서 수 분)도 감싼다.

시간 창 밖의 제보와 관측은 사용하지 않는다.

### 7.4 근거가 없으면 `score`는 `null`이다

**대부분의 격자에는 아무 데이터도 없다.**

```
제보 없음 + 121곳 매핑 없음  ->  score = null, observationStatus = NO_DATA
```

`0`으로 채우면 안 된다. 0은 `very_low`(안전함)로 읽혀서 **지도가 온통 초록색이
된다.** 데이터가 없는 것과 안전한 것은 다르다. ERD의 `score NUMERIC(5,2) NULL`도
이 전제로 보인다.

화면에서는 `NO_DATA` 격자를 색칠하지 않고 비워두는 것이 맞다.

### 7.5 `observationStatus`는 점수의 출처를 가리킨다

```
score 가 reportRisk 에서 나옴   ->  REPORTED
score 가 publicRisk 에서 나옴   ->  PUBLIC_DATA
근거 없음                       ->  NO_DATA
```

동점이면 `REPORTED`다. 제보가 더 강한 근거이기 때문이다.

제보가 있어도 121곳 값이 더 높으면 `PUBLIC_DATA`가 된다. 그 격자에 제보가
있었다는 사실은 `evidenceCount`와 `distinctReporterCount`에 남는다.

### 7.6 `confidenceScore`

**점수를 뒷받침하는 근거의 신뢰도**다. 점수 출처에 따라 다르게 계산한다.

```
NO_DATA      0.0

PUBLIC_DATA  0.5 고정
             정기 관측이라 안정적이지만 넓은 지역 평균이라 격자 정밀도가 낮다.

REPORTED     min(0.3 + 0.2 × distinctReporterCount, 1.0)
               × (제보들의 riskConfidence 최댓값)
```

제보자 수를 쓰는 이유는 **같은 사람이 3건 올린 것보다 3명이 각각 올린 것이 더
믿을 만하기 때문**이다. `distinct_reporter_count` 컬럼이 ERD에 있는 것도 그
의도로 보인다.

```
제보자 1명  -> 0.5
제보자 2명  -> 0.7
제보자 3명  -> 0.9
제보자 4명+ -> 1.0
```

여기에 제보 자체의 신뢰도(`riskConfidence`)를 곱한다. 제보 프레임이 몇 장 안
되거나 품질이 나빴다면 격자 위험도도 그만큼 덜 믿어야 한다.

### 7.7 `evidenceCount`

```
evidenceCount = 사용한 제보 수 + (121곳 관측이 있으면 1)
```

121곳 관측은 15분 창에 5건이 들어와도 **1로 센다.** 같은 소스의 반복이라 여러
건으로 세면 근거가 많은 것처럼 오해된다.

`finalRisk`가 `null`인 제보는 세지 않는다. 위험도의 근거가 아니기 때문이다.

### 7.8 `congestionLevel`

제보 단위와 같은 `RiskLevel` 5단계를 쓴다. 프론트
`frontend-admin/src/utils/adminRisk.ts`와 통일한다.

```
score >= 81  very_high
score >= 61  high
score >= 41  medium
score >= 21  low
그 외        very_low
score = null -> congestionLevel 도 null
```

---

## 8. GridRiskAssessor

```java
package com.ojimasam.safy.report.domain;

import java.util.List;

/**
 * 격자 1개의 위험도를 계산한다.
 *
 * DB, Spring 에 의존하지 않는 순수 계산 클래스다.
 * 근거가 없으면 0 이 아니라 null 을 반환한다.
 */
public final class GridRiskAssessor {

	public static final String CALCULATION_VERSION = "safy-grid-risk-v0.1";
	public static final int WINDOW_MINUTES = 15;
	public static final double REPORTER_CONFIDENCE_BASE = 0.3;
	public static final double REPORTER_CONFIDENCE_STEP = 0.2;

	private GridRiskAssessor() {
	}

	public static GridRiskSnapshot assess(GridRiskInput input) {
		List<GridObservation> usable = input.reportObservations() == null
				? List.of()
				: input.reportObservations().stream()
						.filter(observation -> observation.finalRisk() != null)
						.toList();

		Double reportRisk = usable.stream()
				.map(GridObservation::finalRisk)
				.max(Double::compare)
				.orElse(null);

		Double publicRisk = input.publicCongestion() == null
				? null
				: PublicCongestionScore.of(input.publicCongestion());

		int distinctReporters = (int) usable.stream()
				.map(GridObservation::reporterUserId)
				.distinct()
				.count();

		int evidenceCount = usable.size() + (publicRisk == null ? 0 : 1);

		if (reportRisk == null && publicRisk == null) {
			return new GridRiskSnapshot(
					input.gridCellId(), null, null,
					GridObservationStatus.NO_DATA,
					evidenceCount, distinctReporters, 0.0,
					CALCULATION_VERSION);
		}

		double score;
		GridObservationStatus status;
		double confidence;

		boolean reportWins = reportRisk != null
				&& (publicRisk == null || reportRisk >= publicRisk);

		if (reportWins) {
			score = reportRisk;
			status = GridObservationStatus.REPORTED;
			confidence = reporterFactor(distinctReporters) * maxRiskConfidence(usable);
		} else {
			score = publicRisk;
			status = GridObservationStatus.PUBLIC_DATA;
			confidence = PublicCongestionScore.PUBLIC_DATA_CONFIDENCE;
		}

		score = Math.max(0.0, Math.min(100.0, score));

		return new GridRiskSnapshot(
				input.gridCellId(),
				roundToTwoDecimals(score),
				RiskLevel.fromScore(score),
				status,
				evidenceCount,
				distinctReporters,
				roundToFourDecimals(Math.max(0.0, Math.min(1.0, confidence))),
				CALCULATION_VERSION);
	}

	private static double reporterFactor(int distinctReporters) {
		return Math.min(
				REPORTER_CONFIDENCE_BASE + REPORTER_CONFIDENCE_STEP * distinctReporters,
				1.0);
	}

	private static double maxRiskConfidence(List<GridObservation> usable) {
		return usable.stream()
				.mapToDouble(GridObservation::riskConfidence)
				.max()
				.orElse(0.0);
	}

	private static double roundToTwoDecimals(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private static double roundToFourDecimals(double value) {
		return Math.round(value * 10000.0) / 10000.0;
	}
}
```

---

## 9. 행사 설명 문구

위험도 계산과 분리된 별개의 조회다.

```
격자 위험도가 medium 이상
  -> event_grid_mappings 로 그 격자의 진행 중 행사 조회
     (start_date <= 오늘 <= end_date)
  -> "○○ 축제(8/1~8/3)로 인파가 몰린 것으로 보입니다"
```

`events` 테이블의 `title`, `venue_name`, `start_date`, `end_date`가 문구에 그대로
쓰인다. **예상 인원 정보가 없어도 되는 용도다.**

행사가 없으면 문구를 생략한다. 위험도는 영향받지 않는다.

---

## 10. 테스트 벡터

### 10.1 기대값

`riskConfidence`는 제보 단위 위험도가 함께 반환하는 값이다.

| 벡터 | 제보 | 121곳 | 제보자 | **score** | **등급** | **status** | **confidence** | 근거 |
|---|---|---|---:|---:|---|---|---:|---:|
| G1 | 없음 | 없음 | 0 | **null** | null | `NO_DATA` | 0.0 | 0 |
| G2 | 없음 | 붐빔 | 0 | 60.0 | `medium` | `PUBLIC_DATA` | 0.5 | 1 |
| G3 | 없음 | 여유 | 0 | 10.0 | `very_low` | `PUBLIC_DATA` | 0.5 | 1 |
| G4 | 75 (conf .86) | 없음 | 1 | 75.0 | `high` | `REPORTED` | 0.43 | 1 |
| G5 | 30 (conf .86) | 붐빔 | 1 | 60.0 | `medium` | `PUBLIC_DATA` | 0.5 | 2 |
| G6 | 75/60/40 | 보통 | 3 | 75.0 | `high` | `REPORTED` | 0.81 | 4 |
| G7 | 50 (conf .86) | 붐빔 | 1 | 60.0 | `medium` | `PUBLIC_DATA` | 0.5 | 2 |
| G8 | 분석불가(null) | 약간 붐빔 | 1 | 45.0 | `medium` | `PUBLIC_DATA` | 0.5 | 1 |

G6의 제보 신뢰도는 각각 `0.86 / 0.9 / 0.7`이다.

### 10.2 값이 왜 그렇게 나오는지

**G1** 근거가 없으니 `score`가 `null`이다. `0`이 아니다. 지도에서 색칠하지 않는다.

**G4 = 0.43.** 제보자 1명이라 `0.3 + 0.2 × 1 = 0.5`이고, 제보 자체의 신뢰도
`0.86`을 곱해 `0.43`이다. **제보 1건만으로는 신뢰도가 절반 이하다.**

**G5** 제보(30)보다 121곳(60)이 높아 `score`가 60이고 출처가 공공데이터라
`PUBLIC_DATA`다. 제보가 있었다는 사실은 `evidenceCount = 2`와
`distinctReporterCount = 1`에 남는다.

**G6 = 0.81.** 제보자 3명이라 `0.3 + 0.2 × 3 = 0.9`이고 제보 신뢰도 최댓값
`0.9`를 곱해 `0.81`이다. **여러 사람이 제보하면 신뢰도가 올라간다.**
`evidenceCount = 4`는 제보 3건 + 121곳 관측 1이다.

**G7** 제보 위험도 50이 붐빔 60보다 낮다. **공공데이터가 바닥선 역할을 한다.**

**G8** `finalRisk`가 `null`인 제보는 계산에서 제외되므로 `evidenceCount`가 1이다
(121곳만). 제보가 있었지만 분석하지 못했다는 사실은 별도로 확인해야 한다.

### 10.3 JUnit 코드

```java
package com.ojimasam.safy.report.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ojimasam.safy.place.domain.type.CongestionLevel;

class GridRiskAssessorTest {

	private static final double DELTA = 0.005;
	private static final Long GRID = 1L;

	private static GridObservation report(
			long reportId, long reporterId, Double finalRisk, double confidence) {
		return new GridObservation(reportId, reporterId, finalRisk, confidence);
	}

	private static GridRiskSnapshot assess(
			List<GridObservation> reports, CongestionLevel publicLevel) {
		return GridRiskAssessor.assess(new GridRiskInput(GRID, reports, publicLevel));
	}

	@Test
	@DisplayName("G1 근거가 없으면 score 는 0 이 아니라 null 이다")
	void g1_noData() {
		GridRiskSnapshot snapshot = assess(List.of(), null);

		assertNull(snapshot.score());
		assertNull(snapshot.congestionLevel());
		assertEquals(GridObservationStatus.NO_DATA, snapshot.observationStatus());
		assertEquals(0.0, snapshot.confidenceScore(), DELTA);
		assertEquals(0, snapshot.evidenceCount());
	}

	@Test
	@DisplayName("G2 121곳 붐빔만 있으면 medium 이다 (공공데이터 단독 상한)")
	void g2_crowdedOnly() {
		GridRiskSnapshot snapshot = assess(List.of(), CongestionLevel.CROWDED);

		assertEquals(60.0, snapshot.score(), DELTA);
		assertEquals(RiskLevel.MEDIUM, snapshot.congestionLevel());
		assertEquals(GridObservationStatus.PUBLIC_DATA, snapshot.observationStatus());
		assertEquals(0.5, snapshot.confidenceScore(), DELTA);
		assertEquals(1, snapshot.evidenceCount());
	}

	@Test
	@DisplayName("G3 121곳 여유만 있으면 very_low 다")
	void g3_relaxedOnly() {
		GridRiskSnapshot snapshot = assess(List.of(), CongestionLevel.RELAXED);

		assertEquals(10.0, snapshot.score(), DELTA);
		assertEquals(RiskLevel.VERY_LOW, snapshot.congestionLevel());
	}

	@Test
	@DisplayName("G4 제보 1건만이면 신뢰도가 절반 이하다")
	void g4_singleReport() {
		GridRiskSnapshot snapshot = assess(
				List.of(report(1L, 100L, 75.0, 0.86)), null);

		assertEquals(75.0, snapshot.score(), DELTA);
		assertEquals(RiskLevel.HIGH, snapshot.congestionLevel());
		assertEquals(GridObservationStatus.REPORTED, snapshot.observationStatus());
		assertEquals(0.43, snapshot.confidenceScore(), DELTA);
		assertEquals(1, snapshot.distinctReporterCount());
	}

	@Test
	@DisplayName("G5 121곳이 제보보다 높으면 공공데이터가 바닥선이 된다")
	void g5_publicDataWins() {
		GridRiskSnapshot snapshot = assess(
				List.of(report(1L, 100L, 30.0, 0.86)), CongestionLevel.CROWDED);

		assertEquals(60.0, snapshot.score(), DELTA);
		assertEquals(GridObservationStatus.PUBLIC_DATA, snapshot.observationStatus());
		assertEquals(0.5, snapshot.confidenceScore(), DELTA);
		assertEquals(2, snapshot.evidenceCount());
		assertEquals(1, snapshot.distinctReporterCount());
	}

	@Test
	@DisplayName("G6 제보자가 여러 명이면 신뢰도가 올라간다")
	void g6_multipleReporters() {
		GridRiskSnapshot snapshot = assess(
				List.of(
						report(1L, 100L, 75.0, 0.86),
						report(2L, 200L, 60.0, 0.90),
						report(3L, 300L, 40.0, 0.70)),
				CongestionLevel.NORMAL);

		assertEquals(75.0, snapshot.score(), DELTA);
		assertEquals(RiskLevel.HIGH, snapshot.congestionLevel());
		assertEquals(GridObservationStatus.REPORTED, snapshot.observationStatus());
		assertEquals(0.81, snapshot.confidenceScore(), DELTA);
		assertEquals(4, snapshot.evidenceCount());
		assertEquals(3, snapshot.distinctReporterCount());
	}

	@Test
	@DisplayName("G8 finalRisk 가 null 인 제보는 계산에서 제외된다")
	void g8_unassessableReportIsExcluded() {
		GridRiskSnapshot snapshot = assess(
				List.of(report(1L, 100L, null, 0.0)),
				CongestionLevel.SLIGHTLY_CROWDED);

		assertEquals(45.0, snapshot.score(), DELTA);
		assertEquals(GridObservationStatus.PUBLIC_DATA, snapshot.observationStatus());
		assertEquals(1, snapshot.evidenceCount());
	}

	@Test
	@DisplayName("같은 사람이 여러 건 올려도 제보자 수는 1이다")
	void sameReporterCountsOnce() {
		GridRiskSnapshot snapshot = assess(
				List.of(
						report(1L, 100L, 70.0, 0.86),
						report(2L, 100L, 65.0, 0.86),
						report(3L, 100L, 60.0, 0.86)),
				null);

		assertEquals(1, snapshot.distinctReporterCount());
		assertEquals(3, snapshot.evidenceCount());
		assertEquals(0.43, snapshot.confidenceScore(), DELTA);
	}

	@Test
	@DisplayName("제보와 121곳이 동점이면 REPORTED 다")
	void tieGoesToReported() {
		GridRiskSnapshot snapshot = assess(
				List.of(report(1L, 100L, 60.0, 0.86)), CongestionLevel.CROWDED);

		assertEquals(60.0, snapshot.score(), DELTA);
		assertEquals(GridObservationStatus.REPORTED, snapshot.observationStatus());
	}

	@Test
	@DisplayName("계산 버전을 함께 기록한다")
	void recordsCalculationVersion() {
		GridRiskSnapshot snapshot = assess(List.of(), CongestionLevel.NORMAL);

		assertEquals("safy-grid-risk-v0.1", snapshot.calculationVersion());
	}
}
```

---

## 11. 검증

```bash
cd backend
./gradlew test --tests '*GridRiskAssessorTest*'
```

**테스트가 실패하면 벡터가 아니라 Java 코드를 고쳐라.**

---

## 12. 스케줄러와 저장

계산은 순수 함수이고, 언제 호출할지와 어디에 저장할지는 백엔드가 정한다.

```java
// 예시 — 실제 리포지토리 이름과 주기는 백엔드 구현에 맞춘다
Instant now = clock.instant();
Instant windowStart = now.minus(Duration.ofMinutes(GridRiskAssessor.WINDOW_MINUTES));

for (Long gridCellId : activeGridCellIds) {
    List<GridObservation> reports =
            reportRepository.findGridObservations(gridCellId, windowStart, now);
    CongestionLevel publicLevel =
            cityCongestionRepository.highestLevelByGrid(gridCellId, windowStart, now);

    GridRiskSnapshot snapshot = GridRiskAssessor.assess(
            new GridRiskInput(gridCellId, reports, publicLevel));

    gridRiskSnapshotRepository.save(snapshot, windowStart, now, now);
}
```

연결할 때 확인할 것이다.

```
[ ] 시간 창을 프레임 순번이 아니라 실제 시각으로 자르는가
[ ] finalRisk 가 null 인 제보를 0 으로 바꾸지 않는가
[ ] score 가 null 일 때 0 으로 저장하지 않는가
[ ] 한 격자에 여러 장소가 매핑되면 가장 높은 혼잡도를 넘기는가
[ ] calculation_version 을 함께 저장하는가
[ ] 근거가 없는 격자를 매번 저장할지, 건너뛸지 정했는가
```

마지막 항목은 저장량 문제다. 서울 전체를 격자로 나누면 대부분이 `NO_DATA`다.
**`NO_DATA` 격자는 저장하지 않고 조회 시 없으면 `NO_DATA`로 간주하는 것**을
권한다.

---

## 13. 확정 이력과 재검토할 것

### 13.1 확정한 것

| 항목 | 결정 | 근거 |
|---|---|---|
| 구조 | `max(reportRisk, publicRisk)` | 커버리지가 달라 가중평균은 재정규화 필요 |
| 121곳 변환 | 10 / 30 / 45 / 60 | 공공데이터 단독 상한을 `medium` 으로 |
| 문화행사 | 계산 제외, 설명 문구용 | 인원 정보 없음, 이중 계산, 반대 방향 오류 |
| 시간 창 | 15분 | 121곳 3분 주기 = 5개 표본 |
| 제보 여러 건 | 최댓값 | 가장 위험한 관측이 격자를 대표 |
| `confidenceScore` | 제보자 수 × 제보 신뢰도 | 같은 사람 3건보다 3명 3건이 신뢰도 높음 |
| `evidenceCount` | 제보 수 + 121곳 있으면 1 | 같은 소스 반복을 여러 건으로 세지 않음 |
| `score` 없음 | `null` | 0 이면 지도가 온통 초록색 |
| 등급 | `RiskLevel` 5단계 재사용 | 프론트 `adminRisk.ts` 와 통일 |

### 13.2 재검토할 것

```
1. 121곳 변환값 (10/30/45/60)
   가장 근거가 약하다. 서울시 '붐빔'이 SAFY 척도로 몇 점인지 알 방법이 없다.
   121곳 지역에 제보가 들어오면 그 시점 라벨과 제보 위험도를 대조해 조정한다.
   그것이 유일한 근거 확보 경로다.

2. 시간 창 15분
   실제 제보 유입 빈도를 보고 조정한다. 너무 길면 지나간 상황이 남고,
   너무 짧으면 제보가 창에 안 걸린다.

3. confidenceScore 계수 (0.3 + 0.2 × 제보자 수)
   제보자 수와 실제 정확도의 관계를 관측한 뒤 조정한다.

4. PUBLIC_DATA 고정 신뢰도 0.5
   격자 크기와 장소 면적의 비율에 따라 달라질 수 있다.
```

### 13.3 이 문서가 다루지 않는 것

- 제보 단위 위험도 계산 (`risk-calculation-spec.md`)
- 격자 정의와 위경도 매핑 (`grid_cells` 생성)
- 스케줄러 주기와 저장 정책
- 지도 렌더링과 색상

---

## 14. 관련 문서

- `ai/docs/risk-calculation-spec.md` — 제보 단위 위험도 (이 문서의 입력)
- `ai/docs/backend-integration-contract.md` — AI API 요청·응답 명세
- `ai/docs/congestion-scoring.md` — C·L 계산 규칙 (이미지 8×8 격자)
- `frontend-admin/src/utils/adminRisk.ts` — 등급 이름·경계의 원본
