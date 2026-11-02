'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { api, type AppNotification } from '@/src/lib/api';
import { formatDateTime } from '@/src/lib/format';

/** Tells the header to refresh its unread count. */
const changed = () => window.dispatchEvent(new Event('notifications-changed'));

/**
 * Everything the app has told you over the last 30 days, newest first. Tapping one
 * opens its page; opening that page any other way marks it read too.
 */
export default function NotificationsPage() {
  const router = useRouter();
  const [list, setList] = useState<AppNotification[] | null>(null);
  const [unread, setUnread] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    api.notifications()
      .then((r) => { setList(r.notifications); setUnread(r.unread); })
      .catch((e) => setError(e.message));
  }, []);
  useEffect(() => { load(); }, [load]);

  async function open(n: AppNotification) {
    if (!n.read) await api.readNotification(n.id).catch(() => {});
    changed();
    router.push(n.url);
  }

  async function readAll() {
    setError(null);
    try {
      await api.readAllNotifications();
      changed();
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not mark them read');
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight">Notifications</h1>
          <p className="mt-1 text-sm opacity-70">What the app has told you over the last 30 days. Tap one to open it.</p>
        </div>
        {unread > 0 && <button className="btn-ghost" onClick={() => void readAll()}>Mark all read</button>}
      </div>

      {error && <p className="text-sm text-[var(--danger)]">{error}</p>}

      {list === null ? (
        !error && <p className="opacity-60">Loading…</p>
      ) : list.length === 0 ? (
        <p className="card p-5 text-sm opacity-80">Nothing yet. When the app tells you something, it shows up here too.</p>
      ) : (
        <ul className="card divide-y divide-[var(--border)] overflow-hidden">
          {list.map((n) => (
            <li key={n.id}>
              <button
                className={`block w-full px-5 py-3.5 text-left hover:bg-[var(--surface-2)] ${n.read ? '' : 'bg-[var(--accent-soft)]'}`}
                onClick={() => void open(n)}
              >
                <span className="flex flex-wrap items-baseline justify-between gap-x-3">
                  <span className={`break-words ${n.read ? 'font-semibold' : 'font-extrabold'}`}>
                    {!n.read && <span className="mr-2 text-xs font-bold uppercase text-[var(--accent-strong)]">New</span>}
                    {n.title}
                  </span>
                  <span className="text-xs opacity-60">{formatDateTime(n.createdAt)}</span>
                </span>
                {n.body && <span className="mt-0.5 block break-words text-sm opacity-80">{n.body}</span>}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
