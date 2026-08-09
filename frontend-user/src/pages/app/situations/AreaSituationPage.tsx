import { useParams } from 'react-router'

export function AreaSituationPage() {
  const { areaId } = useParams()

  return (
    <section className="page-section">
      <p className="eyebrow">실시간 지도</p>
      <h1>검색 지역 상황</h1>
      <p>지역 ID {areaId}의 지도, 상황 요약, 지역별 실시간 제보 목록이 들어갈 화면입니다.</p>
    </section>
  )
}
