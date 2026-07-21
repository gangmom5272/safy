export type ReportStatus = 'RECEIVED' | 'CHECKING' | 'COMPLETED' | 'REJECTED'
export type MyPageView = 'MAIN' | 'REPORTS' | 'REPORT_DETAIL' | 'REPORT_EDIT' | 'FAVORITES' | 'SUPPORT' | 'FAQ' | 'NOTICES' | 'NOTICE_DETAIL' | 'INQUIRY_FORM' | 'INQUIRIES' | 'INQUIRY_DETAIL' | 'GUIDE' | 'SETTINGS' | 'ACCOUNT' | 'PRIVACY' | 'NOTIFICATION_SETTINGS'
export interface UserProfile { id: number; nickname: string; email: string; profileImageUrl?: string | null; grade?: string; helpedCount?: number; badgeCount?: number }
export interface ReportSummary { totalCount: number; receivedCount: number; checkingCount: number; completedCount: number; rejectedCount: number }
export interface StatusHistory { status: ReportStatus; changedAt: string; description: string }
export interface MyReport { id: number; type: string; title: string; description: string; locationName: string; address: string; createdAt: string; status: ReportStatus; thumbnailUrl?: string | null; maskedThumbnailUrl?: string | null; mediaUrls: string[]; adminReply?: string | null; history: StatusHistory[] }
export type RiskLevel = 'SAFE' | 'CAUTION' | 'WARNING' | 'DANGER'
export interface FavoriteLocation { id: number; areaId: number; placeName: string; alias: string; address: string; riskLevel: RiskLevel; isPrimary: boolean; latitude?: number; longitude?: number }
export interface Faq { id: number; category: string; question: string; answer: string }
export interface Notice { id: number; title: string; content: string; createdAt: string; isImportant: boolean }
export interface Inquiry { id: number; type: string; title: string; content: string; createdAt: string; status: 'WAITING' | 'ANSWERED'; attachmentName?: string; answer?: string; answeredAt?: string }
export interface NotificationSettingsData { enabled: boolean; reportEnabled: boolean; safetyEnabled: boolean; systemEnabled: boolean; savedLocationEnabled: boolean }
