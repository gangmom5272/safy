import assert from 'node:assert/strict'
import test from 'node:test'
import { isVideoAttachment, normalizeAttachments } from './mypageUtils.ts'

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
