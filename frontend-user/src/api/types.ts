export interface ApiResponse<T> {
  success: true
  data: T
}

export interface ApiFieldError {
  field: string
  reason: string
}

export interface ApiErrorResponse {
  success: false
  code: string
  message: string
  fieldErrors: ApiFieldError[]
  path: string
  timestamp: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface CursorResponse<T> {
  content: T[]
  nextCursor: string | null
  hasNext: boolean
}
