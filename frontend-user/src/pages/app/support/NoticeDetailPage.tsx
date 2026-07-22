import { useParams } from 'react-router'

export function NoticeDetailPage() {
  const { noticeId } = useParams()

  return (
    <section className="page-section">
      <p className="eyebrow">고객센터</p>
      <h1>공지사항 상세</h1>
      <p>공지 ID {noticeId}의 제목, 내용, 작성일이 들어갈 화면입니다.</p>
    </section>
  )
}
