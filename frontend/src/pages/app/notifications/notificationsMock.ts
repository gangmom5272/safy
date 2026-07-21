import type { AppNotification, NotificationSettingsData } from './notificationsTypes'

export const USE_NOTIFICATIONS_MOCK_FALLBACK = true

const now = new Date()
const at = (daysAgo: number, hour: number, minute: number) => {
  const value = new Date(now)
  value.setDate(now.getDate() - daysAgo)
  value.setHours(hour, minute, 0, 0)
  return value.toISOString()
}

export const mockNotifications: AppNotification[] = [
  { id: 1, type: 'SAFETY', title: '홍대입구역 인근 매우 혼잡', content: '홍대입구역 9번 출구 주변 인파가 빠르게 늘고 있어요. 이동 시 주의해 주세요.', createdAt: at(0, 9, 41), isRead: false, isImportant: true, targetType: 'AREA', targetId: 1, areaId: 1, areaName: '홍대입구역' },
  { id: 2, type: 'REPORT', title: '제보 분석이 완료되었어요', content: '등록한 인파 제보의 분석이 완료되었습니다. 분석 결과를 확인해 보세요.', createdAt: at(0, 8, 25), isRead: false, isImportant: false, targetType: 'REPORT', targetId: 101, reportId: 101 },
  { id: 3, type: 'REPORT', title: '관리자가 제보를 확인했어요', content: '제보해 주신 내용을 관리자가 확인하고 처리 중입니다.', createdAt: at(1, 18, 12), isRead: true, isImportant: false, targetType: 'REPORT', targetId: 102, reportId: 102 },
  { id: 4, type: 'SYSTEM', title: '서비스 점검 안내', content: '더 안정적인 서비스를 위해 새벽 시간에 시스템 점검이 진행됩니다.', createdAt: at(1, 11, 0), isRead: false, isImportant: true, targetType: 'NONE' },
  { id: 5, type: 'REPORT', title: '제보가 정상적으로 접수되었어요', content: '소중한 제보가 접수되었습니다. 처리 상태가 변경되면 알려드릴게요.', createdAt: at(3, 14, 30), isRead: true, isImportant: false, targetType: 'REPORT', targetId: 103, reportId: 103 },
  { id: 6, type: 'SYSTEM', title: '앱 업데이트 소식', content: '알림 화면이 새로워졌어요. 중요한 안전 정보를 더 빠르게 확인할 수 있습니다.', createdAt: at(5, 10, 5), isRead: true, isImportant: false, targetType: 'NONE' },
]

export const mockNotificationSettings: NotificationSettingsData = {
  enabled: true,
  reportEnabled: true,
  safetyEnabled: true,
  systemEnabled: true,
  savedLocationEnabled: false,
}
