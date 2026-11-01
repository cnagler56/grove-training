'use client';

import { useEffect, useState } from 'react';
import { api } from '@/src/lib/api';
import { currentSubscription, pushSupport, saveSubscription, turnOffPush, turnOnPush } from '@/src/lib/push';

const DISMISSED_KEY = 'grove.notifications.dismissed';

type State = 'checking' | 'hidden' | 'off' | 'on' | 'denied' | 'needs-install';

/**
 * Home page: turn phone notifications on or off for this device. Each person
 * has to tap once, because phones only ask permission after a tap. Hidden when
 * the server has no push keys, the browser can't do it, or after "Not now".
 */
export default function NotificationsCard() {
  const [state, setState] = useState<State>('checking');
  const [publicKey, setPublicKey] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const { publicKey: key } = await api.pushKey();
      const support = pushSupport();
      let next: State;
      if (!key || support === 'unsupported') {
        next = 'hidden';
      } else if (support === 'needs-install') {
        next = 'needs-install';
      } else if (Notification.permission === 'denied') {
        next = 'denied';
      } else {
        const existing = await currentSubscription();
        if (existing) {
          // Keeps the device tied to whoever is signed in now.
          await saveSubscription(existing);
          next = 'on';
        } else {
          next = 'off';
        }
      }
      let dismissed = false;
      try { dismissed = localStorage.getItem(DISMISSED_KEY) === '1'; } catch { /* private mode: just show it */ }
      if (dismissed && next !== 'on') next = 'hidden';
      if (!cancelled) {
        setPublicKey(key);
        setState(next);
      }
    })().catch(() => { if (!cancelled) setState('hidden'); });
    return () => { cancelled = true; };
  }, []);

  function notNow() {
    try { localStorage.setItem(DISMISSED_KEY, '1'); } catch { /* it just shows again next time */ }
    setState('hidden');
  }

  async function turnOn() {
    setBusy(true);
    setError(null);
    try {
      setState(await turnOnPush(publicKey));
    } catch {
      setError('That didn\'t work on this device. Try again in a moment.');
    } finally {
      setBusy(false);
    }
  }

  async function turnOff() {
    setBusy(true);
    setError(null);
    try {
      await turnOffPush();
      setState('off');
    } catch {
      setError('That didn\'t work on this device. Try again in a moment.');
    } finally {
      setBusy(false);
    }
  }

  if (state === 'checking' || state === 'hidden') return null;

  if (state === 'on') {
    return (
      <p className="text-right text-xs opacity-70 md:col-span-2">
        Notifications are on for this device.{' '}
        <button className="font-semibold underline" onClick={() => void turnOff()} disabled={busy}>Turn off</button>
        {error && <span className="ml-2 text-[var(--danger)]">{error}</span>}
      </p>
    );
  }

  return (
    <section className="card flex flex-wrap items-center justify-between gap-3 p-4 md:col-span-2">
      <div className="min-w-0 text-sm">
        <div className="font-bold">Get notifications on this device</div>
        <div className="opacity-70">
          {state === 'off' && 'Hear about new schedules, time off answers, team board posts, and videos assigned to you.'}
          {state === 'needs-install' && 'On iPhone and iPad, add this site to your Home Screen first, open it from there, and then turn them on. The steps are on this page.'}
          {state === 'denied' && 'Notifications are blocked for this site. Allow them in your device or browser settings, then reload this page.'}
        </div>
        {error && <div className="mt-1 text-[var(--danger)]">{error}</div>}
      </div>
      <div className="flex shrink-0 items-center gap-3">
        {state === 'off' && (
          <button className="btn" onClick={() => void turnOn()} disabled={busy}>{busy ? 'Turning on…' : 'Turn on notifications'}</button>
        )}
        <button className="text-sm font-semibold opacity-60 hover:opacity-100" onClick={notNow}>Not now</button>
      </div>
    </section>
  );
}
