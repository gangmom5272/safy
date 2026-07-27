import { useEffect, useState } from 'react'
import { searchLocations } from '../situations/situationApi'
import type { LocationSearchResult } from '../situations/types'

type FavoritePlaceFormProps = {
  initialAlias?: string
  initialLocation?: LocationSearchResult
  submitLabel: string
  onSubmit: (alias: string, location: LocationSearchResult) => void
  onCancel: () => void
}

export function FavoritePlaceForm({ initialAlias = '', initialLocation, submitLabel, onSubmit, onCancel }: FavoritePlaceFormProps) {
  const [alias, setAlias] = useState(initialAlias)
  const [keyword, setKeyword] = useState(initialLocation?.name ?? '')
  const [selectedLocation, setSelectedLocation] = useState<LocationSearchResult | null>(initialLocation ?? null)
  const [results, setResults] = useState<LocationSearchResult[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const trimmedKeyword = keyword.trim()

  useEffect(() => {
    if (selectedLocation || trimmedKeyword.length < 2) {
      return
    }

    let active = true
    const timer = window.setTimeout(async () => {
      setLoading(true)
      setError('')
      try {
        const locations = await searchLocations(trimmedKeyword)
        if (active) setResults(locations)
      } catch (searchError) {
        console.error('Favorite location search failed', searchError)
        if (active) {
          setResults([])
          setError('검색 결과를 불러오지 못했습니다.')
        }
      } finally {
        if (active) setLoading(false)
      }
    }, 300)

    return () => {
      active = false
      window.clearTimeout(timer)
    }
  }, [selectedLocation, trimmedKeyword])

  const canSubmit = Boolean(
    alias.trim() &&
    selectedLocation?.address &&
    typeof selectedLocation.latitude === 'number' &&
    typeof selectedLocation.longitude === 'number',
  )

  return <section className="mypage-form compact favorite-place-form">
    <label>별칭<input required value={alias} placeholder="예: 회사, 집, 자주 가는 곳" onChange={(event) => setAlias(event.target.value)} /></label>
    <label>주소<span className="favorite-address-search"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg><input value={keyword} placeholder="주소 또는 장소명을 검색해주세요" onChange={(event) => { setKeyword(event.target.value); setSelectedLocation(null); setResults([]); setError(''); setLoading(false) }} /></span></label>
    {!selectedLocation && trimmedKeyword.length >= 2 && <div className="favorite-search-results" role="listbox" aria-label="주소 검색 결과">
      {loading && <p>검색 중입니다.</p>}
      {!loading && error && <p role="alert">{error}</p>}
      {!loading && !error && !results.length && <p>검색 결과가 없습니다.</p>}
      {!loading && !error && results.map((location) => <button type="button" role="option" aria-selected="false" key={location.id} onClick={() => { setSelectedLocation(location); setKeyword(location.name); setResults([]) }}><strong>{location.name}</strong><span>{location.address}</span></button>)}
    </div>}
    {selectedLocation && <div className="favorite-selected-place"><small>선택한 장소</small><strong>{selectedLocation.name}</strong><span>{selectedLocation.address}</span></div>}
    <div className="inline-actions"><button type="button" disabled={!canSubmit} onClick={() => { if (selectedLocation && canSubmit) onSubmit(alias.trim(), selectedLocation) }}>{submitLabel}</button><button type="button" className="secondary" onClick={onCancel}>취소</button></div>
  </section>
}
