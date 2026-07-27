import { useParams } from 'react-router'
import { MyPageApp } from './MyPage'
import './myReportDetail.css'
export function MyReportDetailPage() { const { reportId } = useParams(); return <MyPageApp view="REPORT_DETAIL" reportId={Number(reportId)} /> }
