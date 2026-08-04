import type { RiskLevelValue } from '../../types/admin'
import { getRiskLabel, resolveRiskLevel, RISK_LEVEL_CONFIG, RISK_LEVEL_ORDER } from '../../utils/adminRisk'

interface RiskLevelIndicatorProps {
  level: RiskLevelValue | string | null
  score: number | null
}

export function RiskLevelIndicator({ level, score }: RiskLevelIndicatorProps) {
  const normalizedLevel = resolveRiskLevel(level, score)
  const displayOrder = [...RISK_LEVEL_ORDER].reverse()
  const currentIndex = normalizedLevel ? displayOrder.indexOf(normalizedLevel) : -1
  // 점수는 위험도 평가가 끝난 제보에만 있다. 없을 때는 빈 값을 보여주지 않고 줄 자체를 뺀다.
  const hasScore = typeof score === 'number' && Number.isFinite(score)

  return (
    <div className="risk-indicator" aria-label={`현재 위험도 ${getRiskLabel(level, score)}${hasScore ? `, 위험 점수 ${score}점` : ''}`}>
      {hasScore && (
        <div className="risk-indicator__score">
          <span>현재 위험 점수</span>
          <strong>{`${score}점`}</strong>
        </div>
      )}
      <ol className="risk-indicator__levels">
        {displayOrder.map((riskLevel, index) => {
          const state = index === currentIndex ? 'is-current' : index < currentIndex ? 'is-passed' : 'is-pending'
          return (
            <li key={riskLevel} className={`risk-indicator__level ${state}`} style={index === currentIndex ? { backgroundColor: RISK_LEVEL_CONFIG[riskLevel].markerColor, color: RISK_LEVEL_CONFIG[riskLevel].markerTextColor } : undefined} aria-current={index === currentIndex ? 'step' : undefined}>
              <span>{index + 1}</span>
              <strong>{RISK_LEVEL_CONFIG[riskLevel].label}</strong>
            </li>
          )
        })}
      </ol>
    </div>
  )
}
