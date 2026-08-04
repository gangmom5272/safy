import assert from 'node:assert/strict'
import test from 'node:test'
import { getReportRowKey } from './reportRowKey.ts'

test('같은 제보의 서로 다른 배정 이력은 고유한 행 키를 사용한다', () => {
  const firstKey = getReportRowKey({ id: 5, assignmentId: 101 })
  const secondKey = getReportRowKey({ id: 5, assignmentId: 102 })

  assert.equal(firstKey, 101)
  assert.equal(secondKey, 102)
  assert.notEqual(firstKey, secondKey)
})

test('배정 ID가 없는 제보는 제보 ID를 행 키로 사용한다', () => {
  assert.equal(getReportRowKey({ id: 5 }), 5)
})
