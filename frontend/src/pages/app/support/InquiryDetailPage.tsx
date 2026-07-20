import { useParams } from 'react-router'

export function InquiryDetailPage() {
  const { inquiryId } = useParams()

  return (
    <section className="page-section">
      <p className="eyebrow">고객센터</p>
      <h1>문의 상세</h1>
      <p>문의 ID {inquiryId}의 문의 내용, 첨부파일, 답변 내용이 들어갈 화면입니다.</p>
    </section>
  )
}
