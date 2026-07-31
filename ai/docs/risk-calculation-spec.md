# 최종 위험도 계산 구현 명세

## 0. 이 문서를 읽는 사람에게

이 문서는 백엔드에서 **혼잡도 지속성 P**, **증가 추세 G**, **최종 위험도
FinalRisk**와 **FinalRiskLevel**을 구현하기 위한 명세다. 코드를 그대로 옮겨 쓸
수 있는 수준으로 작성했다.

- 관련 이슈: `S15P11A503-102` (위험도 계산)
- 선행 이슈: `S15P11A503-21` (분석 결과 저장), `S15P11A503-96` (AI 호출 배선)
- 작성: AI 담당 (위험도 공식 설계자)

### 중요: 테스트 벡터가 진실의 기준이다

9장과 13장의 기대값은 검증된 참조 구현으로 계산한 값이다. 이 문서의 Java
코드는 컴파일 검증을 하지 못했다(작성 환경에 JDK가 없었다).

**테스트가 실패하면 벡터가 아니라 Java 코드를 고쳐라.**

### 등급 이름은 프론트가 진실이다

`frontend-admin/src/utils/adminRisk.ts`가 이미 5단계를 구현하고 있다. 백엔드는
그 이름과 경계를 그대로 따른다. 이 문서에서 새로 지어낸 이름은 없다.

---

## 1. 구현 순서 체크리스트

```
[ ] 1. 백엔드 담당자와 패키지 위치 합의 (2장)
[ ] 2. FrameObservation, CongestionTrend 작성 (5장)
[ ] 3. CongestionTrendCalculator 작성 (8장)
[ ] 4. CongestionTrendCalculatorTest 작성 (9장)
[ ] 5. RiskLevel, RiskAssessment, RiskAssessmentInput 작성 (11장)
[ ] 6. RiskAssessor 작성 (11장)
[ ] 7. RiskAssessorTest 작성 (13장)
[ ] 8. ./gradlew test 통과 확인 (14장)
[ ] 9. 21번 완료 후 리포지토리와 연결 (15장)
```

1~8번은 **DB, AI 서버, 다른 이슈와 무관하게 지금 할 수 있다.** 순수 계산
로직이고 새 파일만 추가한다. 기존 파일은 건드리지 않는다.

---

## 2. 패키지 위치 — 먼저 합의할 것

이 문서는 아래를 가정한다. **`S15P11A503-21`이 진행 중이므로 그쪽에서 이미
분석·위험도 패키지를 만들고 있다면 그 위치를 따르라.** 클래스 내용은 바뀌지
않고 `package` 선언만 달라진다.

```
backend/src/main/java/com/ojimasam/safy/report/domain/
  FrameObservation.java
  CongestionTrend.java
  CongestionTrendCalculator.java
  RiskLevel.java
  RiskAssessment.java
  RiskAssessmentInput.java
  RiskAssessor.java

backend/src/test/java/com/ojimasam/safy/report/domain/
  CongestionTrendCalculatorTest.java
  RiskAssessorTest.java
```

### 이름 충돌 두 건 — 재사용 금지

**1. `CongestionLevel`** (`place/domain/type/CongestionLevel.java`)

서울시 공공데이터의 인구 혼잡도 4단계(`RELAXED`, `NORMAL`,
`SLIGHTLY_CROWDED`, `CROWDED`)다. SAFY 위험등급과 다른 개념이므로 위험등급은
`RiskLevel`로 새로 만든다.

**2. `ReviewStatus`** (`frontend-admin/src/types/admin.ts`)

프론트에서 `'pending' | 'approved' | 'rejected'`로 이미 쓰인다. **관제사 승인
워크플로**다. 위험도 설계 문서의 `ReviewStatus = CONFIRM_REQUIRED`와 완전히
다른 개념이므로, "AI 결과를 사람이 확인해야 한다"는 신호는
**`confirmRequired` 불리언**으로 분리한다. 12장 참고.

---

## 3. P·G·FinalRisk 가 무엇인가

### P — 혼잡도 지속성 (0~100)

혼잡한 상태가 **얼마나 오래 유지됐는가**. 스치는 혼잡보다 지속되는 혼잡이
위험하다. 100은 목표 지속시간(60초) 이상 연속으로 혼잡했다는 뜻이다.

### G — 혼잡도 증가 추세 (0~100)

혼잡도가 **올라가고 있는가**. 군중이 계속 유입되는 상황이 정체 상태보다
위험하다. 0은 증가하지 않았다는 뜻이고 100은 2배 이상 증가했다는 뜻이다.

### FinalRisk (0~100) 와 FinalRiskLevel

C·L을 가중평균한 기본 점수에 P·G를 보너스로 더한다. 11장 참고.

### 왜 감소를 음수로 만들지 않는가

G는 0에서 잘린다. 혼잡도가 줄어들어도 G는 0이고 음수가 되지 않는다.

SAFY는 기존 안전관리 프로세스를 대체하지 않고 보조하며 최종 판단은 사람이
한다. 따라서 **위험을 올리는 방향으로만 쓰고 낮추는 근거로는 쓰지 않는다.**
이 원칙은 문서 전체에 일관되게 적용된다.

---

## 4. 사전 조건과 현재 제약

### 4.1 프레임 계열 저장이 아직 없다

P와 G는 프레임 시계열에서 계산한다. 현재 백엔드에는 프레임별 C·L을 저장하는
엔티티가 없다. `ReportAnalysisResponse`는 제보 1건당 결과 한 줄이다.

```
S15P11A503-96   프레임을 AI로 보내는 배선   -> 미배정
S15P11A503-21   분석 결과 저장             -> 진행 중
S15P11A503-102  P·G + 최종 위험도          -> 이 문서
```

**그래서 계산 로직을 DB와 분리해 먼저 만든다.** 21번이 끝나면 리포지토리에서
리스트를 꺼내 넣기만 하면 된다.

### 4.2 실제 시계열 데이터로 검증할 수 없다

현재 프로젝트가 가진 검증 데이터는 전부 단일 이미지다. 프레임 시계열 데이터가
하나도 없다. **산술이 정확한지는 지금 확정할 수 있지만, 가중치와 임계값이
적절한지는 실제 제보가 쌓인 뒤에야 알 수 있다.** 16장에 조정 절차를 적었다.

### 4.3 공식값이 고밀도에서 신뢰할 수 없다 — 가장 중요한 제약

`PERSISTENCE_THRESHOLD = 80.0`과 예외 규칙 `L >= 80`은 프로젝트 위험도 설계의
값을 그대로 가져온 것이다. 그런데 `S15P11A503-138` 독립 홀드아웃 1,589장에서
측정한 결과, **정답 L이 80 이상인 1,272장에서 YOLO의 L은 한 번도 80을 넘지
못했다.**

실제 사례다.

| 표본 | 실제 | YOLO 공식값 | 보정 적용 시 |
|---|---|---|---|
| 광화문 집회 | 9,059명, L=100 | 1명, L=7.5 | 5,756명, L=100 |
| 밀집 표본 | 172명, L=100 | 17명, L=45.0 | 230명, L=100 |

같은 사진인데 보정 여부로 `very_low`와 `very_high`가 갈린다. **가중치를 어떻게
정해도 이 문제는 해결되지 않는다.** 해결 경로는 두 가지다.

1. AI 쪽 C·L 보정(`P2PNET_CONGESTION_CORRECTION_ENABLED`)을 활성화한다.
   서버 부하 실측(`S15P11A503-106`) 후에만 가능하다.
2. YOLO의 실제 분포에 맞춰 임계값을 낮춘다. 근거가 약하다.

**구현할 때는 상수 그대로 진행하라.** 값이 바뀌어도 코드는 바뀌지 않는다.
다만 값이 바뀌면 테스트 벡터 기대값을 다시 계산해야 한다.

G는 비율 기반이라 이 문제의 영향을 훨씬 덜 받는다. YOLO가 300명을 3명으로
세더라도 600명이 되면 6명으로 세므로 **배율 편향이 일정하면 비율은 보존된다.**
이것이 G를 절대 기울기가 아니라 비율로 정의한 이유다.

---

## 5. 입력 계약

### 5.1 FrameObservation

```java
package com.ojimasam.safy.report.domain;

import java.time.Instant;

/**
 * AI 서버가 프레임 1장을 분석한 결과 중 위험도 계산에 필요한 값만 담는다.
 *
 * analysisStatus 가 COMPLETED 인 프레임만 이 레코드로 만든다.
 * INVALID_FRAME 과 최종 실패한 FAILED 는 계열에 넣지 않는다.
 */
public record FrameObservation(
		Instant observedAt,
		double visualCongestionScore,
		double localPeakCongestionScore
) {
}
```

| 필드 | AI 응답 필드 | 설명 |
|---|---|---|
| `observedAt` | (프레임 촬영·추출 시각) | 백엔드가 관리 |
| `visualCongestionScore` | `visualCongestionScore` | 전체 혼잡도 C, 0~100 |
| `localPeakCongestionScore` | `localPeakCongestionScore` | 국소 최대 혼잡도 L, 0~100 |

