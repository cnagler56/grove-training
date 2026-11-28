'use client';

import { useEffect, useState } from 'react';
import { api, type HolidayChoice } from '@/src/lib/api';

const dateLabel = (iso: string) =>
  new Date(`${iso}T12:00:00`).toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });

/**
 * Owner Settings: holidays the salon is closed every year. Checked ones show as closed on
 * the time off calendar, are left out of the month's projected sales, and get a daily goal
 * of zero. One year can still be opened from the calendar.
 */
export default function HolidaysCard() {
  const [list, setList] = useState<HolidayChoice[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.holidays().then(setList).catch((e) => setError(e instanceof Error ? e.message : 'Could not load holidays'));
  }, []);

  async function toggle(h: HolidayChoice) {
    setBusy(true);
    setError(null);
    try {
      setList(await api.setHoliday(h.holiday, !h.on));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save');
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card mt-8 p-5">
      <h2 className="text-lg font-extrabold">Holidays the salon is closed</h2>
      <p className="mt-1 text-sm opacity-70">
        Check the holidays you close for every year. They show as closed on the time off calendar, are left out of the
        month&apos;s projected sales, and get a daily goal of zero. To open on one this year, or to close another day,
        click that day on the time off calendar.
      </p>
      {!list ? (
        <p className="mt-3 text-sm opacity-60">{error ?? 'Loading…'}</p>
      ) : (
        <ul className="mt-3 divide-y divide-[var(--border)]">
          {list.map((h) => (
            <li key={h.holiday}>
              <label className="flex cursor-pointer items-center justify-between gap-3 py-2 text-sm">
                <span className="flex items-center gap-2">
                  <input type="checkbox" className="accent-[var(--accent)]" checked={h.on} disabled={busy}
                    onChange={() => void toggle(h)} />
                  <span className="font-semibold">{h.label}</span>
                </span>
                <span className="text-xs opacity-60">Next: {dateLabel(h.next)}</span>
              </label>
            </li>
          ))}
        </ul>
      )}
      {error && list && <p className="mt-3 text-sm text-[var(--danger)]">{error}</p>}
    </section>
  );
}
