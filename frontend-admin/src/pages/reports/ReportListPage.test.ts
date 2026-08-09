import assert from 'node:assert/strict'
import test from 'node:test'
import { createElement } from 'react'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { createServer } from 'vite'

async function renderReportListPage() {
  const vite = await createServer({
    appType: 'custom',
    logLevel: 'silent',
    server: { middlewareMode: true },
  })

  try {
    const { ReportListPage } = await vite.ssrLoadModule('/src/pages/reports/ReportListPage.tsx')
    return renderToString(createElement(MemoryRouter, null, createElement(ReportListPage)))
  } finally {
    await vite.close()
  }
}

test('제보 목록의 상세조건은 최초 진입 시 닫혀 있다', async () => {
  const html = await renderReportListPage()

  assert.doesNotMatch(html, /id="report-detail-filters"/)
  assert.match(html, />상세조건</)
})

test('건수는 목록 헤더 제목에 들어가고 검색 카드에는 남지 않는다', async () => {
  const html = await renderReportListPage()

  // 하드코딩이 아니라 현재 목록 길이를 쓴다. 서버 렌더 시점에는 아직 0건이다.
  assert.match(html, /총 0건의 제보 목록/)
  // 검색 카드 안에 있던 '총 N건' 줄은 사라졌다.
  assert.doesNotMatch(html, /report-filter__result-bar/)
})

test('정렬 버튼은 목록 헤더로 옮겨졌고 4종이 그대로 있다', async () => {
  const html = await renderReportListPage()

  const heading = /<div class="admin-section__heading">[\s\S]*?<\/div>\s*<div class="admin-card/.exec(html)?.[0] ?? ''
  for (const label of ['최신순', '오래된 순', '위험도 높은 순', '위험도 낮은 순']) {
    assert.match(heading, new RegExp(label), `${label} 이 목록 헤더에 없습니다.`)
  }
})

test('AI 분석 상태 열은 목록 테이블 헤더에 없다', async () => {
  const html = await renderReportListPage()

  assert.doesNotMatch(html, /<th>AI 분석 상태<\/th>/)
})
