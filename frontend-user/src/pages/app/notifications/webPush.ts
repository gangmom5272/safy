import apiClient from '../../../api/client'
import type { ApiResponse } from '../../../api/types'
import type { NotificationSettingsData } from './notificationsTypes'

const serviceWorkerPath = '/sw.js'

interface WebPushPublicKeyResponse {
  publicKey: string
}

function ensureSupported() {
  if (!window.isSecureContext || !('serviceWorker' in navigator)
    || !('PushManager' in window) || !('Notification' in window)) {
    throw new Error('이 브라우저에서는 Web Push를 사용할 수 없습니다.')
  }
}

function urlBase64ToUint8Array(value: string) {
  const padding = '='.repeat((4 - (value.length % 4)) % 4)
  const raw = window.atob(`${value}${padding}`.replace(/-/g, '+').replace(/_/g, '/'))
  return Uint8Array.from(raw, (character) => character.charCodeAt(0))
}

function keyToBase64Url(key: ArrayBuffer | null) {
  if (!key) return ''
  const binary = Array.from(new Uint8Array(key), (byte) => String.fromCharCode(byte)).join('')
  return window.btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '')
}

function sameApplicationServerKey(subscription: PushSubscription, expected: Uint8Array) {
  const current = subscription.options.applicationServerKey
  if (!current) return false
  const bytes = new Uint8Array(current)
  return bytes.length === expected.length && bytes.every((value, index) => value === expected[index])
}

async function registerSubscription(subscription: PushSubscription) {
  await apiClient.post('/web-push/subscriptions', {
    endpoint: subscription.endpoint,
    keys: {
      p256dh: keyToBase64Url(subscription.getKey('p256dh')),
      auth: keyToBase64Url(subscription.getKey('auth')),
    },
    userAgent: navigator.userAgent,
  })
}

export async function ensureWebPushSubscribed(requestPermission = true) {
  ensureSupported()
  const permission = Notification.permission === 'granted'
    ? Notification.permission
    : requestPermission ? await Notification.requestPermission() : Notification.permission
  if (permission !== 'granted') throw new Error('알림 권한이 허용되지 않았습니다.')

  const [{ data }] = await Promise.all([
    apiClient.get<ApiResponse<WebPushPublicKeyResponse>>('/web-push/public-key'),
    navigator.serviceWorker.register(serviceWorkerPath),
  ])
  const ready = await navigator.serviceWorker.ready
  const applicationServerKey = urlBase64ToUint8Array(data.data.publicKey)
  let existing = await ready.pushManager.getSubscription()
  if (existing && !sameApplicationServerKey(existing, applicationServerKey)) {
    await apiClient.delete('/web-push/subscriptions', { data: { endpoint: existing.endpoint } })
    await existing.unsubscribe()
    existing = null
  }
  const subscription = existing ?? await ready.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey,
  })
  await registerSubscription(subscription)
}

export async function synchronizeWebPushSubscription() {
  if (!window.isSecureContext || !('serviceWorker' in navigator)
    || !('PushManager' in window) || !('Notification' in window)
    || Notification.permission !== 'granted') return

  const response = await apiClient.get<ApiResponse<NotificationSettingsData>>('/users/me/notification-settings')
  if (response.data.data.enabled) await ensureWebPushSubscribed(false)
}

export async function disableWebPush() {
  if (!('serviceWorker' in navigator)) return
  const registration = await navigator.serviceWorker.getRegistration(serviceWorkerPath)
  const subscription = await registration?.pushManager.getSubscription()
  if (!subscription) return

  try {
    await apiClient.delete('/web-push/subscriptions', { data: { endpoint: subscription.endpoint } })
  } finally {
    await subscription.unsubscribe()
  }
}
