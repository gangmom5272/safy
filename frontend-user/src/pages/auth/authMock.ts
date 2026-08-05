import type { UserResponse } from './authSession'

// 목 모드에서 로그인 없이 앱에 진입하기 위한 가짜 세션 사용자.
export const mockCitizen: UserResponse = {
  id: 1,
  displayName: '김싸피',
  role: 'CITIZEN',
  status: 'ACTIVE',
  email: 'ssafy@example.com',
  profileImageUrl: null,
  createdAt: '2026-07-01T00:00:00Z',
  updatedAt: '2026-07-01T00:00:00Z',
}
