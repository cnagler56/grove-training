'use client';

import { useEffect, useState } from 'react';
import { api } from '@/src/lib/api';
import { currentSubscription, pushSupport, saveSubscription, turnOffPush, turnOnPush, usesKey } from '@/src/lib/push';

const DISMISSED_KEY = 'grove.notifications.dismissed';

type State = 'checking' | 'hidden' | 'off' | 'on' | 'denied' | 'needs-install';

/**
 * Home page: turn phone notifications on or off for this device. Each person
 * has to tap once, because phones only ask permission after a tap. Hidden when
 * the server has no push keys, the browser can't do it, or after "Not now".
 */
export default function NotificationsCard({ always = false }: { always?: boolean }) {
  const [state, setState] = useState<State>('checking');
  const [unavailable, setUnavailable] = useState('');
  const [publicKey, setPublicKey] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [testResult, setTestResult] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const { publicKey: key } = await api.pushKey();
      const support = pushSupport();
      let next: State;
      if (!key || support === 'unsupported') {
        next = 'hidden';
        setUnavailable(!key ? 'Notifications aren\'t set up on the server yet.' : 'This browser can\'t receive notifications.');
      } else if (support === 'needs-install') {
        next = 'needs-install';
      } else if (Notification.permission === 'denied') {
        next = 'denied';
      } else {
        const existing = await currentSubscription();
        if (existing && !usesKey(existing, key)) {
          // Made with an old key, so it can't receive anything; the next turn-on makes a new one.
          await turnOffPush();
          next = 'off';
        } else if (existing && Notification.permission === 'granted') {
          // Keeps the device tied to whoever is signed in now.
          await saveSubscription(existing);
          next = 'on';
        } else {
          next = 'off';
        }
      }
      let dismissed = false;
      try { dismissed = localStorage.getItem(DISMISSED_KEY) === '1'; } catch { /* private mode: just show it */ }
      if (dismissed && !always && next !== 'on') next = 'hidden';
      if (!cancelled) {
        setPublicKey(key);
        setState(next);
      }
    })().catch(() => {
      if (!cancelled) {
        setUnavailable('Couldn\'t check notifications on this device. Reload the page to try again.');
        setState('hidden');
      }
    });
    return () => { cancelled = true; };
  }, [always]);

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

  /** Send a test and say plainly what the push service answered. */
  async function sendTest() {
    setBusy(true);
    setError(null);
    setTestResult(null);
    try {
      const results = await api.pushTest();
      setTestResult(results.length === 0
        ? 'The server has no device saved for you. Turn notifications off and on again.'
        : results.map((r) => {
          if (r.status >= 200 && r.status < 300) return `${r.service} accepted it. It should appear within a few seconds. If it doesn't, your phone is holding it back: check this app's notification settings and Focus or Do Not Disturb.`;
          if (r.status === 404 || r.status === 410) return `${r.service} says this device's address has expired. Turn notifications off and on again.`;
          if (r.status === 0) return `The server couldn't reach ${r.service}: ${r.answer}`;
          return `${r.service} refused it (${r.status}): ${r.answer}`;
        }).join(' '));
    } catch {
      setError('The test didn\'t go through. Try again in a moment.');
    } finally {
      setBusy(false);
    }
  }

  async function turnOff() {
    setBusy(true);
    setError(null);
    setTestResult(null);
    try {
      await turnOffPush();
      setState('off');
    } catch {
      setError('That didn\'t work on this device. Try again in a moment.');
    } finally {
      setBusy(false);
    }
  }

  if (state === 'checking') return null;
  if (state === 'hidden') {
    // A "Not now" on Home never hides this on My account, so there's always a way back.
    return always ? <p className="text-sm opacity-70">{unavailable}</p> : null;
  }

  if (state === 'on') {
    return (
      <div className={`${always ? 'text-sm' : 'text-right text-xs'} md:col-span-2`}>
        <p className="opacity-70">
          Notifications are on for this device.{' '}
          <button className="font-semibold underline" onClick={() => void sendTest()} disabled={busy}>{busy ? 'Working…' : 'Send a test'}</button>{' '}
          <button className="font-semibold underline" onClick={() => void turnOff()} disabled={busy}>Turn off</button>
        </p>
        {testResult && <p className="mt-1">{testResult}</p>}
        {error && <p className="mt-1 text-[var(--danger)]">{error}</p>}
      </div>
    );
  }

  return (
    <section className={`flex flex-wrap items-center justify-between gap-3 md:col-span-2 ${always ? '' : 'card p-4'}`}>
      <div className="min-w-0 text-sm">
        {!always && <div className="font-bold">Get notifications on this device</div>}
        <div className="opacity-70">
          {state === 'off' && 'Hear about new schedules, time off answers, team board posts, and videos assigned to you.'}
          {state === 'needs-install' && 'On iPhone and iPad, add this site to your Home Screen first, open it from there, and then turn them on. The steps are on the Home page.'}
          {state === 'denied' && 'Notifications are blocked for this site. Allow them in your device or browser settings, then reload this page.'}
        </div>
        {error && <div className="mt-1 text-[var(--danger)]">{error}</div>}
      </div>
      <div className="flex shrink-0 items-center gap-3">
        {state === 'off' && (
          <button className="btn" onClick={() => void turnOn()} disabled={busy}>{busy ? 'Turning on…' : 'Turn on notifications'}</button>
        )}
        {!always && <button className="text-sm font-semibold opacity-60 hover:opacity-100" onClick={notNow}>Not now</button>}
      </div>
    </section>
  );
}