### 5.2 계열을 만드는 규칙 — 반드시 지킬 네 가지

**1. `analysisStatus`가 `COMPLETED`인 프레임만 넣는다.**

```
COMPLETED      -> 계열에 포함
INVALID_FRAME  -> 제외 (재시도하지 않음)
FAILED         -> 1회 재시도, 재시도도 실패하면 제외
```

**2. 실패한 프레임을 사람 0명이나 C=0으로 넣으면 안 된다.**

`COMPLETED` + `personCount = 0`(정말 사람이 없는 정상 프레임)과 분석하지 못한
프레임은 완전히 다르다. 후자를 0으로 넣으면 없는 혼잡도 감소를 만들어내고 G가
실제보다 낮게 나온다.

**3. 프레임 순번이 아니라 실제 타임스탬프를 쓴다.**

프레임은 명목상 5초 간격이지만 제외된 프레임 때문에 실제 간격이 벌어진다.
인덱스로 계산하면 6장이 빠진 35초 구간이 5초로 취급된다.

**4. `congestionSource`가 섞이면 P·G를 계산하지 않는다.**

AI 응답의 `congestionSource`는 `"YOLO"` 또는 `"P2PNET_CORRECTED"`다. 보정이
켜졌다 꺼지면 한 제보 안에서 두 값이 섞일 수 있고, **스케일이 다른 값으로
추세를 계산하면 없는 증가가 만들어진다.** 섞이면 `congestionSourceMixed = true`로
넘긴다.

### 5.3 CongestionTrend

```java
package com.ojimasam.safy.report.domain;

/**
 * 프레임 계열 집계 결과.
 *
 * persistence 와 growth 는 표본이 부족하면 null 이다.
 * null 은 "추세 없음"이 아니라 "알 수 없음"이다. 0 으로 대체하면 안 된다.
 *
 * peak 값은 프레임이 1장이라도 있으면 계산된다. 짧은 제보에서도
 * C·L 기반 위험도는 산출할 수 있어야 하기 때문이다.
 */
public record CongestionTrend(
		Double persistence,
		Double growth,
		Double peakVisualCongestion,
		Double peakLocalCongestion
) {
	public static CongestionTrend unknown() {
		return new CongestionTrend(null, null, null, null);
	}
}
```

---

## 6. 어떤 지표로 계산하는가

| 출력 | 사용 지표 | 이유 |
|---|---|---|
| **P** | **L** | 위험등급 예외 규칙이 L 기준이고 `L >= 90 AND P >= 70`처럼 L과 함께 쓰인다 |
| **G** | **C** | 장면 전체의 혼잡도가 올라가는 추세를 본다 |
| **FinalRisk** | C, L, P, G | 11장 |

### 제보 단위 C·L 은 평활 후 최댓값을 쓴다

C와 L은 프레임마다 하나씩 있다. 제보 1건의 위험도를 계산하려면 먼저 합쳐야
한다. **평균이 아니라 최댓값**을 쓴다.

```
peakVisualCongestion = max(평활된 C)
peakLocalCongestion  = max(평활된 L)
```

이유 세 가지다.

1. **위험은 "가장 나빴던 순간"이 결정한다.** 평균을 쓰면 20초짜리 위험 구간이
   3분짜리 한산 구간에 희석된다.
2. **지속 시간은 이미 P가 담당한다.** 최댓값(얼마나 나빴나) × P(얼마나 오래)
   × G(얼마나 빠르게)로 역할이 나뉜다.
3. **평활을 거치므로 단발 이상치에 흔들리지 않는다.**

짧은 위험 구간은 L이 높아 등급이 올라가지만 P가 낮게 나오므로, 관제사는
"심했지만 짧았다"를 함께 본다. 위험을 놓치지 않는 방향이다.

---

## 7. 상수

### 7.1 추세 계산 상수

```java
NOMINAL_INTERVAL_SECONDS          = 5     // 프레임 명목 간격
TREND_WINDOW_SECONDS              = 30    // G 비교 구간 길이 (W)
PERSISTENCE_TARGET_SECONDS        = 60    // P 가 100 이 되는 지속시간 (T)
PERSISTENCE_MAX_GAP_SECONDS       = 15    // 지속 구간이 끊기는 간격
PERSISTENCE_THRESHOLD             = 80.0  // "혼잡하다" 판정선 (4.3 참고)
MINIMUM_FRAME_COUNT               = 6     // 이보다 적으면 P·G 는 null
MINIMUM_FRAMES_PER_TREND_WINDOW   = 2     // G 각 구간의 최소 프레임 수
```

`TREND_WINDOW_SECONDS`와 `PERSISTENCE_TARGET_SECONDS`는 제보 영상 길이 분포를
확인한 뒤 조정할 수 있다. **30초 제보라면 W=30초로는 비교 구간을 두 개 만들 수
없어 G가 항상 `null`이 된다.** 실제 제보 길이를 OpenVidu 담당자에게 확인하는
것이 좋다.

### 7.2 위험도 계산 상수

```java
WEIGHT_VISUAL                     = 0.4   // C 계수
WEIGHT_LOCAL_PEAK                 = 0.6   // L 계수
WEIGHT_PERSISTENCE                = 0.15  // P 계수
WEIGHT_GROWTH                     = 0.15  // G 계수

EXCEPTION_LOCAL_PEAK_HIGH         = 80.0  // L >= 80 -> 최소 HIGH
EXCEPTION_LOCAL_PEAK_VERY_HIGH    = 90.0  // L >= 90 AND
EXCEPTION_PERSISTENCE_VERY_HIGH   = 70.0  //   P >= 70 -> 최소 VERY_HIGH

FULL_CONFIDENCE_FRAME_COUNT       = 12    // 이 프레임 수부터 표본 신뢰도 100%
CONFIRM_REQUIRED_CONFIDENCE       = 0.5   // 이보다 낮으면 사람 확인 필요

RISK_RULE_VERSION                 = "safy-risk-v0.1"
```

**`WEIGHT_LOCAL_PEAK`의 상한은 0.8이다.** 그 이상이면 `C=0, L=80`에서도 이미
`HIGH`가 되어 예외 규칙 `L >= 80`이 절대 발동하지 않는다. 0.6을 쓰는 이유는
L이 포화되기 때문이다. 정답 L은 고밀도 표본의 74.3%에서 100에 붙으므로,
`wL`이 클수록 상단에서 점수가 뭉쳐 구분력을 잃는다. 0.6이면 L이 포화돼도 C가
40점을 남겨 등급을 가른다.

**보너스 최대 +30이 저밀도를 끌어올리지 못하는 이유**는 P가 정의상
`L >= 80`에서만 0보다 크기 때문이다. 저밀도 장면에서는 G만 붙어 최대 +15이며,
`L=25` 장면은 최대 보너스를 받아도 `low`에 머문다. 구조가 스스로를 제한한다.

**P와 G의 계수를 같게 둔 이유**는 나눌 근거가 없기 때문이다. 유입 속도(G)가
더 위험하다는 주장도, 지속(P)이 더 신뢰할 만하다는 주장도 가능하지만 프레임
시계열 데이터가 한 장도 없는 상태에서 비대칭을 주면 측정이 아니라 직감이다.
같게 두고 실제 데이터로 조정한다.

### 7.3 표본 부족은 `null`이다 — `0`이 아니다

```
프레임 6장 미만                     -> P = null, G = null
G 의 어느 한 구간에 프레임 2장 미만  -> G = null
congestionSource 혼재               -> P = null, G = null
프레임 0장                          -> 전부 null, 위험도 계산 불가
```

**절대 0으로 대체하면 안 된다.** 0은 "증가하지 않았다 / 지속되지 않았다"로
읽혀 위험도를 낮춘다. 데이터가 없는 것과 안전한 것은 다르다.

위험도 계산에서 `null`을 만나면 **그 항만 보너스에서 빼고 계산하며, 위험도를
낮추는 방향으로 보간하지 않는다.** 대신 `confirmRequired`를 `true`로 만든다.

### 7.4 평활 — 왜 중위값인가

YOLO가 한 프레임에서만 튀는 경우가 있다. 평활 없이 차이를 계산하면 그 한
프레임이 G를 100까지 올린다. **3점 이동 중위값**을 쓴다.

```
원본   10  10  10  90  10  10
중위값 10  10  10  10  10  10     <- 90 이 제거됨
평균   10  10  36  36  36  10     <- 90 이 세 칸에 퍼짐
```

9장의 벡터 `V5`가 정확히 이것을 검증한다.

---

## 8. 추세 계산 — 단계와 코드

### 8.1 P 단계

```
1. COMPLETED 프레임만 모아 시간 순으로 정렬한다.
2. 프레임이 6장 미만이면 null 을 반환한다.
3. L 값에 3점 이동 중위값을 적용한다.
4. 평활된 L 이 임계(80) 이상인 프레임들의 연속 구간을 찾는다.
   - 임계 미달 프레임을 만나면 구간이 끊긴다.
   - 앞 프레임과의 간격이 15초를 넘으면 구간이 끊긴다.
     (프레임이 누락된 구간에서 무슨 일이 있었는지 알 수 없으므로)
5. 각 구간의 길이 = (마지막 시각 - 첫 시각) + 5초
   - 프레임 1장을 5초 구간의 대표로 보기 때문에 5초를 더한다.
6. 가장 긴 구간의 길이를 목표(60초)로 나눈다.
7. P = 100 × min(비율, 1.0), 소수 1자리
```

