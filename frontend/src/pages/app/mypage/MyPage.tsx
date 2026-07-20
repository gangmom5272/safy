import { Link } from 'react-router'

export function MyPage() {
  return (
    <section className="page-section">
      <p className="eyebrow">마이페이지</p>
      <h1>마이페이지 메인</h1>
      <p>사용자 정보, 내 제보 요약, 메뉴 목록, 로그아웃 기능이 들어갈 화면입니다.</p>
      <div className="link-list">
        <Link to="/app/mypage/reports">내 제보 관리</Link>
        <Link to="/app/mypage/favorites">즐겨찾기</Link>
        <Link to="/app/mypage/support">고객센터</Link>
        <Link to="/app/mypage/guide">이용안내</Link>
      </div>
    </section>
  )
}
