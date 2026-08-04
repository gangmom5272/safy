import assert from 'node:assert/strict'
import test from 'node:test'
import { createElement } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { createServer } from 'vite'

test('제보 목록의 상세조건은 최초 진입 시 닫혀 있다', async () => {
  const vite = await createServer({
    appType: 'custom',
    logLevel: 'silent',
    server: { middlewareMode: true },
  })

  try {
    const { ReportListPage } = await vite.ssrLoadModule('/src/pages/reports/ReportListPage.tsx')
    const html = renderToString(
      createElement(MemoryRouter, null, createElement(ReportListPage)),
    )

    assert.doesNotMatch(html, /id="report-detail-filters"/)
    assert.match(html, />상세조건</)
  } finally {
    await vite.close()
  }
})
