# P2PNet 그림자 분석 운영 기준

## 목적

이 문서는 이슈 `S15P11A503-136`에서 구현한 P2PNet 그림자 분석의 운영
범위와 기록 형식을 정의한다.

현재 고정 테스트에서는 P2PNet v2가 일부 고밀도 장면에서 유효했지만,
전체 정확도가 YOLO보다 안정적이지 않았다. 따라서 운영 공식 결과는
YOLO11n으로 유지하고 P2PNet은 같은 정상 프레임을 추가 분석하는 관찰
모델로만 사용한다.

## 처리 흐름

1. AI 서버가 기존과 동일하게 프레임 품질을 검사한다.
2. 정상 프레임이면 YOLO11n으로 사람 수와 C·L을 계산한다.
3. YOLO 결과를 기존 API 응답으로 반환한다.
4. 그림자 분석이 활성화되어 있으면 백그라운드에서 P2PNet v2 epoch3를
   실행한다.
5. 두 모델의 결과와 절대 차이를 구조화 로그로 기록한다.

다음 프레임에는 P2PNet을 실행하지 않는다.

- 품질 검사에서 `INVALID_FRAME`으로 판정된 프레임
- YOLO 사람 탐지 또는 C·L 계산이 실패한 프레임

P2PNet 결과는 API 응답, 공식 사람 수, C·L 또는 백엔드 위험도 계산에
반영하지 않는다. 자동 모델 전환도 수행하지 않는다.

## 활성화 설정

기본값은 비활성화다. 배포 환경에서 명시적으로
`P2PNET_SHADOW_ENABLED=true`를 설정해야 실행된다.

| 환경 변수 | 기본값 | 의미 |
|---|---|---|
| `P2PNET_SHADOW_ENABLED` | `false` | 그림자 분석 활성화 여부 |
| `P2PNET_SHADOW_ROOT` | `/opt/p2pnet` | 컨테이너의 P2PNet 소스 루트 |
| `P2PNET_SHADOW_WEIGHT_PATH` | `/var/cache/safy-ai/models/p2pnet/epoch-003.pth` | 검증된 그림자 모델 가중치 캐시 |
| `P2PNET_SHADOW_DEVICE` | `cpu` | 운영 추론 장치 |
| `P2PNET_SHADOW_THRESHOLD` | `0.5` | P2PNet 점수 임계값 |
| `P2PNET_SHADOW_TIMEOUT_SECONDS` | `5.0` | 프레임당 최대 대기시간 |
| `P2PNET_SHADOW_MODEL_VERSION` | `safy-p2pnet-v2-epoch3` | 비교 로그 모델 버전 |
| `P2PNET_SHADOW_WEIGHT_ID` | `p2pnet-finetune-v2-balanced-search/epoch-003.pth` | 비교 로그 가중치 식별자 |

P2PNet 코드는 고정된 외부 저장소 커밋을 컨테이너 이미지에 포함한다.
가중치는 Git에 포함하지 않고 비공개 패키지 저장소에서 내려받아 SHA-256을
검증한 뒤 영속 볼륨에 캐시한다. 자세한 절차는
[P2PNet 운영 자산 및 CPU 배포](p2pnet-deployment-assets.md)를 참고한다.

## 초기화와 동시 실행

P2PNet은 그림자 분석이 비활성화된 동안 import하거나 초기화하지 않는다.
활성화 후 첫 정상 프레임이 들어올 때 한 번만 지연 초기화하며 이후
동일한 모델 인스턴스를 재사용한다.

CPU 메모리 급증과 모델 객체의 동시 접근을 피하기 위해 P2PNet 추론은
프로세스 안에서 한 번에 하나씩 실행한다. 프레임당 시간 제한을 넘기면
해당 그림자 결과만 실패로 기록한다.

## 비교 로그

로그 이벤트 이름은 `p2pnet_shadow_analysis`다. 이미지 원본과 개별 사람
confidence 목록은 기록하지 않으며, 프레임 연결에는 SHA-256 해시를
사용한다.

