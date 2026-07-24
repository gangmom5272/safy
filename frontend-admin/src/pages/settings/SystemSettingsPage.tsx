import { dashboardStats, systemStatus } from '../../mocks/adminMockData'

const statusSections = [
  {
    title: '서버 상태',
    description: '관리자 콘솔과 운영 서버의 현재 동작 상태입니다.',
    items: [{ label: '서버 상태', value: systemStatus.serverStatus }],
  },
  {
    title: 'WebRTC 상태',
    description: '현재 연결된 현장 영상 연결 현황입니다.',
    items: [
      { label: 'WebRTC 연결 수', value: `${systemStatus.webrtcConnections}건` },
      { label: '대시보드 연결 수', value: `${dashboardStats.webrtcConnections}건` },
    ],
  },
  {
    title: '제보 처리 상태',
    description: '관리자 시스템에서 추적 중인 제보 운영 현황입니다.',
    items: [
      { label: '활성 제보 수', value: `${systemStatus.activeReports}건` },
      { label: '위험 제보 수', value: `${dashboardStats.dangerReports}건` },
    ],
  },
  {
    title: 'AI 분석 상태',
    description: 'AI 위험 분석 처리 상태와 최근 갱신 시각입니다.',
    items: [
      { label: 'AI 분석 상태', value: systemStatus.aiAnalysisStatus },
      { label: '최근 업데이트', value: systemStatus.lastUpdatedAt },
    ],
  },
]

export function SystemSettingsPage() {
  return (
    <section className="page-stack">
      <article className="page-section">
        <p className="eyebrow">System</p>
        <h1>시스템 설정</h1>
        <p>관리자 시스템의 현재 운영 상태를 확인합니다.</p>
      </article>

      <article className="admin-card">
        <div className="card-heading">
          <div>
            <h2>시스템 상태 요약</h2>
            <p>Mock 운영 상태 데이터를 기준으로 표시합니다.</p>
          </div>
          <span className="status-badge">최근 업데이트 {systemStatus.lastUpdatedAt}</span>
        </div>
        <div className="system-summary-grid">
          <div>
            <span>서버 상태</span>
            <strong>{systemStatus.serverStatus}</strong>
          </div>
          <div>
            <span>WebRTC 연결 수</span>
            <strong>{systemStatus.webrtcConnections}건</strong>
          </div>
          <div>
            <span>활성 제보 수</span>
            <strong>{systemStatus.activeReports}건</strong>
          </div>
          <div>
            <span>AI 분석 상태</span>
            <strong>{systemStatus.aiAnalysisStatus}</strong>
          </div>
        </div>
      </article>

      <div className="settings-section-grid">
        {statusSections.map((section) => (
          <SystemStatusSection
            key={section.title}
            title={section.title}
            description={section.description}
            items={section.items}
          />
        ))}
      </div>
    </section>
  )
}

interface SystemStatusSectionProps {
  title: string
  description: string
  items: Array<{
    label: string
    value: string
  }>
}

function SystemStatusSection({ title, description, items }: SystemStatusSectionProps) {
  return (
    <article className="admin-card system-status-section">
      <div>
        <h2>{title}</h2>
        <p>{description}</p>
      </div>
      <dl>
        {items.map((item) => (
          <div key={item.label}>
            <dt>{item.label}</dt>
            <dd>{item.value}</dd>
          </div>
        ))}
      </dl>
    </article>
  )
}
