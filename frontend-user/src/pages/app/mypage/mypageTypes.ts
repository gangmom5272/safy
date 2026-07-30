// 백엔드 ReportStatus 7종을 화면에 필요한 5종으로 줄인 값이다. 이름은 백엔드 enum 에
// 있는 것만 써서 대조하기 쉽게 한다. 합쳐지는 규칙은 mypageApi 의 toUiStatus 에 있다.
//   RECEIVED    ← RECEIVED, ASSIGNED        (담당자 확인 전 = 시민 취소 가능)
//   IN_PROGRESS ← ACCEPTED(레거시), IN_PROGRESS
//   CLOSED      ← CLOSED
export type ReportStatus = 'RECEIVED' | 'IN_PROGRESS' | 'CLOSED' | 'REJECTED' | 'CANCELLED'
// 백엔드 ReportType·ReportCategory enum 값 그대로다. 한글 라벨은 백엔드에 없으므로
// mypageUtils 의 getReportMediaTypeLabel·getReportCategoryLabel 에서 붙인다.
export type ReportTypeCode = 'IMAGE' | 'LIVE'
export type ReportCategoryCode = 'CROWD_CONGESTION' | 'ACCIDENT_RISK' | 'FLOOD_WEATHER' | 'FACILITY_ROAD' | 'OTHER'
export type MyPageView = 'MAIN' | 'REPORTS' | 'REPORT_DETAIL' | 'REPORT_EDIT' | 'FAVORITES' | 'SUPPORT' | 'FAQ' | 'NOTICES' | 'NOTICE_DETAIL' | 'INQUIRY_FORM' | 'INQUIRIES' | 'INQUIRY_DETAIL' | 'GUIDE' | 'SETTINGS' | 'PROFILE_EDIT' | 'ACCOUNT' | 'PRIVACY' | 'NOTIFICATION_SETTINGS'
export interface UserProfile { id: number; nickname: string; email: string; profileImageUrl?: string | null; grade?: string; helpedCount?: number; badgeCount?: number }
export interface ReportSummary { totalCount: number; receivedCount: number; inProgressCount: number; closedCount: number; rejectedCount: number }
export interface StatusHistory { status: ReportStatus; changedAt: string; description: string }
// 제보에 붙은 첨부 파일 하나. contentType 은 서버가 준 MIME 이며, 사진·영상 구분에
// 이 값을 먼저 쓴다. 값이 없을 때만 확장자로 추정한다.
export interface ReportAttachment { url: string; contentType: string | null; filename: string | null }
// type 은 목록 카드에 쓰는 표시용 라벨('이미지'·'라이브')이고, reportType 은 서버 원본
// 값이다. 상세의 유형 표시는 첨부 미디어와 원본 값을 함께 봐야 해서 둘 다 들고 있다.
export interface MyReport { id: number; type: string; reportType: ReportTypeCode; category: ReportCategoryCode; title: string; description: string; locationName: string; address: string; createdAt: string; status: ReportStatus; thumbnailUrl?: string | null; maskedThumbnailUrl?: string | null; attachments: ReportAttachment[]; adminReply?: string | null; history: StatusHistory[] }
export type RiskLevel = 'SAFE' | 'CAUTION' | 'WARNING' | 'DANGER'
export interface FavoriteLocation { id: number; areaId: number; placeName: string; alias: string; address: string; riskLevel: RiskLevel; isPrimary: boolean; latitude?: number; longitude?: number }
export interface Faq { id: number; category: string; question: string; answer: string }
export interface Notice { id: number; title: string; content: string; createdAt: string; isImportant: boolean }
export interface Inquiry { id: number; type: string; title: string; content: string; createdAt: string; status: 'WAITING' | 'ANSWERED'; attachmentName?: string; answer?: string; answeredAt?: string }
export interface NotificationSettingsData { enabled: boolean; reportEnabled: boolean; safetyEnabled: boolean; systemEnabled: boolean; savedLocationEnabled: boolean }
