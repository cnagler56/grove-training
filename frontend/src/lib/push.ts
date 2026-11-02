import { api } from './api';

/**
 * What this device can do about notifications:
 * - ready: can turn them on
 * - needs-install: an iPhone or iPad in the browser; Apple only allows them from the Home Screen app
 * - unsupported: this browser can't receive them
 */
export type PushSupport = 'ready' | 'needs-install' | 'unsupported';

export function pushSupport(): PushSupport {
  const nav = navigator as Navigator & { standalone?: boolean };
  const apple = /iPhone|iPad|iPod/.test(navigator.userAgent) || (/Macintosh/.test(navigator.userAgent) && navigator.maxTouchPoints > 1);
  const installed = window.matchMedia('(display-mode: standalone)').matches || nav.standalone === true;
  if (apple && !installed) return 'needs-install';
  return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window ? 'ready' : 'unsupported';
}

function keyBytes(base64url: string) {
  const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(base64url.length / 4) * 4, '=');
  return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
}

/**
 * Take this app's notifications off the phone's notification tray and the count on
 * its icon: the app has opened, and the Notifications list has them all.
 */
export async function clearDelivered() {
  if (!('serviceWorker' in navigator)) return;
  const registration = await navigator.serviceWorker.getRegistration('/');
  if (registration) (await registration.getNotifications()).forEach((n) => n.close());
  const nav = navigator as Navigator & { clearAppBadge?: () => Promise<void> };
  await nav.clearAppBadge?.();
}

/** This device's existing subscription, if it has one. */
export async function currentSubscription() {
  if (pushSupport() !== 'ready') return null;
  const registration = await navigator.serviceWorker.getRegistration('/');
  return registration ? registration.pushManager.getSubscription() : null;
}

/** Whether a subscription was made with this server key. One made with an old key can never receive anything. */
export function usesKey(subscription: PushSubscription, publicKey: string) {
  const current = subscription.options.applicationServerKey;
  if (!current) return true;
  const a = new Uint8Array(current);
  const b = keyBytes(publicKey);
  return a.length === b.length && a.every((v, i) => v === b[i]);
}

/** Tell the server about this device, under whoever is signed in now. */
export async function saveSubscription(subscription: PushSubscription) {
  const keys = subscription.toJSON().keys ?? {};
  await api.pushSubscribe({ endpoint: subscription.endpoint, p256dh: keys.p256dh ?? '', auth: keys.auth ?? '' });
}

/** Ask permission and turn notifications on. Must be called right from a tap: phones refuse otherwise. */
export async function turnOnPush(publicKey: string): Promise<'on' | 'denied'> {
  const permission = await Notification.requestPermission();
  if (permission !== 'granted') return 'denied';
  const registration = await navigator.serviceWorker.register('/sw.js');
  await navigator.serviceWorker.ready;
  const subscription = await registration.pushManager.getSubscription()
    ?? await registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: keyBytes(publicKey) });
  await saveSubscription(subscription);
  return 'on';
}

/** Turn notifications off for this device. Also used at sign-out, so a shared phone stops getting them. */
export async function turnOffPush() {
  const subscription = await currentSubscription();
  if (!subscription) return;
  await api.pushUnsubscribe(subscription.endpoint).catch(() => {});
  await subscription.unsubscribe();
}
