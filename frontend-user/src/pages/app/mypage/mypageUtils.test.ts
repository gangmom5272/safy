import assert from 'node:assert/strict'
import test from 'node:test'
import { formatReceiptNumber, getReportCategoryLabel, getReportMediaTypeLabel, getStatusLabel, isVideoAttachment, normalizeAttachments, pickReportThumbnailUrl, toReportProgressSteps } from './mypageUtils.ts'
import type { ReportStatus, StatusHistory } from './mypageTypes.ts'

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

test('썸네일은 마스킹된 대표 이미지를 먼저 쓴다', () => {
  assert.equal(pickReportThumbnailUrl({
    maskedThumbnailUrl: 'https://x/masked.jpg',
    thumbnailUrl: 'https://x/plain.jpg',
    attachments: [imageAttachment],
  }), 'https://x/masked.jpg')
})

test('마스킹 값이 없으면 대표 이미지, 그것도 없으면 사진 첨부를 쓴다', () => {
  assert.equal(pickReportThumbnailUrl({
    maskedThumbnailUrl: null,
    thumbnailUrl: 'https://x/plain.jpg',
    attachments: [],
  }), 'https://x/plain.jpg')
  assert.equal(pickReportThumbnailUrl({
    maskedThumbnailUrl: null,
    thumbnailUrl: null,
    attachments: [imageAttachment],
  }), 'https://x/a.jpg')
})

test('영상 첨부는 img 로 그릴 수 없으므로 썸네일로 쓰지 않는다', () => {
  assert.equal(pickReportThumbnailUrl({
    maskedThumbnailUrl: null,
    thumbnailUrl: null,
    attachments: [{ url: 'https://x/a.mp4', contentType: 'video/mp4', filename: 'a.mp4' }],
  }), null)
})

// 관리자 이력에서 사용자용 3단계를 뽑는 규칙. 실제 서버 이력은 담당자 배정·오프라인·
// 재배정이 반복돼 같은 상태가 여러 번 들어온다(QA 지적 사항).
const RECEIVED_AT = '2026-08-05T09:49:00'
const adminHistory: StatusHistory[] = [
  { status: 'RECEIVED', changedAt: RECEIVED_AT, reason: 'AUTO_OPERATOR_ASSIGNMENT' },
  { status: 'IN_PROGRESS', changedAt: '2026-08-05T09:50:00', reason: 'OPERATOR_VIEWING' },
  { status: 'RECEIVED', changedAt: '2026-08-05T10:00:00', reason: '가져가' },
  { status: 'RECEIVED', changedAt: '2026-08-05T10:00:30', reason: 'OPERATOR_OFFLINE' },
  { status: 'IN_PROGRESS', changedAt: '2026-08-05T10:01:00', reason: 'OPERATOR_VIEWING' },
]

test('접수만 된 제보는 접수 대기 중 한 단계만 보여준다', () => {
  const steps = toReportProgressSteps({ status: 'RECEIVED', createdAt: RECEIVED_AT, history: [] })

  assert.deepEqual(steps, [{
    status: 'RECEIVED',
    changedAt: RECEIVED_AT,
    description: '접수가 진행 중입니다.',
    isCurrent: true,
  }])
})

test('관리자 이력이 여러 건이어도 사용자 단계는 반복되지 않는다', () => {
  const steps = toReportProgressSteps({ status: 'IN_PROGRESS', createdAt: RECEIVED_AT, history: adminHistory })

  assert.deepEqual(steps.map((step) => step.status), ['RECEIVED', 'IN_PROGRESS'])
  // 처리중은 최초로 진입한 시각을 쓴다. 재배정 뒤 두 번째 진입 시각이 아니다.
  assert.equal(steps[1].changedAt, '2026-08-05T09:50:00')
  assert.equal(steps[1].isCurrent, true)
})

test('처리 완료 제보는 3단계가 되고 완료 시각은 마지막 값을 쓴다', () => {
  const steps = toReportProgressSteps({
    status: 'CLOSED',
    createdAt: RECEIVED_AT,
    history: [...adminHistory, { status: 'CLOSED', changedAt: '2026-08-05T11:00:00', reason: '종결' }],
  })

  assert.deepEqual(steps.map((step) => step.status), ['RECEIVED', 'IN_PROGRESS', 'CLOSED'])
  assert.equal(steps[2].changedAt, '2026-08-05T11:00:00')
  assert.equal(steps[2].isCurrent, true)
  assert.equal(steps[0].isCurrent, false)
})

