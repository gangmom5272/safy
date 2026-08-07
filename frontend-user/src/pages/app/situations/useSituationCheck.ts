import { useEffect, useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useLocation, useNavigate, useSearchParams } from 'react-router'
import { queryKeys } from '../../../api/queryKeys'
import {
  getArea,
  getAreaLiveReports,
  getAreaMap,
  getAreaStatus,
  getCoordinateStatus,
  getLiveReports,
  getLocationByQuery,
  getPlaceStatus,
  searchLocations,
} from './situationApi'
import {
  DEFAULT_REPORT_SORT,
  type LiveReportSummary,
  type LocationSearchResult,
  type ReportSort,
  type ReportType,
} from './types'

const SEARCH_DEBOUNCE_MS = 300
const MIN_SEARCH_LENGTH = 2
// 매번 새 빈 배열을 만들면 지도가 참조 변화로 보고 마커를 다시 그린다.
const EMPTY_REPORTS: LiveReportSummary[] = []

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

  // 자치구만 주소를 보강한다(검색 결과의 address 는 이름을 그대로 반복한 값이라
  // /administrative-areas/{id} 로 실제 주소를 다시 받는다). 장소는 이 상세 조회 자체가
  // 없다 — 검색 결과가 이미 갖고 있는 정보가 전부다.
  const areaQuery = useQuery({
    queryKey: queryKeys.areas.detail(selectedArea?.id ?? submittedQuery),
    queryFn: () => getArea(selectedArea!.id),
    enabled: Boolean(selectedArea) && selectedArea?.type === 'area' && !hasSubmittedQuery,
  })

  // 종류를 가리지 않고 "이 검색어가 가리키는 대상"을 찾는다. 무엇이 나왔는지는
  // activeArea.type 으로 판단해 아래에서 지역/장소/좌표 API 를 갈라 부른다.
  const submittedLocationQuery = useQuery({
    queryKey: queryKeys.locations.detail(submittedQuery),
    queryFn: () => getLocationByQuery(submittedQuery),
    enabled: hasSubmittedQuery,
  })

  const activeArea = submittedLocationQuery.data ?? areaQuery.data ?? selectedArea
  const isAreaSelected = activeArea?.type === 'area'
  const isPlaceSelected = activeArea?.type === 'place'
  // 외부 검색 결과는 우리 식별자가 없어 좌표로만 조회할 수 있다.
  const keywordCoordinate = activeArea?.type === 'keyword'
    && activeArea.latitude !== undefined && activeArea.longitude !== undefined
    ? { latitude: activeArea.latitude, longitude: activeArea.longitude }
    : null
  const areaKey = activeArea?.id ?? submittedQuery
  const displayedSearchKeyword = activeArea && searchKeyword.trim() === submittedQuery
    ? activeArea.name
    : searchKeyword

  // 장소는 지도 응답(zones·경계)이 필요 없다 — 중심 좌표는 검색 결과에 이미 있고,
  // 마커는 지역 제한 없는 전체 피드를 그대로 쓴다(아래 globalReportsQuery).
  const areaMapQuery = useQuery({
    queryKey: queryKeys.areas.map(areaKey),
    queryFn: () => getAreaMap(areaKey),
    enabled: isAreaSelected,
  })

  const areaStatusQuery = useQuery({
    queryKey: queryKeys.areas.status(areaKey),
    queryFn: () => getAreaStatus(areaKey),
    enabled: isAreaSelected,
  })

  // 121장소를 직접 검색했을 때의 상황 요약. 그 구의 최고 혼잡 장소가 아니라 이 장소
  // 자신의 값이다 — 강남구를 검색했을 때와 역삼역을 검색했을 때가 같은 값이면 안 된다.
  const placeStatusQuery = useQuery({
    queryKey: queryKeys.places.status(areaKey),
    queryFn: () => getPlaceStatus(areaKey),
    enabled: isPlaceSelected,
  })

  // 외부 검색으로 찾은 구체적 장소. 혼잡도는 그 좌표의 격자를 덮는 121장소(없으면 최근접),
  // 최근 제보는 반경 1km 다. 두 범위가 달라 응답이 각각의 근거를 함께 준다.
  const coordinateStatusQuery = useQuery({
    queryKey: queryKeys.locations.status(
      keywordCoordinate?.latitude ?? 0,
      keywordCoordinate?.longitude ?? 0,
    ),
    queryFn: () => getCoordinateStatus(keywordCoordinate!.latitude, keywordCoordinate!.longitude),
    enabled: keywordCoordinate !== null,
  })

  const activeStatusQuery = keywordCoordinate
    ? coordinateStatusQuery
    : isPlaceSelected ? placeStatusQuery : areaStatusQuery

  // 마커는 항상 지역 제한 없는 전체 피드(30분 이내 공개 제보)다. 자치구를 선택했을 때만
  // 그 구로 좁힌 별도 피드(areaReportsQuery)를 쓴다.
  const globalReportsQuery = useQuery({
    queryKey: queryKeys.reports.live({ sort, type: typeParam, limit: 20 }),
    queryFn: () => getLiveReports({ sort, types: selectedTypes, limit: 20 }),
    enabled: !isAreaSelected,
  })

  const areaReportsQuery = useQuery({
    queryKey: queryKeys.areas.liveReports(areaKey, { sort, type: typeParam }),
    queryFn: () => getAreaLiveReports(areaKey, { sort, types: selectedTypes }),
    enabled: isAreaSelected,
  })

  const reportsQuery = isAreaSelected ? areaReportsQuery : globalReportsQuery
  const isSubmittedEmpty = hasSubmittedQuery && !activeArea && !submittedLocationQuery.isFetching

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
    // 자치구만 지도 응답(경계·중심)을 함께 쓰므로 둘을 같이 갱신한다.
    if (isAreaSelected) {
      await Promise.allSettled([
        areaStatusQuery.refetch(),
        areaMapQuery.refetch(),
      ])
      return
    }

    await activeStatusQuery.refetch()
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
    reports: isSubmittedEmpty ? EMPTY_REPORTS : (reportsQuery.data ?? EMPTY_REPORTS),
    isReportsLoading: submittedLocationQuery.isFetching || reportsQuery.isFetching,
    reportsError: reportsQuery.error,
    areaStatus: activeStatusQuery.data,
    isAreaStatusLoading: activeStatusQuery.isFetching,
    areaStatusError: activeStatusQuery.error,
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
