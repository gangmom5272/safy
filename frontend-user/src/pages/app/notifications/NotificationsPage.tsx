import { useEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { formatDateGroupLabel, formatRelativeTime } from '../../../utils/dateTime'
import { getNotifications, getNotificationSettings, readAllNotifications, readNotification, updateNotificationSettings } from './notificationsApi'
import { mockNotifications, mockNotificationSettings, USE_NOTIFICATIONS_MOCK_FALLBACK } from './notificationsMock'
import type { AppNotification, NotificationFilter, NotificationSettingsData, NotificationType } from './notificationsTypes'
import { getNotificationTargetUrl } from './notificationsUtils'
import './notifications.css'

const tabs: { value: NotificationFilter; label: string }[] = [
  { value: 'ALL', label: '전체' }, { value: 'REPORT', label: '제보 관련' }, { value: 'SAFETY', label: '안전 정보' }, { value: 'SYSTEM', label: '기타' },
]
const typeMeta: Record<NotificationType, { label: string }> = {
  REPORT: { label: '제보 관련' }, SAFETY: { label: '안전 정보' }, SYSTEM: { label: '시스템' },
}

export function NotificationsPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const mounted = useRef(true)
  const [notifications, setNotifications] = useState<AppNotification[]>([])
  const [settings, setSettings] = useState<NotificationSettingsData>(mockNotificationSettings)
  const [filter, setFilter] = useState<NotificationFilter>(() => {
    const restoredFilter = (location.state as { notificationFilter?: NotificationFilter } | null)?.notificationFilter
    return restoredFilter && ['ALL', 'REPORT', 'SAFETY', 'SYSTEM'].includes(restoredFilter) ? restoredFilter : 'ALL'
  })
  const [loading, setLoading] = useState(true)
  const [usingMock, setUsingMock] = useState(false)
  const [showSettings, setShowSettings] = useState(false)

  useEffect(() => {
    mounted.current = true
    const initialLoad = window.setTimeout(async () => {
      const [notificationsResult, settingsResult] = await Promise.allSettled([getNotifications(), getNotificationSettings()])
      if (!mounted.current) return
      if (notificationsResult.status === 'fulfilled') setNotifications(notificationsResult.value)
      else if (USE_NOTIFICATIONS_MOCK_FALLBACK) { console.error('Notifications request failed; using mock fallback', notificationsResult.reason); setNotifications(mockNotifications); setUsingMock(true) }
      if (settingsResult.status === 'fulfilled') setSettings(settingsResult.value)
      else if (USE_NOTIFICATIONS_MOCK_FALLBACK) { console.error('Notification settings request failed; using mock fallback', settingsResult.reason); setSettings(mockNotificationSettings); setUsingMock(true) }
      setLoading(false)
    }, 0)
    return () => { mounted.current = false; window.clearTimeout(initialLoad) }
  }, [])

  const counts = useMemo(() => ({ ALL: notifications.length, REPORT: notifications.filter((item) => item.type === 'REPORT').length, SAFETY: notifications.filter((item) => item.type === 'SAFETY').length, SYSTEM: notifications.filter((item) => item.type === 'SYSTEM').length }), [notifications])
  const visibleNotifications = useMemo(() => filter === 'ALL' ? notifications : notifications.filter((item) => item.type === filter), [filter, notifications])
  const groups = useMemo(() => groupNotifications(visibleNotifications), [visibleNotifications])
  const unreadCount = notifications.filter((item) => !item.isRead).length

  const handleNotification = (notification: AppNotification) => {
    if (!notification.isRead) {
      setNotifications((items) => items.map((item) => item.id === notification.id ? { ...item, isRead: true } : item))
      if (!usingMock) void readNotification(notification.id).catch((error: unknown) => console.error('Notification read update failed', error))
    }
    const targetUrl = getNotificationTargetUrl(notification)
    if (targetUrl) {
      const navigationState = notification.type === 'REPORT' || notification.type === 'SAFETY'
        ? {
            from: 'notifications',
            returnTo: '/app/notifications',
            notificationFilter: filter,
          }
        : undefined
      navigate(targetUrl, { state: navigationState })
    }
  }

  const handleReadAll = () => {
    setNotifications((items) => items.map((item) => ({ ...item, isRead: true })))
    if (!usingMock) void readAllNotifications().catch((error: unknown) => console.error('Read-all update failed', error))
  }

  const handleSetting = (key: keyof NotificationSettingsData) => {
    const next = { ...settings, [key]: !settings[key] }
    setSettings(next)
    if (!usingMock) void updateNotificationSettings(next).catch((error: unknown) => console.error('Notification settings update failed', error))
  }

  return <div className="notifications-page">
    <header className="notifications-header"><button type="button" className="notifications-back" aria-label="뒤로가기" onClick={() => navigate(-1)}><UiIcon name="back" /></button><h1>알림</h1><span className="notifications-header-spacer" aria-hidden="true" /></header>
    <NotificationTabs selected={filter} counts={counts} onSelect={setFilter} />
    <div className="notification-list-toolbar"><span>{unreadCount > 0 ? `읽지 않은 알림 ${unreadCount}개` : '모든 알림을 확인했어요'}</span><button type="button" onClick={handleReadAll} disabled={unreadCount === 0}>모두 읽음</button></div>
    {loading ? <NotificationLoading /> : groups.length ? <div className="notification-groups">{groups.map(([label, items]) => <NotificationSection key={label} label={label} items={items} onSelect={handleNotification} />)}</div> : <div className="notifications-empty"><span><UiIcon name="check" /></span><strong>새로운 알림이 없어요</strong><p>필요한 소식이 생기면 바로 알려드릴게요.</p></div>}
    {showSettings && <NotificationSettings settings={settings} onToggle={handleSetting} onClose={() => setShowSettings(false)} />}
  </div>
}

