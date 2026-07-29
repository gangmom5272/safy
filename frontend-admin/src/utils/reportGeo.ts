import type { AdminReport } from '../types/admin'

/** 좌표가 없는 제보는 지도에 찍을 수 없다. 담당 이력 응답에는 좌표가 없다. */
export function hasCoordinates(report: AdminReport) {
  return typeof report.latitude === 'number' && typeof report.longitude === 'number'
}
