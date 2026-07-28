import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import { AdminSection } from '../../components/admin/AdminSection'
import { StatusBadge } from '../../components/admin/StatusBadge'
import { RiskBadge } from '../../components/admin/RiskBadge'
import {
  aiAnalysisStatusLabels,
  reportStatusLabels,
  reportTypeLabels,
} from '../../mocks/adminMockData'
import type { AdminReport } from '../../types/admin'
import { getDashboardReports } from './reportApi'
import { createReportDetailPath } from '../../router/paths'
import type { AiAnalysisStatus, ReportStatus, ReportType, RiskLevel } from '../../types/admin'
import { getReportStatusTone } from '../../utils/adminStatus'
import { compareRiskLevels, matchesRiskLevel, RISK_LEVEL_OPTIONS } from '../../utils/adminRisk'
import { subscribeToRealtimeSignals } from '../../api/realtime'

const allValue = 'all'

type SortOrder = 'newest' | 'oldest' | 'risk_high' | 'risk_low'

interface ReportFilters {
  keyword: string
  region: string
  type: ReportType | typeof allValue
  status: ReportStatus | typeof allValue
  riskLevel: RiskLevel | typeof allValue
  startDate: string
  endDate: string
  aiAnalysisStatus: AiAnalysisStatus | typeof allValue
  sortOrder: SortOrder
}

const initialFilters: ReportFilters = {
  keyword: '',
  region: '',
  type: allValue,
  status: allValue,
  riskLevel: allValue,
  startDate: '',
  endDate: '',
  aiAnalysisStatus: allValue,
  sortOrder: 'newest',
}

const sortOrderLabels: Record<SortOrder, string> = {
  newest: '최신순',
  oldest: '오래된 순',
  risk_high: '위험도 높은 순',
  risk_low: '위험도 낮은 순',
}

function RefreshIcon() {
  return (
    <svg className="filter-reset-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <path d="M3 12a9 9 0 0 1 15.3-6.4L21 8M21 3v5h-5" />
      <path d="M21 12a9 9 0 0 1-15.3 6.4L3 16M3 21v-5h5" />
    </svg>
  )
}

function SearchIcon() {
  return (
    <svg className="filter-field-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-4-4" />
    </svg>
  )
}

function CloseIcon() {
  return (
    <svg className="filter-toggle-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <path d="m6 6 12 12M18 6 6 18" />
    </svg>
  )
}

function normalizeReportKeyword(keyword: string) {
  const trimmedKeyword = keyword.trim()
  return /^#\d+$/.test(trimmedKeyword) ? trimmedKeyword.slice(1) : trimmedKeyword
}

