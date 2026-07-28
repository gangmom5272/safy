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

// 카카오 응답 필드명이라 snake_case 를 그대로 둔다.
interface KakaoAddressResult {
  road_address: { address_name: string } | null
  address: { address_name: string } | null
}

interface KakaoGeocoder {
  coord2Address: (
    longitude: number,
    latitude: number,
    callback: (result: KakaoAddressResult[], status: string) => void,
  ) => void
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
  services: {
    Status: { OK: string }
    Geocoder: new () => KakaoGeocoder
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
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&autoload=false&libraries=services`
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

export async function resolveAddress(latitude: number, longitude: number): Promise<string> {
  const maps = await loadKakaoMaps()

  return new Promise<string>((resolve, reject) => {
    new maps.services.Geocoder().coord2Address(longitude, latitude, (result, status) => {
      const found = status === maps.services.Status.OK ? result[0] : undefined
      // 신축 도로 등 도로명 주소가 없는 좌표가 있어 지번 주소로 보완한다.
      const addressName = found?.road_address?.address_name ?? found?.address?.address_name

      if (!addressName) {
        reject(new Error('현재 위치의 주소를 찾지 못했습니다.'))
        return
      }

      resolve(addressName)
    })
  })
}
