import { useParams } from 'react-router'

export function MyReportDetailPage() {
  const { reportId } = useParams()

  return (
    <section className="page-section">
      <p className="eyebrow">마이페이지</p>
      <h1>내 제보 상세</h1>
      <p>제보 ID {reportId}의 상세 내용, 처리 이력, 수정·삭제 기능이 들어갈 화면입니다.</p>
    </section>
  )
}
