# SAFY 관리자 홈 4차 UI 수정 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자 3차 변경을 유지하면서 홈의 WebRTC 대기열을 제거하고 실시간 연결·지도·헤더 UI를 4차 요구사항에 맞게 수정한다.

**Architecture:** `AdminHeader`에는 의존성 없는 inline SVG 아이콘만 추가한다. `DashboardPage`에서는 홈 전용 queue state와 JSX를 제거하고 섹션을 재배치하며, `index.css`에서 WebRTC media를 정상 흐름으로 바꾸고 지도 카드의 중첩 padding을 제거한다.

**Tech Stack:** React 19, TypeScript 6, React Router 8, Vite 8, 전역 CSS

## Global Constraints

- 수정 범위는 관리자 홈, 공통 헤더, 관리자 profile dropdown이다.
- 제보 목록·상세와 사용자 페이지는 수정하지 않는다.
- Primary `#004AAD`, 기존 route, logout, alert modal, WebRTC 데이터와 타입을 유지한다.
- 새 라이브러리와 새 `any`를 추가하지 않는다.

---

### Task 1: 헤더 아이콘과 profile dropdown

**Files:**
- Modify: `src/components/layout/AdminHeader.tsx`
- Modify: `src/index.css`

**Interfaces:**
- Preserves: `useAuth().logout`, profile outside click/Escape, emergency modal/count
- Produces: private `SirenIcon()`과 `LogOutIcon()` SVG components

- [ ] **Step 1: 기준 lint 실행**

Run: `npm run lint`

Expected: exit code 0 또는 기존 오류를 별도 기록한다.

- [ ] **Step 2: header SVG icon 구현**

두 아이콘을 `18x18`, `currentColor`, round stroke의 JSX SVG로 작성하고 `aria-hidden="true"`, `focusable="false"`를 적용한다.

- [ ] **Step 3: header markup 수정**

긴급 버튼 앞에 Siren icon을 추가하고 동적 count를 유지한다. profile trigger의 `⌄`와 공백을 제거한다. dropdown button은 LogOut icon과 `로그아웃` 텍스트를 렌더링하고 기존 handler를 사용한다.

- [ ] **Step 4: header CSS 수정**

긴급 버튼과 dropdown button에 `display: inline-flex`, 중앙 정렬과 gap을 적용한다. profile trigger는 화살표 자리 없는 padding으로 조정하고 focus-visible을 유지한다.

- [ ] **Step 5: lint 실행**

Run: `npm run lint`

Expected: exit code 0.

### Task 2: 홈 queue 제거와 섹션 재배치

**Files:**
- Modify: `src/pages/dashboard/DashboardPage.tsx`

**Interfaces:**
- Preserves: `webrtcConnections`의 live filtering, `/monitoring`, `/risk-map`, report detail navigation
- Removes from home only: waiting filter, requested IDs, queue memo, request handler, queue JSX와 labels import

- [ ] **Step 1: queue 전용 코드 제거**

`useMemo`, `useState`, `connectionStatusLabels`, waiting connections와 request 관련 state/function을 제거한다. 원본 mock/type 파일은 수정하지 않는다.

- [ ] **Step 2: section 순서 변경**

첫 grid에는 지도와 최근 위험 제보를 두고, 실시간 연결 `AdminSection`을 별도의 full-width section으로 이동한다. `전체 보기 >` action과 empty state는 유지한다.

- [ ] **Step 3: WebRTC card markup 정상 흐름화**

카드에 header(장소명/risk badge), media(LIVE/WebRTC), metadata(연결 시간)를 순서대로 렌더링한다. 장소명은 별도 class로 overflow를 제어한다.

- [ ] **Step 4: queue 제거 검색**

Run: `Select-String -Path src/pages/dashboard/DashboardPage.tsx -Pattern '대기열|waitingConnections|queueRows|requestConnection|requestedConnectionIds'`

Expected: 결과 0건.

### Task 3: WebRTC grid와 지도 frame CSS 수정

**Files:**
- Modify: `src/index.css`

**Interfaces:**
- Consumes: Task 2의 `.video-card__header`, `.video-card__media`, `.video-card__meta`, `.video-card__location`
- Preserves: `.live-preview-grid` 내부 scroll과 `scrollbar-gutter`

- [ ] **Step 1: live grid 수정**

`.live-preview-grid`에 `repeat(auto-fit, minmax(min(100%, 280px), 1fr))`, gap, align-items를 적용한다. 카드에는 `min-width: 0`, 정상 grid flow, overflow hidden을 적용하고 card 자체 aspect ratio를 제거한다.

- [ ] **Step 2: media와 metadata 스타일 구현**

`.video-card__media`에 `width: 100%`, `aspect-ratio: 16 / 9`, relative positioning과 overflow hidden을 둔다. LIVE badge만 media 내부 absolute로 유지한다. 장소명은 ellipsis, metadata는 wrap 가능한 구조로 만든다.

- [ ] **Step 3: map frame 제거**

`.dashboard-map-card`에 `padding: 0`, preview에 `border: 0`, `border-radius: inherit`, `width/height: 100%`를 적용한다. 외부 card overflow hidden과 marker 동작은 유지한다.

- [ ] **Step 4: responsive 보완**

좁은 화면에서 live grid가 한 열이 되고 1280px에서 min-width overflow가 발생하지 않도록 `minmax(min(100%, 280px), 1fr)`와 section `min-width: 0`을 유지한다.

### Task 4: 최종 검증

**Files:**
- Review: `src/components/layout/AdminHeader.tsx`
- Review: `src/pages/dashboard/DashboardPage.tsx`
- Review: `src/index.css`

- [ ] **Step 1: 제거 대상과 변경 범위 검색**

Run: `Select-String -Path src/components/layout/AdminHeader.tsx,src/pages/dashboard/DashboardPage.tsx -Pattern '⌄|로그아웃 하기|WebRTC 연결 대기열|requestedConnectionIds|queueRows'`

Expected: 0건.

- [ ] **Step 2: 새 any 검사**

Run: `Select-String -Path src/components/layout/AdminHeader.tsx,src/pages/dashboard/DashboardPage.tsx -Pattern '\bany\b'`

Expected: 0건.

- [ ] **Step 3: lint 실행**

Run: `npm run lint`

Expected: exit code 0.

- [ ] **Step 4: production build 실행**

Run: `npm run build`

Expected: `tsc -b`와 Vite build가 exit code 0. 별도 typecheck script가 없음을 기록한다.

- [ ] **Step 5: dev 서버 응답 및 수동 항목 확인**

`npm run dev -- --host 127.0.0.1 --port 5173` 실행 후 `/admin/login` HTTP 200을 확인한다. 브라우저에서 live card grid/scroll, 지도 frame, profile dropdown/outside click/Escape/logout, emergency icon/modal을 확인한다.

- [ ] **Step 6: diff 검사**

Run: `git diff --check`

Expected: whitespace 오류가 없다.
