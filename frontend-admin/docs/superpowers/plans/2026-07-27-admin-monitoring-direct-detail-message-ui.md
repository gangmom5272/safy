# Admin Monitoring Direct Detail and Message UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 홈 WebRTC 카드에 제보 업로드 시각을 표시하고 고유 연결 ID 기반 상세 화면과 관리자 메시지 입력 UI를 제공한다.

**Architecture:** `/monitoring/:connectionId` route param을 상세 화면의 단일 선택 기준으로 사용한다. 홈 업로드 시각은 같은 `reportId`의 제보 `reportedAt`을 사용하며, 메시지 transport가 없으므로 검증 가능한 입력 UI와 명시적 미연결 오류만 제공한다.

**Tech Stack:** React 19, TypeScript 6, React Router 8, CSS

## Global Constraints

- 기존 홈 배치와 WebRTC 독립 카드 및 내부 스크롤을 유지한다.
- Primary Color `#004AAD`와 5단계 위험도 체계를 유지한다.
- API 이벤트를 임의로 만들거나 성공을 모사하지 않는다.
- 새 라이브러리와 `any`를 추가하지 않는다.
- Git 커밋과 푸시는 사용자가 수행한다.

---

### Task 1: ID route와 업로드 시각

- [ ] `/monitoring/:connectionId` route 및 path helper를 추가한다.
- [ ] 초 단위 관리자 timestamp formatter를 추가한다.
- [ ] 홈 카드 footer를 동일 report의 `reportedAt` 표시로 교체한다.
- [ ] 홈 카드 클릭을 connection ID 상세 route 이동으로 교체한다.

### Task 2: 상세 route 기반 데이터 선택

- [ ] 모니터링 페이지가 route param으로 상세 데이터를 찾도록 변경한다.
- [ ] 목록 카드 클릭도 상세 route를 사용한다.
- [ ] 잘못된 ID empty state와 목록 돌아가기를 추가한다.
- [ ] LIVE 여부 요약 블록을 제거하고 위치와 연결 시간을 2열로 유지한다.

### Task 3: 관리자 메시지 입력 UI

- [ ] 영상 아래 한 줄 입력과 전송 버튼을 추가한다.
- [ ] trim, 빈 값, IME Enter, 최대 길이, 종료 세션을 검증한다.
- [ ] transport 부재 시 입력을 유지하고 명시적 오류를 표시한다.

### Task 4: 검증

- [ ] `npm run lint`를 실행한다.
- [ ] `npm run build`를 실행한다.
- [ ] route, footer, 메시지 상태와 기존 클릭 동작 diff를 검토한다.

