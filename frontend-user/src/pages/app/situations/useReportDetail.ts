import { useQuery } from '@tanstack/react-query'
import { queryKeys } from '../../../api/queryKeys'
import {
  getReportAnalysis,
  getReportDetail,
} from './situationApi'

export function useReportDetail(reportId?: string) {
  const enabled = Boolean(reportId)

  const detailQuery = useQuery({
    queryKey: queryKeys.reports.detail(reportId ?? ''),
    queryFn: () => getReportDetail(reportId!),
    enabled,
  })

  const analysisQuery = useQuery({
    queryKey: queryKeys.reports.analysis(reportId ?? ''),
    queryFn: () => getReportAnalysis(reportId!),
    enabled,
  })

  return {
    report: detailQuery.data,
    isReportLoading: detailQuery.isFetching,
    reportError: detailQuery.error,
    retryReport: detailQuery.refetch,
    analysis: analysisQuery.data,
    isAnalysisLoading: analysisQuery.isFetching,
    analysisError: analysisQuery.error,
    retryAnalysis: analysisQuery.refetch,
  }
}
