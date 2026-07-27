# SAFY 관리자 홈·제보 필터 추가 수정 Implementation Plan

**Goal:** 홈을 좌측 2행·우측 실시간 연결 구조로 바꾸고 WebRTC 요청 UI 및 투명한 제보 필터 레이아웃을 구현한다.

**Files:**
- Modify: `src/pages/dashboard/DashboardPage.tsx`
- Modify: `src/pages/reports/ReportListPage.tsx`
- Modify: `src/index.css`

- [ ] 홈 세 section을 단일 grid로 재배치하고 responsive 순서를 지정한다.
- [ ] 기존 WebRTC status를 사용하는 카드별 controlled request form을 구현한다.
- [ ] 미구현 transport는 성공 처리하지 않고 명확한 오류를 표시한다.
- [ ] 필터 wrapper/title을 제거하고 상단 action 및 2:1:1:1 field grid를 구현한다.
- [ ] refresh icon이 draft/applied 전체 필터와 결과 건수를 초기화하게 한다.
- [ ] placeholder를 수정하되 주소 검색 로직은 유지한다.
- [ ] `npm run lint`, `npm run build`, HTTP smoke와 수동 확인 항목을 검증한다.
