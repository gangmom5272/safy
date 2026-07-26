import { useEffect, useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useLocation, useNavigate, useSearchParams } from 'react-router'
import { queryKeys } from '../../../api/queryKeys'
import {
  getArea,
  getAreaByQuery,
  getAreaLiveReports,
  getAreaMap,
  getAreaStatus,
  getLiveReports,
  searchLocations,
} from './situationApi'
import { DEFAULT_REPORT_SORT, type LocationSearchResult, type ReportSort, type ReportType } from './types'

const SEARCH_DEBOUNCE_MS = 300
const MIN_SEARCH_LENGTH = 2

export function useSituationCheck() {
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const queryParam = searchParams.get('query') ?? ''
  const [searchKeyword, setSearchKeyword] = useState(() => queryParam.trim())
  const [debouncedKeyword, setDebouncedKeyword] = useState('')
  const [selectedArea, setSelectedArea] = useState<LocationSearchResult | null>(null)
  const [selectedTypes, setSelectedTypes] = useState<ReportType[]>([])
  const [sort, setSort] = useState<ReportSort>(DEFAULT_REPORT_SORT)
  const [isFilterOpen, setIsFilterOpen] = useState(false)
  const reportListRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      setDebouncedKeyword(searchKeyword.trim())
    }, SEARCH_DEBOUNCE_MS)

    return () => window.clearTimeout(timeoutId)
  }, [searchKeyword])

  const typeParam = selectedTypes.join(',')
  const canSearch = debouncedKeyword.length >= MIN_SEARCH_LENGTH
  const submittedQuery = queryParam.trim()
  const hasSubmittedQuery = submittedQuery.length > 0

  const searchQuery = useQuery({
    queryKey: queryKeys.locations.search(debouncedKeyword),
    queryFn: () => searchLocations(debouncedKeyword),
    enabled: canSearch,
  })

  const areaQuery = useQuery({
    queryKey: queryKeys.areas.detail(selectedArea?.id ?? submittedQuery),
    queryFn: () => getArea(selectedArea!.id),
    enabled: Boolean(selectedArea) && !hasSubmittedQuery,
  })

  const submittedAreaQuery = useQuery({
    queryKey: queryKeys.areas.detail(submittedQuery),
    queryFn: () => getAreaByQuery(submittedQuery),
    enabled: hasSubmittedQuery,
  })

  const activeArea = submittedAreaQuery.data ?? areaQuery.data ?? selectedArea
  const areaKey = activeArea?.id ?? submittedQuery
  const displayedSearchKeyword = activeArea && searchKeyword.trim() === submittedQuery
    ? activeArea.name
    : searchKeyword

  const areaMapQuery = useQuery({
    queryKey: queryKeys.areas.map(areaKey),
    queryFn: () => getAreaMap(areaKey),
    enabled: Boolean(activeArea),
  })

  const areaStatusQuery = useQuery({
    queryKey: queryKeys.areas.status(areaKey),
    queryFn: () => getAreaStatus(areaKey),
    enabled: Boolean(activeArea),
  })

  const globalReportsQuery = useQuery({
    queryKey: queryKeys.reports.live({ sort, type: typeParam, limit: 20 }),
    queryFn: () => getLiveReports({ sort, types: selectedTypes, limit: 20 }),
    enabled: !activeArea && !hasSubmittedQuery,
  })

  const areaReportsQuery = useQuery({
    queryKey: queryKeys.areas.liveReports(areaKey, { sort, type: typeParam }),
    queryFn: () => getAreaLiveReports(areaKey, { sort, types: selectedTypes }),
    enabled: Boolean(activeArea),
  })

  const reportsQuery = activeArea ? areaReportsQuery : globalReportsQuery
  const isSubmittedEmpty = hasSubmittedQuery && !activeArea && !submittedAreaQuery.isFetching

  useEffect(() => {
    reportListRef.current?.scrollTo({ top: 0, behavior: 'smooth' })
  }, [sort, typeParam, areaKey])

  const selectArea = (area: LocationSearchResult) => {
    setSelectedArea(area)
    setSearchKeyword(area.name)
    setDebouncedKeyword('')
    navigate(`/app/situations?query=${encodeURIComponent(area.name)}`, { replace: false, state: location.state })
  }

  const submitSearch = () => {
    const trimmed = searchKeyword.trim()

    if (!trimmed) {
      clearSelectedArea()
      return
    }

    setSelectedArea(null)
    setDebouncedKeyword('')
    navigate(`/app/situations?query=${encodeURIComponent(trimmed)}`, { replace: false, state: location.state })
  }

  const clearSelectedArea = () => {
    setSelectedArea(null)
    setSearchKeyword('')
    setDebouncedKeyword('')
    navigate('/app/situations', { replace: false, state: location.state })
  }

  const refreshAreaStatus = async () => {
    await Promise.allSettled([
      areaStatusQuery.refetch(),
      areaMapQuery.refetch(),
    ])
  }

  const searchSuggestions = useMemo(() => {
    if (!canSearch) {
      return []
    }

    return searchQuery.data ?? []
  }, [canSearch, searchQuery.data])

  return {
    searchKeyword: displayedSearchKeyword,
    setSearchKeyword,
    searchSuggestions,
    isSearchLoading: searchQuery.isFetching,
    searchError: searchQuery.error,
    selectedArea: activeArea,
    submittedQuery,
    selectedTypes,
    setSelectedTypes,
    sort,
    setSort,
    reports: isSubmittedEmpty ? [] : (reportsQuery.data ?? []),
    isReportsLoading: submittedAreaQuery.isFetching || reportsQuery.isFetching,
    reportsError: reportsQuery.error,
    areaStatus: areaStatusQuery.data,
    isAreaStatusLoading: areaStatusQuery.isFetching,
    areaStatusError: areaStatusQuery.error,
    mapData: areaMapQuery.data,
    isMapLoading: areaMapQuery.isFetching,
    mapError: areaMapQuery.error,
    isFilterOpen,
    setIsFilterOpen,
    reportListRef,
    selectArea,
    submitSearch,
    clearSelectedArea,
    refreshAreaStatus,
    retryReports: reportsQuery.refetch,
  }
}
