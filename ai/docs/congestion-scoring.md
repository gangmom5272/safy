# C·L 임시 혼잡도 계산 규칙

## 문서 상태

- 규칙 버전: `yolo-grid-v0.1`
- 적용 범위: SAFY MVP
- 사용 모델: YOLO11n
- 상태: 임시 규칙
- 관련 이슈: `S15P11A503-50`

이 규칙은 YOLO 사람 탐지 결과를 이용해 C와 L을 임시로 계산하기 위한 MVP 기준이다. 실제 현장 이미지와 모델 비교 테스트 후 변경할 수 있다.

## 입력값

YOLO가 탐지한 사람 바운딩 박스의 정규화된 중심 좌표를 사용한다.

각 좌표의 범위는 다음과 같다.

```text
0.0 <= centerX <= 1.0
0.0 <= centerY <= 1.0
```

YOLO 탐지 confidence의 평균은 C, L 또는 AI 분석 신뢰도로 사용하지 않는다.

## 8×8 격자

입력 프레임을 가로 8칸, 세로 8칸으로 나누어 총 64개의 이미지 칸을 만든다.

각 사람은 바운딩 박스 중심점이 포함된 이미지 칸 하나에 배치한다.

이 이미지 격자는 AI 내부 계산용이며, 실제 지도 격자나 100m 격자를 의미하지 않는다.

## 이미지 칸별 혼잡점수

각 이미지 칸의 사람 수를 다음 임시 기준으로 0~100점으로 변환한다.

| 칸 내부 사람 수 | 혼잡점수 |
|---:|---:|
| 0명 | 0점 |
| 1명 | 30점 |
| 2명 | 60점 |
| 3명 | 90점 |
| 4명 이상 | 100점 |

계산식:

```text
Si = min(100, cellPersonCount × 30)
```

- `Si`: i번째 이미지 칸의 혼잡점수
- `cellPersonCount`: 해당 이미지 칸에 배치된 사람 수
- `30`: MVP 임시 칸별 증가 점수

## C: 전체 시각적 혼잡도

C는 64개 이미지 칸 혼잡점수의 평균이다.

```text
C = (S1 + S2 + ... + S64) / 64
```

출력 필드:

```text
visualCongestionScore
```

## L: 국소 최대 혼잡도

서로 인접한 2×2 이미지 칸을 하나의 국소 영역으로 본다.

각 2×2 영역의 네 칸 평균을 계산하고, 총 49개 영역 중 최댓값을 L로 사용한다.

```text
W(r, c) = (
    S(r, c)
    + S(r+1, c)
    + S(r, c+1)
    + S(r+1, c+1)
) / 4

L = max(W(r, c))
```

출력 필드:

```text
localPeakCongestionScore
```

## 상태별 처리

### 정상 프레임이며 사람이 없는 경우

```text
analysisStatus = COMPLETED
personCount = 0
visualCongestionScore = 0
localPeakCongestionScore = 0
```

### 분석할 수 없는 프레임

```text
analysisStatus = INVALID_FRAME
personCount = null
visualCongestionScore = null
localPeakCongestionScore = null
```

### 모델 추론 실패

```text
analysisStatus = FAILED
personCount = null
visualCongestionScore = null
localPeakCongestionScore = null
```

## 현재 방식의 한계

- YOLO는 고밀도 군중에서 가려진 사람을 놓칠 수 있다.
- 바운딩 박스 중심점은 실제 사람의 머리 좌표가 아니다.
- 촬영 거리, 카메라 각도, 화각을 보정하지 않는다.
- 칸당 30점 증가 규칙은 실제 데이터로 보정되지 않은 임시 기준이다.
- C와 L은 실제 인구밀도 또는 명/㎡를 의미하지 않는다.
- 8×8 격자와 2×2 국소 영역 크기는 모델 테스트 후 변경할 수 있다.

향후 P2PNet 또는 밀도맵 모델을 검토하고, 실제 SAFY 프레임으로 점수와 임계값을 보정한다.