import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { test, type Page } from '@playwright/test'

type CaptureStep = {
  name: string
  title: string
  url: string
  action?: (page: Page) => Promise<void>
}

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const screenshotsDir = path.resolve(__dirname, '..', 'screenshots')
fs.mkdirSync(screenshotsDir, { recursive: true })

async function preparePage(page: Page) {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.addStyleTag({
    content: '*{scroll-behavior:auto !important;transition:none !important;animation:none !important;}',
  })

  await page.evaluate(async () => {
    if (document.fonts?.ready) {
      await document.fonts.ready
    }
  })

  const viewport = page.viewportSize()
  console.log('viewport:', viewport)

  const dimensions = await page.evaluate(() => ({
    innerWidth: window.innerWidth,
    innerHeight: window.innerHeight,
    devicePixelRatio: window.devicePixelRatio,
  }))
  console.log(dimensions)

  await page.waitForTimeout(500)
}

async function captureStep(page: Page, step: CaptureStep) {
  try {
    await page.goto(step.url, { waitUntil: 'domcontentloaded', timeout: 20_000 })
    await preparePage(page)

    if (step.action) {
      await step.action(page)
    }

    await page.locator('body').waitFor({ state: 'visible', timeout: 8_000 })
    await page.screenshot({ path: path.join(screenshotsDir, `${step.name}.png`), fullPage: false })
    console.log(`[성공] ${step.title}`)
  } catch (error) {
    console.log(`[실패] ${step.title}: ${error instanceof Error ? error.message : String(error)}`)
  }
}

test('capture app screens for handoff', async ({ page }) => {
  for (const file of fs.readdirSync(screenshotsDir).filter((name) => name.endsWith('.png'))) {
    fs.rmSync(path.join(screenshotsDir, file))
  }

  const steps: CaptureStep[] = [
    {
      name: 'U-01-home',
      title: '홈',
      url: '/app',
    },
    {
      name: 'U-02-situation-default',
      title: '상황 확인 검색 전',
      url: '/app/situations',
    },
    {
      name: 'U-03-situation-search-result',
      title: '지역 검색 결과',
      url: '/app/situations?query=강남',
    },
    {
      name: 'U-04-report-detail',
      title: '실시간 제보 상세',
      url: '/app/reports/1',
    },
    {
      name: 'U-05-report-create',
      title: '제보하기 메인',
      url: '/app/report',
    },
    {
      name: 'U-06-report-type',
      title: '제보 유형 선택',
      url: '/app/report/type',
    },
    {
      name: 'U-07-report-live',
      title: '실시간 영상 제보',
      url: '/app/report/live',
    },
    {
      name: 'U-08-report-media',
      title: '사진 또는 영상 제보',
      url: '/app/report/media',
    },
    {
      name: 'U-09-report-complete',
      title: '제보 완료',
      url: '/app/report/complete',
    },
    {
      name: 'U-10-notifications',
      title: '알림',
      url: '/app/notifications',
      action: async (currentPage) => {
        const firstNotification = currentPage.locator('button.notification-card').first()
        if (await firstNotification.count()) {
          await firstNotification.click()
          await currentPage.waitForTimeout(500)
        }
      },
    },
    {
      name: 'U-11-mypage',
      title: '마이페이지',
      url: '/app/mypage',
    },
    {
      name: 'U-12-my-reports',
      title: '내 제보 관리',
      url: '/app/mypage/reports',
    },
    {
      name: 'U-13-my-report-detail',
      title: '내 제보 상세',
      url: '/app/mypage/reports/1',
    },
    {
      name: 'U-14-saved-locations',
      title: '즐겨찾기 또는 저장한 위치',
      url: '/app/mypage/favorites',
    },
    {
      name: 'U-15-customer-center',
      title: '고객센터',
      url: '/app/mypage/support',
    },
    {
      name: 'U-16-guide',
      title: '이용안내',
      url: '/app/mypage/guide',
    },
  ]

  for (const step of steps) {
    await captureStep(page, step)
  }
})