### 8.2 G 단계

```
1. COMPLETED 프레임만 모아 시간 순으로 정렬한다.
2. 프레임이 6장 미만이면 null 을 반환한다.
3. C 값에 3점 이동 중위값을 적용한다.
4. 마지막 프레임 시각을 기준으로 두 구간을 나눈다.
   최근 구간: 마지막 시각으로부터 0초 이상 30초 미만
   기준 구간: 마지막 시각으로부터 30초 이상 60초 미만
5. 어느 한 구간의 프레임이 2장 미만이면 null 을 반환한다.
6. ratio = (최근 구간 평균 + 1) / (기준 구간 평균 + 1)
   - +1 은 기준 구간이 0 에 가까울 때 비율 폭발과 0 나누기를 막는다.
7. G = clamp((ratio - 1) × 100, 0, 100), 소수 1자리
```

### 8.3 CongestionTrendCalculator

```java
package com.ojimasam.safy.report.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 프레임 관측 계열에서 지속성 P, 증가 추세 G, 그리고 제보 단위 C·L 최댓값을
 * 계산한다.
 *
 * DB, Spring, AI 서버에 의존하지 않는 순수 계산 클래스다.
 * 표본이 부족하면 0 이 아니라 null 을 반환한다.
 */
public final class CongestionTrendCalculator {

	public static final int NOMINAL_INTERVAL_SECONDS = 5;
	public static final int TREND_WINDOW_SECONDS = 30;
	public static final int PERSISTENCE_TARGET_SECONDS = 60;
	public static final int PERSISTENCE_MAX_GAP_SECONDS = 15;
	public static final double PERSISTENCE_THRESHOLD = 80.0;
	public static final int MINIMUM_FRAME_COUNT = 6;
	public static final int MINIMUM_FRAMES_PER_TREND_WINDOW = 2;

	private CongestionTrendCalculator() {
	}

	public static CongestionTrend calculate(List<FrameObservation> frames) {
		if (frames == null || frames.isEmpty()) {
			return CongestionTrend.unknown();
		}

		List<FrameObservation> ordered = new ArrayList<>(frames);
		ordered.sort(Comparator.comparing(FrameObservation::observedAt));

		double[] smoothedVisual = movingMedian(extractVisual(ordered));
		double[] smoothedLocalPeak = movingMedian(extractLocalPeak(ordered));

		return new CongestionTrend(
				calculatePersistence(ordered, smoothedLocalPeak),
				calculateGrowth(ordered, smoothedVisual),
				maximum(smoothedVisual),
				maximum(smoothedLocalPeak));
	}

	private static Double calculatePersistence(
			List<FrameObservation> ordered,
			double[] smoothedLocalPeak) {

		if (ordered.size() < MINIMUM_FRAME_COUNT) {
			return null;
		}

		long longestSeconds = 0L;
		Instant runStart = null;
		Instant previous = null;

		for (int i = 0; i < ordered.size(); i++) {
			Instant current = ordered.get(i).observedAt();

			if (smoothedLocalPeak[i] < PERSISTENCE_THRESHOLD) {
				runStart = null;
				previous = null;
				continue;
			}

			boolean gapTooLarge = previous != null
					&& secondsBetween(previous, current) > PERSISTENCE_MAX_GAP_SECONDS;

			if (runStart == null || gapTooLarge) {
				runStart = current;
			}

			long covered = secondsBetween(runStart, current) + NOMINAL_INTERVAL_SECONDS;
			longestSeconds = Math.max(longestSeconds, covered);
			previous = current;
		}

		double ratio = (double) longestSeconds / PERSISTENCE_TARGET_SECONDS;
		return roundToOneDecimal(100.0 * Math.min(ratio, 1.0));
	}

	private static Double calculateGrowth(
			List<FrameObservation> ordered,
			double[] smoothedVisual) {

		if (ordered.size() < MINIMUM_FRAME_COUNT) {
			return null;
		}

		Instant last = ordered.get(ordered.size() - 1).observedAt();
		List<Double> recent = new ArrayList<>();
		List<Double> baseline = new ArrayList<>();

		for (int i = 0; i < ordered.size(); i++) {
			long age = secondsBetween(ordered.get(i).observedAt(), last);

			if (age < TREND_WINDOW_SECONDS) {
				recent.add(smoothedVisual[i]);
			} else if (age < 2L * TREND_WINDOW_SECONDS) {
				baseline.add(smoothedVisual[i]);
			}
		}

		if (recent.size() < MINIMUM_FRAMES_PER_TREND_WINDOW
				|| baseline.size() < MINIMUM_FRAMES_PER_TREND_WINDOW) {
			return null;
		}

		double ratio = (mean(recent) + 1.0) / (mean(baseline) + 1.0);
		double growth = (ratio - 1.0) * 100.0;

		return roundToOneDecimal(Math.max(0.0, Math.min(100.0, growth)));
	}

	private static double[] extractVisual(List<FrameObservation> ordered) {
		double[] values = new double[ordered.size()];
		for (int i = 0; i < ordered.size(); i++) {
			values[i] = ordered.get(i).visualCongestionScore();
		}
		return values;
	}

	private static double[] extractLocalPeak(List<FrameObservation> ordered) {
		double[] values = new double[ordered.size()];
		for (int i = 0; i < ordered.size(); i++) {
			values[i] = ordered.get(i).localPeakCongestionScore();
		}
		return values;
	}

	/**
	 * 3점 이동 중위값. 양 끝은 존재하는 이웃만 사용한다.
	 * 단일 프레임 이상치를 제거하기 위한 것이므로 평균을 쓰지 않는다.
	 */
	private static double[] movingMedian(double[] values) {
		double[] result = new double[values.length];

		for (int i = 0; i < values.length; i++) {
			double[] window = Arrays.copyOfRange(
					values,
					Math.max(0, i - 1),
					Math.min(values.length, i + 2));
			Arrays.sort(window);

			int middle = window.length / 2;
			result[i] = window.length % 2 == 1
					? window[middle]
					: (window[middle - 1] + window[middle]) / 2.0;
		}

		return result;
	}

	private static Double maximum(double[] values) {
		if (values.length == 0) {
			return null;
		}
		double max = values[0];
		for (double value : values) {
			max = Math.max(max, value);
		}
		return max;
	}

	private static long secondsBetween(Instant from, Instant to) {
		return Duration.between(from, to).getSeconds();
	}

	private static double mean(List<Double> values) {
		double sum = 0.0;
		for (double value : values) {
			sum += value;
		}
		return sum / values.size();
	}

	private static double roundToOneDecimal(double value) {
		return Math.round(value * 10.0) / 10.0;
	}
}
```

---

## 9. 추세 테스트 벡터

모든 벡터는 `t = 0`초에서 시작해 5초 간격이다(`V8` 제외).

### 9.1 기대값

| 벡터 | 상황 | 프레임 | **P** | **G** | peak C | peak L | 검증 목적 |
|---|---|---:|---:|---:|---:|---:|---|
| V1 | 평탄 | 12 | 0.0 | 0.0 | 10.0 | 20.0 | 변화 없을 때 둘 다 0 |
| V2 | 완만한 증가 | 12 | 0.0 | 27.8 | 15.0 | 30.0 | 증가를 비율로 잡아냄 |
| V3 | 급증 | 12 | 0.0 | 100.0 | 30.0 | 79.0 | 2배 이상은 100 에서 잘림 |
| V4 | 감소 | 12 | 16.7 | 0.0 | 30.0 | 80.0 | **감소가 음수가 되지 않음** |
| V5 | 단발 이상치 | 12 | 0.0 | 0.0 | 10.0 | 20.0 | **평활이 이상치를 제거함** |
| V6 | 지속 혼잡 | 12 | 100.0 | 0.0 | 50.0 | 91.0 | 60초 이상 지속은 100 |
| V7 | 짧은 스파이크 | 12 | 16.7 | 0.0 | 50.0 | 85.0 | 짧은 혼잡은 P 가 낮음 |
| V8 | 프레임 누락 | 10 | 50.0 | 100.0 | 30.0 | 90.0 | **누락이 지속 구간을 끊음** |
| V9 | 표본 부족 | 5 | null | null | 17.0 | 37.5 | **P·G 는 null, peak 는 계산됨** |
| V10 | 빈 계열 | 0 | null | null | null | null | 전부 null |
| V11 | 0에서 급증 | 12 | 0.0 | 100.0 | 14.5 | 57.5 | **0 나누기 방어** |

### 9.2 입력값

`(C, L)` 순서다.

