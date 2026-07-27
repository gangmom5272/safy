import assert from 'node:assert/strict'
import test from 'node:test'
import { createRefreshCoordinator } from './refreshCoordinator.ts'

test('concurrent callers share one refresh request', async () => {
  let requestCount = 0
  let resolveRequest: ((token: string) => void) | undefined
  const request = () => {
    requestCount += 1
    return new Promise<string>((resolve) => {
      resolveRequest = resolve
    })
  }
  const refresh = createRefreshCoordinator(request)

  const first = refresh()
  const second = refresh()
  resolveRequest?.('renewed-token')

  assert.equal(await first, 'renewed-token')
  assert.equal(await second, 'renewed-token')
  assert.equal(requestCount, 1)
})

test('a completed refresh does not block the next refresh request', async () => {
  let requestCount = 0
  const refresh = createRefreshCoordinator(async () => {
    requestCount += 1
    return `token-${requestCount}`
  })

  assert.equal(await refresh(), 'token-1')
  assert.equal(await refresh(), 'token-2')
  assert.equal(requestCount, 2)
})
