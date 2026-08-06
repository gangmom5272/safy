import assert from 'node:assert/strict'
import test from 'node:test'
import {
  createLiveReportParams,
  mapAreaMapData,
  mapAreaStatus,
  mapLiveReport,
  mapLocationSearchResult,
  mapReportAnalysis,
  mapReportWebRtcInfo,
} from './situationMapper.ts'
import { getSubcategoryLabel, localPeakCongestionLevel, riskBadgeLabel } from './types.ts'
import { highestRiskLevel } from './situationMapUtils.ts'

// 공개 피드(/api/v1/feed)의 PublicReportView 그대로.
// 라이브 세션 정보(streamRoomId·streamStatus)와 처리 상태는 시민에게 내려오지 않는다.
const feedItem = {
  reportId: 41,
  reportType: 'LIVE',
  category: 'CROWD_CONGESTION',
  subcategory: 'EXIT_CONGESTION',
  description: '11번 출구 앞 대기 줄이 길어지고 있습니다.',
  latitude: 37.4979,
  longitude: 127.0276,
  address: '서울특별시 강남구 강남대로 396',
  areaId: 24,
  areaCode: '1168000000',
  areaName: '강남구',
  reportedAt: '2026-07-29T01:00:00Z',
  blurredImageUrl: 'http://localhost:9000/blurred.jpg',
  blurredImageExpiresAt: '2026-07-29T01:10:00Z',
}

// docs/api-contract.md 의 AreaStatusResponse 그대로.
const areaStatusResponse = {
  areaId: 24,
  areaCode: '1168000000',
  areaName: '강남구',
  congestion: {
    placeId: 14,
    placeName: '강남역',
    congestionLevel: 'SLIGHTLY_CROWDED',
    congestionMessage: '사람이 몰리고 있어요.',
    populationMin: 42000,
    populationMax: 50000,
    observedAt: '2026-07-29T00:57:00Z',
  },
  recentReportCount: 7,
  updatedAt: '2026-07-29T01:00:00Z',
}

test('피드가 준 위험도 등급을 쓴다', () => {
  // 백엔드 PublicReportView 에 riskLevel(소문자 enum)이 추가됐다. critical 은 very_high 다.
  assert.equal(mapLiveReport({ ...feedItem, riskLevel: 'very_high' }).riskLevel, 'critical')
  assert.equal(mapLiveReport({ ...feedItem, riskLevel: 'very_low' }).riskLevel, 'very_low')
})

test('평가 전 제보는 등급 없이 미평가로 남는다', () => {
  // 기본 등급으로 채우면 판정이 없는데 '낮음'이라고 단정하게 된다. 점수만 있어도 마찬가지다 —
  // 점수는 등급 문자열이 아니라서 폴백에 쓰면 최저 등급으로 떨어진다.
  assert.equal(mapLiveReport({ ...feedItem, riskLevel: null }).riskLevel, undefined)
  assert.equal(mapLiveReport({ ...feedItem, riskLevel: null, riskScore: 88 }).riskLevel, undefined)
  assert.equal(riskBadgeLabel(mapLiveReport({ ...feedItem, riskLevel: null }).riskLevel), '미평가')
})

test('묶인 제보 중 평가된 것이 없으면 마커 등급도 없다', () => {
  // 최저 등급을 돌려주면 미평가 마커가 '매우 낮음' 색으로 칠해진다.
  const unassessed = mapLiveReport({ ...feedItem, riskLevel: null })
  assert.equal(highestRiskLevel([unassessed]), undefined)
  assert.equal(highestRiskLevel([unassessed, mapLiveReport({ ...feedItem, riskLevel: 'high' })]), 'high')
})

test('피드의 세부 분류를 그대로 싣는다', () => {
  // 지도 마커가 느낌표 대신 '출구 혼잡' 같은 구체적인 이름을 쓰려면 이 값이 필요하다.
  assert.equal(mapLiveReport(feedItem).subcategory, 'EXIT_CONGESTION')
})

test('세부 분류 enum 은 제보 작성 화면과 같은 한글 라벨이 된다', () => {
  // 표를 새로 적지 않고 report-create 의 한글→enum 표를 뒤집어 쓰므로 두 화면이 어긋나지 않는다.
  assert.equal(getSubcategoryLabel('EXIT_CONGESTION'), '출구 혼잡')
  assert.equal(getSubcategoryLabel('MOVEMENT_DIFFICULTY'), '이동 어려움')
  assert.equal(getSubcategoryLabel('OTHER_SITUATION'), '기타 상황')
})

