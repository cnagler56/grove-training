// Shows phone notifications sent by the Grove Salons site, and opens the right
// page when one is tapped. It doesn't cache anything or touch normal page loads.

self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()));

self.addEventListener('push', (event) => {
  let message = {};
  try {
    message = event.data ? event.data.json() : {};
  } catch {
    // Phones require something be shown for every push, so fall through to the default.
  }
  event.waitUntil(self.registration.showNotification(message.title || 'Grove Salons', {
    body: message.body || '',
    icon: '/app-icon/192',
    badge: '/app-icon/192',
    data: { url: message.url || '/home' },
  }));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = event.notification.data && event.notification.data.url ? event.notification.data.url : '/home';
  event.waitUntil((async () => {
    const open = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    if (open.length > 0) {
      await open[0].focus();
      if ('navigate' in open[0]) await open[0].navigate(url);
      return;
    }
    await self.clients.openWindow(url);
  })());
});