```
V1  (10,20) × 12

V2  (10,20) (10,20) (11,22) (11,22) (12,24) (12,24)
    (13,26) (14,28) (14,28) (15,30) (15,30) (15,30)

V3  (10,20) (10,20) (10,20) (10,20) (10,20) (10,20)
    (20,50) (24,60) (28,70) (30,75) (30,78) (30,80)

V4  (30,80) (30,80) (28,75) (26,70) (24,65) (20,55)
    (15,40) (12,30) (10,25) (10,20) (10,20) (10,20)

V5  (10,20) (10,20) (10,20) (90,99) (10,20) (10,20)
    (10,20) (10,20) (10,20) (10,20) (10,20) (10,20)

V6  (50,85) (50,86) (50,88) (50,90) (50,90) (50,92)
    (50,91) (50,90) (50,88) (50,86) (50,85) (50,85)

V7  (10,20) (10,20) (10,20) (50,85) (50,86) (10,20)
    (10,20) (10,20) (10,20) (10,20) (10,20) (10,20)

V8  타임스탬프가 불규칙하다
    t=0 (10,85)  t=5 (10,86)  t=10 (10,88)  t=15 (10,90)
    t=20 (10,90) t=25 (10,90)
    t=60 (30,90) t=65 (30,90) t=70 (30,90)  t=75 (30,90)
                 ^^^ 35초 공백

V9  (10,20) (12,25) (14,30) (16,35) (18,40)

V10 (없음)

V11 (0,0) (0,0) (0,0) (0,0) (0,0) (0,0)
    (5,20) (8,30) (10,40) (12,50) (14,55) (15,60)
```

### 9.3 값이 왜 그렇게 나오는지

**V4 = 16.7.** 감소하는 계열인데 P가 0이 아니다. 시작 시점의 L이 80, 80이라 두
프레임(10초)이 임계를 넘었다. `10 / 60 = 16.7%`다. **P는 현재 상태가 아니라
"혼잡이 있었고 얼마나 지속됐는가"를 재기 때문에 정상이다.**

**V8 = 50.0.** 모든 프레임의 L이 85 이상이지만 `t=25`와 `t=60` 사이에 35초
공백이 있다. 15초를 넘으므로 구간이 끊긴다. 첫 구간 `(25-0)+5 = 30초`, 두 번째
`(75-60)+5 = 20초`. 최장 30초 → `30/60 = 50%`. **공백을 무시하고 이으면 80초가
되어 P=100이 나온다. 그것이 이 벡터가 막는 버그다.**

**V8 의 G = 100.** 최근 구간(`t > 45`)의 C 평균은 30, 기준 구간(`15 < t <= 45`)은
`t=20, 25` 두 장으로 평균 10이다. `ratio = 31/11 = 2.82` → 클램프되어 100이다.

**V9 의 peak 는 계산된다.** 프레임 5장이라 P·G는 `null`이지만 `peak C = 17.0`,
`peak L = 37.5`는 나온다. 짧은 제보에서도 C·L 기반 위험도는 산출해야 하기
때문이다.

**V11 의 G = 100.** 기준 구간 평균이 0이다. `+1` 방어가 없으면 0으로 나누기가
발생한다. `ratio = (10.5+1)/(0+1) = 11.5` → 클램프되어 100이다.

### 9.4 JUnit 코드

```java
package com.ojimasam.safy.report.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CongestionTrendCalculatorTest {

	static final Instant BASE = Instant.parse("2026-08-01T00:00:00Z");

	/** 소수 1자리 반올림 차이를 허용한다. */
	static final double DELTA = 0.05;

	/** 5초 간격 계열. values 는 {C, L} 쌍이다. */
	static List<FrameObservation> series(double[][] values) {
		List<FrameObservation> frames = new ArrayList<>();
		for (int i = 0; i < values.length; i++) {
			frames.add(new FrameObservation(
					BASE.plusSeconds(i * 5L), values[i][0], values[i][1]));
		}
		return frames;
	}

	/** 타임스탬프 직접 지정. rows 는 {초, C, L} 이다. */
	static List<FrameObservation> irregularSeries(double[][] rows) {
		List<FrameObservation> frames = new ArrayList<>();
		for (double[] row : rows) {
			frames.add(new FrameObservation(
					BASE.plusSeconds((long) row[0]), row[1], row[2]));
		}
		return frames;
	}

	static double[][] repeat(double c, double l, int count) {
		double[][] values = new double[count][2];
		for (int i = 0; i < count; i++) {
			values[i] = new double[] {c, l};
		}
		return values;
	}

	static final double[][] V2 = {
			{10, 20}, {10, 20}, {11, 22}, {11, 22}, {12, 24}, {12, 24},
			{13, 26}, {14, 28}, {14, 28}, {15, 30}, {15, 30}, {15, 30}};

	static final double[][] V3 = {
			{10, 20}, {10, 20}, {10, 20}, {10, 20}, {10, 20}, {10, 20},
			{20, 50}, {24, 60}, {28, 70}, {30, 75}, {30, 78}, {30, 80}};

	static final double[][] V4 = {
			{30, 80}, {30, 80}, {28, 75}, {26, 70}, {24, 65}, {20, 55},
			{15, 40}, {12, 30}, {10, 25}, {10, 20}, {10, 20}, {10, 20}};

	static final double[][] V5 = {
			{10, 20}, {10, 20}, {10, 20}, {90, 99}, {10, 20}, {10, 20},
			{10, 20}, {10, 20}, {10, 20}, {10, 20}, {10, 20}, {10, 20}};

	static final double[][] V6 = {
			{50, 85}, {50, 86}, {50, 88}, {50, 90}, {50, 90}, {50, 92},
			{50, 91}, {50, 90}, {50, 88}, {50, 86}, {50, 85}, {50, 85}};

	static final double[][] V7 = {
			{10, 20}, {10, 20}, {10, 20}, {50, 85}, {50, 86}, {10, 20},
			{10, 20}, {10, 20}, {10, 20}, {10, 20}, {10, 20}, {10, 20}};

	static final double[][] V8 = {
			{0, 10, 85}, {5, 10, 86}, {10, 10, 88}, {15, 10, 90},
			{20, 10, 90}, {25, 10, 90},
			{60, 30, 90}, {65, 30, 90}, {70, 30, 90}, {75, 30, 90}};

	static final double[][] V9 = {
			{10, 20}, {12, 25}, {14, 30}, {16, 35}, {18, 40}};

	static final double[][] V11 = {
			{0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0},
			{5, 20}, {8, 30}, {10, 40}, {12, 50}, {14, 55}, {15, 60}};

	@Test
	@DisplayName("V1 평탄한 계열은 P 와 G 가 모두 0 이다")
	void v1() {
		CongestionTrend trend = CongestionTrendCalculator.calculate(series(repeat(10, 20, 12)));

		assertEquals(0.0, trend.persistence(), DELTA);
		assertEquals(0.0, trend.growth(), DELTA);
		assertEquals(10.0, trend.peakVisualCongestion(), DELTA);
		assertEquals(20.0, trend.peakLocalCongestion(), DELTA);
	}

	@Test
	@DisplayName("V2 완만한 증가는 G 가 27.8 이다")
	void v2() {
		CongestionTrend trend = CongestionTrendCalculator.calculate(series(V2));

		assertEquals(0.0, trend.persistence(), DELTA);
		assertEquals(27.8, trend.growth(), DELTA);
	}

	@Test
	@DisplayName("V3 급증은 G 가 100 에서 잘린다")
	void v3() {
		CongestionTrend trend = CongestionTrendCalculator.calculate(series(V3));

		assertEquals(0.0, trend.persistence(), DELTA);
		assertEquals(100.0, trend.growth(), DELTA);
	}

	@Test
	@DisplayName("V4 감소해도 G 는 음수가 되지 않는다")
	void v4() {
		CongestionTrend trend = CongestionTrendCalculator.calculate(series(V4));

		assertEquals(16.7, trend.persistence(), DELTA);
		assertEquals(0.0, trend.growth(), DELTA);
	}

	@Test
	@DisplayName("V5 단발 이상치는 평활로 제거된다")
	void v5() {
		CongestionTrend trend = CongestionTrendCalculator.calculate(series(V5));

		assertEquals(0.0, trend.persistence(), DELTA);
		assertEquals(0.0, trend.growth(), DELTA);
		assertEquals(10.0, trend.peakVisualCongestion(), DELTA);
	}

	@Test
	@DisplayName("V6 60초 이상 지속되면 P 는 100 이다")
	void v6() {
		CongestionTrend trend = CongestionTrendCalculator.calculate(series(V6));

		assertEquals(100.0, trend.persistence(), DELTA);
		assertEquals(0.0, trend.growth(), DELTA);
	}

	@Test
	@DisplayName("V7 짧은 스파이크는 P 가 낮다")
	void v7() {
		CongestionTrend trend = CongestionTrendCalculator.calculate(series(V7));

		assertEquals(16.7, trend.persistence(), DELTA);
		assertEquals(0.0, trend.growth(), DELTA);
	}

	@Test
	@DisplayName("V8 프레임 누락 구간은 지속 구간을 끊는다")
	void v8() {
		CongestionTrend trend = CongestionTrendCalculator.calculate(irregularSeries(V8));

		assertEquals(50.0, trend.persistence(), DELTA);
		assertEquals(100.0, trend.growth(), DELTA);
	}

	@Test
	@DisplayName("V9 표본이 부족하면 P·G 는 null 이지만 peak 는 계산된다")
	void v9() {
		CongestionTrend trend = CongestionTrendCalculator.calculate(series(V9));

		assertNull(trend.persistence());
		assertNull(trend.growth());
		assertEquals(17.0, trend.peakVisualCongestion(), DELTA);
		assertEquals(37.5, trend.peakLocalCongestion(), DELTA);
	}

	@Test
	@DisplayName("V10 빈 계열은 전부 null 이다")
	void v10() {
		CongestionTrend trend = CongestionTrendCalculator.calculate(List.of());

		assertNull(trend.persistence());
		assertNull(trend.growth());
		assertNull(trend.peakVisualCongestion());
		assertNull(trend.peakLocalCongestion());
	}

	@Test
	@DisplayName("V11 기준 구간이 0 이어도 0 으로 나누지 않는다")
	void v11() {
		CongestionTrend trend = CongestionTrendCalculator.calculate(series(V11));

		assertEquals(0.0, trend.persistence(), DELTA);
		assertEquals(100.0, trend.growth(), DELTA);
	}

	@Test
	@DisplayName("입력 순서가 뒤섞여도 시간 순으로 정렬해 계산한다")
	void unorderedInputIsSorted() {
		List<FrameObservation> ordered = series(V3);
		List<FrameObservation> shuffled = new ArrayList<>(ordered);
		Collections.reverse(shuffled);

		CongestionTrend a = CongestionTrendCalculator.calculate(ordered);
		CongestionTrend b = CongestionTrendCalculator.calculate(shuffled);

		assertEquals(a.growth(), b.growth(), DELTA);
		assertEquals(a.persistence(), b.persistence(), DELTA);
	}
}
```

