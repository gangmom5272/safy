import { getRiskConfig, getRiskLabel } from '../../utils/adminRisk'

interface RiskBadgeProps {
  value: unknown
  score?: unknown
  className?: string
}

export function RiskBadge({ value, score, className = '' }: RiskBadgeProps) {
  const config = getRiskConfig(value, score)

  return (
    <span
      className={`risk-badge${config ? '' : ' risk-badge--unclassified'} ${className}`.trim()}
      style={config ? {
        backgroundColor: config.backgroundColor,
        color: config.textColor,
        borderColor: config.borderColor,
      } : undefined}
    >
      {getRiskLabel(value, score)}
    </span>
  )
}
