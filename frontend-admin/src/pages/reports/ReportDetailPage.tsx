import { Link, useParams } from 'react-router'
import { useState } from 'react'
import {
  adminReports,
  aiAnalysisStatusLabels,
  reportStatusLabels,
  reportTypeLabels,
  reviewStatusLabels,
  riskLevelLabels,
} from '../../mocks/adminMockData'
import { ROUTE_PATHS } from '../../router/paths'
import type { ReportStatus, ReviewStatus } from '../../types/admin'

export function ReportDetailPage() {
  const { reportId } = useParams()
  const report = adminReports.find((item) => String(item.id) === reportId)
  const [status, setStatus] = useState<ReportStatus>(report?.status ?? 'received')
  const [reviewStatus, setReviewStatus] = useState<ReviewStatus>(
    report?.reviewStatus ?? 'pending',
  )

  if (!report) {
    return (
      <section className="admin-card empty-state">
        <strong>제보를 찾을 수 없습니다</strong>
        <Link className="primary-button" to={ROUTE_PATHS.REPORTS}>
          제보 목록으로 이동
        </Link>
      </section>
    )
  }

  return (
    <section className="page-stack">
      <article className="admin-card">
        <div className="card-heading">
          <div>
            <h2>제보 ID: {report.id}</h2>
            <p>{report.location}</p>
          </div>
          <Link className="secondary-button" to={ROUTE_PATHS.REPORTS}>
            제보 목록으로 돌아가기
          </Link>
        </div>

        <div className="detail-grid">
          <div>
            <span>제보 유형</span>
            <strong>{reportTypeLabels[report.type]}</strong>
          </div>
          <div>
            <span>위험도</span>
            <strong className={`risk-text risk-${report.riskLevel}`}>
              {riskLevelLabels[report.riskLevel]}
            </strong>
          </div>
          <div>
            <span>위험 점수</span>
            <strong>{report.riskScore}</strong>
          </div>
          <div>
            <span>제보 시간</span>
            <strong>{report.reportedAt}</strong>
          </div>
          <div>
            <span>처리 상태</span>
            <strong>{reportStatusLabels[status]}</strong>
          </div>
          <div>
            <span>검토 상태</span>
            <strong>{reviewStatusLabels[reviewStatus]}</strong>
          </div>
          <div>
            <span>AI 분석 상태</span>
            <strong>{aiAnalysisStatusLabels[report.aiAnalysisStatus]}</strong>
          </div>
          <div>
            <span>담당 관리자</span>
            <strong>{report.managerName}</strong>
          </div>
        </div>
      </article>

      <article className="admin-card">
        <h2>AI 상황 요약</h2>
        <p>{report.aiSummary}</p>
        <div className="tag-list">
          {report.riskTags.map((tag) => (
            <span key={tag}>{tag}</span>
          ))}
        </div>
      </article>

      <article className="admin-card">
        <h2>상태 변경</h2>
        <div className="button-row">
          <button type="button" className="primary-button" onClick={() => setReviewStatus('approved')}>
            승인
          </button>
          <button type="button" className="secondary-button" onClick={() => setReviewStatus('rejected')}>
            반려
          </button>
          <button type="button" className="secondary-button" onClick={() => setStatus('received')}>
            접수
          </button>
          <button type="button" className="secondary-button" onClick={() => setStatus('checking')}>
            확인 중
          </button>
          <button type="button" className="secondary-button" onClick={() => setStatus('in_progress')}>
            처리 중
          </button>
          <button type="button" className="secondary-button" onClick={() => setStatus('completed')}>
            처리 완료
          </button>
        </div>
      </article>

      <article className="admin-card">
        <h2>처리 이력</h2>
        <ol className="history-list">
          {report.history.map((history) => (
            <li key={history.id}>
              <strong>{history.action}</strong>
              <span>{history.actor}</span>
              <time>{history.createdAt}</time>
            </li>
          ))}
        </ol>
      </article>
    </section>
  )
}
