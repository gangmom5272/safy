export interface KakaoLatLng {
  getLat: () => number
  getLng: () => number
}

export interface KakaoLatLngBounds {
  extend: (position: KakaoLatLng) => void
  getSouthWest: () => KakaoLatLng
  getNorthEast: () => KakaoLatLng
}

export interface KakaoMap {
  getCenter: () => KakaoLatLng
  setCenter: (position: KakaoLatLng) => void
  getLevel: () => number
  setLevel: (level: number) => void
  getBounds: () => KakaoLatLngBounds
  setBounds: (bounds: KakaoLatLngBounds) => void
  setMinLevel: (level: number) => void
  setMaxLevel: (level: number) => void
  setDraggable: (draggable: boolean) => void
  setZoomable: (zoomable: boolean) => void
  relayout: () => void
}

export interface KakaoMarker {
  setPosition: (position: KakaoLatLng) => void
}

export interface KakaoCustomOverlay {
  setMap: (map: KakaoMap | null) => void
  setPosition: (position: KakaoLatLng) => void
}

export interface KakaoPolygon {
  setMap: (map: KakaoMap | null) => void
}

export interface KakaoCircle {
  setPosition: (position: KakaoLatLng) => void
  setRadius: (radiusMeters: number) => void
  setMap: (map: KakaoMap | null) => void
}

interface KakaoMaps {
  load: (callback: () => void) => void
  LatLng: new (latitude: number, longitude: number) => KakaoLatLng
  LatLngBounds: new () => KakaoLatLngBounds
  Map: new (container: HTMLElement, options: { center: KakaoLatLng; level: number }) => KakaoMap
  Marker: new (options: { position: KakaoLatLng; map: KakaoMap }) => KakaoMarker
  CustomOverlay: new (options: {
    position: KakaoLatLng
    content: HTMLElement | string
    yAnchor?: number
    zIndex?: number
  }) => KakaoCustomOverlay
  Polygon: new (options: {
    map?: KakaoMap
    path: KakaoLatLng[]
    strokeWeight?: number
    strokeColor?: string
    strokeOpacity?: number
    fillColor?: string
    fillOpacity?: number
    zIndex?: number
  }) => KakaoPolygon
  Circle: new (options: {
    center: KakaoLatLng
    radius: number
    strokeWeight: number
    strokeColor: string
    strokeOpacity: number
    fillColor: string
    fillOpacity: number
  }) => KakaoCircle
  event: {
    addListener: (target: KakaoMap, type: string, handler: () => void) => void
    removeListener: (target: KakaoMap, type: string, handler: () => void) => void
  }
}

declare global {
  interface Window {
    kakao?: { maps: KakaoMaps }
  }
}

let loadPromise: Promise<KakaoMaps> | null = null

export function loadKakaoMaps(): Promise<KakaoMaps> {
  if (loadPromise) {
    return loadPromise
  }

  const appKey = import.meta.env.VITE_KAKAO_MAP_JS_KEY

  if (!appKey) {
    return Promise.reject(new Error('VITE_KAKAO_MAP_JS_KEY 가 설정되지 않았습니다.'))
  }

  const pending = new Promise<KakaoMaps>((resolve, reject) => {
    const script = document.createElement('script')
    script.async = true
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&autoload=false`
    script.onload = () => {
      const kakao = window.kakao

      if (!kakao) {
        reject(new Error('카카오맵 SDK 초기화에 실패했습니다.'))
        return
      }

      kakao.maps.load(() => resolve(kakao.maps))
    }
    script.onerror = () => reject(new Error('카카오맵 SDK를 불러오지 못했습니다.'))
    document.head.appendChild(script)
  })

  // 실패한 프라미스를 캐시하면 재시도가 영원히 막히므로 초기화한다.
  pending.catch(() => {
    loadPromise = null
  })

  loadPromise = pending

  return pending
}
