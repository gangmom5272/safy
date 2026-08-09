import assert from 'node:assert/strict'
import test from 'node:test'
import { uploadReportImage } from './mediaUploadFlow.ts'

function imageFile(size = 4) {
  return new File([new Uint8Array(size)], 'scene.jpg', { type: 'image/jpeg' })
}

test('image upload follows issue, direct PUT, then complete order', async () => {
  const calls: string[] = []
  const file = imageFile()

  await uploadReportImage(31, file, {
    issue: async (reportId, request) => {
      calls.push(`issue:${reportId}:${request.filename}:${request.contentType}`)
      return {
        mediaAssetId: 72,
        uploadUrl: 'http://storage.example/upload?signature=abc',
        uploadMethod: 'PUT',
        requiredHeaders: { 'Content-Type': 'image/jpeg', 'x-amz-meta-test': 'value' },
        maxFileSize: 10,
        expiresAt: '2026-07-27T06:00:00Z',
      }
    },
    put: async (url, body, headers) => {
      assert.equal(url, 'http://storage.example/upload?signature=abc')
      assert.equal(body, file)
      assert.deepEqual(headers, { 'Content-Type': 'image/jpeg', 'x-amz-meta-test': 'value' })
      assert.equal('Authorization' in headers, false)
      calls.push('put')
    },
    complete: async (reportId, mediaAssetId) => {
      calls.push(`complete:${reportId}:${mediaAssetId}`)
    },
  })

  assert.deepEqual(calls, ['issue:31:scene.jpg:image/jpeg', 'put', 'complete:31:72'])
})

test('server file-size limit stops direct upload and completion', async () => {
  let putCalled = false
  let completeCalled = false

  await assert.rejects(() => uploadReportImage(31, imageFile(11), {
    issue: async () => ({
      mediaAssetId: 72,
      uploadUrl: 'http://storage.example/upload',
      uploadMethod: 'PUT',
      requiredHeaders: { 'Content-Type': 'image/jpeg' },
      maxFileSize: 10,
      expiresAt: '2026-07-27T06:00:00Z',
    }),
    put: async () => { putCalled = true },
    complete: async () => { completeCalled = true },
  }), /10바이트/)

  assert.equal(putCalled, false)
  assert.equal(completeCalled, false)
})
