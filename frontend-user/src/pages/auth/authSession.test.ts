import assert from 'node:assert/strict'
import test from 'node:test'
import { canUseCitizenApp } from './authSession.ts'

test('only active citizens can enter the citizen app', () => {
  assert.equal(canUseCitizenApp({ role: 'CITIZEN', status: 'ACTIVE' }), true)
  assert.equal(canUseCitizenApp({ role: 'OPERATOR', status: 'ACTIVE' }), false)
  assert.equal(canUseCitizenApp({ role: 'CITIZEN', status: 'SUSPENDED' }), false)
})
