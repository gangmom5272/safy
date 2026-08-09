import type {
  AnalysisStatus,
  AreaDetail,
  AreaMapData,
  AreaStatus,
  CongestionInfo,
  CongestionLevel,
  CongestionMatchType,
  LiveReportSummary,
  LocationResultType,
  LocationSearchResult,
  ReportAnalysis,
  ReportDetail,
  ReportListParams,
  ReportMarker,
  ReportType,
  ReportWebRtcInfo,
  RiskLevel,
  WebRtcStatus,
} from './types'
// node --test 는 확장자 없는 상대 경로를 해석하지 못한다. 타입 import 와 달리 값
// import 는 실행 시점에 남으므로 확장자를 붙인다(reportApi.test.ts 와 같은 규칙).
import { DEFAULT_REPORT_LIMIT } from './types.ts'

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' ? (value as Record<string, unknown>) : {}
}

// 백엔드는 areaId·reportId 같은 식별자를 숫자로 준다. 숫자를 버리면 ID가 빈 값이 되어
// /areas//map 처럼 경로가 만들어지고 400이 온다. 숫자도 문자열로 받아들인다.
function asString(value: unknown, fallback = '') {
  if (typeof value === 'string') return value
  if (typeof value === 'number' && Number.isFinite(value)) return String(value)
  return fallback
}

