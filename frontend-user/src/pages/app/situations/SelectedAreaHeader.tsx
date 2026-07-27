import { useEffect, useMemo, useState } from 'react'
import { useFavoritePlacesStore } from '../../../stores/favoritePlacesStore'
import { formatDateTime } from '../../../utils/dateTime'
import type { LocationSearchResult } from './types'

type SelectedAreaHeaderProps = {
  area: LocationSearchResult
}
export function SelectedAreaHeader({ area }: SelectedAreaHeaderProps) {
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
        <h2>{area.name} 주변</h2>
        <span className="selected-area-address">{area.address}</span>
        <span className="selected-area-time">{formatDateTime(new Date())} 기준</span>
      </div>
      <div className="selected-area-actions">
        <button
          className={isFavorite ? 'favorite-star-button active' : 'favorite-star-button'}
          type="button"
          aria-label={isFavorite ? `${area.name} 즐겨찾기 해제` : `${area.name} 즐겨찾기 추가`}
          aria-pressed={isFavorite}
          onClick={toggleFavorite}
        >
          <FavoriteStarIcon filled={isFavorite} />
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

function FavoriteStarIcon({ filled }: { filled: boolean }) {
  return <svg viewBox="0 0 24 24" fill={filled ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-2.9-5.6 2.9 1.1-6.2L3 9.6l6.2-.9Z" /></svg>
}