function NotificationTabs({ selected, counts, onSelect }: { selected: NotificationFilter; counts: Record<NotificationFilter, number>; onSelect: (value: NotificationFilter) => void }) {
  return <div className="notification-tabs" role="tablist" aria-label="알림 유형">{tabs.map((tab) => <button key={tab.value} type="button" role="tab" aria-selected={selected === tab.value} className={selected === tab.value ? 'active' : ''} onClick={() => onSelect(tab.value)}>{tab.label}<span>{counts[tab.value]}</span></button>)}</div>
}

function NotificationSection({ label, items, onSelect }: { label: string; items: AppNotification[]; onSelect: (item: AppNotification) => void }) {
  return <section className="notification-section"><h2>{label}</h2><div>{items.map((item) => <NotificationCard key={item.id} notification={item} onSelect={onSelect} />)}</div></section>
}

function NotificationCard({ notification, onSelect }: { notification: AppNotification; onSelect: (item: AppNotification) => void }) {
  const meta = typeMeta[notification.type]
  return <button type="button" className={`notification-card type-${notification.type.toLowerCase()}${notification.isRead ? ' read' : ' unread'}`} onClick={() => onSelect(notification)}><span className="notification-accent" aria-hidden="true"/><span className="notification-type-icon" aria-hidden="true"><UiIcon name={notification.type.toLowerCase() as 'report' | 'safety' | 'system'} /></span><span className="notification-body"><span className="notification-meta"><span>{meta.label}{notification.areaName ? ` · ${notification.areaName}` : ''}</span><time dateTime={notification.createdAt}>{formatRelativeTime(notification.createdAt)}</time></span><strong>{notification.title}</strong><span className="notification-content">{notification.content}</span><span className="notification-badges">{!notification.isRead && <em>NEW</em>}{notification.isImportant && <em className="important">중요</em>}</span></span></button>
}

function UiIcon({ name }: { name: 'back' | 'check' | 'report' | 'safety' | 'system' }) {
  const paths = {
    back: <><path d="m15 5-7 7 7 7"/><path d="M8 12h12"/></>,
    check: <path d="m5 12 4 4 10-10"/>,
    report: <><path d="M5 3h14v18l-7-4-7 4Z"/><path d="M9 8h6M9 12h4"/></>,
    safety: <><path d="M12 3 3 20h18Z"/><path d="M12 9v4M12 17h.01"/></>,
    system: <><circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/></>,
  }
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>
}

function NotificationSettings({ settings, onToggle, onClose }: { settings: NotificationSettingsData; onToggle: (key: keyof NotificationSettingsData) => void; onClose: () => void }) {
  const options: { key: keyof NotificationSettingsData; label: string; description: string }[] = [
    { key: 'enabled', label: '전체 알림', description: '모든 앱 알림을 허용합니다.' }, { key: 'reportEnabled', label: '제보 관련 알림', description: '접수 및 처리 상태를 알려드려요.' }, { key: 'safetyEnabled', label: '안전 정보 알림', description: '주변 위험과 혼잡 정보를 알려드려요.' }, { key: 'systemEnabled', label: '시스템 알림', description: '공지와 점검 소식을 알려드려요.' }, { key: 'savedLocationEnabled', label: '즐겨찾기 위치 알림', description: '저장한 지역의 변화를 알려드려요.' },
  ]
  return <div className="settings-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}><section className="notification-settings" role="dialog" aria-modal="true" aria-labelledby="notification-settings-title"><header><div><p>SETTINGS</p><h2 id="notification-settings-title">알림 설정</h2></div><button type="button" aria-label="알림 설정 닫기" onClick={onClose}>×</button></header><div className="settings-list">{options.map((option) => <div className="setting-row" key={option.key}><label htmlFor={`setting-${option.key}`}><strong>{option.label}</strong><span>{option.description}</span></label><button id={`setting-${option.key}`} type="button" role="switch" aria-checked={settings[option.key]} className={settings[option.key] ? 'toggle on' : 'toggle'} onClick={() => onToggle(option.key)} disabled={option.key !== 'enabled' && !settings.enabled}><span /></button></div>)}</div><button type="button" className="settings-done" onClick={onClose}>완료</button></section></div>
}

function NotificationLoading() { return <div className="notification-loading" aria-label="알림을 불러오는 중">{[1, 2, 3].map((item) => <span key={item} />)}</div> }

function groupNotifications(items: AppNotification[]) {
  const groups = new Map<string, AppNotification[]>()
  items.forEach((item) => { const label = getDateLabel(item.createdAt); groups.set(label, [...(groups.get(label) ?? []), item]) })
  return Array.from(groups.entries())
}

function getDateLabel(value: string) {
  return formatDateGroupLabel(value)
}
