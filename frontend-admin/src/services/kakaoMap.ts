// 카카오맵 SDK 로더. frontend-user 의 동일 모듈과 같은 방식이며,
// 두 앱은 의도적으로 코드를 공유하지 않으므로 관리자 화면에 필요한 타입만 담는다.

export interface KakaoLatLng {
  getLat: () => number
  getLng: () => number
}

export interface KakaoLatLngBounds {
  extend: (position: KakaoLatLng) => void
  isEmpty: () => boolean
}

export interface KakaoMap {
  setCenter: (position: KakaoLatLng) => void
  setLevel: (level: number) => void
  setBounds: (bounds: KakaoLatLngBounds) => void
  relayout: () => void
}

export interface KakaoCustomOverlay {
  setMap: (map: KakaoMap | null) => void
}

interface KakaoMaps {
  load: (callback: () => void) => void
  LatLng: new (latitude: number, longitude: number) => KakaoLatLng
  LatLngBounds: new () => KakaoLatLngBounds
  Map: new (container: HTMLElement, options: { center: KakaoLatLng; level: number }) => KakaoMap
  CustomOverlay: new (options: {
    position: KakaoLatLng
    content: HTMLElement | string
    map?: KakaoMap
    yAnchor?: number
    clickable?: boolean
  }) => KakaoCustomOverlay
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
