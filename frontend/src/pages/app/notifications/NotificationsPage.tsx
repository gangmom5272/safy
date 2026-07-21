import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { getNotifications, getNotificationSettings, readAllNotifications, readNotification, updateNotificationSettings } from './notificationsApi'
import { mockNotifications, mockNotificationSettings, USE_NOTIFICATIONS_MOCK_FALLBACK } from './notificationsMock'
import type { AppNotification, NotificationFilter, NotificationSettingsData, NotificationType } from './notificationsTypes'
import { getNotificationTargetUrl } from './notificationsUtils'
import './notifications.css'

const tabs: { value: NotificationFilter; label: string }[] = [
  { value: 'ALL', label: '전체' }, { value: 'REPORT', label: '제보 관련' }, { value: 'SAFETY', label: '안전 정보' }, { value: 'SYSTEM', label: '시스템' },
]
const typeMeta: Record<NotificationType, { icon: string; label: string }> = {
  REPORT: { icon: '✓', label: '제보 관련' }, SAFETY: { icon: '!', label: '안전 정보' }, SYSTEM: { icon: 'i', label: '시스템' },
}

export function NotificationsPage() {
  const navigate = useNavigate()
  const mounted = useRef(true)
  const [notifications, setNotifications] = useState<AppNotification[]>([])
  const [settings, setSettings] = useState<NotificationSettingsData>(mockNotificationSettings)
  const [filter, setFilter] = useState<NotificationFilter>('ALL')
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
    if (targetUrl) navigate(targetUrl)
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
    <header className="notifications-header"><div><p>NOTIFICATIONS</p><h1>알림 {unreadCount > 0 && <span>{unreadCount}</span>}</h1></div><div className="header-actions"><button type="button" className="read-all-button" onClick={handleReadAll} disabled={unreadCount === 0}>전체 읽음</button><button type="button" className="settings-icon-button" aria-label="알림 설정 열기" onClick={() => setShowSettings(true)}>⚙</button></div></header>
    <NotificationTabs selected={filter} counts={counts} onSelect={setFilter} />
    <NotificationBanner onOpenSettings={() => setShowSettings(true)} />
    {usingMock && <p className="mock-notice">연결 전 예시 알림을 표시하고 있어요.</p>}
    {loading ? <NotificationLoading /> : groups.length ? <div className="notification-groups">{groups.map(([label, items]) => <NotificationSection key={label} label={label} items={items} onSelect={handleNotification} />)}</div> : <div className="notifications-empty"><span>✓</span><strong>새로운 알림이 없어요</strong><p>필요한 소식이 생기면 바로 알려드릴게요.</p></div>}
    {showSettings && <NotificationSettings settings={settings} onToggle={handleSetting} onClose={() => setShowSettings(false)} />}
  </div>
}

function NotificationTabs({ selected, counts, onSelect }: { selected: NotificationFilter; counts: Record<NotificationFilter, number>; onSelect: (value: NotificationFilter) => void }) {
  return <div className="notification-tabs" role="tablist" aria-label="알림 유형">{tabs.map((tab) => <button key={tab.value} type="button" role="tab" aria-selected={selected === tab.value} className={selected === tab.value ? 'active' : ''} onClick={() => onSelect(tab.value)}>{tab.label}<span>{counts[tab.value]}</span></button>)}</div>
}

function NotificationBanner({ onOpenSettings }: { onOpenSettings: () => void }) {
  return <aside className="notification-banner"><span aria-hidden="true">🔔</span><div><strong>중요 알림을 놓치지 마세요!</strong><p>알림을 켜두면 더 빠르게 정보를 받아볼 수 있어요.</p></div><button type="button" onClick={onOpenSettings}>설정</button></aside>
}

function NotificationSection({ label, items, onSelect }: { label: string; items: AppNotification[]; onSelect: (item: AppNotification) => void }) {
  return <section className="notification-section"><h2>{label}</h2><div>{items.map((item) => <NotificationCard key={item.id} notification={item} onSelect={onSelect} />)}</div></section>
}

function NotificationCard({ notification, onSelect }: { notification: AppNotification; onSelect: (item: AppNotification) => void }) {
  const meta = typeMeta[notification.type]
  return <button type="button" className={`notification-card type-${notification.type.toLowerCase()}${notification.isRead ? ' read' : ' unread'}`} onClick={() => onSelect(notification)}><span className="notification-type-icon" aria-hidden="true">{meta.icon}</span><span className="notification-body"><span className="notification-meta"><span>{meta.label}{notification.areaName ? ` · ${notification.areaName}` : ''}</span><time dateTime={notification.createdAt}>{formatNotificationTime(notification.createdAt)}</time></span><strong>{notification.title}</strong><span className="notification-content">{notification.content}</span><span className="notification-badges">{!notification.isRead && <em>NEW</em>}{notification.isImportant && <em className="important">중요</em>}</span></span></button>
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
  const date = new Date(value); const today = new Date(); const yesterday = new Date(today); yesterday.setDate(today.getDate() - 1)
  const key = date.toDateString()
  if (key === today.toDateString()) return '오늘'
  if (key === yesterday.toDateString()) return '어제'
  return date.toLocaleDateString('ko-KR', { month: 'long', day: 'numeric', weekday: 'short' })
}

function formatNotificationTime(value: string) { return new Date(value).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }) }
