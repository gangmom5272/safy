import { useEffect, useState } from 'react'
import { useAuth } from '../../auth/useAuth'
import {
  changeMyPresence,
  getMyPresence,
  PRESENCE_LABELS,
  SELECTABLE_PRESENCE,
  type PresenceStatus,
} from './presenceApi'

/**
 * 근무 상태 토글. 자동 배정은 근무중인 담당자에게만 가므로, 이 토글이 없으면
 * 담당자가 배정을 잠시 멈출 방법이 없다(화면을 열면 서버가 자동으로 근무중으로 본다).
 * 근무 상태 API 는 OPERATOR 전용이라 SUPER_ADMIN 에게는 노출하지 않는다.
 */
export function OperatorPresenceToggle() {
  const { user } = useAuth()
  const isOperator = user?.role === 'OPERATOR'
  const [status, setStatus] = useState<PresenceStatus | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!isOperator) return

    let cancelled = false
    getMyPresence()
      .then((presence) => {
        if (!cancelled) setStatus(presence.status)
      })
      .catch(() => {
        // 조회 실패는 화면을 막지 않는다. 토글만 뜨지 않는다.
      })

    return () => {
      cancelled = true
    }
  }, [isOperator])

  if (!isOperator || status === null) return null

  const change = async (next: PresenceStatus) => {
    if (next === status || isSaving) return

    setIsSaving(true)
    setError('')
    const previous = status
    setStatus(next)
    try {
      const updated = await changeMyPresence(next)
      setStatus(updated.status)
    } catch (caught) {
      setStatus(previous)
      setError(caught instanceof Error ? caught.message : '근무 상태를 바꾸지 못했습니다.')
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <div className="presence-toggle">
      <span className={`presence-dot presence-dot--${status.toLowerCase()}`} aria-hidden="true" />
      <div className="presence-toggle__buttons" role="group" aria-label="근무 상태">
        {SELECTABLE_PRESENCE.map((option) => (
          <button
            key={option}
            type="button"
            className={option === status ? 'active' : undefined}
            aria-pressed={option === status}
            disabled={isSaving}
            onClick={() => void change(option)}
          >
            {PRESENCE_LABELS[option]}
          </button>
        ))}
      </div>
      {status === 'OFFLINE' && (
        <span className="presence-toggle__hint">연결이 끊겨 배정이 오지 않습니다</span>
      )}
      {error && <span className="presence-toggle__error" role="alert">{error}</span>}
    </div>
  )
}
