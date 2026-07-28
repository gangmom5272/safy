import axios from 'axios'
import type { ApiErrorResponse, ApiFieldError } from './types'

const FALLBACK_ERROR_MESSAGE = '요청을 처리하지 못했습니다.'

interface ApiRequestErrorOptions {
  status?: number
  code?: string
  fieldErrors?: ApiFieldError[]
}

export class ApiRequestError extends Error {
  readonly status?: number
  readonly code?: string
  readonly fieldErrors: ApiFieldError[]

  constructor(message: string, options: ApiRequestErrorOptions = {}) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = options.status
    this.code = options.code
    this.fieldErrors = options.fieldErrors ?? []
  }
}

function isApiErrorResponse(value: unknown): value is ApiErrorResponse {
  if (!value || typeof value !== 'object') return false

  const response = value as Partial<ApiErrorResponse>
  return response.success === false && typeof response.message === 'string'
}

export function toApiRequestError(error: unknown): ApiRequestError {
  if (error instanceof ApiRequestError) return error

  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    const response = error.response
    if (response && isApiErrorResponse(response.data)) {
      return new ApiRequestError(response.data.message, {
        status: response.status,
        code: response.data.code,
        fieldErrors: response.data.fieldErrors,
      })
    }

    return new ApiRequestError(FALLBACK_ERROR_MESSAGE, {
      status: response?.status,
    })
  }

  return new ApiRequestError(FALLBACK_ERROR_MESSAGE)
}
