# YOLO·P2PNet 이중 모델 운영 모니터링

> 현재 운영 전체 구조의 최종 진입점은
> [SAFY 운영 AI 분석 최종 명세](production-ai-analysis.md)다.
> 이 문서는 그중 로그·대시보드·경고 기준을 상세히 설명한다.

## 목적

이 문서는 `S15P11A503-142`에서 정한 이중 모델 운영 관측 기준이다.
정상 프레임마다 YOLO와 P2PNet을 함께 실행하는 현재 운영 방식을 대상으로 한다.

모니터링의 목적은 다음 세 가지다.

- P2PNet 혼잡도 보정과 오탐 게이트가 의도대로 동작하는지 확인
- GPU 노트북 주력 경로와 서버 CPU 자동 폴백 상태 확인
- 모델·게이트 재보정에 사용할 운영 표본이 충분히 쌓였는지 판단

운영 로그만으로 모델의 정답 여부를 확정하지 않는다. 정확도 평가는 사람이 검수한
정답 라벨과 함께 수행한다.

## 현재 분석 정책

| 항목 | 정책 |
|---|---|
| 정상 프레임 | YOLO와 P2PNet 모두 실행 |
| `personCount` | YOLO 결과 유지 |
| C·L | 오탐 게이트 미발동 시 YOLO·P2PNet 중 큰 값 사용 |
| 오탐 게이트 발동 | YOLO C·L 유지 |
| P2PNet 실패·시간 초과 | 정확도 우선 모드에서는 프레임 `FAILED` |
| `INVALID_FRAME` | 모델 분석 제외 |

## 수집 경로

GPU 노트북의 AI 프로세스 로그는 배포 서버의 Alloy가 직접 읽을 수 없다. 따라서 AI가
내부 응답 헤더로 분석 지표를 전달하고, AI failover nginx가 구조화 JSON 로그로
stdout에 기록한다.

```text
backend
  -> nginx:8010
       -> GPU 노트북 AI (주력)
       -> 서버 CPU AI (자동 폴백)
       -> 응답 헤더를 ai_dual_model_request JSON 로그로 기록
       -> Alloy -> Loki -> Grafana
```

응답 JSON과 백엔드 DTO 계약은 바꾸지 않는다. 다음 헤더는 내부 프록시 관측용이다.

| 헤더 | 의미 |
|---|---|
| `X-SAFY-AI-Mode` | `DUAL_MODEL` |
| `X-SAFY-P2PNet-Status` | `COMPLETED`, `TIMEOUT`, `INFERENCE_FAILED` 등 |
| `X-SAFY-Congestion-Source` | `YOLO` 또는 `P2PNET_CORRECTED` |
| `X-SAFY-P2PNet-Gate-Triggered` | 오탐 게이트 발동 여부 |
| `X-SAFY-YOLO-Processing-Time-Ms` | YOLO 처리시간 |
| `X-SAFY-P2PNet-Processing-Time-Ms` | P2PNet 처리시간 |
| `X-SAFY-Person-Count-Difference` | P2PNet 인원수 - YOLO 인원수 |
| `X-SAFY-Visual-Congestion-Difference` | P2PNet C - YOLO C |
| `X-SAFY-Local-Peak-Difference` | P2PNet L - YOLO L |

nginx 로그에는 `upstreamAddress`, `upstreamStatus`, `aiRoute`, 전체 왕복시간도 함께
들어간다. 폴백 시 `$upstream_addr`가 쉼표 또는 ` : `로 구분된 복수 값이 되는 점을 nginx
`map`이 처리한다. `aiRoute=GPU_PRIMARY`는 GPU 노트북 주력 경로이고,
`aiRoute=CPU_FALLBACK`은 서버 CPU 자동 폴백 경로다. 원본 upstream 값도 함께 보존한다.

전체 왕복 p95는 폴백 시 복수 값이 되는 `$upstream_response_time`을 직접 숫자로
파싱하지 않고, 요청 전체에서 항상 단일 값인 `$request_time`을 사용한다.

AI 프로세스 자체도 `p2pnet_shadow_analysis` 구조화 로그를 남긴다. 이 로그의
`operation` 객체에는 보정 활성화 여부, 최종 혼잡도 출처, 게이트 발동, 실행 장치와
두 모델 처리시간 합계가 포함된다. 서버 CPU 내부 원인 분석에 사용하고, GPU·CPU 통합
대시보드는 nginx 로그를 기준으로 한다.

## Grafana 대시보드

대시보드 파일:

```text
infra/grafana/dashboards/safy-ai-dual-model.json
```

Grafana에서 `Dashboards > New > Import`를 선택하고 JSON 파일을 올린 뒤 기존 Loki
데이터소스를 지정한다.

제공 패널:

- 이중 모델 분석 요청 수
- P2PNet 완료율과 실패·타임아웃 수
- P2PNet 보정 적용률
- 오탐 게이트 발동률
- 전체 왕복 p95
- YOLO·P2PNet 사람 수와 C·L 차이
- 모델별 처리시간
- GPU 주력·CPU 자동 폴백 경로
- 최근 구조화 로그

