import apiClient from '../../api/client'

/** 현재 비밀번호 확인 후 새 비밀번호로 변경한다. OPERATOR·SUPER_ADMIN 공통. */
export async function changeMyPassword(currentPassword: string, newPassword: string) {
  await apiClient.patch('/admin/me/password', { currentPassword, newPassword })
}
