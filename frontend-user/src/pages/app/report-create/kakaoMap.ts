export interface KakaoLatLng {
  getLat: () => number
  getLng: () => number
}

export interface KakaoMap {
  getCenter: () => KakaoLatLng
  setCenter: (position: KakaoLatLng) => void
  setMinLevel: (level: number) => void
  setMaxLevel: (level: number) => void
  setDraggable: (draggable: boolean) => void
  setZoomable: (zoomable: boolean) => void
}

export interface KakaoMarker {
  setPosition: (position: KakaoLatLng) => void
}

interface KakaoMaps {
  load: (callback: () => void) => void
  LatLng: new (latitude: number, longitude: number) => KakaoLatLng
  Map: new (container: HTMLElement, options: { center: KakaoLatLng; level: number }) => KakaoMap
  Marker: new (options: { position: KakaoLatLng; map: KakaoMap }) => KakaoMarker
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
