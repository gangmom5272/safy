import { create } from 'zustand'
import type { FavoritePlace } from '../pages/app/situations/types'

export const FAVORITE_PLACES_STORAGE_KEY = 'safeon.favoritePlaces.v1'

type FavoritePlacesState = {
  favoritePlaces: FavoritePlace[]
  storageError: string
  hydrate: () => void
  isFavoritePlace: (placeId: string) => boolean
  addFavoritePlace: (place: Omit<FavoritePlace, 'savedAt'>) => void
  removeFavoritePlace: (placeId: string) => void
}

function isFavoritePlace(value: unknown): value is FavoritePlace {
  if (!value || typeof value !== 'object') {
    return false
  }

  const item = value as Record<string, unknown>

  return (
    typeof item.id === 'string' &&
    typeof item.name === 'string' &&
    typeof item.searchQuery === 'string' &&
    typeof item.address === 'string' &&
    typeof item.latitude === 'number' &&
    typeof item.longitude === 'number' &&
    typeof item.savedAt === 'string'
  )
}

function readStoredFavorites(): { places: FavoritePlace[]; error: string } {
  try {
    const raw = window.localStorage.getItem(FAVORITE_PLACES_STORAGE_KEY)

    if (!raw) {
      return { places: [], error: '' }
    }

    const parsed: unknown = JSON.parse(raw)

    if (!Array.isArray(parsed)) {
      return { places: [], error: '저장된 즐겨찾기 형식이 올바르지 않습니다.' }
    }

    return { places: parsed.filter(isFavoritePlace), error: '' }
  } catch {
    return { places: [], error: '저장된 즐겨찾기를 복원하지 못했습니다.' }
  }
}

function persistFavorites(places: FavoritePlace[]) {
  try {
    window.localStorage.setItem(FAVORITE_PLACES_STORAGE_KEY, JSON.stringify(places))
    return ''
  } catch {
    return '즐겨찾기를 저장하지 못했습니다.'
  }
}

export const useFavoritePlacesStore = create<FavoritePlacesState>((set, get) => ({
  favoritePlaces: [],
  storageError: '',

  hydrate: () => {
    const { places, error } = readStoredFavorites()
    set({ favoritePlaces: places, storageError: error })
  },

  isFavoritePlace: (placeId) =>
    get().favoritePlaces.some((place) => place.id === placeId || place.searchQuery === placeId),

  addFavoritePlace: (place) => {
    set((state) => {
      const nextPlace: FavoritePlace = {
        ...place,
        savedAt: new Date().toISOString(),
      }
      const withoutDuplicate = state.favoritePlaces.filter(
        (item) => item.id !== nextPlace.id && item.searchQuery !== nextPlace.searchQuery,
      )
      const favoritePlaces = [nextPlace, ...withoutDuplicate]

      return {
        favoritePlaces,
        storageError: persistFavorites(favoritePlaces),
      }
    })
  },

  removeFavoritePlace: (placeId) => {
    set((state) => {
      const favoritePlaces = state.favoritePlaces.filter(
        (place) => place.id !== placeId && place.searchQuery !== placeId,
      )

      return {
        favoritePlaces,
        storageError: persistFavorites(favoritePlaces),
      }
    })
  },
}))
