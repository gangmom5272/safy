export interface AdminLiveMessagePayload {
  connectionId: number
  reportId: number
  adminEmail: string
  message: string
}

export async function sendAdminLiveMessage(payload: AdminLiveMessagePayload): Promise<void> {
  void payload
  throw new Error('관리자 메시지 전송 API 또는 실시간 이벤트가 연결되지 않았습니다.')
}
