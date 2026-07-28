import { dashboardStats, systemStatus } from '../../mocks/adminMockData'
import { AdminSection } from '../../components/admin/AdminSection'

const statusSections = [
  {
    title: '서버 상태',
    items: [{ label: '서버 상태', value: systemStatus.serverStatus }],
  },
  {
    title: 'WebRTC 상태',
    items: [
      { label: 'WebRTC 연결 수', value: `${systemStatus.webrtcConnections}건` },
      { label: '홈 연결 수', value: `${dashboardStats.webrtcConnections}건` },
    ],
  },
  {
    title: '제보 처리 상태',
    items: [
      { label: '활성 제보 수', value: `${systemStatus.activeReports}건` },
      { label: '위험 제보 수', value: `${dashboardStats.dangerReports}건` },
    ],
  },
  {
    title: 'AI 분석 상태',
    items: [
      { label: 'AI 분석 상태', value: systemStatus.aiAnalysisStatus },
      { label: '최근 업데이트', value: systemStatus.lastUpdatedAt },
    ],
  },
]

export function SystemSettingsPage() {
  return (
    <section className="page-stack">
      <AdminSection title="시스템 상태 요약" action={<span className="status-badge">최근 업데이트 {systemStatus.lastUpdatedAt}</span>} cardClassName="system-summary-section">
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
      </AdminSection>

      <div className="settings-section-grid">
        {statusSections.map((section) => (
          <SystemStatusSection
            key={section.title}
            title={section.title}
            items={section.items}
          />
        ))}
      </div>
    </section>
  )
}

interface SystemStatusSectionProps {
  title: string
  items: Array<{
    label: string
    value: string
  }>
}

function SystemStatusSection({ title, items }: SystemStatusSectionProps) {
  return (
    <AdminSection title={title} cardClassName="system-status-section">
      <dl>
        {items.map((item) => (
          <div key={item.label}>
            <dt>{item.label}</dt>
            <dd>{item.value}</dd>
          </div>
        ))}
      </dl>
    </AdminSection>
  )
}
