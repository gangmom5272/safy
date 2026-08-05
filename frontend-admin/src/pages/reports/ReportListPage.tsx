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
import { getMyAssignedReports } from './reportApi'
import { createReportDetailPath } from '../../router/paths'
import type { AiAnalysisStatus, ReportStatus, ReportType, RiskLevel } from '../../types/admin'
import { formatAdminTimestamp } from '../../utils/adminDate'
import { getReportStatusTone } from '../../utils/adminStatus'
import { compareRiskLevels, matchesRiskLevel, RISK_LEVEL_OPTIONS } from '../../utils/adminRisk'
import { subscribeToRealtimeSignals } from '../../api/realtime'
import { deduplicateReportsById } from './reportRowKey'

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
  media: {
    live: boolean
    image: boolean
  }
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
  media: { live: true, image: true },
}

const sortOrderLabels: Record<SortOrder, string> = {
  newest: '최신순',
  oldest: '오래된 순',
  risk_high: '위험도 높은 순',
  risk_low: '위험도 낮은 순',
}

function FilterSettingsIcon() {
  return (
    <svg className="filter-settings-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <path d="M4 7h10M18 7h2M4 17h2M10 17h10" />
      <circle cx="16" cy="7" r="2" />
      <circle cx="8" cy="17" r="2" />
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

function FilterToggleChevron({ open }: { open: boolean }) {
  return (
    <svg className={`filter-toggle-chevron${open ? ' is-open' : ''}`} viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <path d="m7 9 5 5 5-5" />
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
      const loaded = await getMyAssignedReports()
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

    return deduplicateReportsById(reports)
      .filter((report) => {
        const searchableText = [
          String(report.id),
          report.location,
          report.aiSummary,
          report.managerName,
          report.reporterName ?? '',
        ].join(' ').toLowerCase()
        // 화면에 보이는 날짜와 같은 기준으로 걸러야 한다. 원본은 UTC ISO 문자열이라
        // 앞 10자를 그대로 쓰면 한국 시간으로 넘어간 밤 시간대 제보가 하루 어긋난다.
        const reportDate = formatAdminTimestamp(report.reportedAt)?.slice(0, 10) ?? ''
        const matchesMedia = report.isLive
          ? appliedFilters.media.live
          : appliedFilters.media.image

        return (
          (!keyword || searchableText.includes(keyword)) &&
          matchesMedia &&
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

  const toggleMediaFilter = (media: keyof ReportFilters['media']) => {
    setDraftFilters((current) => ({
      ...current,
      media: { ...current.media, [media]: !current.media[media] },
    }))
  }

  const selectSortOrder = (sortOrder: SortOrder) => {
    setDraftFilters((current) => ({ ...current, sortOrder }))
    setAppliedFilters((current) => ({ ...current, sortOrder }))
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
            <input
              aria-label="통합 검색어"
              type="search"
              value={draftFilters.keyword}
              onChange={(event) => setDraftFilters((current) => ({ ...current, keyword: event.target.value }))}
              placeholder="검색어를 입력해주세요."
            />
            <span className="report-filter__search-hint">제보 ID · 지역 · 담당자</span>
          </div>
          <button
            type="button"
            className="secondary-button filter-detail-toggle"
            aria-expanded={isDetailOpen}
            aria-controls="report-detail-filters"
            onClick={() => setIsDetailOpen((current) => !current)}
          >
            <FilterSettingsIcon />
            <span>{isDetailOpen ? '상세조건 닫기' : '상세조건'}</span>
            <FilterToggleChevron open={isDetailOpen} />
          </button>
          <button type="submit" className="primary-button report-filter__search-button">검색</button>
        </div>

        {isDetailOpen && (
          <div id="report-detail-filters" className="report-filter__detail">
            <fieldset className="report-filter__group report-filter__category">
              <legend>카테고리</legend>
              <div className="report-filter__chips">
                {[[allValue, '전체'], ...Object.entries(reportTypeLabels)].map(([value, label]) => (
                  <button
                    key={value}
                    type="button"
                    className="filter-chip"
                    aria-pressed={draftFilters.type === value}
                    onClick={() => setDraftFilters((current) => ({ ...current, type: value as ReportFilters['type'] }))}
                  >
                    {label}
                  </button>
                ))}
              </div>
            </fieldset>

            <fieldset className="report-filter__group report-filter__status">
              <legend>처리 상태</legend>
              <div className="report-filter__chips">
                {[[allValue, '전체'], ...Object.entries(reportStatusLabels).filter(([value]) => value !== 'received')].map(([value, label]) => (
                  <button
                    key={value}
                    type="button"
                    className="filter-chip"
                    aria-pressed={draftFilters.status === value}
                    onClick={() => setDraftFilters((current) => ({ ...current, status: value as ReportFilters['status'] }))}
                  >
                    {label}
                  </button>
                ))}
              </div>
            </fieldset>

            <fieldset className="report-filter__group report-filter__risk">
              <legend>위험 단계</legend>
              <div className="report-filter__chips">
                {[[allValue, '전체'], ...RISK_LEVEL_OPTIONS.map(({ value, label }) => [value, label])].map(([value, label]) => (
                  <button
                    key={value}
                    type="button"
                    className="filter-chip"
                    aria-pressed={draftFilters.riskLevel === value}
                    onClick={() => setDraftFilters((current) => ({ ...current, riskLevel: value as ReportFilters['riskLevel'] }))}
                  >
                    {label}
                  </button>
                ))}
              </div>
            </fieldset>

            <div className="report-filter__group report-filter__date">
              <span className="report-filter__group-title">제보 날짜</span>
              <div className="report-filter__date-range">
                <input aria-label="제보 시작 날짜" type="date" value={draftFilters.startDate} onChange={(event) => setDraftFilters((current) => ({ ...current, startDate: event.target.value }))} />
                <span aria-hidden="true">~</span>
                <input aria-label="제보 종료 날짜" type="date" value={draftFilters.endDate} onChange={(event) => setDraftFilters((current) => ({ ...current, endDate: event.target.value }))} />
              </div>
            </div>

            <fieldset className="report-filter__group report-filter__media">
              <legend>미디어</legend>
              <div className="report-filter__media-options">
                <label className="media-filter-option">
                  <input name="media-filter" value="live" type="checkbox" checked={draftFilters.media.live} onChange={() => toggleMediaFilter('live')} />
                  <span>영상</span>
                </label>
                <label className="media-filter-option">
                  <input name="media-filter" value="image" type="checkbox" checked={draftFilters.media.image} onChange={() => toggleMediaFilter('image')} />
                  <span>사진</span>
                </label>
              </div>
            </fieldset>

            <label className="report-filter__group report-filter__analysis">
              <span className="report-filter__group-title">AI 분석 상태</span>
              <select aria-label="AI 분석 상태" value={draftFilters.aiAnalysisStatus} onChange={(event) => setDraftFilters((current) => ({ ...current, aiAnalysisStatus: event.target.value as ReportFilters['aiAnalysisStatus'] }))}>
                <option value={allValue}>전체</option>
                {Object.entries(aiAnalysisStatusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            </label>

            <div className="report-filter__actions">
              <button type="button" className="secondary-button" onClick={() => { resetFilters(); void loadReports() }}>초기화</button>
              <button type="submit" className="primary-button">조건 적용</button>
            </div>
          </div>
        )}

      </form>

      {/* 건수와 정렬은 검색 카드가 아니라 목록 헤더에 둔다. 검색 조건이 아니라
          지금 보고 있는 목록을 설명하는 정보이기 때문이다. 정렬 버튼은 위치만
          옮기고 기존 클래스를 그대로 써서 디자인과 동작을 유지한다. */}
      <AdminSection
        className="report-list-section"
        title={`총 ${filteredReports.length}건의 제보 목록`}
        action={(
          <div className="report-filter__sort" role="group" aria-label="제보 정렬">
            {Object.entries(sortOrderLabels).map(([value, label]) => (
              <button
                key={value}
                type="button"
                aria-pressed={appliedFilters.sortOrder === value}
                onClick={() => selectSortOrder(value as SortOrder)}
              >
                {label}
              </button>
            ))}
          </div>
        )}
      >
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
              <thead><tr><th>제보 ID</th><th>위치</th><th>카테고리</th><th>위험도</th><th>제보 시간</th><th>담당자</th><th>처리 상태</th></tr></thead>
              <tbody>
                {filteredReports.map((report) => (
                  <tr key={report.id} className="clickable-row" onClick={() => navigate(createReportDetailPath(report.id))}>
                    <td>#{report.id}</td><td>{report.location}</td><td>{reportTypeLabels[report.type]}</td>
                    <td><RiskBadge value={report.riskLevel} score={report.riskScore} /></td>
                    <td>{formatAdminTimestamp(report.reportedAt) ?? '정보 없음'}</td><td>{report.managerName}</td>
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
