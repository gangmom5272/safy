import { Link } from 'react-router'
import { ROUTE_PATHS } from '../../router/paths'

export function NotFoundPage() {
  return (
    <section className="admin-card empty-state">
      <strong>페이지를 찾을 수 없습니다</strong>
      <p>요청한 관리자 페이지 주소가 올바르지 않습니다.</p>
      <Link className="primary-button" to={ROUTE_PATHS.DASHBOARD}>
        홈으로 이동
      </Link>
    </section>
  )
}
