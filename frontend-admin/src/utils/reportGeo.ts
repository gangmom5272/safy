import type { AdminReport } from '../types/admin'

export type PlottableReport = AdminReport & { latitude: number; longitude: number }

function isLatitude(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && Math.abs(value) <= 90
}

function isLongitude(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && Math.abs(value) <= 180
}

/**
 * 좌표가 없거나 범위를 벗어난 제보는 지도에 찍을 수 없다.
 * 담당 이력 응답에는 좌표가 없고, 잘못된 좌표 하나로 지도 전체가 깨지지 않도록 여기서 걸러낸다.
 */
export function hasCoordinates(report: AdminReport): report is PlottableReport {
  return isLatitude(report.latitude) && isLongitude(report.longitude)
}