---

## 10. 등급 — 프론트가 정한 5단계

`frontend-admin/src/utils/adminRisk.ts`가 진실이다. **새로 지어내지 않는다.**

```
very_low(1) < low(2) < medium(3) < high(4) < very_high(5)
매우 낮음      낮음      보통       높음     매우 높음
```

점수 경계도 프론트의 `getRiskLevelFromScore()`와 같다.

```
score >= 81  ->  very_high
score >= 61  ->  high
score >= 41  ->  medium
score >= 21  ->  low
그 외        ->  very_low
```

Java enum 은 대문자로 두고 **직렬화 시 소문자로 내보낸다.** 프론트는
`value.trim().toLowerCase()`로 정규화하므로 대문자도 받아들이지만, 계약을
명확히 하기 위해 소문자로 통일한다.

```java
package com.ojimasam.safy.report.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * SAFY 위험등급 5단계.
 *
 * 이름과 점수 경계는 frontend-admin/src/utils/adminRisk.ts 와 일치해야 한다.
 * place/domain/type/CongestionLevel 은 서울시 공공데이터의 인구 혼잡도이며
 * 이 등급과 다른 개념이다.
 */
public enum RiskLevel {

	VERY_LOW(1),
	LOW(2),
	MEDIUM(3),
	HIGH(4),
	VERY_HIGH(5);

	private final int order;

	RiskLevel(int order) {
		this.order = order;
	}

	public int order() {
		return order;
	}

	/** 프론트 표기와 맞추기 위해 소문자로 직렬화한다. */
	@JsonValue
	public String jsonValue() {
		return name().toLowerCase();
	}

	public boolean isAtLeast(RiskLevel other) {
		return this.order >= other.order;
	}

	/** 예외 규칙이 요구하는 최소 등급까지 끌어올린다. 낮추지는 않는다. */
	public RiskLevel raiseTo(RiskLevel minimum) {
		return isAtLeast(minimum) ? this : minimum;
	}

	public static RiskLevel fromScore(double score) {
		if (score >= 81.0) {
			return VERY_HIGH;
		}
		if (score >= 61.0) {
			return HIGH;
		}
		if (score >= 41.0) {
			return MEDIUM;
		}
		if (score >= 21.0) {
			return LOW;
		}
		return VERY_LOW;
	}
}
```

---

## 11. FinalRisk 와 FinalRiskLevel

### 11.1 계산식

```
BaseRisk  = 0.4 × C + 0.6 × L
Bonus     = 0.15 × P + 0.15 × G        (null 인 항은 0)
FinalRisk = clamp(BaseRisk + Bonus, 0, 100)

FinalRiskLevel = RiskLevel.fromScore(FinalRisk)
```

`C`와 `L`은 제보 단위 값이다. 6장에서 정한 대로 **평활 후 최댓값**을 쓴다.

### 11.2 예외 규칙 — 바닥선이다

```
L >= 80              -> FinalRiskLevel 을 최소 HIGH 로
L >= 90 AND P >= 70  -> FinalRiskLevel 을 최소 VERY_HIGH 로
```

**바닥선이므로 이미 그 이상이면 아무 일도 하지 않는다.** 등급을 낮추는 데
쓰지 않는다.

두 규칙은 배타적이 아니다. 둘 다 조건을 만족하면 더 높은 쪽이 적용된다.
아래 코드는 `VERY_HIGH` 규칙을 먼저 검사한다.

`P`가 `null`이면 두 번째 규칙은 발동하지 않는다. 첫 번째 규칙은 `L`만 보므로
**표본이 부족해도 그대로 발동한다.** 데이터가 부족해도 위험 신호는 살아남아야
하기 때문이다.

### 알고 있어야 할 부작용

`P`가 `null`이 되는 상황(프레임 6장 미만, `congestionSource` 혼재)에서는
**두 번째 규칙이 발동할 수 없어 자동 등급의 상한이 `HIGH`에 머문다.**

예를 들어 지속 혼잡 계열(`peak L = 91`, `P = 100`)은 정상적으로 `very_high`가
나오지만, 같은 계열에서 `congestionSource`가 섞이면 보너스가 빠져 `74.6`이
되고 등급은 `high`가 된다.

이는 의도된 동작이다. `P`를 신뢰할 수 없을 때 그 값을 근거로 최고 등급을
자동 부여하지 않는다. 대신 `confirmRequired = true`로 사람이 확인하게 만든다.
**위험을 감추는 것이 아니라 자동 판정을 보류하는 것이다.**

추가 예외 규칙은 없다. `denseScene.suspected`(140번 신호)는 **위험도 계산에
사용하지 않는다.** API 응답에 노출되지 않아 백엔드가 받을 방법이 없고, 같은
문제를 C·L 보정이 더 정확하게 해결한다. 17장 참고.

### 11.3 입력과 출력 레코드

```java
package com.ojimasam.safy.report.domain;

import java.util.List;

/**
 * 제보 1건의 위험도를 계산하기 위한 입력.
 *
 * completedFrames        analysisStatus = COMPLETED 인 프레임만
 * totalFrameCount        추출된 전체 프레임 수 (제외된 것 포함)
 * qualityCleanFrameCount COMPLETED 중 qualityIssues 가 빈 배열인 프레임 수
 * congestionSourceMixed  한 제보에 YOLO 와 P2PNET_CORRECTED 가 섞였는가
 */
public record RiskAssessmentInput(
		List<FrameObservation> completedFrames,
		int totalFrameCount,
		int qualityCleanFrameCount,
		boolean congestionSourceMixed
) {
}
```

```java
package com.ojimasam.safy.report.domain;

/**
 * 제보 1건의 위험도 산출 결과.
 *
 * riskConfidence 가 낮아도 finalRisk 와 finalRiskLevel 은 조정하지 않는다.
 * confirmRequired 만 true 로 설정한다.
 */
public record RiskAssessment(
		Double finalRisk,
		RiskLevel finalRiskLevel,
		Double persistence,
		Double growth,
		double riskConfidence,
		boolean confirmRequired,
		String riskRuleVersion
) {
	/** 분석 성공 프레임이 하나도 없어 위험도를 낼 수 없는 경우. */
	public static RiskAssessment notAssessable(String riskRuleVersion) {
		return new RiskAssessment(null, null, null, null, 0.0, true, riskRuleVersion);
	}
}
```

### 11.4 RiskAssessor

