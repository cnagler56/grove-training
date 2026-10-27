'use client';

import { useEffect, useState } from 'react';
import { api, type TeamMember } from '@/src/lib/api';
import { employeeName } from '@/src/lib/format';
import { canManageSite, roleLabel } from '@/src/lib/roles';

/**
 * Manage videos: who can see one video. Everyone on staff, or only the people
 * ticked. Managers and up always see every video, so they aren't listed.
 */
export default function VideoAudienceEditor({ videoId, team, onSaved, onCancel }: {
  videoId: number;
  team: TeamMember[];
  onSaved: () => void;
  onCancel: () => void;
}) {
  const [everyone, setEveryone] = useState<boolean | null>(null);
  const [picked, setPicked] = useState<Set<number>>(new Set());
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.videoAudience(videoId)
      .then((a) => { setEveryone(a.everyone); setPicked(new Set(a.userIds)); })
      .catch((e) => setError(e.message));
  }, [videoId]);

  const people = team
    .filter((m) => m.active && m.role !== 'CUSTOMER' && !canManageSite(m.role))
    .sort((a, b) => employeeName(a).localeCompare(employeeName(b)));

  function toggle(id: number) {
    const next = new Set(picked);
    if (next.has(id)) next.delete(id); else next.add(id);
    setPicked(next);
  }

  async function save() {
    setSaving(true);
    setError(null);
    try {
      await api.setVideoAudience(videoId, { everyone: !!everyone, userIds: everyone ? [] : [...picked] });
      onSaved();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save');
    } finally {
      setSaving(false);
    }
  }

  if (everyone === null) return <p className="text-sm opacity-60">{error ?? 'Loading…'}</p>;

  return (
    <div className="space-y-3 rounded-xl bg-[var(--surface-2)] p-4">
      <div className="font-semibold">Who can see it</div>
      <div className="flex flex-wrap gap-4 text-sm">
        <label className="flex cursor-pointer items-center gap-2">
          <input type="radio" className="accent-[var(--accent)]" checked={everyone} onChange={() => setEveryone(true)} />
          Everyone on staff
        </label>
        <label className="flex cursor-pointer items-center gap-2">
          <input type="radio" className="accent-[var(--accent)]" checked={!everyone} onChange={() => setEveryone(false)} />
          Only certain people
        </label>
      </div>

      {!everyone && (
        <>
          <div className="flex flex-wrap items-center gap-3 text-xs">
            <span className="opacity-70">{picked.size} picked</span>
            <button type="button" className="font-semibold text-[var(--accent-strong)] hover:underline"
              onClick={() => setPicked(new Set(people.map((p) => p.userId)))}>All</button>
            <button type="button" className="font-semibold text-[var(--accent-strong)] hover:underline"
              onClick={() => setPicked(new Set())}>None</button>
          </div>
          <div className="grid gap-x-4 gap-y-1.5 text-sm sm:grid-cols-2 lg:grid-cols-3">
            {people.map((p) => (
              <label key={p.userId} className="flex cursor-pointer items-center gap-2">
                <input type="checkbox" className="accent-[var(--accent)]" checked={picked.has(p.userId)} onChange={() => toggle(p.userId)} />
                <span>{employeeName(p)} <span className="opacity-60">· {roleLabel(p.role)}</span></span>
              </label>
            ))}
          </div>
          <p className="text-xs opacity-60">Managers and up always see every video.</p>
        </>
      )}

      {error && <p className="text-sm text-[var(--danger)]">{error}</p>}
      <div className="flex gap-2">
        <button className="btn" onClick={() => void save()} disabled={saving || (!everyone && picked.size === 0)}>
          {saving ? 'Saving…' : 'Save'}
        </button>
        <button className="btn-ghost" onClick={onCancel}>Cancel</button>
      </div>
    </div>
  );
}
