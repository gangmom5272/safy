import { useCallback, useEffect, useRef, useState } from 'react'
import type { LocationStatus, ReportLocation } from './reportTypes'

interface GeolocationState {
  status: LocationStatus
  location: ReportLocation | null
  /** 측위 오차 반경(m). 핀을 얼마나 움직이게 둘지 정하는 데 쓴다. */
  accuracy: number | null
  message: string
  requestLocation: () => void
}

interface UseGeolocationOptions {
  /**
   * 제보 작성은 핀을 놓을 자리라 정밀 측위가 필요하다(기본값). 지도 중심처럼
   * 수십 m 오차가 문제되지 않는 곳은 false 로 껐을 때 훨씬 빨리 끝난다.
   */
  highAccuracy?: boolean
}

/** 저정밀 좌표를 재사용할 수 있는 시간. 지도 중심을 잡는 용도라 이 정도 오차는 무해하다. */
const CACHE_TTL_MS = 5 * 60 * 1000

const HIGH_ACCURACY_OPTIONS: PositionOptions = {
  enableHighAccuracy: true,
  timeout: 10000,
  maximumAge: 60000,
}

const COARSE_OPTIONS: PositionOptions = {
  enableHighAccuracy: false,
  timeout: 10000,
  maximumAge: CACHE_TTL_MS,
}

/**
 * 마지막으로 잡은 좌표. 화면을 오갈 때마다 훅이 새로 마운트되는데, 그때마다 다시
 * 측위하면 캐시가 만료된 재진입에서 몇 초씩 빈 지도를 보게 된다.
 * 정밀 측위 요청은 이 값을 쓰지 않는다 — 몇 분 전 좌표로 핀을 놓으면 안 된다.
 */
let cachedPosition: { location: ReportLocation; accuracy: number | null; at: number } | null = null

function readCachedPosition() {
  if (!cachedPosition || Date.now() - cachedPosition.at > CACHE_TTL_MS) {
    return null
  }

  return cachedPosition
}

function getGeolocationErrorState(error: GeolocationPositionError): Pick<GeolocationState, 'status' | 'message'> {
  if (error.code === error.PERMISSION_DENIED) {
    return {
      status: 'denied',
      message: '위치 권한이 거부되었습니다. 브라우저 설정에서 위치 권한을 허용한 뒤 다시 시도해주세요.',
    }
  }

  if (error.code === error.TIMEOUT) {
    return {
      status: 'timeout',
      message: '위치 확인 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.',
    }
  }

  return {
    status: 'error',
    message: '현재 위치를 확인하지 못했습니다. 네트워크 또는 기기 위치 설정을 확인해주세요.',
  }
}

export function useGeolocation({ highAccuracy = true }: UseGeolocationOptions = {}): GeolocationState {
  const [initialPosition] = useState(() => (highAccuracy ? null : readCachedPosition()))
  const [status, setStatus] = useState<LocationStatus>(initialPosition ? 'success' : 'idle')
  const [location, setLocation] = useState<ReportLocation | null>(initialPosition?.location ?? null)
  const [accuracy, setAccuracy] = useState<number | null>(initialPosition?.accuracy ?? null)
  const [message, setMessage] = useState(
    initialPosition ? '현재 위치 확인 완료' : '현재 위치 확인 전입니다.',
  )
  const requestInProgressRef = useRef(false)
  const mountedRef = useRef(false)

  const requestLocation = useCallback(() => {
    if (requestInProgressRef.current) {
      return
    }

    if (!navigator.geolocation) {
      setStatus('unsupported')
      setLocation(null)
      setAccuracy(null)
      setMessage('이 브라우저는 위치 확인 기능을 지원하지 않습니다.')
      return
    }

    requestInProgressRef.current = true
    setStatus('checking')
    setMessage('현재 위치를 확인하는 중입니다.')

    navigator.geolocation.getCurrentPosition(
      (position) => {
        requestInProgressRef.current = false

        const nextLocation = {
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
        }
        // 언마운트됐어도 캐시에는 남긴다. 다음 진입이 이 좌표로 바로 시작한다.
        cachedPosition = { location: nextLocation, accuracy: position.coords.accuracy, at: Date.now() }

        if (!mountedRef.current) {
          return
        }

        setLocation(nextLocation)
        setAccuracy(position.coords.accuracy)
        setStatus('success')
        setMessage('현재 위치 확인 완료')
      },
      (error) => {
        requestInProgressRef.current = false

        if (!mountedRef.current) {
          return
        }

        const errorState = getGeolocationErrorState(error)
        setLocation(null)
        setAccuracy(null)
        setStatus(errorState.status)
        setMessage(errorState.message)
      },
      highAccuracy ? HIGH_ACCURACY_OPTIONS : COARSE_OPTIONS,
    )
  }, [highAccuracy])

  useEffect(() => {
    mountedRef.current = true

    // 캐시로 시작했으면 이미 쓸 수 있는 좌표가 있다. 여기서 또 측위하면
    // 재진입 때마다 몇 초를 기다리던 원래 문제로 돌아간다.
    if (initialPosition) {
      return () => {
        mountedRef.current = false
      }
    }

    const requestTimer = window.setTimeout(requestLocation, 0)

    return () => {
      window.clearTimeout(requestTimer)
      mountedRef.current = false
    }
  }, [initialPosition, requestLocation])

  return { status, location, accuracy, message, requestLocation }
}
