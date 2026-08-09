# SAFY 관리자 페이지 3차 UI/UX 수정 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 관리자 앱의 인증, 라우팅, enum과 mock 데이터 흐름을 유지하면서 홈, 제보 목록, 제보 상세 UI를 3차 요구사항에 맞게 개편한다.

**Architecture:** 전역 CSS 변수와 작고 재사용 가능한 관리자 공통 컴포넌트/helper를 추가하고 기존 페이지가 이를 조합한다. 실제 API가 없는 현재 구조에서는 mock 데이터와 로컬 상태 변경을 유지하며, 누락 필드는 선택적 타입과 fallback UI로 처리한다.

**Tech Stack:** React 19, TypeScript 6, React Router 8, Vite 8, 전역 CSS

## Global Constraints

- 관리자 Primary Color는 `#004AAD`, hover는 `#003B8A`, light background는 `#EAF2FF`다.
- 기존 `/dashboard`, `/reports`, `/reports/:reportId`, `/monitoring` 라우팅을 유지한다.
- 백엔드 API 스펙을 만들거나 변경하지 않는다.
- 사용자 페이지와 관리자 범위 밖의 화면은 수정하지 않는다.
- 새 라이브러리를 설치하지 않고 새 `any` 타입을 사용하지 않는다.
- 기존 `RiskLevel`, `ReportType`, `ReportStatus`, `ReviewStatus`, `AiAnalysisStatus`를 재사용한다.
- 실제 데이터가 없는 제보자명, 이미지, AI 근거는 안전한 fallback을 표시한다.
- 테스트 러너가 없으므로 승인된 설계에 따라 `npm run lint`와 `npm run build` 및 수동 검증을 사용한다.

---

## File Structure

- Create `src/components/admin/AdminSection.tsx`: 카드 밖 제목/액션과 카드 콘텐츠를 조합한다.
- Create `src/components/admin/AdminModal.tsx`: Escape와 backdrop 닫기를 지원하는 접근 가능한 공통 모달이다.
- Create `src/components/admin/StatusBadge.tsx`: 상태 tone에 따른 공통 badge를 렌더링한다.
- Create `src/components/admin/RiskLevelIndicator.tsx`: 기존 4단계 위험도와 점수를 표현한다.
- Create `src/utils/adminDate.ts`: 관리자 날짜를 요일과 초까지 포맷한다.
- Create `src/utils/adminStatus.ts`: 위험 단계 순서와 상태별 tone 매핑을 제공한다.
- Modify `src/types/admin.ts`: 현재 데이터에 없는 상세 필드를 선택적으로 받을 수 있게 한다.
- Modify `src/components/layout/AdminSidebar.tsx`: 메뉴 표시명을 변경한다.
- Modify `src/components/layout/AdminHeader.tsx`: 긴급 알림과 관리자 dropdown을 구현한다.
- Modify `src/pages/dashboard/DashboardPage.tsx`: 홈 섹션 구조와 WebRTC 레이아웃을 변경한다.
- Modify `src/pages/reports/ReportListPage.tsx`: 적용형 상세 필터와 테이블 컬럼을 변경한다.
- Modify `src/pages/reports/ReportDetailPage.tsx`: 상세 3행 레이아웃과 통합 타임라인을 구현한다.
- Modify `src/index.css`: 관리자 공통 테마와 신규 화면 반응형 스타일을 정의한다.

### Task 1: 공통 타입, 날짜 및 상태 helper

**Files:**
- Modify: `src/types/admin.ts`
- Create: `src/utils/adminDate.ts`
- Create: `src/utils/adminStatus.ts`

**Interfaces:**
- Produces: `formatAdminDate(value?: string | null): string`
- Produces: `RISK_LEVEL_ORDER: readonly RiskLevel[]`
- Produces: `StatusTone`과 위험/검토/처리/AI tone 조회 함수
- Extends: `AdminReport`의 선택적 `reporterName?: string`, `imageUrl?: string`, `aiIndicators?: string[]`, `aiCompletedAt?: string`

- [ ] **Step 1: 현재 타입 검증 기준 기록**

Run: `npm run build`

Expected: 기존 기준 build가 성공하거나, 실패 시 구현 전 오류를 별도로 기록한다.

- [ ] **Step 2: 선택적 상세 필드 추가**

`AdminReport`에 기존 객체를 깨뜨리지 않는 선택적 필드만 추가한다. 기존 enum과 필수 필드는 변경하지 않는다.

