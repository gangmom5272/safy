import assert from 'node:assert/strict'
import test from 'node:test'
import { createServer } from 'vite'

import type { AdminReport } from '../types/admin.ts'

// adminVerification 은 adminDate 를 확장자 없이 import 한다. node 의 타입 스트립 실행기는
// 확장자 없는 경로를 찾지 못하므로 ReportListPage.test.ts 처럼 Vite 로 모듈을 불러온다.
async function loadAdminVerification(): Promise<typeof import('./adminVerification.ts')> {
  const vite = await createServer({
    appType: 'custom',
    logLevel: 'silent',
    server: { middlewareMode: true },
  })

  try {
    return await vite.ssrLoadModule('/src/utils/adminVerification.ts') as typeof import('./adminVerification.ts')
  } finally {
    await vite.close()
  }
}

const { isNewReportAt, isUncheckedReport } = await loadAdminVerification()

const NOW = Date.parse('2026-08-06T12:00:00Z')

function report(overrides: Partial<AdminReport> = {}): AdminReport {
  return {
    id: 1,
    location: '서울 마포구 홍대입구역 9번 출구',
    type: 'crowd',
    riskLevel: 'danger',
    riskScore: 92,
    reportedAt: '2026-08-06T11:50:00Z',
    assignedAt: '2026-08-06T11:50:00Z',
    status: 'checking',
    verificationStatus: 'NEW',
    reviewStatus: null,
    aiAnalysisStatus: null,
    managerName: '김관리',
    aiSummary: '',
    riskTags: [],
    history: [],
    ...overrides,
  }
}

test('배정 시각과 제보 시각이 모두 30분 안인 미확인 제보만 신규로 강조한다', () => {
  assert.equal(isNewReportAt(report(), NOW), true)
  assert.equal(isNewReportAt(report({ reportedAt: '2026-08-06T11:31:00Z', assignedAt: '2026-08-06T11:31:00Z' }), NOW), true)
  assert.equal(isNewReportAt(report({ assignedAt: '2026-08-06T11:29:00Z' }), NOW), false)
  assert.equal(isNewReportAt(report({ reportedAt: '2026-08-06T11:29:00Z' }), NOW), false)
  assert.equal(isNewReportAt(report({ verificationStatus: 'CHECKED' }), NOW), false)
})

test('제보 시각이 30분을 넘었으면 방금 재배정돼도 강조하지 않는다', () => {
  const reassigned = report({ reportedAt: '2026-08-06T08:00:00Z', assignedAt: '2026-08-06T11:55:00Z' })
  assert.equal(isNewReportAt(reassigned, NOW), false)
})

test('최근 제보를 재배정으로 받으면 새 배정 시각으로 강조한다', () => {
  const reassigned = report({ reportedAt: '2026-08-06T11:40:00Z', assignedAt: '2026-08-06T11:55:00Z' })
  assert.equal(isNewReportAt(reassigned, NOW), true)
})

test('배정 시각이 없는 응답은 제보 시각으로 강조 여부를 판단한다', () => {
  assert.equal(isNewReportAt(report({ assignedAt: null }), NOW), true)
  assert.equal(isNewReportAt(report({ assignedAt: null, reportedAt: '2026-08-06T09:00:00Z' }), NOW), false)
})

test('강조가 끝나도 열어보지 않은 제보는 미확인 집계에 남는다', () => {
  const stale = report({ assignedAt: '2026-08-06T09:00:00Z' })
  assert.equal(isNewReportAt(stale, NOW), false)
  assert.equal(isUncheckedReport(stale), true)
})
