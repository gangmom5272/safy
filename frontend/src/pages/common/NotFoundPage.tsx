import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <section className="page-section">
      <p className="eyebrow">404</p>
      <h1>페이지를 찾을 수 없습니다</h1>
      <p>요청한 경로가 없거나 아직 연결되지 않았습니다.</p>
      <Link className="text-link" to="/app">
        사용자 앱 홈으로 이동
      </Link>
    </section>
  )
}
