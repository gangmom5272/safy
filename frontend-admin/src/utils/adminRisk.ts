import type { LegacyRiskLevel, RiskLevel, RiskLevelValue } from '../types/admin'

export interface RiskLevelConfig {
  label: string
  order: number
  backgroundColor: string
  textColor: string
  borderColor: string
  markerColor: string
  markerTextColor: string
}

export const RISK_LEVEL_ORDER = [
  'very_high',
  'high',
  'medium',
  'low',
  'very_low',
] as const satisfies readonly RiskLevel[]

export const RISK_LEVEL_CONFIG: Record<RiskLevel, RiskLevelConfig> = {
  very_high: { label: '매우 높음', order: 5, backgroundColor: '#FEE2E2', textColor: '#991B1B', borderColor: '#FCA5A5', markerColor: '#DC2626', markerTextColor: '#FFFFFF' },
  high: { label: '높음', order: 4, backgroundColor: '#FFEDD5', textColor: '#9A3412', borderColor: '#FDBA74', markerColor: '#F97316', markerTextColor: '#FFFFFF' },
  medium: { label: '보통', order: 3, backgroundColor: '#FEF9C3', textColor: '#713F12', borderColor: '#FDE047', markerColor: '#EAB308', markerTextColor: '#422006' },
  low: { label: '낮음', order: 2, backgroundColor: '#DCFCE7', textColor: '#166534', borderColor: '#86EFAC', markerColor: '#16A34A', markerTextColor: '#FFFFFF' },
  very_low: { label: '매우 낮음', order: 1, backgroundColor: '#DBEAFE', textColor: '#1E40AF', borderColor: '#93C5FD', markerColor: '#2563EB', markerTextColor: '#FFFFFF' },
}

export const RISK_LEVEL_OPTIONS = RISK_LEVEL_ORDER.map((value) => ({
  value,
  label: RISK_LEVEL_CONFIG[value].label,
}))

const normalizedRiskAliases: Record<string, RiskLevel> = {
  very_high: 'very_high',
  veryhigh: 'very_high',
  '매우 높음': 'very_high',
  high: 'high',
  높음: 'high',
  medium: 'medium',
  보통: 'medium',
  low: 'low',
  낮음: 'low',
  very_low: 'very_low',
  verylow: 'very_low',
  '매우 낮음': 'very_low',
  // LegacyRiskLevel — 목 데이터와 구 API 응답이 쓰는 4단계 표기
  danger: 'very_high',
  warning: 'high',
  caution: 'medium',
  safe: 'low',
}

export function normalizeRiskLevel(value: unknown): RiskLevel | null {
  if (typeof value !== 'string') return null
  return normalizedRiskAliases[value.trim().toLowerCase()] ?? null
}

export function getRiskLabel(value: unknown): string {
  const level = normalizeRiskLevel(value)
  return level ? RISK_LEVEL_CONFIG[level].label : '미분류'
}

export function getRiskConfig(value: unknown): RiskLevelConfig | null {
  const level = normalizeRiskLevel(value)
  return level ? RISK_LEVEL_CONFIG[level] : null
}

export function getRiskOrder(value: unknown): number | null {
  return getRiskConfig(value)?.order ?? null
}

export function compareRiskLevels(left: unknown, right: unknown, direction: 'high' | 'low'): number {
  const leftOrder = getRiskOrder(left)
  const rightOrder = getRiskOrder(right)
  if (leftOrder === null && rightOrder === null) return 0
  if (leftOrder === null) return 1
  if (rightOrder === null) return -1
  return direction === 'high' ? rightOrder - leftOrder : leftOrder - rightOrder
}

export function matchesRiskLevel(value: unknown, selected: RiskLevel): boolean {
  return normalizeRiskLevel(value) === selected
}

export function isEmergencyRisk(value: RiskLevelValue | null): boolean {
  const normalized = normalizeRiskLevel(value)
  return normalized === 'very_high' || value === 'danger'
}

export function isHighRisk(value: RiskLevelValue | null): boolean {
  const normalized = normalizeRiskLevel(value)
  const legacyValue = value as LegacyRiskLevel
  return normalized === 'very_high' || normalized === 'high' || legacyValue === 'danger' || legacyValue === 'warning'
}
