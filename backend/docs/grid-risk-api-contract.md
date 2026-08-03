# 격자 위험도 조회 API 계약

> 대상: 프론트엔드(관제 콘솔·시민 앱)
>
> 백엔드 이슈: S15P11A503-102 · 선행: S15P11A503-156, S15P11A503-158
>
> 계산 명세 원본: `ai/docs/grid-risk-calculation-spec.md`

100m 격자 단위 위험도를 지도에 그리기 위한 API다. 제보 위험도와 서울시
121곳 혼잡도를 백엔드가 합쳐 계산하므로 **화면에서 위험도를 다시 계산하지 않는다.**

---

## 1. 엔드포인트

```
GET /api/v1/grid-risks                     뷰포트 안 격자 위험도
GET /api/v1/grid-risks/{gridCode}/trend    한 격자의 위험도 추이
```

둘 다 **인증이 필요하지 않다.** 로그인 전 시민 앱 지도에서도 쓴다. 제보 내용이나
정확한 위치는 나가지 않고 격자 단위로 집계된 점수만 나간다.

---

## 2. 뷰포트 조회

### 요청

```
GET /api/v1/grid-risks
      ?minLat=37.55&maxLat=37.57
      &minLng=126.92&maxLng=126.94
      &resolution=1
```

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `minLat` / `maxLat` | O | 남·북 경계 위도 |
| `minLng` / `maxLng` | O | 서·동 경계 경도 |
| `resolution` | X | 묶을 배수. 기본 1(=100m), 최대 16(=1.6km) |

**제약**

```
minLat < maxLat, minLng < maxLng      아니면 400 GRID_001
위도 폭 <= 0.5도, 경도 폭 <= 0.7도     넘으면 400 GRID_002
```

서울 전체가 위도 약 0.25도·경도 약 0.35도이므로 한 화면에서 이 상한에 닿는 일은 없다.
인증 없이 열린 API라 전체 스캔을 막기 위한 값이다.

### 응답

```json
{
  "success": true,
  "data": [
    {
      "gridCode": "S100-r175-c199",
      "resolutionMeters": 800,
      "southWestLatitude": 37.5561000,
      "southWestLongitude": 126.9243100,
      "northEastLatitude": 37.5633000,
      "northEastLongitude": 126.9333500,
      "score": 88.0,
      "congestionLevel": "very_high",
      "observationStatus": "REPORTED",
      "evidenceCount": 2,
      "distinctReporterCount": 1,
      "confidenceScore": 0.81,
      "windowEndedAt": "2026-08-03T12:03:00Z"
    }
  ]
}
```

| 필드 | 설명 |
|---|---|
| `gridCode` | **가장 위험한 100m 칸**의 코드. 묶은 칸이어도 이 코드로 추이 조회가 된다 |
| `resolutionMeters` | 이 칸의 한 변 길이(m). `resolution=1`이면 100 |
| `southWest*` / `northEast*` | **그릴 사각형의 경계.** 그대로 폴리곤 좌표로 쓴다 |
| `score` | 0~100. 묶은 칸이면 최댓값 |
| `congestionLevel` | `very_low` / `low` / `medium` / `high` / `very_high` (소문자) |
| `observationStatus` | `REPORTED` / `PUBLIC_DATA` — 점수 출처 |
| `evidenceCount` | 제보 수 + (121곳 관측이 있으면 1) |
| `distinctReporterCount` | 서로 다른 제보자 수 |
| `confidenceScore` | 0~1 |
| `windowEndedAt` | 이 값이 계산된 15분 창의 끝 |

---

## 3. 반드시 지켜야 할 네 가지

### 3.1 응답에 없는 칸은 색칠하지 않는다

근거가 없는 격자는 **응답에 아예 담기지 않는다.** 서울 전역 12만 칸 중 값이 있는
칸만 내려온다.

```
받은 칸    색칠
안 받은 칸  비워 둔다 (회색·투명)
```

**`score = 0`으로 채워 그리면 안 된다.** 0은 `very_low`(안전함)로 읽혀서
**지도가 온통 초록색이 된다.** "데이터가 없다"와 "안전하다"는 다르다.

같은 이유로 `observationStatus: "NO_DATA"`는 조회 응답에 나오지 않는다(내부적으로
위험 종료를 기록하는 값이며 서버가 걸러 낸다).

### 3.2 경계 좌표를 그대로 쓴다

`gridCode`를 파싱해 좌표를 역산하지 않는다. 격자 산식이 프론트와 백엔드 두 곳에
생기면 어긋났을 때 **조용히 다른 칸을 그린다.** 응답의 경계 4개를 그대로
`kakao.maps.Polygon`의 좌표로 쓴다.

```ts
new kakao.maps.Polygon({
  path: [
    new kakao.maps.LatLng(risk.southWestLatitude, risk.southWestLongitude),
    new kakao.maps.LatLng(risk.southWestLatitude, risk.northEastLongitude),
    new kakao.maps.LatLng(risk.northEastLatitude, risk.northEastLongitude),
    new kakao.maps.LatLng(risk.northEastLatitude, risk.southWestLongitude),
  ],
  ...
})
```

`gridCode`는 **클릭 시 추이 조회용 식별자**로만 쓴다.

### 3.3 줌 아웃은 `resolution`으로, 화면에서 다시 묶지 않는다

지도 레벨에 맞춰 `resolution`을 보내면 **서버가 묶어서** 내려준다.

| 카카오맵 레벨 | `resolution` | 칸 크기 |
|---|---:|---|
| 1~4 | 1 | 100m |
| 5 | 2 | 200m |
| 6 | 4 | 400m |
| 7 | 8 | 800m |
| 8 이상 | 16 | 1.6km |