```java
package com.ojimasam.safy.report.domain;

/**
 * 제보 1건의 최종 위험도를 계산한다.
 *
 * 신뢰도가 낮거나 표본이 부족해도 위험도를 낮추지 않는다.
 * 그 사실은 riskConfidence 와 confirmRequired 로만 표현한다.
 */
public final class RiskAssessor {

	public static final String RISK_RULE_VERSION = "safy-risk-v0.1";

	public static final double WEIGHT_VISUAL = 0.4;
	public static final double WEIGHT_LOCAL_PEAK = 0.6;
	public static final double WEIGHT_PERSISTENCE = 0.15;
	public static final double WEIGHT_GROWTH = 0.15;

	public static final double EXCEPTION_LOCAL_PEAK_HIGH = 80.0;
	public static final double EXCEPTION_LOCAL_PEAK_VERY_HIGH = 90.0;
	public static final double EXCEPTION_PERSISTENCE_VERY_HIGH = 70.0;

	public static final int FULL_CONFIDENCE_FRAME_COUNT = 12;
	public static final double CONFIRM_REQUIRED_CONFIDENCE = 0.5;

	private RiskAssessor() {
	}

	public static RiskAssessment assess(RiskAssessmentInput input) {
		CongestionTrend trend =
				CongestionTrendCalculator.calculate(input.completedFrames());

		if (trend.peakLocalCongestion() == null || trend.peakVisualCongestion() == null) {
			return RiskAssessment.notAssessable(RISK_RULE_VERSION);
		}

		double visual = trend.peakVisualCongestion();
		double localPeak = trend.peakLocalCongestion();

		Double persistence = input.congestionSourceMixed() ? null : trend.persistence();
		Double growth = input.congestionSourceMixed() ? null : trend.growth();

		double base = WEIGHT_VISUAL * visual + WEIGHT_LOCAL_PEAK * localPeak;
		double bonus = 0.0;
		if (persistence != null) {
			bonus += WEIGHT_PERSISTENCE * persistence;
		}
		if (growth != null) {
			bonus += WEIGHT_GROWTH * growth;
		}

		double finalRisk = roundToOneDecimal(
				Math.max(0.0, Math.min(100.0, base + bonus)));

		RiskLevel level = applyExceptionRules(
				RiskLevel.fromScore(finalRisk), localPeak, persistence);

		double confidence = calculateConfidence(input);
		boolean confirmRequired = confidence < CONFIRM_REQUIRED_CONFIDENCE
				|| persistence == null
				|| growth == null
				|| input.congestionSourceMixed();

		return new RiskAssessment(
				finalRisk, level, persistence, growth,
				confidence, confirmRequired, RISK_RULE_VERSION);
	}

	/**
	 * 예외 규칙은 최소 등급을 보장하는 바닥선이다. 등급을 낮추지 않는다.
	 * 두 규칙이 동시에 성립하면 더 높은 쪽이 적용된다.
	 */
	private static RiskLevel applyExceptionRules(
			RiskLevel level, double localPeak, Double persistence) {

		if (localPeak >= EXCEPTION_LOCAL_PEAK_VERY_HIGH
				&& persistence != null
				&& persistence >= EXCEPTION_PERSISTENCE_VERY_HIGH) {
			return level.raiseTo(RiskLevel.VERY_HIGH);
		}

		if (localPeak >= EXCEPTION_LOCAL_PEAK_HIGH) {
			return level.raiseTo(RiskLevel.HIGH);
		}

		return level;
	}

	/**
	 * 세 요소의 곱이다. 한 축이라도 나쁘면 신뢰도가 떨어져야 하므로
	 * 가중평균을 쓰지 않는다.
	 */
	private static double calculateConfidence(RiskAssessmentInput input) {
		int analyzed = input.completedFrames() == null
				? 0 : input.completedFrames().size();

		if (analyzed == 0 || input.totalFrameCount() <= 0) {
			return 0.0;
		}

		double sampleFactor =
				Math.min((double) analyzed / FULL_CONFIDENCE_FRAME_COUNT, 1.0);
		double inclusionFactor =
				(double) analyzed / input.totalFrameCount();
		double qualityFactor =
				(double) input.qualityCleanFrameCount() / analyzed;

		double confidence = sampleFactor * inclusionFactor * qualityFactor;
		return Math.max(0.0, Math.min(1.0, confidence));
	}

	private static double roundToOneDecimal(double value) {
		return Math.round(value * 10.0) / 10.0;
	}
}
```

---

## 12. RiskConfidence 와 confirmRequired

### 12.1 계산식

```
sampleFactor    = min(분석성공 프레임수 / 12, 1.0)      표본이 충분한가
inclusionFactor = 분석성공 프레임수 / 전체 프레임수      얼마나 버려졌나
qualityFactor   = 품질경고 없는 프레임수 / 분석성공 수   프레임이 깨끗한가

RiskConfidence = sampleFactor × inclusionFactor × qualityFactor   (0.0 ~ 1.0)
```

**곱을 쓰는 이유**는 한 축이라도 나쁘면 신뢰도가 떨어져야 하기 때문이다.
가중평균이면 표본이 3장뿐인데도 품질이 좋다는 이유로 신뢰도가 높게 나온다.

`0.0 ~ 1.0` 실수로 반환한다. 프론트가
`Math.round(confidence * 100)`으로 퍼센트를 만들므로 그대로 맞는다
(`ReportDetailPage.tsx`).

### 12.2 Q 값은 필요 없다

`qualityFactor`는 **"`qualityIssues`가 빈 배열인 프레임의 비율"**이다. 단순
카운트다.

`S15P11A503-144`는 "AI가 0~100 연속 품질 점수를 합성해야 하는가"를 묻는
이슈인데 **이 설계에서는 필요 없다.** `brightnessScore`·`blurScore`로 근사치를
만들 필요도 없다. 144번은 불필요로 정리할 수 있다.

### 12.3 confirmRequired

```
confirmRequired = true  if
    RiskConfidence < 0.5
    또는 P 가 null
    또는 G 가 null
    또는 congestionSource 혼재
```

`ReviewStatus`라는 이름을 쓰지 않는다. 프론트에서 이미 관제사 승인
워크플로(`pending` / `approved` / `rejected`)로 쓰이는 이름이다. 관제사가 아직
승인하지 않은 것과 AI 결과가 못 믿을 만한 것은 다른 문제다.

### 12.4 가장 중요한 원칙

```
RiskConfidence 가 낮아도 FinalRisk 와 FinalRiskLevel 은 절대 바꾸지 않는다.
confirmRequired 만 true 로 설정한다.
```

확신이 낮다고 위험 신호를 감추지 않는다. 신호는 그대로 보내면서 "확신도가
낮다"를 함께 알린다. `S15P11A503-140`에서 오탐 게이트를 거부권으로 쓰지 않기로
한 것과 같은 이유다.

---

## 13. 위험도 테스트 벡터

### 13.1 추세 벡터를 재사용한 위험도

9장의 입력 계열을 그대로 쓴다. `totalFrameCount`는 프레임 수와 같고
`qualityCleanFrameCount`도 같으며 `congestionSourceMixed = false`인 경우다.

| 벡터 | C | L | P | G | **FinalRisk** | **등급** |
|---|---:|---:|---:|---:|---:|---|
| V1 평탄 | 10.0 | 20.0 | 0.0 | 0.0 | 16.0 | `very_low` |
| V2 완만한 증가 | 15.0 | 30.0 | 0.0 | 27.8 | 28.2 | `low` |
| V3 급증 | 30.0 | 79.0 | 0.0 | 100.0 | 74.4 | `high` |
| V4 감소 | 30.0 | 80.0 | 16.7 | 0.0 | 62.5 | `high` |
| V5 단발 이상치 | 10.0 | 20.0 | 0.0 | 0.0 | 16.0 | `very_low` |
| V6 지속 혼잡 | 50.0 | 91.0 | 100.0 | 0.0 | 89.6 | `very_high` |
| V7 짧은 스파이크 | 50.0 | 85.0 | 16.7 | 0.0 | 73.5 | `high` |
| V8 프레임 누락 | 30.0 | 90.0 | 50.0 | 100.0 | 88.5 | `very_high` |
| V9 표본 부족 | 17.0 | 37.5 | null | null | 29.3 | `low` |
| V11 0에서 급증 | 14.5 | 57.5 | 0.0 | 100.0 | 55.3 | `medium` |

**V9 를 주의해서 보라.** P·G가 `null`인데 FinalRisk는 계산된다. 보너스가 0이
되고 C·L만으로 29.3이 나온다. `confirmRequired`는 `true`다.

### 13.2 예외 규칙이 실제로 등급을 올리는 벡터

**13.1의 벡터들에서는 예외 규칙이 한 번도 발동하지 않는다.** 가중평균이 이미
그 등급에 도달하기 때문이다. 즉 **예외 규칙을 아예 구현하지 않아도 13.1은 전부
통과한다.** 아래 두 벡터가 그 구멍을 막는다.

| 벡터 | 입력 | C | L | P | 규칙 적용 전 | **최종** | 발동 규칙 |
|---|---|---:|---:|---:|---|---|---|
| **R1** | `(5,20)×4, (5,85)×2, (5,20)×6` | 5.0 | 85.0 | 16.7 | `medium` (55.5) | **`high`** | `L >= 80` |
| **R2** | `(5,90) × 12` | 5.0 | 90.0 | 100.0 | `high` (71.0) | **`very_high`** | `L >= 90 AND P >= 70` |

R1 입력을 풀어 쓰면 다음과 같다.

```
R1  (5,20) (5,20) (5,20) (5,20) (5,85) (5,85)
    (5,20) (5,20) (5,20) (5,20) (5,20) (5,20)

R2  (5,90) × 12
```

R1 에서 L이 85인 프레임을 **2장 연속**으로 둔 이유는, 1장만 두면 3점 이동
중위값이 제거해버려 규칙이 발동하지 않기 때문이다.

### 13.3 RiskConfidence 벡터

