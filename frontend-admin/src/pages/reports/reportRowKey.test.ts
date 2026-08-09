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

// QA 화면에서 실제로 나온 모양. #21 이 6행, #20 이 2행, #16 이 3행으로 반복됐다.
// /operator/reports/me 가 배정 1건당 1행을 주기 때문이며, 재배정 횟수만큼 늘어난다.
test('재배정이 반복된 제보도 제보 1건당 한 행만 남는다', () => {
  const assignments = [
    report({ id: 21, assignmentId: 210, assignedAt: '2026-08-05T01:00:00Z', status: 'reassigned' }),
    report({ id: 21, assignmentId: 211, assignedAt: '2026-08-05T01:10:00Z', status: 'reassigned' }),
    report({ id: 21, assignmentId: 212, assignedAt: '2026-08-05T01:20:00Z', status: 'reassigned' }),
    report({ id: 21, assignmentId: 213, assignedAt: '2026-08-05T01:30:00Z', status: 'reassigned' }),
    report({ id: 21, assignmentId: 214, assignedAt: '2026-08-05T01:40:00Z', status: 'reassigned' }),
    report({ id: 21, assignmentId: 215, assignedAt: '2026-08-05T01:50:00Z', status: 'in_progress' }),
    report({ id: 20, assignmentId: 200, assignedAt: '2026-08-05T00:10:00Z', status: 'reassigned' }),
    report({ id: 20, assignmentId: 201, assignedAt: '2026-08-05T00:20:00Z', status: 'in_progress' }),
    report({ id: 16, assignmentId: 160, assignedAt: '2026-08-04T23:00:00Z', status: 'reassigned' }),
    report({ id: 16, assignmentId: 161, assignedAt: '2026-08-04T23:30:00Z', status: 'reassigned' }),
    report({ id: 16, assignmentId: 162, assignedAt: '2026-08-05T00:00:00Z', status: 'completed' }),
  ]

  const unique = deduplicateReportsById(assignments)

  assert.deepEqual(unique.map((item) => item.id), [21, 20, 16])
  assert.deepEqual(unique.map((item) => item.status), ['in_progress', 'in_progress', 'completed'])
})

test('서로 다른 제보는 표시 내용이 같아도 합쳐지지 않는다', () => {
  const same = {
    location: '서울 강남구 역삼동 804',
    reportedAt: '2026-08-05T01:00:00Z',
    managerName: '강남구 담당자 A',
    status: 'in_progress' as const,
  }
  const unique = deduplicateReportsById([
    report({ id: 31, assignmentId: 310, ...same }),
    report({ id: 32, assignmentId: 320, ...same }),
  ])

  assert.equal(unique.length, 2)
})

test('배정 시각이 없어도 제보 1건당 한 행만 남는다', () => {
  // assignedAt 이 비면 reportedAt 으로 비교한다. 둘 다 같으면 assignmentId 가 큰 쪽이 최신이다.
  const unique = deduplicateReportsById([
    report({ id: 40, assignmentId: 400, status: 'reassigned' }),
    report({ id: 40, assignmentId: 401, status: 'completed' }),
  ])

  assert.equal(unique.length, 1)
  assert.equal(unique[0].status, 'completed')
})