```
강남 일대 레벨 7   resolution 미지정 256칸 · 51KB   →  resolution=8  4칸 · 1KB
서울 전체 레벨 9   6,782칸 · 1.3MB                 →  resolution=16 106칸 · 21KB
```

**받은 칸을 화면에서 다시 병합하면 안 된다.** 큰 칸에서 위험도를 재계산하면 위험
지점이 칸 중심에서 밀려나 **위험도가 사라져 보인다**(실측: 69점 → 19점). 서버는
항상 100m 기준으로 계산한 뒤 **최댓값**으로 묶는다.

### 3.4 등급으로 색, 점수로 농도

혼잡도 4단계가 5단계 등급으로 가면 **압축된다.**

| 혼잡도 | `score` | `congestionLevel` |
|---|---:|---|
| 여유 | 10 | `very_low` |
| 보통 | 30 | `low` |
| 약간 붐빔 | 45 | `medium` |
| 붐빔 | 60 | **`medium`** ← 같음 |

`congestionLevel`만 쓰면 '약간 붐빔'과 '붐빔'이 같은 색이 된다. **등급으로 기본 색을
정하고 `score`로 같은 등급 안 농도를 나눈다.**

```
level=medium, score=45  →  연한 노랑
level=medium, score=60  →  진한 노랑
```

---

## 4. 추이 조회

### 요청

```
GET /api/v1/grid-risks/S100-r174-c199/trend?hours=6
```

`hours`는 선택이고 기본 6시간이다.

### 응답

```json
{
  "success": true,
  "data": [
    { "windowEndedAt": "2026-08-03T12:03:00Z", "score": 75.0,
      "congestionLevel": "high", "observationStatus": "REPORTED" },
    { "windowEndedAt": "2026-08-03T11:21:00Z", "score": 30.0,
      "congestionLevel": "low", "observationStatus": "PUBLIC_DATA" }
  ]
}
```

**최근 것부터**이고, 점 간격이 일정하지 않다. 이력은 **값이 바뀔 때만** 쌓이기
때문이다.

```
12:03  75점   ← 다음 점이 나올 때까지 이 값이 유지된 것으로 읽는다
11:21  30점
```

그래프로 그릴 때 **계단 함수(step)** 로 이어야 한다. 선형 보간하면 없던 변화를
만들어 낸다.

`score`가 `null`인 점은 그 시점에 **위험이 끝났다**는 기록이다(근거 만료). 그 구간은
그리지 않는다.

셀 코드가 유효한데 이력이 없으면 빈 배열이다 — "그 칸에 아직 아무 일도 없었다"는
뜻이며 오류가 아니다.

---

## 5. 캐시와 요청 빈도

응답에 `Cache-Control: public, max-age=180`이 붙는다. 백엔드가 3분 주기로 다시
계산하므로 그보다 짧게 캐시해도 새 값이 없다.

**다만 지금은 브라우저 캐시가 잘 맞지 않는다.** 요청 URL에 지도가 준 실수 좌표가
그대로 들어가서, 1픽셀만 움직여도 캐시 키가 달라진다.

```
minLat=37.472416979582206   ← 소수점 15자리. 같은 화면인데 매번 다른 URL
```

**프론트에서 요청 bbox를 격자 경계로 스냅해 보내는 것을 권한다.** 그러면 같은 칸
범위를 보는 동안 URL이 같아져 캐시가 그대로 듣는다.

```ts
// resolution 에 맞춘 칸 경계로 내림/올림
const step = 0.0009 * resolution          // 위도
const snapped = Math.floor((lat - 37.4) / step) * step + 37.4
```

원점과 간격은 아래 §7에 있다. 이동·줌에 **디바운스(200~300ms)** 를 두는 것도 함께
권한다.

---

## 6. 오류

| 코드 | HTTP | 상황 |
|---|---|---|
| `GRID_001` | 400 | `minLat >= maxLat` 또는 `minLng >= maxLng` |
| `GRID_002` | 400 | 조회 범위가 상한(위도 0.5도 / 경도 0.7도)을 넘음 |

`resolution`은 범위를 벗어나면 오류가 아니라 **1~16으로 잘린다.**

---

## 7. 참고 — 격자 산식

좌표를 직접 다뤄야 할 때(요청 bbox 스냅 등)만 쓴다. **칸을 그리는 데는 응답의 경계를
쓴다.**

```
원점    위도 37.4      경도 126.7
간격    위도 0.0009    경도 0.00113      (서울 위도에서 각각 약 100m, 99.7m)
셀 코드 S100-r{row}-c{column}

row    = floor((lat - 37.4)  / 0.0009)
column = floor((lng - 126.7) / 0.00113)
```

`r`/`c` 접두사는 원점 남·서쪽에서 음수 인덱스가 될 때 하이픈이 겹쳐 파싱이 깨지는
것을 막기 위한 것이다(`S100-r-12-c-9`).

---

## 8. 데이터가 언제 생기는가

```
제보           등록 즉시 셀이 정해지고, AI 분석 후 위험도가 붙는다
121곳 혼잡도   3분 주기 수집
격자 위험도    3분 주기 재계산 (15분 창)
```

제보는 최대 3분 늦게 격자에 반영된다. 담당자에게 가는 제보 알림은 SSE로 즉시
전달되므로, 격자는 **배경 위험도**로 다루면 된다.

공공데이터만 있는 칸은 **절대 `high` 이상이 되지 않는다**(상한 60 = `medium`).
서울시 혼잡도는 보행 편의성 척도이고 최고 등급 '붐빔'도 평일 저녁마다 뜨는
일상적인 값이기 때문이다. **주황·빨강은 실제 제보가 있을 때만 나온다.**