test('모르는 세부 분류와 빈 값은 null 이라 상위 분류로 대체된다', () => {
  // 영문 enum 이 지도에 그대로 나가면 안 되고, '기타'로 뭉개면 상위 분류보다 정보가 적다.
  assert.equal(getSubcategoryLabel('NEW_FROM_BACKEND'), null)
  assert.equal(getSubcategoryLabel(undefined), null)
  assert.equal(getSubcategoryLabel(''), null)
})

test('목록 조회는 서버가 받는 size 로만 요청한다', () => {
  // 서버(/reports/live)는 cursor·size 만 받는다. limit 으로 보내면 무시되어 항상 20건이
  // 돌아오고, sort·type 은 지원 자체가 없다.
  assert.deepEqual(createLiveReportParams({ sort: 'risk_desc', types: ['crowd'], limit: 50 }), {
    size: 50,
  })
})

test('조회 건수를 지정하지 않으면 기본값으로 요청한다', () => {
  assert.deepEqual(createLiveReportParams({ sort: 'latest', types: [] }), { size: 20 })
})

test('제보 피드 항목이 목록 카드로 변환된다', () => {
  const report = mapLiveReport(feedItem)

  // 숫자 ID 가 빈 문자열이 되면 상세 경로가 /reports/ 로 만들어져 요청이 실패한다.
  assert.equal(report.id, '41')
  assert.equal(report.type, 'crowd')
  assert.equal(report.locationName, '강남구')
  assert.equal(report.address, '서울특별시 강남구 강남대로 396')
  // 백엔드에 제목 필드가 없어 본문을 제목으로 쓴다.
  assert.equal(report.title, '11번 출구 앞 대기 줄이 길어지고 있습니다.')
  // 시민에게는 원본이 아니라 블러 처리된 대표 이미지만 내려온다.
  assert.equal(report.maskedThumbnailUrl, 'http://localhost:9000/blurred.jpg')
})

test('공개 피드 항목은 송출 중으로 표시되지 않는다', () => {
  // 라이브 제보라도 시민에게는 스트림 정보를 주지 않으므로 카드가 송출 중으로 뜨면 안 된다.
  const report = mapLiveReport(feedItem)

  assert.equal(report.isLive, false)
  assert.equal(report.isWebRtc, false)
})

test('블러 이미지가 아직 없으면 썸네일 없이 처리한다', () => {
  const report = mapLiveReport({ ...feedItem, blurredImageUrl: null })

  assert.equal(report.maskedThumbnailUrl, null)
})

test('지역 지도 마커가 제보의 지역명을 그대로 쓴다', () => {
  const mapData = mapAreaMapData({
    status: areaStatusResponse,
    boundaryGeoJson: null,
    reports: { content: [feedItem], nextCursor: null, hasNext: false },
  })

  assert.deepEqual(mapData.reportMarkers, [
    {
      reportId: '41',
      latitude: 37.4979,
      longitude: 127.0276,
      // 예전에는 백엔드에 없는 locationName·title 을 읽어 늘 빈 문자열이었다.
      locationName: '강남구',
      // 이 픽스처에는 riskLevel 이 없다. 예전에는 기본값 'low' 로 떨어져 미평가 제보가
      // '낮음'으로 표시됐다.
      riskLevel: undefined,
    },
  ])
})

test('지역 지도 응답의 상황 요약이 버려지지 않는다', () => {
  const mapData = mapAreaMapData({
    status: areaStatusResponse,
    boundaryGeoJson: null,
    reports: { content: [feedItem], nextCursor: null, hasNext: false },
  })

  // 지도 요약은 현재 페이지에 실린 마커 수가 아니라 지역 전체 건수를 써야 한다.
  assert.equal(mapData.status?.recentReportCount, 7)
  assert.equal(mapData.status?.congestion?.level, 'slightly_crowded')
})

test('상황 요약이 없는 지도 데이터도 처리한다', () => {
  const mapData = mapAreaMapData({ reports: { content: [], nextCursor: null, hasNext: false } })

  assert.equal(mapData.status, undefined)
  assert.deepEqual(mapData.reportMarkers, [])
})

