import assert from 'node:assert/strict'
import test from 'node:test'
import { getAnalysisStatusLabel, getSubcategoryLabel } from './reportLabels.ts'

test('세부 분류 enum 은 카테고리별로 한글 라벨이 된다', () => {
  assert.equal(getSubcategoryLabel('EXIT_CONGESTION'), '출구 혼잡')
  assert.equal(getSubcategoryLabel('TRAFFIC_ACCIDENT'), '교통사고')
  assert.equal(getSubcategoryLabel('ROAD_FLOODING'), '도로 침수')
  assert.equal(getSubcategoryLabel('ROAD_DAMAGE'), '도로 파손')
  // QA 화면에 'OTHER_SITUATION' 이 그대로 노출됐던 값이다.
  assert.equal(getSubcategoryLabel('OTHER_SITUATION'), '기타 상황')
})

test('세부 분류가 없으면 null 이라 상위 카테고리만 남는다', () => {
  assert.equal(getSubcategoryLabel(null), null)
  assert.equal(getSubcategoryLabel(undefined), null)
  assert.equal(getSubcategoryLabel(''), null)
})

test('모르는 세부 분류는 영문 enum 대신 안전한 라벨로 바뀐다', () => {
  assert.equal(getSubcategoryLabel('BRAND_NEW_SITUATION'), '기타')
})

test('분석 상태도 한글 라벨로 바뀌고 없으면 null 이다', () => {
  assert.equal(getAnalysisStatusLabel('COMPLETED'), '분석 완료')
  assert.equal(getAnalysisStatusLabel('NOT_ASSESSABLE'), '판정 불가')
  assert.equal(getAnalysisStatusLabel(null), null)
})