- [ ] **Step 3: 날짜 formatter 구현**

`2026-07-27 10:23`과 ISO 형식 입력을 파싱하여 `2026.07.27 (월) 10:23:00` 형태로 반환하고, 유효하지 않은 입력은 `정보 없음`을 반환한다. 한국어 요일 배열은 helper 파일 한 곳에만 둔다.

- [ ] **Step 4: 상태 상수와 tone helper 구현**

위험 단계는 `safe`, `caution`, `warning`, `danger` 순서로 고정하고 각 enum을 `neutral | primary | success | warning | danger` tone으로 매핑한다. unknown 문자열을 받지 않고 기존 union 타입만 입력으로 받는다.

- [ ] **Step 5: 타입 검증**

Run: `npm run build`

Expected: `tsc -b`와 Vite build가 성공한다.

### Task 2: 공통 관리자 UI 컴포넌트

**Files:**
- Create: `src/components/admin/AdminSection.tsx`
- Create: `src/components/admin/AdminModal.tsx`
- Create: `src/components/admin/StatusBadge.tsx`
- Create: `src/components/admin/RiskLevelIndicator.tsx`
- Modify: `src/index.css`

**Interfaces:**
- Consumes: Task 1의 `StatusTone`, `RISK_LEVEL_ORDER`, `RiskLevel`
- Produces: `AdminSection({ title, action?, children, className? })`
- Produces: `AdminModal({ open, title, onClose, children, footer? })`
- Produces: `StatusBadge({ tone, children, className? })`
- Produces: `RiskLevelIndicator({ level, score })`

- [ ] **Step 1: 공통 컴포넌트 계약 작성**

모든 props는 명시적 interface와 `ReactNode`로 정의하고 `any`를 사용하지 않는다. `AdminSection`은 `<section>` 아래 heading row와 `.admin-card` 콘텐츠를 렌더링한다.

- [ ] **Step 2: 공통 모달 구현**

`open`일 때만 렌더링하고 `role="dialog"`, `aria-modal="true"`, 제목 연결을 제공한다. document의 `keydown` listener로 Escape를 처리하고 backdrop 자신을 클릭한 경우에만 닫는다.

- [ ] **Step 3: badge와 위험도 지표 구현**

badge는 tone class만 조합한다. 위험도 지표는 네 단계를 모두 렌더링하고 현재 단계에 `is-current`, 이전 단계에 `is-passed`, 이후 단계에 `is-pending`을 적용하며 현재 점수를 표시한다.

- [ ] **Step 4: 관리자 공통 CSS 추가**

Primary 변수를 교체하고 hover/light 변수를 추가한다. `.admin-section`, `.admin-section__heading`, `.admin-modal`, `.status-badge--*`, `.risk-indicator`를 기존 8px radius와 shadow에 맞춰 정의한다.

- [ ] **Step 5: 정적 검증**

Run: `npm run lint`

Expected: 신규 컴포넌트의 hook dependency와 접근성 관련 JSX 오류 없이 성공한다.

### Task 3: 사이드바와 헤더

**Files:**
- Modify: `src/components/layout/AdminSidebar.tsx`
- Modify: `src/components/layout/AdminHeader.tsx`
- Modify: `src/index.css`

**Interfaces:**
- Consumes: 기존 `useAuth()`, `ROUTE_PATHS`, `adminReports`, `emergencyBanner`, Task 2의 `AdminModal`
- Preserves: `logout()` 후 `/login` replace 이동

- [ ] **Step 1: 사이드바와 페이지명 변경**

표시 문자열 `대시보드`를 `홈`, `제보 관리`를 `제보 목록`으로 변경하고 라우팅 상수는 그대로 둔다. 상세 header title은 `제보 상세`로 표시한다.

- [ ] **Step 2: 관리자 표시명과 dropdown 구현**

`adminEmail?.split('@')[0] || '관리자'`에서 표시명을 만들고 `${displayName} 관리자님`을 렌더링한다. button에 `aria-haspopup="menu"`, `aria-expanded`, `aria-label`을 주고 외부 `pointerdown`과 Escape로 닫는다.

- [ ] **Step 3: 긴급 알림 모달 구현**

위험도가 `danger`인 기존 제보 수를 표시하고 `emergencyBanner` 및 위험 제보 목록을 모달에 렌더링한다. 빨간색 의미 스타일을 사용하며 빈 경우 `현재 긴급 위험 알림이 없습니다`를 표시한다.

