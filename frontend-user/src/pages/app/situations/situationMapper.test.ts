import assert from 'node:assert/strict'
import test from 'node:test'
import {
  createLiveReportParams,
  mapAreaMapData,
  mapAreaStatus,
  mapLiveReport,
  mapLocationSearchResult,
  mapReportWebRtcInfo,
} from './situationMapper.ts'

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
  riskLevel: 'MEDIUM',
  activeReportCount: 7,
  liveReportCount: 2,
  updatedAt: '2026-07-29T01:00:00Z',
}

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
      riskLevel: 'low',
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
  assert.equal(mapData.status?.liveReportCount, 2)
  assert.equal(mapData.status?.recentReportCount, 7)
  assert.equal(mapData.status?.riskLevel, 'medium')
})

test('상황 요약이 없는 지도 데이터도 처리한다', () => {
  const mapData = mapAreaMapData({ reports: { content: [], nextCursor: null, hasNext: false } })

  assert.equal(mapData.status, undefined)
  assert.deepEqual(mapData.reportMarkers, [])
})

test('지역 상황 요약이 활성·라이브 제보 수를 모두 읽는다', () => {
  const status = mapAreaStatus(areaStatusResponse)

  assert.equal(status.riskLevel, 'medium')
  assert.equal(status.recentReportCount, 7)
  assert.equal(status.liveReportCount, 2)
  // 백엔드 AreaStatusResponse 에는 혼잡도·날씨 필드가 없다.
  assert.equal(status.congestionLevel, 'normal')
  assert.equal(status.weather, undefined)
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
