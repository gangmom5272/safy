import type { AdminUser } from '../types/auth'

// 목 모드에서 로그인 없이 콘솔에 진입하기 위한 가짜 세션 사용자.
export const mockOperator: AdminUser = {
  id: 1,
  displayName: '관제 담당자',
  role: 'OPERATOR',
  status: 'ACTIVE',
  controlCenterId: 23,
  controlCenterName: '강남구 재난안전상황실',
}