export function ReportListPage() {
  const navigate = useNavigate()
  const [draftFilters, setDraftFilters] = useState<ReportFilters>(initialFilters)
  const [appliedFilters, setAppliedFilters] = useState<ReportFilters>(initialFilters)
  const [isDetailOpen, setIsDetailOpen] = useState(false)
  const [reports, setReports] = useState<AdminReport[]>([])
  const [loadError, setLoadError] = useState('')
  const [isLoading, setIsLoading] = useState(true)

  const loadReports = useCallback(async () => {
    try {
      const loaded = await getDashboardReports()
      setReports(loaded)
      setLoadError('')
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : '제보 목록을 불러오지 못했습니다.')
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    const initialLoad = window.setTimeout(() => void loadReports(), 0)
    const unsubscribe = subscribeToRealtimeSignals((signal) => {
      if (signal.kind === 'connected' || signal.event.eventType !== 'REPORT_MESSAGE') {
        void loadReports()
      }
    })
    return () => {
      window.clearTimeout(initialLoad)
      unsubscribe()
    }
  }, [loadReports])

  const filteredReports = useMemo(() => {
    const keyword = appliedFilters.keyword.trim().toLowerCase()

    return reports
      .filter((report) => {
        const searchableText = [
          String(report.id),
          report.location,
          report.aiSummary,
          report.reporterName ?? '',
        ].join(' ').toLowerCase()
        const reportDate = report.reportedAt.slice(0, 10)

        return (
          (!keyword || searchableText.includes(keyword)) &&
          (appliedFilters.type === allValue || report.type === appliedFilters.type) &&
          (appliedFilters.status === allValue || report.status === appliedFilters.status) &&
          (appliedFilters.riskLevel === allValue || matchesRiskLevel(report.riskLevel, appliedFilters.riskLevel, report.riskScore)) &&
          (appliedFilters.aiAnalysisStatus === allValue || report.aiAnalysisStatus === appliedFilters.aiAnalysisStatus) &&
          (!appliedFilters.startDate || reportDate >= appliedFilters.startDate) &&
          (!appliedFilters.endDate || reportDate <= appliedFilters.endDate)
        )
      })
      .sort((first, second) => {
        if (appliedFilters.sortOrder === 'risk_high') return compareRiskLevels(first.riskLevel, second.riskLevel, 'high')
        if (appliedFilters.sortOrder === 'risk_low') return compareRiskLevels(first.riskLevel, second.riskLevel, 'low')
        const difference = new Date(second.reportedAt.replace(' ', 'T')).getTime() - new Date(first.reportedAt.replace(' ', 'T')).getTime()
        return appliedFilters.sortOrder === 'newest' ? difference : -difference
      })
  }, [appliedFilters, reports])

  const resetFilters = () => {
    setDraftFilters(initialFilters)
    setAppliedFilters(initialFilters)
  }

  return (
    <section className="page-stack">
      <form className="report-filter" onSubmit={(event) => {
        event.preventDefault()
        setAppliedFilters({ ...draftFilters, keyword: normalizeReportKeyword(draftFilters.keyword) })
      }}>
          <div className="report-filter__basic">
            <div className="report-filter__search-field">
              <SearchIcon />
              <input aria-label="통합 검색어" type="search" value={draftFilters.keyword} onChange={(event) => setDraftFilters((current) => ({ ...current, keyword: event.target.value }))} placeholder="검색어를 입력해 주세요. (예: 제보 ID, 주소, 지역 등)" />
            </div>
            <button type="button" className="secondary-button filter-detail-toggle" aria-expanded={isDetailOpen} aria-controls="report-detail-filters" aria-label={`상세조건 ${isDetailOpen ? '닫기' : '열기'}`} onClick={() => setIsDetailOpen((current) => !current)}>
              {isDetailOpen && <CloseIcon />}상세조건
            </button>
            <button type="button" className="secondary-button filter-reset-button" onClick={() => { resetFilters(); void loadReports() }} aria-label="필터 초기화 및 목록 새로고침" title="새로고침">
              <RefreshIcon />
            </button>
            <button type="submit" className="primary-button">필터 적용</button>
          </div>

          {isDetailOpen && (
            <div id="report-detail-filters" className="report-filter__detail">
              <label>
                제보일
                <span className="report-filter__date-range">
                  <input aria-label="제보 시작 날짜" type="date" value={draftFilters.startDate} onChange={(event) => setDraftFilters((current) => ({ ...current, startDate: event.target.value }))} />
                  <span aria-hidden="true">~</span>
                  <input aria-label="제보 종료 날짜" type="date" value={draftFilters.endDate} onChange={(event) => setDraftFilters((current) => ({ ...current, endDate: event.target.value }))} />
                </span>
              </label>
              <label>
                카테고리
                <select value={draftFilters.type} onChange={(event) => setDraftFilters((current) => ({ ...current, type: event.target.value as ReportFilters['type'] }))}>
                  <option value={allValue}>전체</option>
                  {Object.entries(reportTypeLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </select>
              </label>
              <label>
                위험도
                <select value={draftFilters.riskLevel} onChange={(event) => setDraftFilters((current) => ({ ...current, riskLevel: event.target.value as ReportFilters['riskLevel'] }))}>
                  <option value={allValue}>전체</option>
                  {RISK_LEVEL_OPTIONS.map(({ value, label }) => <option key={value} value={value}>{label}</option>)}
                </select>
              </label>
              <label>
                AI 분석 상태
                <select value={draftFilters.aiAnalysisStatus} onChange={(event) => setDraftFilters((current) => ({ ...current, aiAnalysisStatus: event.target.value as ReportFilters['aiAnalysisStatus'] }))}>
                  <option value={allValue}>전체</option>
                  {Object.entries(aiAnalysisStatusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </select>
              </label>
              <label>
                정렬
                <select value={draftFilters.sortOrder} onChange={(event) => setDraftFilters((current) => ({ ...current, sortOrder: event.target.value as SortOrder }))}>
                  {Object.entries(sortOrderLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </select>
              </label>
            </div>
          )}

          <div className="report-filter__divider" />

      </form>

      <AdminSection title={`검색 결과 ${filteredReports.length}건`}>
        {isLoading ? (
          <p role="status">제보 목록을 불러오는 중입니다...</p>
        ) : loadError ? (
          <div className="empty-state">
            <strong role="alert">{loadError}</strong>
          </div>
        ) : filteredReports.length === 0 ? (
          <div className="empty-state">
            <strong>조건에 맞는 제보가 없습니다.</strong>
            <button type="button" className="primary-button" onClick={resetFilters}>전체 목록 보기</button>
          </div>
        ) : (
          <div className="table-wrap">
            <table className="admin-table">
              <thead><tr><th>제보 ID</th><th>위치</th><th>카테고리</th><th>위험도</th><th>제보 시간</th><th>AI 분석 상태</th><th>담당자</th><th>처리 상태</th></tr></thead>
              <tbody>
                {filteredReports.map((report) => (
                  <tr key={report.id} className="clickable-row" onClick={() => navigate(createReportDetailPath(report.id))}>
                    <td>#{report.id}</td><td>{report.location}</td><td>{reportTypeLabels[report.type]}</td>
                    <td><RiskBadge value={report.riskLevel} score={report.riskScore} /></td>
                    <td>{report.reportedAt}</td><td>{report.aiAnalysisStatus ? aiAnalysisStatusLabels[report.aiAnalysisStatus] : '-'}</td><td>{report.managerName}</td>
                    <td><StatusBadge tone={getReportStatusTone(report.status)}>{reportStatusLabels[report.status]}</StatusBadge></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </AdminSection>
    </section>
  )
}
