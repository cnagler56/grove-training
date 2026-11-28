'use client';

import { useEffect, useState } from 'react';
import { api, type ModCandidate } from '@/src/lib/api';
import { roleLabel } from '@/src/lib/roles';

/**
 * Owner Settings: who can be picked as manager on duty (MOD) in the schedule
 * editor's MOD row. Until the owner changes it, general managers and managers
 * are checked. Each click saves right away.
 */
export default function ManagerOnDutyCard() {
  const [people, setPeople] = useState<ModCandidate[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.modCandidates().then(setPeople).catch((e) => setError(e.message));
  }, []);

  async function toggle(p: ModCandidate) {
    setBusy(true);
    setError(null);
    try {
      setPeople(await api.setModCandidate(p.userId, !p.on));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save');
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card mt-8 p-5">
      <h2 className="text-lg font-extrabold">Who can be manager on duty</h2>
      <p className="mt-1 text-sm opacity-70">
        Check everyone who can be picked in the MOD row when the schedule is built. Days already saved keep their
        manager on duty if you uncheck someone later.
      </p>
      {!people ? (
        <p className="mt-3 text-sm opacity-60">{error ?? 'Loading…'}</p>
      ) : people.length === 0 ? (
        <p className="mt-3 text-sm opacity-60">No one is on the schedule yet.</p>
      ) : (
        <div className="mt-3 flex flex-wrap gap-2">
          {people.map((p) => (
            <label key={p.userId}
              className={`flex cursor-pointer select-none items-center gap-2 rounded-full border px-3.5 py-1.5 text-sm font-semibold ${
                p.on ? 'border-[var(--accent)] bg-[var(--accent-soft)] text-[var(--accent-strong)]' : 'border-[var(--border)]'
              }`}>
              <input type="checkbox" className="accent-[var(--accent)]" checked={p.on} disabled={busy} onChange={() => void toggle(p)} />
              {p.name}
              <span className="text-xs font-normal opacity-60">{roleLabel(p.role)}</span>
            </label>
          ))}
        </div>
      )}
      {error && people && <p className="mt-3 text-sm text-[var(--danger)]">{error}</p>}
    </section>
  );
}
