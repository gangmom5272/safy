import apiClient from '../../api/client'
import type { ApiResponse, PageResponse } from '../../api/types'

export interface OperatorSummary {
  id: number
  loginId: string
  displayName: string
  role: 'OPERATOR' | 'SUPER_ADMIN'
  status: 'ACTIVE' | 'SUSPENDED'
  controlCenterId: number | null
  controlCenterCode: string | null
  controlCenterName: string | null
  membershipRole: string | null
  lastLoginAt: string | null
  createdAt: string
}

export interface CreateOperatorPayload {
  loginId: string
  password: string
  displayName: string
  controlCenterId: number
  membershipRole: string
}

export interface UpdateOperatorPayload {
  displayName: string
  controlCenterId: number
  membershipRole: string
}

export interface ControlCenterSummary {
  id: number
  code: string
  name: string
  centerType: string
  status: 'ACTIVE' | 'INACTIVE'
  address: string | null
}

export const OPERATOR_STATUS_LABELS: Record<OperatorSummary['status'], string> = {
  ACTIVE: '활성',
  SUSPENDED: '정지',
}

export const OPERATOR_ROLE_LABELS: Record<OperatorSummary['role'], string> = {
  OPERATOR: '관제 담당자',
  SUPER_ADMIN: '최고 관리자',
}

/** 소속 직책은 자유 문자열이지만 시드·운영에서 쓰는 값만 선택지로 제공한다. */
export const MEMBERSHIP_ROLE_OPTIONS = [
  { value: 'OPERATOR', label: '관제 요원' },
  { value: 'MANAGER', label: '센터 관리자' },
] as const

export function membershipRoleLabel(value: string | null) {
  if (!value) return '미지정'
  return MEMBERSHIP_ROLE_OPTIONS.find((option) => option.value === value)?.label ?? value
}

export async function getOperators(page = 0, size = 20) {
  const response = await apiClient.get<ApiResponse<PageResponse<OperatorSummary>>>(
    '/admin/operators',
    { params: { page, size, sort: 'id,desc' } },
  )
  return response.data.data
}

export async function createOperator(payload: CreateOperatorPayload) {
  const response = await apiClient.post<ApiResponse<OperatorSummary>>('/admin/operators', payload)
  return response.data.data
}

export async function updateOperator(operatorId: number, payload: UpdateOperatorPayload) {
  const response = await apiClient.patch<ApiResponse<OperatorSummary>>(
    `/admin/operators/${operatorId}`,
    payload,
  )
  return response.data.data
}

export async function changeOperatorStatus(operatorId: number, status: OperatorSummary['status']) {
  const response = await apiClient.patch<ApiResponse<OperatorSummary>>(
    `/admin/operators/${operatorId}/status`,
    { status },
  )
  return response.data.data
}

/** 생성·수정 폼의 소속 관제센터 선택지. 활성 센터만 보여준다. */
export async function getControlCenters(size = 100) {
  const response = await apiClient.get<ApiResponse<PageResponse<ControlCenterSummary>>>(
    '/admin/control-centers',
    { params: { status: 'ACTIVE', page: 0, size, sort: 'name,asc' } },
  )
  return response.data.data.content
}