function asNumber(value: unknown, fallback = 0) {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

function asOptionalNumber(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined
}

function asBoolean(value: unknown) {
  return value === true
}

// 백엔드는 모든 응답을 { success, data } 로 감싼다.
export function unwrapEnvelope(raw: unknown): unknown {
  const item = asRecord(raw)

  return 'success' in item && 'data' in item ? item.data : raw
}

export function readArrayResponse(raw: unknown): unknown[] {
  const unwrapped = unwrapEnvelope(raw)

  if (Array.isArray(unwrapped)) {
    return unwrapped
  }

  const item = asRecord(unwrapped)

  // 커서 페이지 응답은 content 에 목록이 담긴다.
  if (Array.isArray(item.content)) {
    return item.content
  }

  if (Array.isArray(item.items)) {
    return item.items
  }

  if (Array.isArray(item.data)) {
    return item.data
  }

  return []
}

function normalizeReportType(value: unknown): ReportType {
  if (value === 'crowd' || value === 'CROWD_CONGESTION' || value === '인파 밀집') {
    return 'crowd'
  }

  if (value === 'accident' || value === 'ACCIDENT_RISK' || value === '사고·위험') {
    return 'accident'
  }

  if (value === 'flood_weather' || value === 'FLOOD_WEATHER' || value === 'weather' || value === '침수·기상') {
    return 'flood_weather'
  }

  if (value === 'facility_road' || value === 'FACILITY_ROAD' || value === 'facility' || value === '시설물·도로') {
    return 'facility_road'
  }

  return 'etc'
}

// 라이브 세션이 열려 있으면(CREATED/ACTIVE) 송출 중으로 본다.
function isLiveStreamStatus(value: unknown) {
  return value === 'CREATED' || value === 'ACTIVE'
}

// 백엔드 RiskLevel.jsonValue() 는 소문자 enum 이름(very_low~very_high)이다.
// very_low 를 빠뜨리면 '매우 낮음'이 '낮음'으로 조용히 합쳐진다.
export function normalizeRiskLevel(value: unknown): RiskLevel {
  if (value === 'critical' || value === 'very_high' || value === '매우 높음' || value === 4) {
    return 'critical'
  }

  if (value === 'high' || value === 'HIGH' || value === '높음' || value === 3) {
    return 'high'
  }

  if (value === 'medium' || value === 'MEDIUM' || value === 'normal' || value === '보통' || value === 2) {
    return 'medium'
  }

  if (value === 'very_low' || value === 'VERY_LOW' || value === '매우 낮음') {
    return 'very_low'
  }

  return 'low'
}

// 백엔드는 enum 이름(CROWDED)을 내려준다. 원본 한글 라벨도 받아 두면 수집 계층이 값을
// 그대로 흘려보내도 단계가 '보통'으로 조용히 뭉개지지 않는다.
function normalizeCongestionLevel(value: unknown): CongestionLevel {
  if (value === 'CROWDED' || value === 'crowded' || value === '붐빔') {
    return 'crowded'
  }

  if (value === 'SLIGHTLY_CROWDED' || value === 'slightly_crowded' || value === '약간 붐빔') {
    return 'slightly_crowded'
  }

  if (value === 'RELAXED' || value === 'relaxed' || value === '여유') {
    return 'relaxed'
  }

  return 'normal'
}

// 백엔드가 보내는 값은 NOT_STARTED · COMPLETED · NOT_ASSESSABLE 세 가지다.
// NOT_ASSESSABLE 을 모르고 pending 으로 떨어뜨리면 프레임이 전부 무효라 평가가 끝난
// 제보에 "분석 대기 중"이 영구히 표시된다.
function normalizeAnalysisStatus(value: unknown): AnalysisStatus {
  if (value === 'processing' || value === 'completed' || value === 'failed' || value === 'not_assessable') {
    return value
  }

  if (value === 'PROCESSING') return 'processing'
  if (value === 'COMPLETED') return 'completed'
  if (value === 'NOT_ASSESSABLE') return 'not_assessable'
  if (value === 'FAILED') return 'failed'

  return 'pending'
}

function normalizeWebRtcStatus(value: unknown): WebRtcStatus {
  if (
    value === 'waiting' ||
    value === 'connecting' ||
    value === 'connected' ||
    value === 'ended' ||
    value === 'failed'
  ) {
    return value
  }

  // 백엔드 LiveSessionStatus 를 화면 상태로 옮긴다.
  if (value === 'CREATED') return 'waiting'
  if (value === 'ACTIVE') return 'connected'
  if (value === 'DISCONNECTED') return 'connecting'
  if (value === 'ENDED') return 'ended'
  if (value === 'FAILED') return 'failed'

  return 'none'
}

// 목록 조회 API(/reports/live, /areas/{areaId}/reports/live)가 받는 쿼리는 cursor 와
// size 뿐이다. limit 이라는 이름으로 보내면 서버가 무시해 항상 기본 20건만 돌아온다.
// sort·type 도 서버가 지원하지 않아 보내지 않는다. 정렬·필터는 별도 티켓에서 다룬다.
export function createLiveReportParams({ limit }: ReportListParams) {
  return { size: limit ?? DEFAULT_REPORT_LIMIT }
}

// 백엔드는 대문자("AREA"/"PLACE"/"KEYWORD")로 보낸다. 자치구 상세(/administrative-areas/{id})
// 처럼 애초에 type 필드가 없는 응답도 이 매퍼를 함께 쓰므로, 없으면 area 로 본다 — 그
// 경로는 항상 자치구 문맥이라 추측이 아니라 그 응답의 실제 의미다.
function normalizeLocationResultType(value: unknown): LocationResultType {
  if (value === 'PLACE' || value === 'place') return 'place'
  if (value === 'KEYWORD' || value === 'keyword') return 'keyword'
  return 'area'
}

function normalizeCongestionMatchType(value: unknown): CongestionMatchType | undefined {
  if (value === 'GRID' || value === 'grid') return 'grid'
  if (value === 'NEAREST' || value === 'nearest') return 'nearest'
  return undefined
}

export function mapLocationSearchResult(raw: unknown): LocationSearchResult {
  const item = asRecord(raw)

  return {
    id: asString(item.id ?? item.areaId ?? item.locationId),
    type: normalizeLocationResultType(item.type),
    name: asString(item.name ?? item.areaName ?? item.locationName),
    // 지역명 검색 응답은 주소를 displayName 으로 준다.
    address: asString(item.address ?? item.roadAddress ?? item.displayName),
    latitude: asOptionalNumber(item.latitude ?? item.lat),
    longitude: asOptionalNumber(item.longitude ?? item.lng),
  }
}

export function mapAreaDetail(raw: unknown): AreaDetail {
  const item = asRecord(raw)
  const location = mapLocationSearchResult(item)
  const center = asRecord(item.center)

  return {
    type: 'area',
    id: location.id,
    name: location.name,
    address: location.address,
    latitude: location.latitude ?? asNumber(item.centerLatitude ?? center.latitude, 0),
    longitude: location.longitude ?? asNumber(item.centerLongitude ?? center.longitude, 0),
  }
}

export function mapLiveReport(raw: unknown): LiveReportSummary {
  const item = asRecord(raw)

  const description = asString(item.description ?? item.summary)
  const isLive = item.streamStatus === undefined
    ? asBoolean(item.isLive ?? item.live)
    : isLiveStreamStatus(item.streamStatus)

  return {
    id: asString(item.id ?? item.reportId),
    // 백엔드 피드에는 제목 필드가 없어 본문 앞부분을 제목으로 쓴다.
    title: asString(item.title, '') || description || '제목 없는 제보',
    type: normalizeReportType(item.type ?? item.category),
    subcategory: asString(item.subcategory) || undefined,
    locationName: asString(item.locationName ?? item.areaName ?? item.placeName),
    address: asString(item.address),
    latitude: asOptionalNumber(item.latitude ?? item.lat),
    longitude: asOptionalNumber(item.longitude ?? item.lng),
    reportedAt: asString(item.reportedAt ?? item.createdAt ?? item.createdDate),
    relativeTime: asString(item.relativeTime),
    // 백엔드는 평가 전 제보의 riskLevel 을 null 로 준다. 기본 등급으로 채우면 '미평가'가
    // '낮음'이 된다. riskScore 로 폴백해서도 안 된다 — 점수는 등급 문자열이 아니어서
    // normalizeRiskLevel 의 어느 분기에도 걸리지 않고 결국 최저 등급으로 떨어진다.
    riskLevel: item.riskLevel ? normalizeRiskLevel(item.riskLevel) : undefined,
    description,
    // 공개 피드는 블러 처리된 대표 이미지만 blurredImageUrl 로 내려준다(원본은 관제 전용).
    maskedThumbnailUrl:
      asString(
        item.blurredImageUrl ?? item.maskedThumbnailUrl ?? item.thumbnailUrl ?? item.maskedImageUrl,
        '',
      ) || null,
    isWebRtc: item.streamRoomId ? true : asBoolean(item.isWebRtc ?? item.webRtc ?? item.webrtc),
    isLive,
    viewerCount: asOptionalNumber(item.viewerCount ?? item.participantCount),
    thumbnailType: asString(item.thumbnailType),
  }
}

// 지도 마커는 제보 피드 항목과 같은 페이로드다. 예전에는 백엔드에 없는 locationName·title
// 을 읽어 마커 이름이 늘 빈 문자열이었다. 카드와 이름·위험도가 어긋나지 않도록 같은
// 매핑을 재사용한다.
function mapReportMarker(raw: unknown): ReportMarker {
  const report = mapLiveReport(raw)

  return {
    reportId: report.id,
    latitude: report.latitude ?? 0,
    longitude: report.longitude ?? 0,
    locationName: report.locationName,
    riskLevel: report.riskLevel,
  }
}

export function mapAreaMapData(raw: unknown): AreaMapData {
  const item = asRecord(raw)
  const center = asRecord(item.center)
  const zones = Array.isArray(item.zones) ? item.zones : []
  // 백엔드 AreaMapResponse 는 마커를 reports.content 로 내려준다.
  const markerSource = item.reportMarkers ?? item.markers ?? asRecord(item.reports).content
  const markers = Array.isArray(markerSource) ? markerSource : []
  const firstMarker = asRecord(markers[0])

  return {
    // AreaMapResponse.status 에는 지역 위험도와 제보 건수가 들어 있다. 지도 요약이 현재
    // 페이지에 실린 마커 수 대신 지역 전체 건수를 쓸 수 있도록 함께 넘긴다.
    status: item.status ? mapAreaStatus(item.status) : undefined,
    center: {
      // 백엔드에 중심 좌표 필드가 없어 첫 제보 위치로 대체한다.
      latitude: asNumber(center.latitude ?? center.lat ?? firstMarker.latitude, 0),
      longitude: asNumber(center.longitude ?? center.lng ?? firstMarker.longitude, 0),
    },
    zones: zones.map((zoneRaw) => {
      const zone = asRecord(zoneRaw)
      const coordinates = Array.isArray(zone.coordinates) ? zone.coordinates : []

      return {
        id: asString(zone.id),
        name: asString(zone.name ?? zone.label),
        level: normalizeRiskLevel(zone.level ?? zone.riskLevel),
        coordinates: coordinates.map((coordinateRaw) => {
          const coordinate = asRecord(coordinateRaw)

          return {
            latitude: asNumber(coordinate.latitude ?? coordinate.lat, 0),
            longitude: asNumber(coordinate.longitude ?? coordinate.lng, 0),
          }
        }),
      }
    }),
    reportMarkers: markers.map(mapReportMarker),
  }
}

/**
 * 근거가 없으면 백엔드가 null 로 내려준다. 없음을 '보통'으로 채우면 관측이 끊긴 상태가
 * 정상으로 보이므로 undefined 를 그대로 넘긴다.
 */
function mapCongestionInfo(raw: unknown): CongestionInfo | undefined {
  if (!raw) {
    return undefined
  }

  const item = asRecord(raw)

  return {
    placeName: asString(item.placeName),
    level: normalizeCongestionLevel(item.congestionLevel),
    populationMin: asOptionalNumber(item.populationMin),
    populationMax: asOptionalNumber(item.populationMax),
    observedAt: asString(item.observedAt) || undefined,
  }
}

/**
 * 자치구·121장소·좌표 세 경로가 같은 모양으로 응답한다. 좌표 조회만 근거의 정확도
 * (`congestionMatchType`)와 제보 반경(`reportRadiusMeters`)을 추가로 주므로, 없으면
 * undefined 로 남겨 각주가 문구를 달리 쓰게 한다.
 */
export function mapAreaStatus(raw: unknown): AreaStatus {
  const item = asRecord(raw)

  return {
    congestion: mapCongestionInfo(item.congestion),
    congestionMatchType: normalizeCongestionMatchType(item.congestionMatchType),
    recentReportCount: asNumber(item.recentReportCount, 0),
    reportRadiusMeters: asOptionalNumber(item.reportRadiusMeters),
    updatedAt: asString(item.updatedAt),
  }
}

export function mapReportDetail(raw: unknown): ReportDetail {
  const item = asRecord(raw)

  return {
    ...mapLiveReport(item),
    maskedMediaUrl: asString(item.maskedMediaUrl ?? item.maskedVideoUrl, '') || null,
  }
}

export function mapReportAnalysis(raw: unknown): ReportAnalysis {
  const item = asRecord(raw)

  return {
    reportId: asString(item.reportId),
    status: normalizeAnalysisStatus(item.status),
    // 제보 분석은 그 제보를 분석한 결과만 담는다. 혼잡도도 AI가 계산한 국소 최대
    // 혼잡도를 쓴다. 예전에는 이 필드가 없어 riskLevel 로 폴백해 두 값이 늘 같았다.
    localPeakCongestionScore: asOptionalNumber(item.peakLocalCongestionScore),
    riskLevel: item.riskLevel ? normalizeRiskLevel(item.riskLevel) : undefined,
    riskScore: asOptionalNumber(item.riskScore),
    confidence: asOptionalNumber(item.confidence),
    estimatedPeopleCount: asOptionalNumber(item.estimatedPeopleCount ?? item.crowdCount),
    aiSummary: asString(item.aiSummary ?? item.summary),
    analyzedAt: asString(item.analyzedAt),
    errorMessage: asString(item.errorMessage),
  }
}

export function mapReportWebRtcInfo(raw: unknown): ReportWebRtcInfo {
  const item = asRecord(raw)

  return {
    reportId: asString(item.reportId),
    roomId: asString(item.streamRoomId ?? item.roomId),
    roomName: asString(item.roomName),
    status: normalizeWebRtcStatus(item.status),
    // 서버가 계산해 주는 입장 가능 여부. DISCONNECTED(재접속 대기)면 false 이므로
    // 화면에서 대기 상태와 구분하려면 status 를 함께 봐야 한다.
    joinable: asBoolean(item.joinable),
  }
}