test('지역 상황 요약이 서울시 실시간 혼잡도와 최근 제보 수를 읽는다', () => {
  const status = mapAreaStatus(areaStatusResponse)

  assert.equal(status.recentReportCount, 7)
  assert.equal(status.congestion?.placeName, '강남역')
  assert.equal(status.congestion?.level, 'slightly_crowded')
  assert.equal(status.congestion?.populationMin, 42000)
  assert.equal(status.congestion?.populationMax, 50000)
})

test('혼잡도 근거가 없으면 단계를 보통으로 채우지 않는다', () => {
  // 관측 지점이 없는 자치구와 수집이 멈춘 경우 백엔드가 congestion 을 null 로 준다.
  // 없음을 '보통'으로 메우면 관측이 끊긴 상태가 정상으로 보인다.
  const status = mapAreaStatus({ ...areaStatusResponse, congestion: null })

  assert.equal(status.congestion, undefined)
  assert.equal(status.recentReportCount, 7)
})

// 백엔드 ReportAnalysisResponse 그대로.
const analysisResponse = {
  reportId: 41,
  status: 'COMPLETED',
  riskLevel: 'high',
  riskScore: 76.4,
  crowdCount: 120,
  confidence: 0.81,
  confirmRequired: false,
  peakVisualCongestionScore: 10.78,
  peakLocalCongestionScore: 82.0,
  analyzedAt: '2026-07-29T01:00:00Z',
}

test('제보 분석이 AI 국소 최대 혼잡도를 읽는다', () => {
  const analysis = mapReportAnalysis(analysisResponse)

  // 예전에는 congestionLevel 필드가 없어 riskLevel 로 폴백했고 두 값이 늘 같았다.
  assert.equal(analysis.localPeakCongestionScore, 82.0)
  assert.equal(analysis.riskLevel, 'high')
  assert.equal(analysis.riskScore, 76.4)
  // confirmRequired 는 관제사 검수 판단용 내부 신호라 시민 화면 모델에 담지 않는다.
  assert.equal('confirmRequired' in analysis, false)
})

test('혼잡도 점수가 없으면 0으로 채우지 않는다', () => {
  // 0 은 "사람이 없는 정상 프레임"이고 없음은 "근거가 없다"이므로 구분해야 한다.
  const analysis = mapReportAnalysis({ ...analysisResponse, peakLocalCongestionScore: null })

  assert.equal(analysis.localPeakCongestionScore, undefined)
  assert.equal(analysis.riskLevel, 'high')
})

test('국소 최대 혼잡도 단계 경계가 30·60·80이다', () => {
  // 30·60 은 칸당 1명·2명 점수, 80 은 고밀도 경계다. 경계를 바꾸면 화면 단계가 통째로
  // 달라지므로 고정한다.
  assert.equal(localPeakCongestionLevel(0), 'relaxed')
  assert.equal(localPeakCongestionLevel(29.9), 'relaxed')
  assert.equal(localPeakCongestionLevel(30), 'normal')
  assert.equal(localPeakCongestionLevel(59.9), 'normal')
  assert.equal(localPeakCongestionLevel(60), 'slightly_crowded')
  assert.equal(localPeakCongestionLevel(79.9), 'slightly_crowded')
  assert.equal(localPeakCongestionLevel(80), 'crowded')
  assert.equal(localPeakCongestionLevel(100), 'crowded')
})

test('평가 불가 제보가 분석 대기 중으로 표시되지 않는다', () => {
  // NOT_ASSESSABLE 은 프레임이 전부 무효라 평가가 끝난 상태다. pending 으로 떨어뜨리면
  // "분석 대기 중"이 영구히 남는다.
  const analysis = mapReportAnalysis({ ...analysisResponse, status: 'NOT_ASSESSABLE', riskLevel: null })

  assert.equal(analysis.status, 'not_assessable')
})

test('아직 평가가 없는 제보는 대기 상태로 읽는다', () => {
  const analysis = mapReportAnalysis({ reportId: 41, status: 'NOT_STARTED', congestion: null })

  assert.equal(analysis.status, 'pending')
  assert.equal(analysis.riskLevel, undefined)
})

test('위험도 매우 낮음이 낮음으로 합쳐지지 않는다', () => {
  const analysis = mapReportAnalysis({ ...analysisResponse, riskLevel: 'very_low', riskScore: 9 })

  assert.equal(analysis.riskLevel, 'very_low')
})

