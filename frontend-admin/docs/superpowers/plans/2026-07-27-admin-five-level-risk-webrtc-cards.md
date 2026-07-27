# Admin Five-Level Risk and WebRTC Cards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자 전체 위험도 표시를 공통 5단계 모델로 통일하고 홈 WebRTC 항목을 독립 카드로 분리한다.

**Architecture:** 원시 API 위험도와 화면 표시 위험도를 분리하는 adapter와 단일 config를 추가한다. 모든 관리자 화면은 config 기반 `RiskBadge`와 정렬 helper를 사용하고, legacy 값은 추측 변환 없이 미분류로 처리한다.

**Tech Stack:** React 19, TypeScript 6, React Router 8, CSS

## Global Constraints

- Primary Color `#004AAD`를 유지한다.
- 백엔드 API 스펙과 WebRTC 동작을 변경하지 않는다.
- 새 `any` 타입과 새 라이브러리를 추가하지 않는다.
- 사용자 페이지를 수정하지 않는다.
- Git 커밋과 푸시는 사용자가 수행한다.

---

### Task 1: 공통 5단계 위험도 모델

**Files:**
- Modify: `src/types/admin.ts`
- Create: `src/utils/adminRisk.ts`
- Modify: `src/utils/adminStatus.ts`
- Create: `src/components/admin/RiskBadge.tsx`
- Modify: `src/components/admin/RiskLevelIndicator.tsx`

- [ ] 정규 5단계 타입과 legacy 입력 타입을 분리한다.
- [ ] 표시명, 색상, 마커 색상, 우선순위 config를 정의한다.
- [ ] 정확한 새 값만 정규화하고 legacy 값은 미분류로 반환하는 adapter를 구현한다.
- [ ] 고위험 호환 판별과 정렬 helper를 구현한다.
- [ ] 공통 배지와 5단계 지표가 config를 소비하게 한다.

### Task 2: 관리자 화면 적용

**Files:**
- Modify: `src/components/layout/AdminHeader.tsx`
- Modify: `src/pages/dashboard/DashboardPage.tsx`
- Modify: `src/pages/reports/ReportListPage.tsx`
- Modify: `src/pages/reports/ReportDetailPage.tsx`
- Modify: `src/pages/monitoring/MonitoringPage.tsx`
- Modify: `src/pages/risk-map/RiskMapPage.tsx`
- Modify: `src/mocks/adminMockData.ts`

- [ ] mock label의 책임을 공통 config로 이동한다.
- [ ] 모든 배지와 텍스트 위험도 표시를 `RiskBadge` 또는 공통 label helper로 교체한다.
- [ ] 필터를 5단계 option으로 교체하고 raw 값을 정규화해 비교한다.
- [ ] 정렬을 공통 우선순위로 교체하고 미분류를 마지막에 둔다.
- [ ] 지도 마커와 긴급/고위험 판별을 공통 helper로 교체한다.

### Task 3: WebRTC 독립 카드 스타일

**Files:**
- Modify: `src/index.css`
- Modify: `src/pages/dashboard/DashboardPage.tsx`

- [ ] 목록에 16px gap과 안전한 내부 여백을 적용한다.
- [ ] 각 카드에 독립 배경, 대비 border, radius, shadow, overflow를 적용한다.
- [ ] 영상과 하단 영역 경계를 명확히 하고 기존 클릭 이벤트를 유지한다.

### Task 4: 검증

**Files:**
- Verify: `src/**/*`

- [ ] 사용자 표시 label에 legacy 4단계 문구가 남아 있는지 검색한다.
- [ ] `npm run lint`를 실행한다.
- [ ] `npm run build`를 실행한다.
- [ ] 변경 파일 diff를 검토해 사용자 기존 변경을 보존했는지 확인한다.