- [ ] **Step 4: 제거 대상 정리**

검색 input, 느낌표 버튼, 이메일 profile card, 고정 logout 버튼과 관련 CSS를 제거하거나 더 이상 영향을 주지 않게 정리한다.

- [ ] **Step 5: 정적 검증**

Run: `npm run lint`

Expected: event listener cleanup과 hook dependency 오류 없이 성공한다.

### Task 4: 홈 레이아웃과 WebRTC 연결 카드

**Files:**
- Modify: `src/pages/dashboard/DashboardPage.tsx`
- Modify: `src/index.css`

**Interfaces:**
- Consumes: 기존 `riskMarkers`, `webrtcConnections`, `adminReports`, 연결 요청 state
- Consumes: Task 2의 `AdminSection`, `StatusBadge`
- Preserves: 지도 `/risk-map`, 실시간 연결 `/monitoring`, 제보 상세 route 이동

- [ ] **Step 1: 기존 페이지 배너 제거와 섹션 전환**

페이지 내부의 노란 emergency banner를 제거한다. 지도, 실시간 연결, 최근 위험 제보, 대기열을 `AdminSection`으로 감싸 제목을 카드 밖으로 이동하고 반복 설명 문구를 삭제한다.

- [ ] **Step 2: 홈 영역 순서 수정**

첫 행은 지도/실시간 연결, 둘째 행은 최근 위험 제보/WebRTC 연결 대기열 순서로 렌더링한다.

- [ ] **Step 3: 실시간 연결 카드 수정**

위험 badge를 카드 우측 상단에 두고 `AI 위험 감지` 문장을 제거한다. 장소, 영상 placeholder, 연결 시간만 유지하고 목록이 비면 empty state를 렌더링한다. 제목 오른쪽 `전체 보기 >`는 `/monitoring`으로 이동한다.

- [ ] **Step 4: 동일 높이와 내부 스크롤 CSS 구현**

첫 행 grid item을 stretch하고 두 콘텐츠 카드에 동일한 min/max block size를 적용한다. 지도 preview는 `height: 100%`, 연결 목록은 `overflow-y: auto`, `scrollbar-gutter: stable`, 영상은 `aspect-ratio: 16 / 9`를 적용한다.

- [ ] **Step 5: 동작 검증**

Run: `npm run build`

Expected: 기존 연결 요청 state와 모든 navigate 호출 타입이 유지되며 build가 성공한다.

### Task 5: 제보 목록 상세 필터

**Files:**
- Modify: `src/pages/reports/ReportListPage.tsx`
- Modify: `src/index.css`

**Interfaces:**
- Consumes: 기존 report label maps와 `AdminReport` 필드
- Produces: `ReportFilters`에 keyword, region, type, status, riskLevel, startDate, endDate, aiAnalysisStatus, sortOrder
- Preserves: row click의 `createReportDetailPath(report.id)`

- [ ] **Step 1: draft/applied 필터 상태 정의**

`draftFilters`와 `appliedFilters`를 같은 초기값으로 만들고 상세 조건 open state를 별도로 둔다. 지원하지 않는 서버 query parameter는 만들지 않는다.

- [ ] **Step 2: 실제 필터와 정렬 구현**

검색어는 ID, location, aiSummary, 선택적 reporterName을 검색한다. region은 location을 검색한다. enum 필드는 완전 일치 비교하고 날짜는 `reportedAt` 날짜 부분으로 inclusive 비교한다. 정렬은 최신, 오래된, 위험 점수 높은/낮은 순을 지원한다.

- [ ] **Step 3: 반응형 필터 UI 구현**

기본 행에 검색어, 지역, 카테고리, 처리 상태, 상세 조건 토글을 배치한다. 확장 영역에 위험도, 시작일, 종료일, AI 상태, 정렬을 둔다. 하단에 초기화와 필터 적용 버튼을 둔다.

- [ ] **Step 4: 테이블 컬럼 정리**

검토 상태 header와 cell을 제거하고 처리 상태는 공통 tone badge로 표시한다. `reviewStatus` 데이터와 상세 화면 사용은 유지한다.

- [ ] **Step 5: 정적 검증**

Run: `npm run lint`

Expected: `useMemo` dependency가 정확하고 type assertion은 기존 union select 처리 범위에만 존재한다.

### Task 6: 제보 상세 3행 구조와 상태 타임라인

**Files:**
- Modify: `src/pages/reports/ReportDetailPage.tsx`
- Modify: `src/index.css`

