import { useMemo, useState } from 'react'
import { getRiskConfig } from '../../utils/adminRisk'
import type { GridRiskTrendPoint } from './gridRiskApi'

const WIDTH = 260
const HEIGHT = 96
const PADDING = { top: 8, right: 8, bottom: 18, left: 26 }

interface Segment {
  x1: number
  x2: number
  y: number
  color: string
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
}

/**
 * 격자 위험도 추이. 이력은 값이 바뀔 때만 쌓이므로 다음 점이 나올 때까지 값이 유지된
 * 것으로 읽어야 한다 — 계단(step)으로 그리고 선형 보간하지 않는다.
 * score 가 null 인 점은 위험 종료 기록이므로 그 구간은 비운다.
 */
export function GridTrendChart({ points }: { points: GridRiskTrendPoint[] }) {
  // 추이를 새로 조회할 때마다 이 컴포넌트가 다시 마운트되므로 그 시점을 "지금"으로 쓴다.
  const [endMs] = useState(() => Date.now())
  const chart = useMemo(() => {
    if (points.length === 0) return null

    // 응답은 최근 것부터다. 시간 오름차순으로 뒤집어 왼쪽→오른쪽으로 그린다.
    const ordered = [...points].reverse()
    const startMs = new Date(ordered[0].windowEndedAt).getTime()
    const spanMs = Math.max(endMs - startMs, 60_000)

    const plotWidth = WIDTH - PADDING.left - PADDING.right
    const plotHeight = HEIGHT - PADDING.top - PADDING.bottom
    const toX = (ms: number) => PADDING.left + ((ms - startMs) / spanMs) * plotWidth
    const toY = (score: number) => PADDING.top + (1 - score / 100) * plotHeight

    const segments: Segment[] = []
    ordered.forEach((point, index) => {
      if (point.score === null) return

      const fromMs = new Date(point.windowEndedAt).getTime()
      const next = ordered[index + 1]
      const toMs = next ? new Date(next.windowEndedAt).getTime() : endMs
      segments.push({
        x1: toX(fromMs),
        x2: toX(toMs),
        y: toY(point.score),
        color: getRiskConfig(point.congestionLevel, point.score)?.markerColor ?? '#94a3b8',
      })
    })

    return {
      segments,
      startLabel: formatTime(ordered[0].windowEndedAt),
      plotBottom: PADDING.top + plotHeight,
      toY,
    }
  }, [endMs, points])

  if (!chart) {
    return <p className="empty-state" role="status">이 격자에는 아직 기록된 추이가 없습니다.</p>
  }

  return (
    <figure className="grid-trend-chart">
      <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} role="img" aria-label="격자 위험도 추이 그래프">
        {[0, 50, 100].map((score) => (
          <g key={score}>
            <line
              x1={PADDING.left}
              x2={WIDTH - PADDING.right}
              y1={chart.toY(score)}
              y2={chart.toY(score)}
              stroke="#e4e7ec"
              strokeWidth={1}
            />
            <text x={PADDING.left - 4} y={chart.toY(score) + 3} textAnchor="end" fontSize={9} fill="#667085">
              {score}
            </text>
          </g>
        ))}

        {chart.segments.map((segment, index) => {
          const next = chart.segments[index + 1]
          return (
            <g key={`${segment.x1}-${index}`}>
              <line
                x1={segment.x1}
                x2={segment.x2}
                y1={segment.y}
                y2={segment.y}
                stroke={segment.color}
                strokeWidth={2.5}
                strokeLinecap="round"
              />
              {/* 값이 바뀌는 지점은 수직선으로 잇는다. 위험 종료로 끊긴 구간은 잇지 않는다. */}
              {next && next.x1 === segment.x2 && (
                <line
                  x1={segment.x2}
                  x2={segment.x2}
                  y1={segment.y}
                  y2={next.y}
                  stroke={next.color}
                  strokeWidth={1.5}
                  strokeDasharray="2 2"
                />
              )}
            </g>
          )
        })}

        <text x={PADDING.left} y={HEIGHT - 4} fontSize={9} fill="#667085">{chart.startLabel}</text>
        <text x={WIDTH - PADDING.right} y={HEIGHT - 4} textAnchor="end" fontSize={9} fill="#667085">지금</text>
      </svg>
      <figcaption className="grid-trend-chart__caption">
        값이 바뀐 시점만 기록됩니다. 선이 없는 구간은 위험이 없던 시간입니다.
      </figcaption>
    </figure>
  )
}