test('라이브 세션 정보가 입장 가능 여부를 읽는다', () => {
  const info = mapReportWebRtcInfo({
    reportId: 41,
    streamRoomId: '0d1f2e3a-4b5c-6d7e-8f90-123456789abc',
    roomName: 'report-41',
    status: 'DISCONNECTED',
    startedAt: '2026-07-29T00:50:00Z',
    endedAt: null,
    expiresAt: '2026-07-29T02:50:00Z',
    joinable: false,
  })

  assert.deepEqual(info, {
    reportId: '41',
    roomId: '0d1f2e3a-4b5c-6d7e-8f90-123456789abc',
    roomName: 'report-41',
    // DISCONNECTED 는 재접속 대기이므로 연결 중으로 보여 준다.
    status: 'connecting',
    // 재접속 대기 세션은 입장할 수 없다.
    joinable: false,
  })
})

test('지역명 검색 결과가 주소 필드로 변환된다', () => {
  // 지역명 검색 응답은 주소를 displayName 으로 준다.
  const location = mapLocationSearchResult({
    areaId: 24,
    areaCode: '1168000000',
    areaName: '강남구',
    displayName: '강남구',
  })

  assert.equal(location.id, '24')
  assert.equal(location.name, '강남구')
  assert.equal(location.address, '강남구')
})

test('type 필드가 없으면 자치구로 본다', () => {
  // 자치구 상세(/administrative-areas/{id})처럼 애초에 type 이 없는 응답도 이
  // 매퍼를 함께 쓴다. 그 경로는 항상 자치구 문맥이라 area 가 추측이 아니라 사실이다.
  const location = mapLocationSearchResult({ id: 24, name: '강남구', displayName: '강남구' })

  assert.equal(location.type, 'area')
})

test('통합 검색 응답의 121장소 결과를 place 로 인식한다', () => {
  const location = mapLocationSearchResult({
    type: 'PLACE',
    id: 14,
    name: '역삼역',
    displayName: '역삼역',
    latitude: 37.5006,
    longitude: 127.0364,
  })

  assert.equal(location.type, 'place')
  assert.equal(location.id, '14')
  assert.equal(location.latitude, 37.5006)
})

test('외부 검색 결과는 식별자 없이 좌표로만 온다', () => {
  const location = mapLocationSearchResult({
    type: 'KEYWORD',
    id: null,
    name: '멀티캠퍼스 역삼지점',
    displayName: '서울 강남구 테헤란로 212',
    latitude: 37.5013,
    longitude: 127.0396,
  })

  assert.equal(location.type, 'keyword')
  // 우리 DB에 없는 장소라 id 가 빈 문자열이다. 좌표로 상황 요약을 조회한다.
  assert.equal(location.id, '')
  assert.equal(location.address, '서울 강남구 테헤란로 212')
  assert.equal(location.latitude, 37.5013)
})

test('좌표 기준 상황 요약이 근거 정확도와 제보 반경을 읽는다', () => {
  // 각주가 "가까운 ~ 기준"으로 바뀌는 근거이고, 혼잡도와 제보의 범위가 다름을 밝히는 값이다.
  const status = mapAreaStatus({
    congestion: {
      placeId: 14,
      placeName: '역삼역',
      congestionLevel: 'SLIGHTLY_CROWDED',
      populationMin: 21000,
      populationMax: 24000,
      observedAt: '2026-07-29T00:57:00Z',
    },
    congestionMatchType: 'NEAREST',
    recentReportCount: 2,
    reportRadiusMeters: 1000,
    updatedAt: '2026-07-29T01:00:00Z',
  })

  assert.equal(status.congestionMatchType, 'nearest')
  assert.equal(status.reportRadiusMeters, 1000)
  assert.equal(status.congestion?.level, 'slightly_crowded')
})

test('자치구·장소 상황 요약에는 근거 정확도와 반경이 없다', () => {
  // 그 둘은 기준 장소가 자명해 백엔드가 보내지 않는다. 없음을 값으로 채우면 각주가
  // 있지도 않은 반경을 표시한다.
  const status = mapAreaStatus(areaStatusResponse)

  assert.equal(status.congestionMatchType, undefined)
  assert.equal(status.reportRadiusMeters, undefined)
})
