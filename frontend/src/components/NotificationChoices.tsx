'use client';

import { useEffect, useState } from 'react';
import { api, type NotificationChoice, type NotificationKind } from '@/src/lib/api';

/**
 * My account: which kinds of notifications this person gets. Only the kinds that can
 * reach them are listed. Each switch saves right away, counts on every device they
 * use, and also keeps that kind off their Notifications list.
 */
export default function NotificationChoices() {
  const [choices, setChoices] = useState<NotificationChoice[] | null>(null);
  const [saving, setSaving] = useState<NotificationKind | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.notificationChoices()
      .then(setChoices)
      .catch((e) => setError(e instanceof Error ? e.message : 'Couldn\'t load your choices. Reload the page to try again.'));
  }, []);

  async function toggle(c: NotificationChoice) {
    setSaving(c.kind);
    setError(null);
    try {
      setChoices(await api.setNotificationChoice(c.kind, !c.on));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'That didn\'t save. Try again in a moment.');
    } finally {
      setSaving(null);
    }
  }

  if (!choices) return <p className="text-sm opacity-60">{error ?? 'Loading…'}</p>;
  if (choices.length === 0) return <p className="text-sm opacity-70">Nothing on the site sends you notifications.</p>;

  return (
    <div>
      <ul className="divide-y divide-[var(--border)]">
        {choices.map((c) => (
          <li key={c.kind} className="flex items-center justify-between gap-4 py-3">
            <div className="min-w-0">
              <div className="text-sm font-semibold">{c.label}</div>
              <div className="text-xs opacity-70">{c.description}</div>
            </div>
            <button
              type="button"
              role="switch"
              aria-checked={c.on}
              aria-label={c.label}
              disabled={saving !== null}
              onClick={() => void toggle(c)}
              className={`w-16 shrink-0 rounded-full border px-3 py-1 text-sm font-semibold disabled:opacity-60 ${
                c.on
                  ? 'border-[var(--accent)] bg-[var(--accent)] text-[var(--accent-ink)]'
                  : 'border-[var(--border)] bg-[var(--surface)]'
              }`}
            >
              {c.on ? 'On' : 'Off'}
            </button>
          </li>
        ))}
      </ul>
      {error && <p className="mt-2 text-sm text-[var(--danger)]">{error}</p>}
    </div>
  );
}