```json
{
  "event": "p2pnet_shadow_analysis",
  "analysisId": "4e021f43-2ae8-4d35-8fa6-c2fa32b77d67",
  "frameSha256": "...",
  "selectionStatus": "YOLO_PRIMARY_P2PNET_SHADOW",
  "automaticFallback": "DISABLED",
  "official": {
    "modelName": "YOLO",
    "modelVersion": "yolo11n",
    "modelWeightId": "yolo11n.pt",
    "analysisStatus": "COMPLETED",
    "personCount": 14,
    "visualCongestionScore": 6.56,
    "localPeakCongestionScore": 37.5,
    "processingTimeMs": 13,
    "failureCodes": [],
    "lowConfidenceSignal": null
  },
  "shadow": {
    "modelName": "P2PNet",
    "modelVersion": "safy-p2pnet-v2-epoch3",
    "modelWeightId": "p2pnet-finetune-v2-balanced-search/epoch-003.pth",
    "analysisStatus": "COMPLETED",
    "personCount": 29,
    "visualCongestionScore": 8.75,
    "localPeakCongestionScore": 100.0,
    "processingTimeMs": 120,
    "failureCodes": [],
    "lowConfidenceSignal": false
  },
  "differences": {
    "personCount": 15,
    "visualCongestionScore": 2.19,
    "localPeakCongestionScore": 62.5
  }
}
```

차이는 `abs(YOLO - P2PNet)`으로 계산한다. 어느 한쪽이라도 실패했거나
비교 값이 없으면 차이 필드는 `null`로 기록한다.

## 저밀도 대형 오탐 신호 (`lowConfidenceSignal`)

이슈 `S15P11A503-140`에서 추가한 진단 필드다. P2PNet은 질감이 복잡한
저밀도 장면에서 배경을 사람으로 오인해 대형 오탐을 만드는 경우가 있는데
(`S15P11A503-138` 홀드아웃 검증에서 EMPTY 75%, SPARSE 20% 비율로 확인),
운영 중에는 정답이 없으므로 YOLO 결과와 비교하는 대신 **P2PNet 자신의
포인트별 confidence 분포**로 이를 진단한다.

```text
경계 구간(confidence 0.5~0.6) 점 비율 >= 15%
→ lowConfidenceSignal = true (대형 오탐 의심)
```

임계값 15%는 138번 홀드아웃의 대형 오탐 23건과 정상 21건으로 튜닝한
값이며, 이 표본에서 정확도 81.8%(재현율·정밀도 각 82.6%)를 보였다.
표본이 작아 실제 운영 데이터가 쌓이면 재조정이 필요하다.

이 필드는 **진단 전용이며 공식 응답이나 자동 전환에 영향을 주지
않는다.** YOLO 결과에는 항상 `null`을 기록하고(YOLO에는 적용하지 않는
개념), P2PNet 포인트가 하나도 없으면(사람 없음) `false`로 기록한다.

## 실패 격리

P2PNet 모델 로딩, 추론 또는 시간 초과는 다음처럼 처리한다.

- YOLO 공식 응답은 그대로 유지한다.
- P2PNet 상태만 `FAILED`로 기록한다.
- P2PNet의 `failureCodes`에는 `MODEL_INFERENCE_FAILED`를 기록한다.
- 실제 예외 메시지와 스택 트레이스는 서버 오류 로그에만 남긴다.
- 그림자 결과를 YOLO 대신 사용하거나 재응답하지 않는다.

그림자 분석 설정을 읽거나 백그라운드 작업을 등록하는 단계에서 문제가
발생해도 기존 YOLO 응답은 정상 반환한다.

## 백엔드 및 후속 이슈 경계

AI 서버는 프레임 단위 비교 로그를 생성한다. 제보 ID, 추출 프레임 ID와의
연결, DB 적재 여부와 보존 기간은 백엔드 이슈 `S15P11A503-96`에서 정한다.
백엔드는 공식 값과 그림자 값을 분리해 저장해야 하며 그림자 값을 사용자
응답이나 최종 위험도에 사용하면 안 된다.

동시 요청에서의 CPU·메모리 사용량, 대기열, 시간 초과 비율과 처리량 검증은
부하 테스트 이슈 `S15P11A503-106`에서 수행한다. 이 결과를 확인하기 전에는
자동 전환을 활성화하지 않는다.
