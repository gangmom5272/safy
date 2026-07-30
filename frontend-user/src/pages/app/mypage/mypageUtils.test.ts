import assert from 'node:assert/strict'
import test from 'node:test'
import { formatReceiptNumber, getReportCategoryLabel, getReportMediaTypeLabel, isVideoAttachment, normalizeAttachments } from './mypageUtils.ts'

// docs/api-contract.md 의 ReportImageView 모양.
const imageView = {
  previewUrl: 'https://minio.example/report-1-a.jpg?X-Amz-Signature=abc',
  contentType: 'image/jpeg',
  originalFilename: 'a.jpg',
  displayOrder: 0,
}

test('상세 응답의 images 배열이 첨부 목록 전체로 변환된다', () => {
  const attachments = normalizeAttachments([
    imageView,
    { ...imageView, previewUrl: 'https://minio.example/report-1-b.jpg', originalFilename: 'b.jpg', displayOrder: 1 },
  ])

  // 예전에는 화면이 첫 장만 그려서 두 번째가 사라졌다.
  assert.equal(attachments.length, 2)
  assert.deepEqual(attachments.map((item) => item.filename), ['a.jpg', 'b.jpg'])
})

test('첨부는 displayOrder 순서로 정렬된다', () => {
  const attachments = normalizeAttachments([
    { ...imageView, previewUrl: 'https://minio.example/second.jpg', displayOrder: 2 },
    { ...imageView, previewUrl: 'https://minio.example/first.jpg', displayOrder: 1 },
  ])

  assert.deepEqual(attachments.map((item) => item.url), [
    'https://minio.example/first.jpg',
    'https://minio.example/second.jpg',
  ])
})

test('목록 응답의 대표 이미지 단일 객체도 배열이 된다', () => {
  const attachments = normalizeAttachments(imageView)

  assert.equal(attachments.length, 1)
  assert.equal(attachments[0].contentType, 'image/jpeg')
})

test('URL 문자열만 남은 예전 데이터도 배열이 된다', () => {
  const attachments = normalizeAttachments('https://minio.example/legacy.jpg')

  assert.deepEqual(attachments, [
    { url: 'https://minio.example/legacy.jpg', contentType: null, filename: null },
  ])
})

test('첨부가 없으면 빈 배열이다', () => {
  assert.deepEqual(normalizeAttachments(null), [])
  assert.deepEqual(normalizeAttachments(undefined), [])
  assert.deepEqual(normalizeAttachments([]), [])
  assert.deepEqual(normalizeAttachments(''), [])
})

test('업로드가 끝나지 않아 previewUrl 이 없는 첨부는 제외된다', () => {
  const attachments = normalizeAttachments([imageView, { ...imageView, previewUrl: null }])

  assert.equal(attachments.length, 1)
})

test('영상 판단은 서버가 준 MIME 을 먼저 쓴다', () => {
  assert.equal(isVideoAttachment({ url: 'https://x/a.jpg', contentType: 'video/mp4', filename: null }), true)
  // 확장자가 mp4 여도 MIME 이 이미지면 이미지로 본다.
  assert.equal(isVideoAttachment({ url: 'https://x/a.mp4', contentType: 'image/jpeg', filename: null }), false)
})

test('MIME 이 없으면 확장자로 추정하고, presigned 쿼리가 붙어도 판단한다', () => {
  assert.equal(isVideoAttachment({ url: 'https://x/a.mp4?X-Amz-Signature=abc', contentType: null, filename: null }), true)
  assert.equal(isVideoAttachment({ url: 'https://x/a.jpg?X-Amz-Signature=abc', contentType: null, filename: null }), false)
})

const imageAttachment = { url: 'https://x/a.jpg', contentType: 'image/jpeg', filename: 'a.jpg' }

test('유형은 첨부가 여러 장이어도 이미지 하나로 표시한다', () => {
  assert.equal(getReportMediaTypeLabel({ reportType: 'IMAGE', attachments: [imageAttachment, imageAttachment] }), '이미지')
})

test('영상 첨부가 섞여 있으면 영상으로 표시한다', () => {
  const video = { url: 'https://x/a.mp4', contentType: 'video/mp4', filename: 'a.mp4' }

  assert.equal(getReportMediaTypeLabel({ reportType: 'IMAGE', attachments: [imageAttachment, video] }), '영상')
})

test('첨부가 없는 라이브 제보는 영상으로 표시한다', () => {
  assert.equal(getReportMediaTypeLabel({ reportType: 'LIVE', attachments: [] }), '영상')
  assert.equal(getReportMediaTypeLabel({ reportType: 'IMAGE', attachments: [] }), '이미지')
})

test('카테고리 enum 은 한글 라벨로 바뀐다', () => {
  assert.equal(getReportCategoryLabel('CROWD_CONGESTION'), '인파 밀집')
  assert.equal(getReportCategoryLabel('FACILITY_ROAD'), '시설물·도로')
})

test('카테고리가 없거나 모르는 값이면 안전한 라벨을 쓴다', () => {
  assert.equal(getReportCategoryLabel(null), '카테고리 없음')
  // 서버에 카테고리가 추가되어도 [object Object] 나 빈칸이 나오지 않아야 한다.
  assert.equal(getReportCategoryLabel('BRAND_NEW_CATEGORY' as never), '기타')
})

test('접수 번호는 제보 ID 를 그대로 쓴다', () => {
  assert.equal(formatReceiptNumber(123465), '접수 번호 #123465')
})

test('쓸 수 없는 ID 면 번호를 만들지 않고 확인 불가로 표시한다', () => {
  assert.equal(formatReceiptNumber(undefined), '접수 번호 확인 불가')
  assert.equal(formatReceiptNumber(null), '접수 번호 확인 불가')
  assert.equal(formatReceiptNumber(Number.NaN), '접수 번호 확인 불가')
  assert.equal(formatReceiptNumber(0), '접수 번호 확인 불가')
})
