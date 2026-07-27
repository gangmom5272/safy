import assert from 'node:assert/strict'
import test from 'node:test'
import { clearAccessToken, getAccessToken, setAccessToken } from './accessToken.ts'

test('access token is kept only in module memory and can be cleared', () => {
  clearAccessToken()
  assert.equal(getAccessToken(), null)

  setAccessToken('access-token')
  assert.equal(getAccessToken(), 'access-token')

  clearAccessToken()
  assert.equal(getAccessToken(), null)
})
