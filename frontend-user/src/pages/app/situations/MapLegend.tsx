import { RISK_MARKER_COLORS } from './situationMapUtils'
import { shortRiskLevelLabels, type RiskLevel } from './types'

// 지도의 네모칸(격자)과 핀(제보)이 같은 5색 팔레트를 쓰므로 범례 하나로 둘 다 설명한다.
// 관리자 콘솔 RISK_LEVEL_ORDER 와 같은 순서(높은 등급 먼저)로 둔다. 안전 정보라
// 위험한 쪽을 먼저 읽게 하고, 두 화면의 범례가 어긋나지 않게 한다.
const LEGEND_ORDER = ['critical', 'high', 'medium', 'low', 'very_low'] as const satisfies readonly RiskLevel[]

/**
 * 색 5단계 칩. 관리자 위험도 지도처럼 지도 위 우측 하단 오버레이로 얹는다.
 * 같은 자리를 쓰는 상태 토스트와는 .situation-map-overlays 스택이 세로로 갈라 준다.
 * 지도 프레임 안에서만 위치가 잡히므로 SituationMap 이 그린다.
 */
export function MapLegendScale() {
  return (
    <ul className="map-legend-scale" aria-label="지도 색깔 범례">
      {LEGEND_ORDER.map((level) => (
        <li key={level}>
          <span
            className="map-legend-scale__swatch"
            style={{ backgroundColor: RISK_MARKER_COLORS[level] }}
            aria-hidden="true"
          />
          {shortRiskLevelLabels[level]}
        </li>
      ))}
    </ul>
  )
}

/** 오버레이에 넣을 수 없는 설명 문장. 지도 바로 아래에 둔다. */
export function MapLegendNotes() {
  return (
    <div className="map-legend-notes">
      <p>네모칸은 그 구역의 혼잡 정도, 핀은 개별 제보입니다.</p>
      {/*
        색이 없는 칸을 '안전한 곳'으로 읽으면 정반대의 오해가 된다. 서버는 근거가 있는
        칸만 내려주고 없는 칸은 응답에서 빠지므로, 빈 곳은 판단 불가라는 뜻이다.
      */}
      <p>색이 없는 곳은 아직 정보가 없는 곳입니다. 안전하다는 뜻은 아닙니다.</p>
    </div>
  )
}
