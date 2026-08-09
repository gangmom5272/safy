import assert from 'node:assert/strict'
import test from 'node:test'
import { buildCreateReportRequest, getReportSubcategory } from './reportMapper.ts'

test('every UI category and detail maps to the backend enum contract', () => {
  assert.equal(getReportSubcategory('crowd', '출구 혼잡'), 'EXIT_CONGESTION')
  assert.equal(getReportSubcategory('accident', '화재·연기'), 'FIRE_SMOKE')
  assert.equal(getReportSubcategory('weather', '도로 침수'), 'ROAD_FLOODING')
  assert.equal(getReportSubcategory('facility', '신호등 고장'), 'TRAFFIC_SIGNAL_FAILURE')
  assert.equal(getReportSubcategory('etc', '기타 상황'), 'OTHER_SITUATION')
})

test('report form is converted to an API create request', () => {
  assert.deepEqual(buildCreateReportRequest({
    method: 'photo',
    category: 'crowd',
    selectedTags: ['이동 어려움'],
    content: '현장에 인파가 많아 이동하기 어렵습니다.',
    location: { latitude: 37.5, longitude: 127 },
  }), {
    reportType: 'IMAGE',
    category: 'CROWD_CONGESTION',
    subcategory: 'MOVEMENT_DIFFICULTY',
    description: '현장에 인파가 많아 이동하기 어렵습니다.',
    latitude: 37.5,
    longitude: 127,
  })
})

test('missing required classification is rejected before an API request', () => {
  assert.throws(() => buildCreateReportRequest({
    method: 'live',
    category: 'crowd',
    selectedTags: [],
    content: '제보 내용입니다.',
    location: { latitude: 37.5, longitude: 127 },
  }))
})
