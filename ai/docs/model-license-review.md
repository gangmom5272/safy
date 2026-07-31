# 모델·데이터 라이선스 검토

## 1. 문서 상태

- 관련 이슈: `S15P11A503-147`
- 검토 기준일: 2026-07-31
- 현재 사용 목적: SSAFY 교육 과정 학습 프로젝트, 비상업
- 상태: 현재 사용 범위 준수 확인 완료

이 문서는 법률 자문이 아니다. 실제로 상업 서비스나 외부 공개 서비스로
전환할 계획이 생기면 전문가 검토가 필요하다.

## 2. 결론 요약

현재 학업 용도는 **P2PNet, JHU-CROWD++, 팀 사진 모두 허용 범위 안**이다.
세 라이선스가 모두 "학술 연구·비상업" 사용을 명시적으로 허용한다.

다만 **AGPL-3.0은 상업성과 무관하다.** 공식 모델 YOLO의 `ultralytics`
패키지가 AGPL-3.0이고, 이 조건의 발동 기준은 돈이 아니라 네트워크를 통한
사용자 상호작용이다. 비상업 학업 프로젝트도 외부 사용자가 네트워크로
접근하는 서비스로 배포하면 소스 공개 의무가 생긴다.

지금 챙길 항목은 3장에, 상용 전환 시 필요한 조치는 6장에 있다.

## 3. 지금 챙길 것

| 항목 | 근거 | 조치 |
|---|---|---|
| JHU-CROWD++ 논문 2편 인용 | 약관 조항 6의 의무 | 발표 자료와 보고서에 인용 추가 |
| AGPL 네트워크 배포 조건 인지 | `ultralytics` AGPL-3.0 §13 | 외부 공개 서비스로 열 계획이면 팀·인프라와 사전 논의 |
| 팀 사진 동의 범위 기록 | 현재 문서에 "동의 받음"만 있고 범위 문구가 없음 | 동의 범위를 명시적으로 기록 |

### JHU-CROWD++ 인용 문구

약관 조항 6은 "any publication making use of the Data & Software"에
다음 두 편을 인용하도록 요구한다. 발표 자료도 publication에 해당할 수
있으므로 포함한다.

```text
Sindagi et al., "Pushing the frontiers of unconstrained crowd counting:
New dataset and benchmark method", ICCV 2019.

Sindagi et al., "JHU-CROWD++: Large-Scale Crowd Counting Dataset and
A Benchmark Method", Technical Report 2020.
```

## 4. 구성 요소별 검토

### 4.1 P2PNet 코드 (Tencent)

```text
repository: https://github.com/TencentYoutuResearch/CrowdCounting-P2PNet.git
pinned commit: 5c91a81ca062b1c7fd3db3ad1c55b1c21f0a7455
license: 자체 BSD 계열 변형 (학술 연구 한정)
```

조항 1의 원문은 다음과 같다.

```text
1. Use in source and binary forms shall only be for the purpose of
   academic research.
```

- 현재 학업 목적 사용은 조항 1에 부합한다.
- 조항 2·3은 소스·바이너리 재배포 시 저작권 고지, 조건 목록, 면책 문구를
  유지하도록 요구한다. AI 컨테이너 이미지는 P2PNet 소스를 포함하므로
  바이너리 재배포에 해당할 수 있다. `Dockerfile`이 저장소를 그대로 clone
  하므로 `LICENSE` 파일이 이미지에 함께 포함된다.
- 조항 4는 Tencent나 기여자 이름을 제품 홍보에 사용하지 못하게 한다.
  발표 자료에서 "Tencent 기술 채택" 같은 표현은 쓰지 않는다.
- 상업적 사용은 별도 허가 없이는 불가능하다.

### 4.2 미세 조정 학습 데이터: JHU-CROWD++ v2.0

```text
사용량: 4,344장
약관 사본: ai/runs/datasets/safy-head-candidates/licenses/
           jhu-crowd-terms-of-use.html
```

약관 첫 문장이 허용 범위를 명시한다.

```text
This dataset is for academic and non-commercial uses (such as academic
research, teaching, scientific publications, or personal experimentation).
```

- 현재 학업 목적 사용은 허용 범위 안이다.
- 조항 3·4가 상업적 사용을 금지한다. 특히 조항 3의 "any portion of
  derived data"와 조항 4의 "any derivative work"가 중요하다. 이
  데이터로 미세 조정한 `safy-p2pnet-v2-epoch3` 가중치는 파생물에
  해당하므로, 가중치 자체를 상업적으로 쓸 수 없다.
- 조항 6은 인용 의무다. 3장 참고.
- 조항 1은 이미지가 인터넷에서 수집됐음을 명시하고, 본인이나 소유물이
  포함된 것을 발견하면 삭제를 요청할 수 있게 한다.

### 4.3 평가 데이터: ShanghaiTech Part A·B

- 공식 라이선스 문서를 확보하지 못했다. 로컬에도 약관 파일이 없다.
- 학습에는 사용하지 않았고 모델 평가와 프레임 품질 검증 표본으로만
  사용했다.
- 통상 연구 목적 사용이 관례이지만 근거 문서가 없으므로, 필요해지면
  원 저자 배포 페이지에서 조건을 확인한다.

### 4.4 팀 사진 51장