| 상황 | 분석/전체 | 품질양호 | **RiskConfidence** | 표시 | **confirmRequired** |
|---|---|---:|---:|---:|---|
| 완벽 | 20/20 | 20 | 1.0000 | 100% | `false` |
| 정상 | 12/14 | 12 | 0.8571 | 86% | `false` |
| 야간 품질경고 | 12/14 | 6 | 0.4286 | 43% | `true` |
| 절반 거부 | 7/14 | 7 | 0.2917 | 29% | `true` |
| 짧은 제보 | 4/5 | 4 | 0.2667 | 27% | `true` |
| 대부분 거부 | 3/15 | 3 | 0.0500 | 5% | `true` |

경계 참고: `12/14` 분석에 품질양호 `7`장이면 `0.5000`이 **정확히** 나온다.
조건이 `< 0.5`이므로 `confirmRequired = false`다. 부동소수점 경계이므로 이 조합을
테스트 벡터로 쓰지 않는다.

### 13.4 JUnit 코드

`CongestionTrendCalculatorTest`의 `series` / `irregularSeries` 헬퍼를 재사용한다.

```java
package com.ojimasam.safy.report.domain;

import static com.ojimasam.safy.report.domain.CongestionTrendCalculatorTest.BASE;
import static com.ojimasam.safy.report.domain.CongestionTrendCalculatorTest.DELTA;
import static com.ojimasam.safy.report.domain.CongestionTrendCalculatorTest.irregularSeries;
import static com.ojimasam.safy.report.domain.CongestionTrendCalculatorTest.series;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RiskAssessorTest {

	/** 전체 프레임 = 분석 성공, 품질 전부 양호, 소스 혼재 없음. */
	private static RiskAssessmentInput clean(List<FrameObservation> frames) {
		return new RiskAssessmentInput(frames, frames.size(), frames.size(), false);
	}

	@Test
	@DisplayName("V1 평탄한 계열은 very_low 다")
	void v1() {
		RiskAssessment a = RiskAssessor.assess(
				clean(series(CongestionTrendCalculatorTest.repeat(10, 20, 12))));

		assertEquals(16.0, a.finalRisk(), DELTA);
		assertEquals(RiskLevel.VERY_LOW, a.finalRiskLevel());
	}

	@Test
	@DisplayName("V4 감소 계열은 high 다")
	void v4() {
		RiskAssessment a = RiskAssessor.assess(clean(series(CongestionTrendCalculatorTest.V4)));

		assertEquals(62.5, a.finalRisk(), DELTA);
		assertEquals(RiskLevel.HIGH, a.finalRiskLevel());
	}

	@Test
	@DisplayName("V6 지속 혼잡은 very_high 다")
	void v6() {
		RiskAssessment a = RiskAssessor.assess(clean(series(CongestionTrendCalculatorTest.V6)));

		assertEquals(89.6, a.finalRisk(), DELTA);
		assertEquals(RiskLevel.VERY_HIGH, a.finalRiskLevel());
	}

	@Test
	@DisplayName("V8 프레임 누락 계열은 very_high 다")
	void v8() {
		RiskAssessment a = RiskAssessor.assess(
				clean(irregularSeries(CongestionTrendCalculatorTest.V8)));

		assertEquals(88.5, a.finalRisk(), DELTA);
		assertEquals(RiskLevel.VERY_HIGH, a.finalRiskLevel());
	}

	@Test
	@DisplayName("V9 표본이 부족해도 C·L 로 위험도를 계산하고 확인을 요구한다")
	void v9() {
		RiskAssessment a = RiskAssessor.assess(clean(series(CongestionTrendCalculatorTest.V9)));

		assertEquals(29.3, a.finalRisk(), DELTA);
		assertEquals(RiskLevel.LOW, a.finalRiskLevel());
		assertNull(a.persistence());
		assertNull(a.growth());
		assertTrue(a.confirmRequired());
	}

	@Test
	@DisplayName("R1 L>=80 규칙이 medium 을 high 로 올린다")
	void r1_exceptionRaisesToHigh() {
		List<FrameObservation> frames = series(new double[][] {
				{5, 20}, {5, 20}, {5, 20}, {5, 20}, {5, 85}, {5, 85},
				{5, 20}, {5, 20}, {5, 20}, {5, 20}, {5, 20}, {5, 20}});

		RiskAssessment a = RiskAssessor.assess(clean(frames));

		assertEquals(55.5, a.finalRisk(), DELTA);
		assertEquals(RiskLevel.MEDIUM, RiskLevel.fromScore(a.finalRisk()));
		assertEquals(RiskLevel.HIGH, a.finalRiskLevel());
	}

	@Test
	@DisplayName("R2 L>=90 AND P>=70 규칙이 high 를 very_high 로 올린다")
	void r2_exceptionRaisesToVeryHigh() {
		RiskAssessment a = RiskAssessor.assess(
				clean(series(CongestionTrendCalculatorTest.repeat(5, 90, 12))));

		assertEquals(71.0, a.finalRisk(), DELTA);
		assertEquals(RiskLevel.HIGH, RiskLevel.fromScore(a.finalRisk()));
		assertEquals(RiskLevel.VERY_HIGH, a.finalRiskLevel());
		assertEquals(100.0, a.persistence(), DELTA);
	}

	@Test
	@DisplayName("예외 규칙은 등급을 낮추지 않는다")
	void exceptionRulesNeverLower() {
		RiskAssessment a = RiskAssessor.assess(clean(series(CongestionTrendCalculatorTest.V6)));

		assertEquals(RiskLevel.VERY_HIGH, a.finalRiskLevel());
	}

	@Test
	@DisplayName("분석 성공 프레임이 없으면 위험도를 낼 수 없다")
	void noFramesIsNotAssessable() {
		RiskAssessment a = RiskAssessor.assess(
				new RiskAssessmentInput(List.of(), 10, 0, false));

		assertNull(a.finalRisk());
		assertNull(a.finalRiskLevel());
		assertEquals(0.0, a.riskConfidence(), 0.0001);
		assertTrue(a.confirmRequired());
	}

	@Test
	@DisplayName("congestionSource 가 섞이면 P·G 를 쓰지 않고 확인을 요구한다")
	void mixedCongestionSourceDropsTrend() {
		List<FrameObservation> frames = series(CongestionTrendCalculatorTest.V6);

		RiskAssessment mixed = RiskAssessor.assess(
				new RiskAssessmentInput(frames, frames.size(), frames.size(), true));

		assertNull(mixed.persistence());
		assertNull(mixed.growth());
		assertTrue(mixed.confirmRequired());

		// 보너스가 빠져 89.6 -> 74.6 으로 낮아진다.
		// L=91 이지만 P 가 null 이므로 VERY_HIGH 규칙은 발동하지 않고,
		// L>=80 규칙이 HIGH 바닥선만 보장한다.
		assertEquals(74.6, mixed.finalRisk(), DELTA);
		assertEquals(RiskLevel.HIGH, mixed.finalRiskLevel());
	}

	@Test
	@DisplayName("신뢰도는 표본·포함·품질의 곱이다")
	void confidenceIsProductOfThreeFactors() {
		List<FrameObservation> twelve =
				series(CongestionTrendCalculatorTest.repeat(10, 20, 12));

		assertEquals(0.8571,
				RiskAssessor.assess(new RiskAssessmentInput(twelve, 14, 12, false))
						.riskConfidence(), 0.001);

		assertEquals(0.4286,
				RiskAssessor.assess(new RiskAssessmentInput(twelve, 14, 6, false))
						.riskConfidence(), 0.001);
	}

	@Test
	@DisplayName("신뢰도가 0.5 미만이면 확인을 요구한다")
	void lowConfidenceRequiresConfirmation() {
		List<FrameObservation> twelve =
				series(CongestionTrendCalculatorTest.repeat(10, 20, 12));

		assertFalse(RiskAssessor.assess(
				new RiskAssessmentInput(twelve, 14, 12, false)).confirmRequired());

		assertTrue(RiskAssessor.assess(
				new RiskAssessmentInput(twelve, 14, 6, false)).confirmRequired());
	}

	@Test
	@DisplayName("신뢰도가 낮아도 위험도와 등급은 바뀌지 않는다")
	void lowConfidenceDoesNotChangeRisk() {
		List<FrameObservation> frames = series(CongestionTrendCalculatorTest.V6);

		RiskAssessment high = RiskAssessor.assess(
				new RiskAssessmentInput(frames, frames.size(), frames.size(), false));
		RiskAssessment low = RiskAssessor.assess(
				new RiskAssessmentInput(frames, frames.size() * 3, 1, false));

		assertEquals(high.finalRisk(), low.finalRisk(), DELTA);
		assertEquals(high.finalRiskLevel(), low.finalRiskLevel());
		assertTrue(low.confirmRequired());
		assertFalse(high.confirmRequired());
	}

	@Test
	@DisplayName("직렬화는 소문자다")
	void serializesAsLowercase() {
		assertEquals("very_high", RiskLevel.VERY_HIGH.jsonValue());
		assertEquals("medium", RiskLevel.MEDIUM.jsonValue());
	}
}
```

