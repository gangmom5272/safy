import { useCallback, useEffect, useRef, useState } from 'react'
import type { LocationStatus, ReportLocation } from './reportTypes'

interface GeolocationState {
  status: LocationStatus
  location: ReportLocation | null
  message: string
  requestLocation: () => void
}

const GEOLOCATION_OPTIONS: PositionOptions = {
  enableHighAccuracy: true,
  timeout: 10000,
  maximumAge: 60000,
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

export function useGeolocation(): GeolocationState {
  const [status, setStatus] = useState<LocationStatus>('idle')
  const [location, setLocation] = useState<ReportLocation | null>(null)
  const [message, setMessage] = useState('현재 위치 확인 전입니다.')
  const requestInProgressRef = useRef(false)
  const mountedRef = useRef(false)

  const requestLocation = useCallback(() => {
    if (requestInProgressRef.current) {
      return
    }

    if (!navigator.geolocation) {
      setStatus('unsupported')
      setLocation(null)
      setMessage('이 브라우저는 위치 확인 기능을 지원하지 않습니다.')
      return
    }

    requestInProgressRef.current = true
    setStatus('checking')
    setMessage('현재 위치를 확인하는 중입니다.')

    navigator.geolocation.getCurrentPosition(
      (position) => {
        requestInProgressRef.current = false

        if (!mountedRef.current) {
          return
        }

        setLocation({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
        })
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
        setStatus(errorState.status)
        setMessage(errorState.message)
      },
      GEOLOCATION_OPTIONS,
    )
  }, [])

  useEffect(() => {
    mountedRef.current = true
    const requestTimer = window.setTimeout(requestLocation, 0)

    return () => {
      window.clearTimeout(requestTimer)
      mountedRef.current = false
    }
  }, [requestLocation])

  return { status, location, message, requestLocation }
}
