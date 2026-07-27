import { getRiskConfig, getRiskLabel } from '../../utils/adminRisk'

interface RiskBadgeProps {
  value: unknown
  className?: string
}

export function RiskBadge({ value, className = '' }: RiskBadgeProps) {
  const config = getRiskConfig(value)

  return (
    <span
      className={`risk-badge${config ? '' : ' risk-badge--unclassified'} ${className}`.trim()}
      style={config ? {
        backgroundColor: config.backgroundColor,
        color: config.textColor,
        borderColor: config.borderColor,
      } : undefined}
    >
      {getRiskLabel(value)}
    </span>
  )
}
