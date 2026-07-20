import { Link } from 'react-router'

export function ReportCreatePage() {
  return (
    <section className="page-section">
      <p className="eyebrow">제보하기</p>
      <h1>제보 시작</h1>
      <p>제보 유형을 선택하고 실시간 영상 또는 사진·영상 제보 흐름으로 이동합니다.</p>
      <Link className="text-link" to="/app/report/type">
        제보 유형 선택
      </Link>
    </section>
  )
}
