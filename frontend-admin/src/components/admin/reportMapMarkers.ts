// 지도 마커·정보창 DOM 생성. 카카오 CustomOverlay 는 HTMLElement 를 받으므로
// 컴포넌트 JSX 가 아니라 여기서 만들고, 상태별 스타일 매핑도 이 파일에 모아 둔다.
import type { AdminReport } from '../../types/admin'
import { formatAdminTimestamp } from '../../utils/adminDate'
import { getRiskConfig, getRiskLabel } from '../../utils/adminRisk'
import { getVerificationConfig, isNewReport } from '../../utils/adminVerification'

/** 핀 요소 크기. yAnchor 1 과 맞물려 요소의 아래쪽 끝이 제보 좌표가 된다. */
export const PIN_HEIGHT = 40
const PIN_WIDTH = 30

/** 위험도가 아직 없는 제보의 핀 내부 색. */
const UNCLASSIFIED_RISK_COLOR = '#94A3B8'

function riskColorOf(report: AdminReport): string {
  return getRiskConfig(report.riskLevel, report.riskScore)?.markerColor ?? UNCLASSIFIED_RISK_COLOR
}

/**
 * 물방울 모양 위치 핀. 끝점이 (PIN_WIDTH / 2, PIN_HEIGHT) 로 정확히 떨어지도록 그린다.
 * 외곽선 대신 그림자를 써서 요소 박스 밖으로 도형이 삐져나가지 않게 한다.
 */
function pinSvg(riskColor: string): string {
  return `<svg class="map-pin__svg" width="${PIN_WIDTH}" height="${PIN_HEIGHT}"`
    + ` viewBox="0 0 ${PIN_WIDTH} ${PIN_HEIGHT}" aria-hidden="true" focusable="false">`
    + '<path class="map-pin__body" d="M15 40C15 40 28 24 28 15A13 13 0 1 0 2 15C2 24 15 40 15 40Z"/>'
    + '<circle cx="15" cy="15" r="6" fill="#FFFFFF"/>'
    + `<circle cx="15" cy="15" r="4" fill="${riskColor}"/>`
    + '</svg>'
}

/** 신규/확인 상태는 핀 전체 색으로, 위험도는 핀 내부 원으로 나눠 표현한다. */
export function createReportPin(report: AdminReport, onClick: () => void): HTMLButtonElement {
  const verification = getVerificationConfig(report)
  const isNew = isNewReport(report)

  const pin = document.createElement('button')
  pin.type = 'button'
  pin.className = `map-pin${isNew ? ' map-pin--new' : ''}`
  pin.style.setProperty('--pin-color', verification.pinColor)
  pin.setAttribute(
    'aria-label',
    `${report.location} · ${verification.label} · 위험도 ${getRiskLabel(report.riskLevel, report.riskScore)}`,
  )
  pin.innerHTML = '<span class="map-pin__pulse" aria-hidden="true"></span>'
    + pinSvg(riskColorOf(report))
    + (isNew ? '<span class="map-pin__badge" aria-hidden="true">NEW</span>' : '')
  pin.addEventListener('click', onClick)

  return pin
}

function appendRow(rows: HTMLElement, term: string, description: string) {
  const dt = document.createElement('dt')
  dt.textContent = term
  const dd = document.createElement('dd')
  dd.textContent = description
  rows.append(dt, dd)
}

interface InfoWindowHandlers {
  onDetail: () => void
  onClose: () => void
}

/** 마커를 클릭했을 때만 열리는 정보창. 주소는 지도에 상시 노출하지 않는다. */
export function createReportInfoWindow(
  report: AdminReport,
  { onDetail, onClose }: InfoWindowHandlers,
): HTMLElement {
  const verification = getVerificationConfig(report)

  const info = document.createElement('div')
  info.className = 'map-info'

  const header = document.createElement('header')
  header.className = 'map-info__header'

  const number = document.createElement('strong')
  number.className = 'map-info__number'
  // 백엔드에 별도 접수 번호 필드가 없어 제보 ID 를 접수 번호로 표시한다.
  number.textContent = `제보 #${report.id}`

  const status = document.createElement('span')
  status.className = 'map-info__status'
  status.textContent = verification.label
  status.style.backgroundColor = verification.badgeBackgroundColor
  status.style.color = verification.badgeTextColor

  const close = document.createElement('button')
  close.type = 'button'
  close.className = 'map-info__close'
  close.setAttribute('aria-label', '정보창 닫기')
  close.textContent = '×'
  close.addEventListener('click', onClose)

  header.append(number, status, close)

  const address = document.createElement('p')
  address.className = 'map-info__address'
  address.textContent = report.location

  const rows = document.createElement('dl')
  rows.className = 'map-info__rows'
  appendRow(rows, '위험도', getRiskLabel(report.riskLevel, report.riskScore))
  appendRow(rows, '등록 시간', formatAdminTimestamp(report.reportedAt) ?? '정보 없음')

  const detail = document.createElement('button')
  detail.type = 'button'
  detail.className = 'text-button map-info__detail'
  detail.textContent = '상세 보기 >'
  detail.addEventListener('click', onDetail)

  info.append(header, address, rows, detail)

  return info
}
