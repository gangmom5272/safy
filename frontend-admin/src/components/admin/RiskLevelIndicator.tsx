import type { RiskLevelValue } from '../../types/admin'
import { getRiskLabel, resolveRiskLevel, RISK_LEVEL_CONFIG, RISK_LEVEL_ORDER } from '../../utils/adminRisk'

interface RiskLevelIndicatorProps {
  level: RiskLevelValue | string | null
  score: number | null
}

export function RiskLevelIndicator({ level, score }: RiskLevelIndicatorProps) {
  const normalizedLevel = resolveRiskLevel(level, score)
  // 척도는 낮은 단계부터 오른쪽으로 올라가야 눈금(1~5)과 순서가 맞는다.
  const ascendingOrder = [...RISK_LEVEL_ORDER].reverse()
  const currentIndex = normalizedLevel ? ascendingOrder.indexOf(normalizedLevel) : -1
  const currentConfig = normalizedLevel ? RISK_LEVEL_CONFIG[normalizedLevel] : null
  // 점수는 위험도 평가가 끝난 제보에만 있다. 없을 때는 다른 항목과 같이 '-' 로 표시한다.
  const hasScore = typeof score === 'number' && Number.isFinite(score)

  return (
    <div className="risk-indicator" aria-label={`현재 위험도 ${getRiskLabel(level, score)}${hasScore ? `, 위험 점수 ${score}점` : ''}`}>
      <div className="risk-indicator__score">
        <span>현재 위험 점수</span>
        <strong>{hasScore ? `${score}점` : '-'}</strong>
      </div>

      <ol className="risk-indicator__levels">
        {ascendingOrder.map((riskLevel, index) => {
          const isCurrent = index === currentIndex
          const isFilled = currentIndex >= 0 && index <= currentIndex
          return (
            <li
              key={riskLevel}
              className={`risk-indicator__level${isCurrent ? ' is-current' : ''}`}
              aria-current={isCurrent ? 'step' : undefined}
            >
              {/* 채워진 구간은 모두 '현재 단계'의 색을 쓴다. 구간마다 다른 색을 칠하면
                  실제 단계가 어디인지 색만 보고는 알 수 없다. */}
              <span
                className="risk-indicator__bar"
                style={isFilled && currentConfig ? { backgroundColor: currentConfig.markerColor } : undefined}
              />
              <span className="risk-indicator__step">{index + 1}</span>
              <span className="risk-indicator__label">{RISK_LEVEL_CONFIG[riskLevel].label}</span>
            </li>
          )
        })}
      </ol>

      <dl className="risk-indicator__summary">
        <div>
          <dt>현재 단계</dt>
          <dd>
            <span
              className="risk-indicator__current"
              style={currentConfig ? {
                backgroundColor: currentConfig.backgroundColor,
                color: currentConfig.textColor,
                borderColor: currentConfig.borderColor,
              } : undefined}
            >
              {getRiskLabel(level, score)}
            </span>
          </dd>
        </div>
      </dl>
    </div>
  )
}
