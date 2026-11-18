'use client';

import { useEffect, useState } from 'react';
import { api, type SummaryRecipient } from '@/src/lib/api';

/**
 * Owner Settings: which owners get the daily sales summary on their phone, 30 minutes
 * after closing. Everyone starts on. The Business card on Home stays either way.
 */
export default function SalesSummaryCard() {
  const [people, setPeople] = useState<SummaryRecipient[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.salesSummaryRecipients().then(setPeople).catch((e) => setError(e.message));
  }, []);

  async function toggle(p: SummaryRecipient) {
    setBusy(true);
    setError(null);
    try {
      setPeople(await api.setSalesSummaryRecipient(p.userId, !p.on));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save');
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card mt-8 p-5">
      <h2 className="text-lg font-extrabold">Daily sales summary</h2>
      <p className="mt-1 text-sm opacity-70">
        A notification 30 minutes after closing with that day&apos;s sales. Turn it off for anyone who doesn&apos;t want it;
        the Business card on Home stays either way.
      </p>
      {!people ? (
        <p className="mt-3 text-sm opacity-60">{error ?? 'Loading…'}</p>
      ) : (
        <div className="mt-3 flex flex-wrap gap-2">
          {people.map((p) => (
            <label key={p.userId}
              className={`flex cursor-pointer select-none items-center gap-2 rounded-full border px-3.5 py-1.5 text-sm font-semibold ${
                p.on ? 'border-[var(--accent)] bg-[var(--accent-soft)] text-[var(--accent-strong)]' : 'border-[var(--border)]'
              }`}>
              <input type="checkbox" className="accent-[var(--accent)]" checked={p.on} disabled={busy} onChange={() => void toggle(p)} />
              {p.name}
            </label>
          ))}
        </div>
      )}
      {error && people && <p className="mt-3 text-sm text-[var(--danger)]">{error}</p>}
    </section>
  );
}
