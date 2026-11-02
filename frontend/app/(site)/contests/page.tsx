'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, type Contest, type ContestInput, type ContestMetric } from '@/src/lib/api';
import { canManageSite } from '@/src/lib/roles';
import { useUser } from '@/src/lib/UserContext';
import { useDialogs } from '@/src/components/Dialogs';
import DateField from '@/src/components/DateField';
import ContestCard from '@/src/components/ContestCard';

const METRICS: { value: ContestMetric; label: string }[] = [
  { value: 'TREATMENTS', label: 'Treatments' },
  { value: 'COLORS', label: 'Colors' },
  { value: 'WAXES', label: 'Waxes' },
];
const MAX_TEAMS = 4;

type Form = {
  name: string;
  metric: ContestMetric;
  startDate: string | null;
  endDate: string | null;
  prize: string;
  useTeams: boolean;
  teamNames: string[];
  /** Who's in, and their team (null without teams). */
  picked: Record<number, number | null>;
};

const empty = (): Form => ({
  name: '', metric: 'TREATMENTS', startDate: null, endDate: null, prize: '',
  useTeams: false, teamNames: ['Team 1', 'Team 2'], picked: {},
});

function fromContest(c: Contest): Form {
  return {
    name: c.name,
    metric: c.metric,
    startDate: c.startDate,
    endDate: c.endDate,
    prize: c.prize ?? '',
    useTeams: c.teams.length > 0,
    teamNames: c.teams.length > 0 ? c.teams.map((t) => t.name) : ['Team 1', 'Team 2'],
    picked: Object.fromEntries(c.standings.map((s) => [s.userId, s.team])),
  };
}

/**
 * Contests: who can do the most treatments, colors, or waxes, counted from Salon
 * Ultimate every 4 hours, alone or in teams. Everyone on staff sees the standings;
 * managers and up start, change, and remove contests.
 */
export default function ContestsPage() {
  const { user } = useUser();
  const dialogs = useDialogs();
  const isManager = canManageSite(user?.roles);
  const [contests, setContests] = useState<Contest[] | null>(null);
  const [people, setPeople] = useState<{ userId: number; name: string }[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  // null = form closed; 'new' = starting one; a number = changing that contest.
  const [editing, setEditing] = useState<number | 'new' | null>(null);
  const [form, setForm] = useState<Form>(empty);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    api.contests().then(setContests).catch((e) => setError(e.message));
  }, []);
  useEffect(() => {
    load();
    if (isManager) api.contestPeople().then(setPeople).catch((e) => setError(e.message));
  }, [load, isManager]);

  function open(which: number | 'new', f: Form) {
    setForm(f);
    setEditing(which);
    setError(null);
    setNotice(null);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async function run(action: () => Promise<unknown>, done: string) {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      await action();
      setNotice(done);
      load();
      return true;
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong');
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (editing === null) return;
    const input: ContestInput = {
      name: form.name,
      metric: form.metric,
      startDate: form.startDate,
      endDate: form.endDate,
      prize: form.prize.trim() || null,
      teams: form.useTeams ? form.teamNames : [],
      people: Object.entries(form.picked).map(([id, team]) => ({ userId: Number(id), team: form.useTeams ? team : null })),
    };
    const saved = await run(
      () => (editing === 'new' ? api.createContest(input) : api.updateContest(editing, input)),
      editing === 'new' ? `Started ${form.name.trim()}.` : `Saved ${form.name.trim()}.`,
    );
    if (saved) setEditing(null);
  }

  async function remove(c: Contest) {
    if (!(await dialogs.confirm({
      title: `Delete ${c.name}?`,
      message: 'Its standings go with it. This can\'t be undone.',
      confirmLabel: 'Delete',
      danger: true,
    }))) return;
    if (editing === c.id) setEditing(null);
    void run(() => api.deleteContest(c.id), `Deleted ${c.name}.`);
  }

  const current = (contests ?? []).filter((c) => c.status !== 'FINISHED');
  const past = (contests ?? []).filter((c) => c.status === 'FINISHED');

  const actions = (c: Contest) => isManager && (
    <>
      {(c.status === 'LIVE' || c.status === 'ENDED') && (
        <button className="btn-ghost" disabled={busy} onClick={() => void run(() => api.checkContest(c.id), 'Checked Salon Ultimate.')}>
          Check now
        </button>
      )}
      {c.status !== 'FINISHED' && <button className="btn-ghost" onClick={() => open(c.id, fromContest(c))}>Edit</button>}
      <button className="text-sm font-bold text-[var(--danger)] hover:underline" onClick={() => void remove(c)}>Delete</button>
    </>
  );

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight">Contests</h1>
          <p className="mt-1 text-sm opacity-70">Who can do the most? Counts come from Salon Ultimate every 4 hours.</p>
        </div>
        {isManager && editing === null && <button className="btn" onClick={() => open('new', empty())}>New contest</button>}
      </div>

      {error && <p className="text-sm text-[var(--danger)]">{error}</p>}
      {notice && <p className="text-sm font-semibold text-[var(--success)]">{notice}</p>}

      {editing !== null && (
        <ContestForm form={form} setForm={setForm} people={people} busy={busy} isNew={editing === 'new'}
          onSubmit={save} onCancel={() => setEditing(null)} />
      )}

      {contests === null ? (
        !error && <p className="opacity-60">Loading…</p>
      ) : current.length === 0 && past.length === 0 ? (
        <p className="card p-5 text-sm opacity-80">
          No contests yet.{isManager ? ' Use New contest to start one.' : ' When one starts, the standings show up here.'}
        </p>
      ) : (
        <>
          {current.map((c) => <ContestCard key={c.id} contest={c} viewerId={user?.userId} actions={actions(c)} />)}
          {past.length > 0 && (
            <div className="space-y-4">
              <h2 className="text-lg font-extrabold">Past contests</h2>
              {past.map((c) => <ContestCard key={c.id} contest={c} viewerId={user?.userId} actions={actions(c)} />)}
            </div>
          )}
        </>
      )}
    </div>
  );
}

