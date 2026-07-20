import { useParams } from 'react-router'

export function ReportDetailPage() {
  const { reportId } = useParams()

  return (
    <section className="page-section">
      <p className="eyebrow">실시간 제보 상세</p>
      <h1>제보 상세</h1>
      <p>제보 ID {reportId}의 실시간 영상, 제보 정보, 현장 분석, 공유 기능이 들어갈 화면입니다.</p>
    </section>
  )
}
