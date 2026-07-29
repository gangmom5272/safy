export type CongestionLevel = 'RELAXED' | 'NORMAL' | 'CROWDED' | 'VERY_CROWDED'
export type RiskLevel = 'SAFE' | 'CAUTION' | 'WARNING' | 'DANGER'

export interface AreaLocation { areaId: number; areaName: string; address: string | null; latitude: number; longitude: number }
export interface MapZone { id: number; name: string; latitude: number; longitude: number; congestionLevel: CongestionLevel }
export interface MapReport { id: number; latitude: number; longitude: number; riskLevel: RiskLevel }
export interface AreaMapData { center: { latitude: number; longitude: number }; zones: MapZone[]; reports: MapReport[] }
export interface AreaStatus { riskLevel: RiskLevel | null; congestionLevel: CongestionLevel | null; recentReportCount: number; weather: { temperature: number | null; condition: string | null } | null; updatedAt: string | null }
export type ReportMediaType = 'IMAGE' | 'VIDEO' | 'LIVE'
export interface RecentReport { id: number; maskedThumbnailUrl?: string | null; thumbnailUrl?: string | null; mediaType?: ReportMediaType; reportType: string; title: string; locationName: string; createdAt: string; riskLevel: RiskLevel; isLive: boolean }
export type LoadState = 'idle' | 'loading' | 'success' | 'error'