**Interfaces:**
- Consumes: Task 1의 formatter/tone helper
- Consumes: Task 2의 `AdminSection`, `AdminModal`, `StatusBadge`, `RiskLevelIndicator`
- Preserves: 기존 `nextReportStatusMap`, review/status transition validation

- [ ] **Step 1: Breadcrumb와 상세 grid 구현**

route param을 그대로 Breadcrumb 현재 항목에 사용한다. 첫 행 이미지/요약, 둘째 행 위험 지표/타임라인, 셋째 행 AI 요약의 CSS grid를 만들고 1180px 이하에서 한 열로 전환한다.

- [ ] **Step 2: 정보 요약 재구성**

ID, 제보자 fallback, 카테고리, formatted time, 위험 정보, 처리 정보를 요구 순서로 렌더링하고 주소를 full-span 하단에 둔다. 모든 긴 문자열에 줄바꿈 가능한 class를 적용한다.

- [ ] **Step 3: 이미지와 위험도 지표 구현**

선택적 `imageUrl`이 있으면 alt와 함께 표시하고 없으면 제보 ID를 포함한 placeholder를 표시한다. `RiskLevelIndicator`에 API의 `riskLevel`, `riskScore`를 직접 전달한다.

- [ ] **Step 4: 통합 세로 타임라인 구현**

접수, AI 분석, 검토, 처리 상태 네 단계를 한 카드에 렌더링한다. 단계 class는 현재 review/status 값으로 완료/현재/대기/실패를 계산한다. history는 처리 상태 단계 하단에 표시한다.

- [ ] **Step 5: 승인·반려와 처리 확인 모달 유지**

승인/반려는 pending일 때만 가능하게 하고 모달 확인 후 같은 `reviewStatus` state를 갱신한다. 처리 상태는 승인 이후 기존 다음 단계만 허용한다. `isSubmitting`으로 확인 버튼 중복 클릭을 막고 오류 state 영역을 준비하되 가짜 API 호출은 하지 않는다.

- [ ] **Step 6: AI 상황 요약 구현**

위험 단계/점수, `aiIndicators ?? riskTags`, `aiSummary`, 태그를 렌더링한다. 빈 요약/배열에는 분석 결과 없음 메시지를 표시한다.

- [ ] **Step 7: 정적 검증**

Run: `npm run build`

Expected: route param, 선택적 필드와 상태 전이 타입이 안전하며 build가 성공한다.

### Task 7: 전체 스타일 정리와 완료 검증

**Files:**
- Modify: `src/index.css`
- Review: all files changed by Tasks 1-6

**Interfaces:**
- Verifies: 관리자 화면에만 영향을 주는 선택자와 기존 route/API/auth 보존

- [ ] **Step 1: 반복 설명과 기존 파란색 검사**

Run: `Select-String -Path src/**/*.tsx,src/index.css -Pattern '#1570ef|#53b1fd|주요 지역 위험도 마커|AI 위험 감지 감지됨'`

Expected: 관리자 Primary로 쓰이는 기존 파란색과 제거 대상 문구가 남아 있지 않는다. 사용자 공통 스타일에 해당하는 값은 관리자 영향 여부를 확인한 뒤 유지한다.

- [ ] **Step 2: 변경 범위 검사**

Run: `git diff --name-only 42caac6..HEAD`

Expected: 관리자 관련 파일, 공통 관리자 helper/component, 설계/계획 문서만 표시된다.

- [ ] **Step 3: lint 실행**

Run: `npm run lint`

Expected: exit code 0.

- [ ] **Step 4: 타입 검사 및 production build 실행**

Run: `npm run build`

Expected: `tsc -b`와 Vite production build 모두 exit code 0. 별도 `typecheck` script가 없음을 결과에 기록한다.

- [ ] **Step 5: 수동 검증**

`npm run dev -- --host 127.0.0.1`로 실행하고 `/admin/login`에서 mock 계정으로 로그인한다. 홈 dropdown/ESC/외부 클릭/긴급 모달/전체 보기/내부 스크롤, 목록 필터 적용·초기화·접기·정렬, 상세 Breadcrumb/날짜/fallback/승인·반려 modal/상태 동기화를 확인한다. 1920px와 1280px, 좁은 viewport에서 겹침 여부를 확인한다.

- [ ] **Step 6: 최종 diff 검사**

Run: `git diff --check`

Expected: whitespace 오류가 없다.