function ContestForm({ form, setForm, people, busy, isNew, onSubmit, onCancel }: {
  form: Form;
  setForm: (f: Form) => void;
  people: { userId: number; name: string }[] | null;
  busy: boolean;
  isNew: boolean;
  onSubmit: (e: React.FormEvent) => void;
  onCancel: () => void;
}) {
  const pickedIds = Object.keys(form.picked).map(Number);

  function toggle(userId: number) {
    const next = { ...form.picked };
    if (userId in next) delete next[userId];
    else next[userId] = form.useTeams ? 0 : null;
    setForm({ ...form, picked: next });
  }

  function setTeams(useTeams: boolean) {
    // Turning teams on puts everyone already picked on the first team.
    const picked = Object.fromEntries(pickedIds.map((id) => [id, useTeams ? form.picked[id] ?? 0 : null]));
    setForm({ ...form, useTeams, picked });
  }

  function renameTeam(i: number, name: string) {
    setForm({ ...form, teamNames: form.teamNames.map((t, j) => (j === i ? name : t)) });
  }

  function removeTeam(i: number) {
    // People on a removed team move to the first one; those after it shift down a place.
    const picked = Object.fromEntries(pickedIds.map((id) => {
      const t = form.picked[id] ?? 0;
      return [id, t === i ? 0 : t > i ? t - 1 : t];
    }));
    setForm({ ...form, teamNames: form.teamNames.filter((_, j) => j !== i), picked });
  }

  /** Shuffle everyone picked and deal them out, so team sizes differ by at most one. */
  function splitEvenly() {
    const shuffled = [...pickedIds].sort(() => Math.random() - 0.5);
    setForm({ ...form, picked: Object.fromEntries(shuffled.map((id, i) => [id, i % form.teamNames.length])) });
  }

  return (
    <form onSubmit={onSubmit} className="card space-y-5 p-5">
      <h2 className="text-lg font-extrabold">{isNew ? 'Start a contest' : `Change ${form.name || 'contest'}`}</h2>

      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-sm">
          <span className="mb-1 block font-semibold opacity-80">Name</span>
          <input className="input" value={form.name} required maxLength={80} autoFocus placeholder="Treatment Blitz"
            onChange={(e) => setForm({ ...form, name: e.target.value })} />
        </label>
        <label className="block text-sm">
          <span className="mb-1 block font-semibold opacity-80">Who does the most</span>
          <select className="input cursor-pointer" value={form.metric}
            onChange={(e) => setForm({ ...form, metric: e.target.value as ContestMetric })}>
            {METRICS.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
          </select>
        </label>
        <DateField label="First day" value={form.startDate} onChange={(startDate) => setForm({ ...form, startDate })} />
        <DateField label="Last day" value={form.endDate} onChange={(endDate) => setForm({ ...form, endDate })} />
      </div>
      <label className="block text-sm">
        <span className="mb-1 block font-semibold opacity-80">Prize (optional)</span>
        <input className="input" value={form.prize} maxLength={200} placeholder="$50 gift card"
          onChange={(e) => setForm({ ...form, prize: e.target.value })} />
      </label>
      {!isNew && <p className="text-xs opacity-60">Changing what&apos;s counted or the first day starts the standings over at the next check.</p>}

      <fieldset className="space-y-3 text-sm">
        <label className="flex cursor-pointer items-center gap-2 font-semibold">
          <input type="checkbox" className="h-5 w-5 accent-[var(--accent)]" checked={form.useTeams}
            onChange={(e) => setTeams(e.target.checked)} />
          Split people into teams
        </label>
        {form.useTeams && (
          <div className="space-y-2">
            {form.teamNames.map((t, i) => (
              <div key={i} className="flex items-center gap-2">
                <input className="input max-w-xs" value={t} maxLength={40} required aria-label={`Team ${i + 1} name`}
                  onChange={(e) => renameTeam(i, e.target.value)} />
                {form.teamNames.length > 2 && (
                  <button type="button" className="text-sm font-bold text-[var(--danger)] hover:underline" onClick={() => removeTeam(i)}>
                    Remove
                  </button>
                )}
              </div>
            ))}
            <div className="flex flex-wrap gap-2">
              {form.teamNames.length < MAX_TEAMS && (
                <button type="button" className="btn-ghost"
                  onClick={() => setForm({ ...form, teamNames: [...form.teamNames, `Team ${form.teamNames.length + 1}`] })}>
                  Add a team
                </button>
              )}
              <button type="button" className="btn-ghost" disabled={pickedIds.length < 2} onClick={splitEvenly}>
                Split evenly at random
              </button>
            </div>
          </div>
        )}
      </fieldset>

      <fieldset className="text-sm">
        <div className="mb-2 flex flex-wrap items-center gap-3">
          <legend className="font-semibold opacity-80">Who&apos;s in ({pickedIds.length})</legend>
          {people && people.length > 0 && (
            <>
              <button type="button" className="text-sm font-semibold text-[var(--accent-strong)] hover:underline"
                onClick={() => setForm({ ...form, picked: Object.fromEntries(people.map((p) => [p.userId, form.picked[p.userId] ?? (form.useTeams ? 0 : null)])) })}>
                Everyone
              </button>
              <button type="button" className="text-sm font-semibold text-[var(--accent-strong)] hover:underline"
                onClick={() => setForm({ ...form, picked: {} })}>
                No one
              </button>
            </>
          )}
        </div>
        {people === null ? (
          <p className="opacity-60">Loading…</p>
        ) : people.length === 0 ? (
          <p className="opacity-80">No one is linked to Salon Ultimate yet. Link people on the Team page first.</p>
        ) : (
          <ul className="grid gap-x-4 sm:grid-cols-2">
            {people.map((p) => {
              const inIt = p.userId in form.picked;
              return (
                <li key={p.userId} className="flex flex-wrap items-center justify-between gap-2 py-1.5">
                  <label className="flex min-w-0 cursor-pointer items-center gap-2">
                    <input type="checkbox" className="h-5 w-5 shrink-0 accent-[var(--accent)]" checked={inIt} onChange={() => toggle(p.userId)} />
                    <span className="break-words font-semibold">{p.name}</span>
                  </label>
                  {inIt && form.useTeams && (
                    <select className="input w-auto cursor-pointer py-1" aria-label={`${p.name}'s team`}
                      value={form.picked[p.userId] ?? 0}
                      onChange={(e) => setForm({ ...form, picked: { ...form.picked, [p.userId]: Number(e.target.value) } })}>
                      {form.teamNames.map((t, i) => <option key={i} value={i}>{t || `Team ${i + 1}`}</option>)}
                    </select>
                  )}
                </li>
              );
            })}
          </ul>
        )}
        <p className="mt-2 text-xs opacity-60">Only people linked to their Salon Ultimate name can be in a contest.</p>
      </fieldset>

      <div className="flex flex-wrap gap-2">
        <button type="submit" className="btn" disabled={busy}>{busy ? 'Saving…' : isNew ? 'Start contest' : 'Save changes'}</button>
        <button type="button" className="btn-ghost" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}
