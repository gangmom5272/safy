## 3.8 인바운드 규칙 (갱신안)

상태: 1차 조사 완료, 서버 적용 전 (local-deployment 실측 대조 포함, 7/20)

| 유형 | 프로토콜/포트 | 비고 |
|---|---|---|
| SSH | TCP 22 | 절대 변경 안 함 |
| HTTP | TCP 80 | HTTPS 리다이렉트 + Let's Encrypt 인증 |
| HTTPS/API/Dashboard/Meet | TCP 443 | LiveKit API, Dashboard, Meet, WHIP API, TURN over TLS, Custom layouts |
| STUN/TURN | UDP 443 | 매뉴얼 오전 초안에 누락돼 있던 부분, 공식 문서에 별도 명시 |
| RTMP 인입 (선택) | TCP 1935 | Ingress 사용 시에만 |
| WebRTC TCP 폴백 | TCP 7881 | |
| WHIP 인입 | UDP 7885 | (오늘 local-deployment 실측: 7895 — 버전/구성 차이로 보임, 서버 적용은 공식값 7885 기준) |
| MinIO 외부 공개 (선택) | TCP 9000 | 팀 MinIO 컨테이너와 포트 겹침 여부 확인 필요 |
| WebRTC 미디어 릴레이 | UDP 50000–60000 | 메인 미디어 트래픽, 범위 넓음 — 보안그룹 신청 시 핵심 |

SSAFY 예약 포트(8988/8989/29418) 대조: 위 목록과 겹치지 않음, 문제없음.

출처: openvidu.io/latest/docs/self-hosting/single-node/on-premises/install/ (Community, Single Node, v3.8 기준, 7/20 확인)

참고 — local-deployment vs 실서버 차이: 오늘 로컬 실습(community/docker-compose.yaml)은 로컬 전용 경량 구성이라 포트 범위가 자체적으로 좁혀져 있음(예: UDP 7900-7999). 실서버는 이 값이 아니라 위 공식 install 문서 기준으로 방화벽을 짜야 함.