test('반려·취소는 처리 완료로 바뀌지 않는다', () => {
  const rejected = toReportProgressSteps({
    status: 'REJECTED',
    createdAt: RECEIVED_AT,
    history: [{ status: 'REJECTED', changedAt: '2026-08-05T11:00:00', reason: '허위 제보' }],
  })
  const cancelled = toReportProgressSteps({
    status: 'CANCELLED',
    createdAt: RECEIVED_AT,
    history: [{ status: 'CANCELLED', changedAt: '2026-08-05T10:10:00', reason: '시민 취소' }],
  })

  assert.deepEqual(rejected.map((step) => step.status), ['RECEIVED', 'REJECTED'])
  assert.deepEqual(cancelled.map((step) => step.status), ['RECEIVED', 'CANCELLED'])
})

test('처리중을 거치지 않고 종결된 제보는 처리중을 만들어내지 않는다', () => {
  const steps = toReportProgressSteps({
    status: 'CLOSED',
    createdAt: RECEIVED_AT,
    history: [{ status: 'CLOSED', changedAt: '2026-08-05T11:00:00', reason: '종결' }],
  })

  assert.deepEqual(steps.map((step) => step.status), ['RECEIVED', 'CLOSED'])
})

test('처리중에서 접수 대기로 되돌아간 제보는 현재 단계까지만 보여준다', () => {
  // QA 화면의 제보가 이 경우다. 관리자가 다시 가져가면 마지막 상태가 접수 대기 중이다.
  const steps = toReportProgressSteps({ status: 'RECEIVED', createdAt: RECEIVED_AT, history: adminHistory })

  assert.deepEqual(steps.map((step) => step.status), ['RECEIVED'])
  assert.equal(steps[0].isCurrent, true)
})

test('모르는 상태가 이력에 섞여 있어도 단계로 노출되지 않는다', () => {
  const steps = toReportProgressSteps({
    status: 'IN_PROGRESS',
    createdAt: RECEIVED_AT,
    history: [{ status: 'ESCALATED' as ReportStatus, changedAt: '2026-08-05T09:55:00', reason: 'CENTER_TRANSFER' }],
  })

  // 내부 코드가 화면에 나가지 않고, 현재 상태 단계는 시각 없이도 남는다.
  assert.deepEqual(steps.map((step) => step.status), ['RECEIVED', 'IN_PROGRESS'])
  assert.equal(steps[1].changedAt, null)
})

test('사용자 상태 라벨은 처리 중·처리 완료로 띄어 쓴다', () => {
  assert.equal(getStatusLabel('RECEIVED'), '접수 대기 중')
  assert.equal(getStatusLabel('IN_PROGRESS'), '처리 중')
  assert.equal(getStatusLabel('CLOSED'), '처리 완료')
  assert.equal(getStatusLabel('REJECTED'), '반려됨')
  assert.equal(getStatusLabel('CANCELLED'), '취소됨')
})

test('모르는 상태의 라벨도 내부 코드를 노출하지 않는다', () => {
  assert.equal(getStatusLabel('ESCALATED' as ReportStatus), '상태 확인 중')
})

test('단계마다 상태에 맞는 한글 안내 문구가 붙는다', () => {
  const steps = toReportProgressSteps({
    status: 'CLOSED',
    createdAt: RECEIVED_AT,
    history: [...adminHistory, { status: 'CLOSED', changedAt: '2026-08-05T11:00:00', reason: 'WORK_COMPLETED' }],
  })

  assert.deepEqual(steps.map((step) => step.description), [
    '접수가 진행 중입니다.',
    '관리자가 처리 중입니다.',
    '관리자가 제보 처리를 완료했습니다.',
  ])
})

test('관리자 내부 코드는 어떤 단계의 안내 문구에도 들어가지 않는다', () => {
  const steps = toReportProgressSteps({
    status: 'CLOSED',
    createdAt: RECEIVED_AT,
    history: [
      ...adminHistory,
      { status: 'RECEIVED', changedAt: '2026-08-05T10:30:00', reason: '강남구 담당자 B 에게 이관' },
      { status: 'CLOSED', changedAt: '2026-08-05T11:00:00', reason: 'WORK_COMPLETED' },
    ],
  })
  const text = steps.map((step) => step.description).join(' ')

  for (const internal of ['AUTO_OPERATOR_ASSIGNMENT', 'OPERATOR_VIEWING', 'OPERATOR_OFFLINE', 'WORK_COMPLETED', '가져가', '이관']) {
    assert.equal(text.includes(internal), false, `${internal} 이 사용자 문구에 들어갔습니다.`)
  }
})

