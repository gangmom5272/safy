import { useEffect, useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { queryKeys } from '../../../api/queryKeys'
import {
  getArea,
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
  const [searchKeyword, setSearchKeyword] = useState('')
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

  const searchQuery = useQuery({
    queryKey: queryKeys.locations.search(debouncedKeyword),
    queryFn: () => searchLocations(debouncedKeyword),
    enabled: canSearch,
  })

  const areaQuery = useQuery({
    queryKey: queryKeys.areas.detail(selectedArea?.id ?? ''),
    queryFn: () => getArea(selectedArea!.id),
    enabled: Boolean(selectedArea),
  })

  const areaMapQuery = useQuery({
    queryKey: queryKeys.areas.map(selectedArea?.id ?? ''),
    queryFn: () => getAreaMap(selectedArea!.id),
    enabled: Boolean(selectedArea),
  })

  const areaStatusQuery = useQuery({
    queryKey: queryKeys.areas.status(selectedArea?.id ?? ''),
    queryFn: () => getAreaStatus(selectedArea!.id),
    enabled: Boolean(selectedArea),
  })

  const globalReportsQuery = useQuery({
    queryKey: queryKeys.reports.live({ sort, type: typeParam, limit: 20 }),
    queryFn: () => getLiveReports({ sort, types: selectedTypes, limit: 20 }),
    enabled: !selectedArea,
  })

  const areaReportsQuery = useQuery({
    queryKey: queryKeys.areas.liveReports(selectedArea?.id ?? '', { sort, type: typeParam }),
    queryFn: () => getAreaLiveReports(selectedArea!.id, { sort, types: selectedTypes }),
    enabled: Boolean(selectedArea),
  })

  const reportsQuery = selectedArea ? areaReportsQuery : globalReportsQuery

  useEffect(() => {
    reportListRef.current?.scrollTo({ top: 0, behavior: 'smooth' })
  }, [sort, typeParam, selectedArea?.id])

  const selectArea = (area: LocationSearchResult) => {
    setSelectedArea(area)
    setSearchKeyword(area.name)
    setDebouncedKeyword('')
  }

  const clearSelectedArea = () => {
    setSelectedArea(null)
    setSearchKeyword('')
    setDebouncedKeyword('')
  }

  const refreshAreaStatus = () => {
    void areaStatusQuery.refetch()
  }

  const searchSuggestions = useMemo(() => {
    if (!canSearch) {
      return []
    }

    return searchQuery.data ?? []
  }, [canSearch, searchQuery.data])

  return {
    searchKeyword,
    setSearchKeyword,
    searchSuggestions,
    isSearchLoading: searchQuery.isFetching,
    searchError: searchQuery.error,
    selectedArea: areaQuery.data ?? selectedArea,
    selectedTypes,
    setSelectedTypes,
    sort,
    setSort,
    reports: reportsQuery.data ?? [],
    isReportsLoading: reportsQuery.isFetching,
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
    clearSelectedArea,
    refreshAreaStatus,
    retryReports: reportsQuery.refetch,
  }
}
