import assert from 'node:assert/strict'
import test from 'node:test'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { ApiRequestError, toApiRequestError } from './errors.ts'
import type { ApiErrorResponse } from './types.ts'

test('API error envelope is converted without losing server details', () => {
  const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig
  const data: ApiErrorResponse = {
    success: false,
    code: 'COMMON_006',
    message: '이미 처리된 요청입니다.',
    fieldErrors: [{ field: 'reason', reason: '필수 값입니다.' }],
    path: '/api/v1/operator/reports/1/reject',
    timestamp: '2026-07-27T05:00:00Z',
  }
  const source = new AxiosError('Request failed', 'ERR_BAD_REQUEST', config, undefined, {
    data,
    status: 409,
    statusText: 'Conflict',
    headers: new AxiosHeaders(),
    config,
  })

  const error = toApiRequestError(source)

  assert.ok(error instanceof ApiRequestError)
  assert.equal(error.status, 409)
  assert.equal(error.code, 'COMMON_006')
  assert.deepEqual(error.fieldErrors, data.fieldErrors)
})

test('unknown failures use a stable fallback message', () => {
  const error = toApiRequestError(new Error('socket detail'))

  assert.equal(error.message, '요청을 처리하지 못했습니다.')
  assert.equal(error.status, undefined)
})