test('반려 사유가 있으면 원문을 그대로 붙여 보여준다', () => {
  const steps = toReportProgressSteps({
    status: 'REJECTED',
    createdAt: RECEIVED_AT,
    history: [...adminHistory, { status: 'REJECTED', changedAt: '2026-08-05T11:00:00', reason: '허위 신고로 확인되었습니다.' }],
  })

  assert.equal(steps.at(-1)?.status, 'REJECTED')
  assert.equal(steps.at(-1)?.description, '관리자가 다음 사유로 제보를 반려했습니다: 허위 신고로 확인되었습니다.')
})

test('반려 사유 앞뒤 공백은 지우고 빈 문자열은 사유 없음으로 본다', () => {
  const padded = toReportProgressSteps({
    status: 'REJECTED',
    createdAt: RECEIVED_AT,
    history: [{ status: 'REJECTED', changedAt: '2026-08-05T11:00:00', reason: '  현장 확인 결과 위험이 없었습니다.  ' }],
  })
  const blank = toReportProgressSteps({
    status: 'REJECTED',
    createdAt: RECEIVED_AT,
    history: [{ status: 'REJECTED', changedAt: '2026-08-05T11:00:00', reason: '   ' }],
  })

  assert.equal(padded.at(-1)?.description, '관리자가 다음 사유로 제보를 반려했습니다: 현장 확인 결과 위험이 없었습니다.')
  assert.equal(blank.at(-1)?.description, '관리자 검토 결과 제보가 반려되었습니다.')
})

test('반려 사유가 없으면 기본 안내 문구를 쓴다', () => {
  const steps = toReportProgressSteps({
    status: 'REJECTED',
    createdAt: RECEIVED_AT,
    history: [{ status: 'REJECTED', changedAt: '2026-08-05T11:00:00', reason: null }],
  })

  assert.equal(steps.at(-1)?.description, '관리자 검토 결과 제보가 반려되었습니다.')
})

test('재배정·센터 이관 사유를 반려 사유로 쓰지 않는다', () => {
  // 두 사유는 서버가 toStatus=RECEIVED 이력에 남기므로 상태로 걸러진다.
  const steps = toReportProgressSteps({
    status: 'REJECTED',
    createdAt: RECEIVED_AT,
    history: [
      { status: 'RECEIVED', changedAt: '2026-08-05T10:00:00', reason: '담당자 휴가로 재배정합니다.' },
      { status: 'RECEIVED', changedAt: '2026-08-05T10:30:00', reason: '송파구 관제센터로 이관합니다.' },
      { status: 'REJECTED', changedAt: '2026-08-05T11:00:00', reason: null },
    ],
  })

  assert.equal(steps.at(-1)?.description, '관리자 검토 결과 제보가 반려되었습니다.')
})

test('반려 사유가 여러 건이면 마지막 반려 사유를 쓴다', () => {
  const steps = toReportProgressSteps({
    status: 'REJECTED',
    createdAt: RECEIVED_AT,
    history: [
      { status: 'REJECTED', changedAt: '2026-08-05T10:00:00', reason: '이전 반려 사유' },
      { status: 'REJECTED', changedAt: '2026-08-05T11:00:00', reason: '최종 반려 사유' },
    ],
  })

  assert.equal(steps.at(-1)?.description, '관리자가 다음 사유로 제보를 반려했습니다: 최종 반려 사유')
})

test('처리 중에서 반려로 바뀐 제보는 마지막 단계가 반려됨이다', () => {
  const steps = toReportProgressSteps({
    status: 'REJECTED',
    createdAt: RECEIVED_AT,
    history: [...adminHistory, { status: 'REJECTED', changedAt: '2026-08-05T11:00:00', reason: '허위 신고' }],
  })

  assert.deepEqual(steps.map((step) => step.status), ['RECEIVED', 'IN_PROGRESS', 'REJECTED'])
  // 반려 제보에 처리 완료 단계를 덧붙이지 않는다.
  assert.equal(steps.some((step) => step.status === 'CLOSED'), false)
  assert.equal(steps.at(-1)?.isCurrent, true)
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
