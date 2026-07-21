import { useNavigate } from 'react-router'

export function SituationHeader() {
  const navigate = useNavigate()

  return (
    <header className="situation-header">
      <div>
        <p>현재 지역의 실시간 제보</p>
        <h1>상황 확인</h1>
      </div>
      <button
        className="situation-icon-button"
        type="button"
        aria-label="알림 페이지로 이동"
        onClick={() => navigate('/app/notifications')}
      >
        <span aria-hidden="true">알림</span>
      </button>
    </header>
  )
}