- 사용 동의를 받고 수동으로 머리 좌표를 승인한 51장을 학습에 사용했다.
- 원본 이미지는 `runs/`에만 두고 Git에 포함하지 않는다.
- 문서에는 동의 여부(`consent_confirmed`)만 기록돼 있고 **동의 범위
  문구가 없다.** 학업 범위 사용은 동의 취지에 부합하는 것으로 보이지만,
  범위가 기록되지 않은 것은 갭이다.
- 얼굴이 식별 가능한 개인정보이므로, 상용 전환이나 외부 공개 시에는
  재동의가 필요하다.

### 4.5 공식 모델: YOLO11n (`ultralytics`)

```text
package: ultralytics 8.4.102
license: AGPL-3.0 (GNU Affero General Public License v3 or later)
weights: yolo11n.pt (Ultralytics 배포)
```

**이 항목만 상업성과 무관하게 조건이 발동한다.**

AGPL-3.0 §13은 프로그램을 네트워크를 통해 사용자와 상호작용하게 만들면,
그 사용자에게 대응 소스 전체를 제공하도록 요구한다. 기준은 상업성이
아니라 네트워크 배포다.

Ultralytics 공식 안내는 다음 둘 중 하나를 요구한다.

```text
1. 프로젝트 전체를 AGPL-3.0으로 공개
2. Enterprise License 취득
```

현재 SAFY 상황:

- 저장소는 비공개 GitLab(`lab.ssafy.com`)이고 루트에 `LICENSE` 파일이
  없다.
- nginx에 AI 라우트가 아직 없어 외부에서 AI API에 직접 도달할 수 없다.
- 서비스 전체도 SSAFY 과정 내부 평가 범위에서 운영된다.

따라서 과정 내부 평가용 배포에서는 실질 노출이 낮다. 그러나 프로젝트를
외부에 공개 서비스로 열거나 저장소를 공개하지 않은 채 일반 사용자에게
제공하면 의무가 발생한다.

교육 프로젝트에는 보호할 영업 비밀이 없으므로, 필요해지면 **프로젝트를
AGPL-3.0으로 공개하는 것이 비용 없는 정합 경로**다. 저장소 `LICENSE`
파일 결정은 팀 합의가 필요하므로 이 문서에서 단독으로 정하지 않는다.

### 4.6 런타임 의존성

| 패키지 | 라이선스 | 비고 |
|---|---|---|
| torch 2.13.0 | BSD 계열 | 문제 없음 |
| torchvision 0.28.0 | BSD | 문제 없음 |
| opencv-python 5.0.0 | Apache 2.0 | 문제 없음 |
| scipy 1.17.1 | BSD | 문제 없음 |
| fastapi, numpy, pillow | MIT/BSD 계열 | 문제 없음 |

`ultralytics`를 제외하면 런타임 의존성에 copyleft 제약이 없다.

## 5. 제약 계층 정리

현재 파이프라인은 서로 독립적인 네 겹의 제약을 갖는다.

```text
공식 경로 (YOLO)
  코드·가중치: AGPL-3.0
  → 상업성 무관, 네트워크 배포가 조건 발동 기준

그림자 경로 (P2PNet)
  코드:     학술 연구 한정 (Tencent)
  가중치:   JHU-CROWD++ 파생물 → 학술·비상업 한정
  팀 사진:  동의 범위 내
  → 상업 사용은 두 겹 모두 차단
```

그림자 경로가 상업 사용에서 더 강하게 막혀 있고, 공식 경로는 상업
사용이 가능하지만 조건이 붙는다.

## 6. 상용 전환 시 필요한 조치

현재 계획에는 없다. 나중에 필요해질 경우의 참고용이다.

| 대상 | 필요 조치 | 난이도 |
|---|---|---|
| YOLO (`ultralytics`) | Enterprise License 취득 또는 전체 AGPL-3.0 공개 | 비용 또는 소스 공개 |
| P2PNet 코드 | Tencent 별도 서면 허가, 또는 허용적 라이선스 모델로 교체 | 교체가 현실적 |
| 미세 조정 가중치 | JHU-CROWD++ 데이터를 배제하고 상업 사용 가능한 데이터로 재학습 | 재학습 필요 |
| 팀 사진 | 상업 사용 범위로 재동의 | 개인정보 처리 검토 필요 |
| ShanghaiTech | 평가 데이터 라이선스 확인 후 필요 시 교체 | 확인 필요 |

### P2PNet 대체 후보

`S15P11A503-74` 검증에서 DM-Count를 함께 확인했다.

```text
repository: https://github.com/cvlab-stonybrook/DM-Count
code license: MIT
```

- 코드 라이선스는 MIT로 깨끗하다.
- 다만 공개 가중치는 연구용 데이터셋으로 학습된 결과이므로, 코드를
  교체해도 **가중치의 데이터 제약은 그대로 남는다.**
- 즉 상업 전환에서 진짜 병목은 코드가 아니라 학습 데이터다. 상업 사용
  가능한 데이터로 재학습하는 경로(`S15P11A503-146`)와 함께 검토해야
  한다.

## 7. 재검토 조건

다음 중 하나라도 발생하면 이 문서를 다시 검토한다.

- 프로젝트를 외부에 공개 서비스로 배포하기로 결정
- 저장소를 공개로 전환
- 교육 과정 범위를 벗어난 사용 계획 수립
- `ultralytics` 또는 P2PNet 버전·라이선스 변경
- 새 학습 데이터셋 추가
