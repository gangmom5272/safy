import { useEffect, useMemo, useState } from 'react'
import { useFavoritePlacesStore } from '../../../stores/favoritePlacesStore'
import type { LocationSearchResult } from './types'

type SelectedAreaHeaderProps = {
  area: LocationSearchResult
  onClear: () => void
}

export function SelectedAreaHeader({ area, onClear }: SelectedAreaHeaderProps) {
  const {
    favoritePlaces,
    storageError,
    hydrate,
    addFavoritePlace,
    removeFavoritePlace,
  } = useFavoritePlacesStore()
  const [message, setMessage] = useState('')
  const placeId = area.id || area.name
  const isFavorite = useMemo(
    () => favoritePlaces.some((place) => place.id === placeId || place.searchQuery === area.name),
    [area.name, favoritePlaces, placeId],
  )

  useEffect(() => {
    hydrate()
  }, [hydrate])

  const toggleFavorite = () => {
    if (isFavorite) {
      removeFavoritePlace(placeId)
      setMessage('즐겨찾기를 해제했습니다.')
      return
    }

    addFavoritePlace({
      id: placeId,
      name: area.name,
      searchQuery: area.name,
      address: area.address,
      latitude: area.latitude ?? 0,
      longitude: area.longitude ?? 0,
    })
    setMessage('즐겨찾기에 추가했습니다.')
  }

  useEffect(() => {
    if (!message) {
      return undefined
    }

    const timer = window.setTimeout(() => setMessage(''), 2200)
    return () => window.clearTimeout(timer)
  }, [message])

  return (
    <section className="selected-area-header" aria-label="선택된 지역">
      <div>
        <p>선택 지역</p>
        <h2>{area.name} 주변</h2>
        <span>{area.address}</span>
        <span>검색 시간 {formatSearchTime()} 기준</span>
      </div>
      <div className="selected-area-actions">
        <button
          className={isFavorite ? 'favorite-star-button active' : 'favorite-star-button'}
          type="button"
          aria-label={isFavorite ? `${area.name} 즐겨찾기 해제` : `${area.name} 즐겨찾기 추가`}
          aria-pressed={isFavorite}
          onClick={toggleFavorite}
        >
          {isFavorite ? '★' : '☆'}
        </button>
        <button type="button" onClick={onClear}>
          뒤로 가기
        </button>
      </div>
      {message || storageError ? (
        <p className="favorite-feedback" role="status">
          {message || storageError}
        </p>
      ) : null}
    </section>
  )
}

function formatSearchTime() {
  return new Intl.DateTimeFormat('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date())
}
