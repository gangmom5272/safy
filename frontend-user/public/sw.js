self.addEventListener('push', (event) => {
  const fallback = {
    title: 'SAFY',
    body: '새로운 알림이 도착했습니다.',
    linkUrl: '/app/notifications',
  }

  let payload = fallback
  try {
    payload = event.data ? event.data.json() : fallback
  } catch {
    payload = fallback
  }

  event.waitUntil(self.registration.showNotification(payload.title || fallback.title, {
    body: payload.body || fallback.body,
    icon: '/favicon.svg',
    badge: '/favicon.svg',
    tag: payload.notificationId ? `safy-notification-${payload.notificationId}` : undefined,
    data: {
      linkUrl: payload.linkUrl || fallback.linkUrl,
      notificationId: payload.notificationId,
    },
  }))
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const linkUrl = event.notification.data?.linkUrl || '/app/notifications'
  const targetUrl = new URL(linkUrl, self.location.origin).href

  event.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clients) => {
    for (const client of clients) {
      if ('navigate' in client && 'focus' in client) {
        return client.navigate(targetUrl).then(() => client.focus())
      }
    }
    return self.clients.openWindow ? self.clients.openWindow(targetUrl) : undefined
  }))
})