`CongestionTrendCalculatorTest`의 헬퍼와 벡터 상수를 다른 테스트에서 쓰려면
`static` 접근이 가능해야 한다. 위 코드는 그 클래스의 `BASE`, `DELTA`,
`series`, `irregularSeries`, `repeat`, `V4`, `V6`, `V8`, `V9`가
package-private `static`이라고 가정한다. 9장 코드가 그렇게 작성되어 있다.

---

## 14. 검증

```bash
cd backend
./gradlew test --tests '*CongestionTrendCalculatorTest*' --tests '*RiskAssessorTest*'
```

전체 빌드로도 확인한다.

```bash
cd backend
./gradlew build
```

**테스트가 실패하면 벡터가 아니라 Java 코드를 고쳐라.** 9장과 13장의 기대값은
검증된 참조 구현으로 계산한 값이고, 이 문서의 Java 코드는 컴파일 검증을 하지
못했다.

---

## 15. 21번 완료 후 연결

프레임 저장이 구현되면 리포지토리에서 계열을 꺼내 넣는다. 계산 클래스는
바뀌지 않는다.

```java
// 예시 — 실제 리포지토리 이름과 시그니처는 21번 구현에 맞춘다
List<FrameObservation> completed = frameAnalysisRepository
		.findCompletedByReportIdOrderByObservedAt(reportId);

int total = frameAnalysisRepository.countByReportId(reportId);
int clean = frameAnalysisRepository.countCompletedWithoutQualityIssues(reportId);
boolean mixed = frameAnalysisRepository.countDistinctCongestionSource(reportId) > 1;

RiskAssessment assessment = RiskAssessor.assess(
		new RiskAssessmentInput(completed, total, clean, mixed));
```

연결할 때 확인할 것이다.

```
[ ] COMPLETED 만 completedFrames 에 넣는가 (INVALID_FRAME, FAILED 제외)
[ ] totalFrameCount 에 제외된 프레임도 포함하는가
[ ] 실패 프레임을 C=0 으로 저장하지 않았는가
[ ] observedAt 이 실제 프레임 시각인가 (저장 시각이 아님)
[ ] congestionSource 가 섞였는지 확인하는가
[ ] persistence 나 growth 가 null 일 때 0 으로 바꾸지 않는가
[ ] riskConfidence 가 낮을 때 finalRisk 를 조정하지 않는가
[ ] riskRuleVersion 을 함께 저장하는가
```

### riskRuleVersion 을 저장해야 하는 이유

가중치나 임계값을 바꾸면 **이전에 저장된 점수는 다른 공식으로 계산된 값**이다.
버전이 없으면 섞여서 비교가 불가능해진다.

프로젝트에 같은 패턴이 이미 있다. `congestionRuleVersion`,
`frameQualityRuleVersion`이 그렇게 동작하며 `S15P11A503-78`에서 정한 원칙이다.

---

## 16. 가중치 재조정 절차

### 16.1 언제 가능한가

가중치는 상수라 바꾸기 쉽다. 문제는 **무엇과 비교해 바꿀 것인가**다.

```
프레임 계열 (C, L, 시각)   ->  입력.  21번·96번이 주면 확보
AI 가 계산한 FinalRisk     ->  예측.  이 문서로 확보
실제 위험도가 얼마였는가     ->  정답.  사람이 판단해야 함
```

정답 없이는 조정할 수 없다. 현재 계획은 **통합 테스트 단계에서 직접 눈으로
확인하고 기록**하는 방식이다. 관제사 판단을 DB에 저장하는 구조는 이 프로젝트
범위 밖으로 둔다.

### 16.2 기록 양식 — 입력값을 반드시 함께 적는다

```
❌ "3번 제보가 medium 인데 좀 낮은 것 같다"
   -> 가중치를 바꿔도 이 제보가 어떻게 변하는지 알 수 없다

✅ "3번 제보: C=25 L=85 P=50 G=0 -> 68.0 high, 내 판단은 very_high"
   -> 가중치를 바꿔 넣어 다시 계산할 수 있다
```

| 제보 | C | L | P | G | AI점수 | AI등급 | 내 판단 | 메모 |
|---|---|---|---|---|---|---|---|---|
| 1 | 5 | 15 | 0 | 0 | 11.0 | very_low | very_low | 맞음 |
| 2 | 25 | 85 | 50 | 0 | 68.0 | high | very_high | 사람이 꽤 뭉쳐 있었음 |
| 3 | 40 | 45 | 0 | 80 | 55.0 | medium | medium | 맞음 |

C·L은 AI 응답에 그대로 있고 P·G는 백엔드 계산값이다. **추가 저장 구조 없이
눈으로 보이는 값들이다.**

기록이 쌓이면 AI 쪽에서 가중치 조합을 훑어 "판단과 가장 잘 맞는 계수"를 찾는
스크립트를 만들 수 있다. 백엔드 변경은 필요 없다.

### 16.3 기대치

표본이 적으면 결론을 낼 수 없다. `S15P11A503-125`에서 CROWDED 표본이 2장뿐이라
자동 모델 전환 기준을 만들지 못한 것과 같은 상황이다.

| 관측 수 | 할 수 있는 것 |
|---|---|
| 5~10건 | 명백히 틀린 방향만 잡힌다 (예: 전부 너무 낮게 나옴) |
| 10~20건 | `wC : wL` 비율 조정 판단 가능 |
| 그 이하 | 조정 근거 없음. 기본값 유지 |

특히 **고밀도 제보를 몇 건 확보하느냐**가 중요하다. 한산한 제보 20건은 가중치
판단에 거의 도움이 되지 않는다.

---

## 17. 확정 이력과 남은 것

### 17.1 이 문서에서 확정한 것

| 항목 | 결정 | 근거 |
|---|---|---|
| 등급 목록 | 5단계 `very_low`~`very_high` | 프론트 `adminRisk.ts` 기존 구현 |
| 점수 경계 | 81 / 61 / 41 / 21 | 프론트 `getRiskLevelFromScore()` |
| 표기 | Java enum 대문자, 직렬화 소문자 | 프론트가 소문자를 기대 |
| 공식 구조 | `Base(C·L) + Bonus(P·G)` | P·G 가 `null` 이어도 재정규화 불필요 |
| 가중치 | 0.4 / 0.6 / 0.15 / 0.15 | `wL` 상한 0.8, L 포화 고려, P·G 비대칭 근거 없음 |
| 예외 규칙 목표 등급 | `L>=80 → high`, `L>=90 AND P>=70 → very_high` | 레거시 `caution`(=medium)은 실제 고밀도에 약함 |
| 추가 예외 규칙 | 없음 | 기존 두 규칙도 아직 검증되지 않음 |
| `denseScene` 사용 | 사용하지 않음 | API 응답에 없어 백엔드가 받을 수 없음 |
| 제보 단위 C·L | 평활 후 최댓값 | 위험은 가장 나빴던 순간이 결정 |
| RiskConfidence | 세 요소의 곱, 0.0~1.0 | 한 축이라도 나쁘면 신뢰도가 떨어져야 함 |
| Q 값 | 필요 없음 | 품질양호 프레임 비율로 대체. 144번 불필요 |
| 확인 필요 신호 | `confirmRequired` 불리언 | `ReviewStatus` 이름은 프론트가 이미 사용 |

### 17.2 나중에 재검토할 것

```
1. PERSISTENCE_THRESHOLD = 80.0
   YOLO 공식값으로는 발동하지 않는다(4.3). C·L 보정 활성화 또는 재보정 필요.
   S15P11A503-106 서버 실측이 선행 조건이다.

2. 가중치 4개
   실제 제보 관측 10~20건 확보 후 조정(16장).

3. TREND_WINDOW_SECONDS = 30, PERSISTENCE_TARGET_SECONDS = 60
   실제 제보 영상 길이 분포 확인 후 조정.

4. denseScene 사용 여부
   C·L 보정이 활성화되면 다시 검토할 필요가 없어질 가능성이 높다.

5. 제보 단위 C·L 집계 방식
   최댓값이 과하다고 판단되면 p95 나 평균으로 바꿀 수 있다.
```

### 17.3 이 문서가 다루지 않는 것

- 프레임 저장 구조 (`S15P11A503-21`)
- 프레임을 AI 로 보내는 배선 (`S15P11A503-96`)
- 관제사 판단 저장 구조 (프로젝트 범위 밖으로 결정)
- 위험도 결과를 응답 DTO 나 화면에 노출하는 방법

---

## 18. 관련 문서

- `ai/docs/backend-integration-contract.md` — AI API 요청·응답 명세
- `ai/docs/congestion-scoring.md` — C·L 계산 규칙
- `ai/docs/dense-scene-escalation.md` — 고밀도 신호와 데이터 신뢰도 원칙
- `ai/docs/official-congestion-correction-proposal.md` — C·L 보정 제안과 근거
- `ai/docs/frame-quality-validation.md` — 품질 판정 기준
- `ai/docs/video-frame-analysis.md` — 프레임 상태와 실패 처리
- `frontend-admin/src/utils/adminRisk.ts` — 등급 이름·경계의 원본
