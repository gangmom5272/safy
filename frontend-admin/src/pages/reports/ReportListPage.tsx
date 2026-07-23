import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import {
  adminReports,
  aiAnalysisStatusLabels,
  reportStatusLabels,
  reportTypeLabels,
  reviewStatusLabels,
  riskLevelLabels,
} from '../../mocks/adminMockData'
import { createReportDetailPath } from '../../router/paths'
import type { ReportStatus, ReportType, ReviewStatus, RiskLevel } from '../../types/admin'

const allValue = 'all'

interface ReportFilters {
  keyword: string
  type: ReportType | typeof allValue
  riskLevel: RiskLevel | typeof allValue
  status: ReportStatus | typeof allValue
  reviewStatus: ReviewStatus | typeof allValue
}

const initialFilters: ReportFilters = {
  keyword: '',
  type: allValue,
  riskLevel: allValue,
  status: allValue,
  reviewStatus: allValue,
}

export function ReportListPage() {
  const navigate = useNavigate()
  const [filters, setFilters] = useState<ReportFilters>(initialFilters)
  const [exportMessage, setExportMessage] = useState('')

  const filteredReports = useMemo(
    () =>
      adminReports.filter((report) => {
        const keyword = filters.keyword.trim().toLowerCase()
        const matchesKeyword =
          keyword.length === 0 ||
          String(report.id).includes(keyword) ||
          report.location.toLowerCase().includes(keyword)
        const matchesType = filters.type === allValue || report.type === filters.type
        const matchesRisk =
          filters.riskLevel === allValue || report.riskLevel === filters.riskLevel
        const matchesStatus = filters.status === allValue || report.status === filters.status
        const matchesReview =
          filters.reviewStatus === allValue || report.reviewStatus === filters.reviewStatus

        return matchesKeyword && matchesType && matchesRisk && matchesStatus && matchesReview
      }),
    [filters],
  )

  const resetFilters = () => setFilters(initialFilters)

  return (
    <section className="page-stack">
      <article className="admin-card">
        <div className="card-heading">
          <div>
            <h2>제보 관리</h2>
            <p>Mock 제보 데이터를 검색하고 필터링합니다.</p>
          </div>
          <button
            type="button"
            className="secondary-button"
            onClick={() => setExportMessage('내보내기 기능은 API 연동 후 제공됩니다')}
          >
            목록 내보내기
          </button>
        </div>
        {exportMessage && <p className="notice-text">{exportMessage}</p>}
        <div className="filter-grid">
          <label>
            제보 번호 또는 위치
            <input
              type="search"
              value={filters.keyword}
              onChange={(event) =>
                setFilters((current) => ({ ...current, keyword: event.target.value }))
              }
              placeholder="예: 1 또는 홍대"
            />
          </label>
          <label>
            제보 유형
            <select
              value={filters.type}
              onChange={(event) =>
                setFilters((current) => ({
                  ...current,
                  type: event.target.value as ReportFilters['type'],
                }))
              }
            >
              <option value={allValue}>전체</option>
              {Object.entries(reportTypeLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label>
            위험도
            <select
              value={filters.riskLevel}
              onChange={(event) =>
                setFilters((current) => ({
                  ...current,
                  riskLevel: event.target.value as ReportFilters['riskLevel'],
                }))
              }
            >
              <option value={allValue}>전체</option>
              {Object.entries(riskLevelLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label>
            처리 상태
            <select
              value={filters.status}
              onChange={(event) =>
                setFilters((current) => ({
                  ...current,
                  status: event.target.value as ReportFilters['status'],
                }))
              }
            >
              <option value={allValue}>전체</option>
              {Object.entries(reportStatusLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label>
            검토 상태
            <select
              value={filters.reviewStatus}
              onChange={(event) =>
                setFilters((current) => ({
                  ...current,
                  reviewStatus: event.target.value as ReportFilters['reviewStatus'],
                }))
              }
            >
              <option value={allValue}>전체</option>
              {Object.entries(reviewStatusLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label>
            날짜
            <input type="date" />
          </label>
          <button type="button" className="secondary-button" onClick={resetFilters}>
            필터 초기화
          </button>
        </div>
      </article>

      <article className="admin-card">
        {filteredReports.length === 0 ? (
          <div className="empty-state">
            <strong>조건에 맞는 제보가 없습니다.</strong>
            <button type="button" className="primary-button" onClick={resetFilters}>
              필터 초기화
            </button>
          </div>
        ) : (
          <div className="table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>제보 번호</th>
                  <th>위치</th>
                  <th>유형</th>
                  <th>위험도</th>
                  <th>제보 시간</th>
                  <th>AI 분석 상태</th>
                  <th>담당자</th>
                  <th>처리 상태</th>
                  <th>검토 상태</th>
                </tr>
              </thead>
              <tbody>
                {filteredReports.map((report) => (
                  <tr
                    key={report.id}
                    className="clickable-row"
                    onClick={() => navigate(createReportDetailPath(report.id))}
                  >
                    <td>#{report.id}</td>
                    <td>{report.location}</td>
                    <td>{reportTypeLabels[report.type]}</td>
                    <td>
                      <span className={`risk-badge risk-${report.riskLevel}`}>
                        {riskLevelLabels[report.riskLevel]}
                      </span>
                    </td>
                    <td>{report.reportedAt}</td>
                    <td>{aiAnalysisStatusLabels[report.aiAnalysisStatus]}</td>
                    <td>{report.managerName}</td>
                    <td>{reportStatusLabels[report.status]}</td>
                    <td>{reviewStatusLabels[report.reviewStatus]}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </article>
    </section>
  )
}
