import { useParams } from 'react-router'
import { MyPageApp } from './MyPage'
export function MyReportDetailPage() { const { reportId } = useParams(); return <MyPageApp view="REPORT_DETAIL" reportId={Number(reportId)} /> }
