import assert from 'node:assert/strict'
import test from 'node:test'
import type { AdminReport } from '../../types/admin.ts'
import { deduplicateReportsById } from './reportRowKey.ts'

function report(overrides: Partial<AdminReport>): AdminReport {
  return {
    id: 1,
    location: '강남구',
    type: 'crowd',
    riskLevel: 'low',
    riskScore: 20,
    reportedAt: '2026-08-05T01:00:00Z',
    status: 'checking',
    reviewStatus: null,
    aiAnalysisStatus: null,
    managerName: '관리자',
    aiSummary: '',
    riskTags: [],
    history: [],
    ...overrides,
  }
}

test('같은 제보의 배정 이력은 최신 상태 한 건만 남긴다', () => {
  const unique = deduplicateReportsById([
    report({
      id: 5,
      assignmentId: 101,
      assignedAt: '2026-08-05T01:00:00Z',
      status: 'reassigned',
    }),
    report({
      id: 5,
      assignmentId: 102,
      assignedAt: '2026-08-05T02:00:00Z',
      status: 'in_progress',
    }),
    report({ id: 6, assignmentId: 103, status: 'completed' }),
  ])

  assert.equal(unique.length, 2)
  assert.equal(unique.find((item) => item.id === 5)?.assignmentId, 102)
  assert.equal(unique.find((item) => item.id === 5)?.status, 'in_progress')
})
