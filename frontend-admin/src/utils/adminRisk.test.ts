import assert from 'node:assert/strict'
import test from 'node:test'

import { isEmergencyRisk } from './adminRisk.ts'

test('긴급 위험 알림은 위험과 매우 위험 등급만 포함한다', () => {
  assert.equal(isEmergencyRisk('very_high'), true)
  assert.equal(isEmergencyRisk('high'), true)
  assert.equal(isEmergencyRisk('medium'), false)
  assert.equal(isEmergencyRisk('low'), false)
  assert.equal(isEmergencyRisk('very_low'), false)
})