새 로그는 nginx 설정이 배포된 뒤부터 쌓인다. 과거 요청은 소급 생성되지 않는다.

## Grafana Explore용 LogQL

전체 이중 모델 요청:

```logql
{container="infra-nginx-1"}
| json
| event="ai_dual_model_request"
| analysisMode="DUAL_MODEL"
```

P2PNet 실패와 시간 초과:

```logql
{container="infra-nginx-1"}
| json
| event="ai_dual_model_request"
| analysisMode="DUAL_MODEL"
| p2pnetStatus!="COMPLETED"
```

오탐 게이트 발동:

```logql
{container="infra-nginx-1"}
| json
| event="ai_dual_model_request"
| analysisMode="DUAL_MODEL"
| falsePositiveGateTriggered="true"
```

P2PNet C·L 보정 적용:

```logql
{container="infra-nginx-1"}
| json
| event="ai_dual_model_request"
| analysisMode="DUAL_MODEL"
| congestionSource="P2PNET_CORRECTED"
```

CPU 자동 폴백 요청:

```logql
{container="infra-nginx-1"}
| json
| event="ai_dual_model_request"
| analysisMode="DUAL_MODEL"
| aiRoute="CPU_FALLBACK"
```

P2PNet 처리시간 p95:

```logql
quantile_over_time(
  0.95,
  {container="infra-nginx-1"}
  | json
  | event="ai_dual_model_request"
  | analysisMode="DUAL_MODEL"
  | unwrap p2pnetProcessingTimeMs [1h]
)
```

## 운영 경고 기준

다음 값은 모델 선택 기준이 아니라 장애 대응을 위한 1차 운영 경고선이다.

| 지표 | 경고 기준 | 확인할 내용 |
|---|---:|---|
| P2PNet 실패율 | 최근 15분 `1%` 초과 | 가중치, 장치, 메모리, 예외 로그 |
| P2PNet 시간 초과 | 최근 15분 1건 이상 | 중복 요청, 락 대기, CPU 폴백 여부 |
| GPU 주력 전체 왕복 p95 | 최근 15분 `5초` 초과 | 터널·네트워크·노트북 상태 |
| CPU 폴백 | 노트북 운영 예정 시간에 1건 이상 | 터널, 절전, 전원, 망 전환 |
| 오탐 게이트 발동률 | 7일 기준선보다 2배 이상 증가 | 입력 장면 변화 또는 모델 이상 |

CPU 폴백은 정상 복구 수단이지만 GPU 주력 운영 시간에는 장애 신호로 본다.

## 데이터 축적 완료 기준

모델 또는 오탐 게이트 재보정을 검토하기 전에 아래 조건을 만족한다.

1. 연속 7일 이상 운영 로그를 수집한다.
2. `COMPLETED` 이중 모델 프레임을 최소 1,000건 확보한다.
3. `YOLO` 유지와 `P2PNET_CORRECTED` 적용 결과를 각각 최소 100건 확보한다.
4. 7일 동안 한 분류가 100건 미만이면 최대 30일까지 수집 기간을 연장한다.
5. 사람이 확인할 대표 프레임을 최소 50장 선정한다.
   - P2PNet 보정 적용 20장
   - 오탐 게이트 발동 20장
   - 그 외 YOLO 유지 무작위 10장
6. 해당 분류가 20장보다 적으면 발생한 표본을 전부 검수하고 부족 사실을 보고한다.
7. 실패와 시간 초과는 최대 20건까지 전수 원인 확인한다.

로그에는 원본 이미지가 저장되지 않는다. 수동 정확도 검수는 보존 정책과 사용 동의를
충족하는 대표 프레임 ID를 백엔드 기록과 연결해 별도로 수행한다.

## 재보정 판단 원칙

- 두 모델의 예측 차이가 크다는 사실만으로 어느 모델이 정답인지 판단하지 않는다.
- 오탐 게이트 임계값 변경에는 사람이 검수한 정답 라벨이 필요하다.
- 운영 로그의 급격한 변화는 먼저 입력 분포, GPU/CPU 경로와 장애 여부를 확인한다.
- 모델·게이트 변경은 고정 테스트셋 회귀 검증과 별도 이슈를 거친다.
- 대시보드 관측만으로 운영 값을 자동 변경하지 않는다.

## 배포 후 확인

1. nginx 설정 검사와 재기동이 성공했는지 확인한다.
2. 정상 프레임 1장을 분석한다.
3. 응답이 `COMPLETED`인지 확인한다.
4. Loki에서 `event="ai_dual_model_request"` 로그가 조회되는지 확인한다.
5. `analysisMode`, `p2pnetStatus`, `congestionSource`, `upstreamAddress`, `aiRoute`가
   채워졌는지
   확인한다.
6. Grafana 대시보드 시간 범위를 최근 15분으로 설정해 요청이 집계되는지 확인한다.
7. GPU 터널 중단 테스트는 인프라 담당자와 협의된 시간에만 수행한다.
