'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, type ContestHandicap, type TrailGame, type TrailGameInput } from '@/src/lib/api';
import { canManageSite } from '@/src/lib/roles';
import { useUser } from '@/src/lib/UserContext';
import { useDialogs } from '@/src/components/Dialogs';
import DateField from '@/src/components/DateField';
import TrailGameCard from '@/src/components/TrailGameCard';

const HANDICAPS: { value: ContestHandicap; label: string; help: string }[] = [
  { value: 'NONE', label: 'None', help: 'Every dollar of sales is 1 coin for everyone.' },
  { value: 'HOURS', label: 'Hours worked', help: 'People who work fewer hours than the group\'s average get more coins per dollar, up to double.' },
  { value: 'RATE', label: 'Usual sales per hour', help: 'People whose usual sales per hour are below the group\'s average get more coins per dollar, up to double.' },
  { value: 'BOTH', label: 'Hours and sales per hour', help: 'Both handicaps together, still at most double.' },
];

type Form = {
  name: string;
  startDate: string | null;
  turns: number;
  prize: string;
  handicap: ContestHandicap;
  picked: number[];
  bonusTreatment: number;
  bonusColor: number;
  bonusWax: number;
};

const empty = (): Form => ({
  name: 'The Grove Trail', startDate: null, turns: 40, prize: '', handicap: 'NONE', picked: [],
  bonusTreatment: 25, bonusColor: 20, bonusWax: 15,
});

/**
 * The Grove Trail: an Oregon Trail style race from Maple Grove to the Oregon Coast,
 * paid for with coins from service sales. Players see the map, their party, the
 * turn's scenario, and the shop; managers and up start games.
 */
export default function TrailPage() {
  const { user } = useUser();
  const dialogs = useDialogs();
  const isManager = canManageSite(user?.roles);
  const [games, setGames] = useState<TrailGame[] | null>(null);
  const [people, setPeople] = useState<{ userId: number; name: string }[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [form, setForm] = useState<Form | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    api.trailGames().then(setGames).catch((e) => setError(e.message));
  }, []);
  useEffect(() => {
    load();
    if (isManager) api.trailPeople().then(setPeople).catch((e) => setError(e.message));
  }, [load, isManager]);

  /** Swap in a game the server just sent back. */
  const replace = (g: TrailGame) => setGames((list) => (list ?? []).map((x) => (x.id === g.id ? g : x)));

  async function create(e: React.FormEvent) {
    e.preventDefault();
    if (!form) return;
    setBusy(true);
    setError(null);
    setNotice(null);
    const input: TrailGameInput = {
      name: form.name,
      startDate: form.startDate,
      turns: form.turns,
      prize: form.prize.trim() || null,
      handicap: form.handicap,
      userIds: form.picked,
      bonusTreatment: form.bonusTreatment,
      bonusColor: form.bonusColor,
      bonusWax: form.bonusWax,
    };
    try {
      await api.createTrailGame(input);
      setNotice(`Started ${form.name.trim()}.`);
      setForm(null);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong');
    } finally {
      setBusy(false);
    }
  }

  async function remove(g: TrailGame) {
    if (!(await dialogs.confirm({
      title: `Delete ${g.name}?`,
      message: 'Everyone\'s progress, coins, and the whole log go with it. This can\'t be undone.',
      confirmLabel: 'Delete',
      danger: true,
    }))) return;
    try {
      await api.deleteTrailGame(g.id);
      setNotice(`Deleted ${g.name}.`);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong');
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight">The Grove Trail</h1>
          <p className="mt-1 text-sm opacity-70">
            From the salon in Maple Grove to the Oregon Coast, paid for with your service sales. A turn runs at every Salon Ultimate check.
          </p>
        </div>
        {isManager && form === null && <button className="btn" onClick={() => setForm(empty())}>New game</button>}
      </div>

      {error && <p className="text-sm text-[var(--danger)]">{error}</p>}
      {notice && <p className="text-sm font-semibold text-[var(--success)]">{notice}</p>}

      {form && (
        <GameForm form={form} setForm={setForm} people={people} busy={busy} onSubmit={create} onCancel={() => setForm(null)} />
      )}

      {games === null ? (
        !error && <p className="opacity-60">Loading…</p>
      ) : games.length === 0 ? (
        <p className="card p-5 text-sm opacity-80">
          No one is on the trail yet.{isManager ? ' Use New game to start one.' : ' When a game starts, it shows up here.'}
        </p>
      ) : (
        games.map((g) => (
          <TrailGameCard key={g.id} game={g} viewerId={user?.userId} isManager={isManager}
            onChange={replace} onDelete={() => void remove(g)} />
        ))
      )}
    </div>
  );
}

function GameForm({ form, setForm, people, busy, onSubmit, onCancel }: {
  form: Form;
  setForm: (f: Form) => void;
  people: { userId: number; name: string }[] | null;
  busy: boolean;
  onSubmit: (e: React.FormEvent) => void;
  onCancel: () => void;
}) {
  const toggle = (id: number) =>
    setForm({ ...form, picked: form.picked.includes(id) ? form.picked.filter((x) => x !== id) : [...form.picked, id] });
  const days = Math.ceil(form.turns / 4);

  return (
    <form onSubmit={onSubmit} className="card space-y-5 p-5">
      <h2 className="text-lg font-extrabold">Start a game</h2>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-sm">
          <span className="mb-1 block font-semibold opacity-80">Name</span>
          <input className="input" value={form.name} required maxLength={80} autoFocus
            onChange={(e) => setForm({ ...form, name: e.target.value })} />
        </label>
        <DateField label="First day" value={form.startDate} onChange={(startDate) => setForm({ ...form, startDate })} />
        <label className="block text-sm">
          <span className="mb-1 block font-semibold opacity-80">Turns (30 to 50)</span>
          <input className="input" type="number" min={30} max={50} required value={form.turns}
            onChange={(e) => setForm({ ...form, turns: Number(e.target.value) })} />
          <span className="mt-1 block text-xs opacity-70">4 turns a day, so about {days} days.</span>
        </label>
        <label className="block text-sm">
          <span className="mb-1 block font-semibold opacity-80">Prize (optional)</span>
          <input className="input" value={form.prize} maxLength={200} placeholder="$50 gift card"
            onChange={(e) => setForm({ ...form, prize: e.target.value })} />
          <span className="mt-1 block text-xs opacity-70">A winning party splits it.</span>
        </label>
      </div>

      <fieldset className="text-sm">
        <legend className="mb-1 font-semibold opacity-80">Bonus coins for each</legend>
        <div className="grid grid-cols-3 gap-3 sm:max-w-md">
          {([['bonusTreatment', 'Treatment'], ['bonusColor', 'Color'], ['bonusWax', 'Wax']] as const).map(([key, label]) => (
            <label key={key} className="block">
              <span className="mb-1 block text-xs opacity-70">{label}</span>
              <input className="input" type="number" min={0} max={500} value={form[key]}
                onChange={(e) => setForm({ ...form, [key]: Number(e.target.value) })} />
            </label>
          ))}
        </div>
        <span className="mt-1 block text-xs opacity-70">On top of 1 coin for every dollar of service sales.</span>
      </fieldset>

      <label className="block text-sm">
        <span className="mb-1 block font-semibold opacity-80">Handicap</span>
        <select className="input cursor-pointer sm:max-w-xs" value={form.handicap}
          onChange={(e) => setForm({ ...form, handicap: e.target.value as ContestHandicap })}>
          {HANDICAPS.map((h) => <option key={h.value} value={h.value}>{h.label}</option>)}
        </select>
        <span className="mt-1 block text-xs opacity-70">
          {HANDICAPS.find((h) => h.value === form.handicap)?.help}
          {form.handicap !== 'NONE' && ' It comes from each person\'s last 4 weeks before the game starts.'}
        </span>
      </label>

      <fieldset className="text-sm">
        <div className="mb-2 flex flex-wrap items-center gap-3">
          <legend className="font-semibold opacity-80">Who&apos;s playing ({form.picked.length})</legend>
          {people && people.length > 0 && (
            <>
              <button type="button" className="text-sm font-semibold text-[var(--accent-strong)] hover:underline"
                onClick={() => setForm({ ...form, picked: people.map((p) => p.userId) })}>Everyone</button>
              <button type="button" className="text-sm font-semibold text-[var(--accent-strong)] hover:underline"
                onClick={() => setForm({ ...form, picked: [] })}>No one</button>
            </>
          )}
        </div>
        {people === null ? (
          <p className="opacity-60">Loading…</p>
        ) : people.length === 0 ? (
          <p className="opacity-80">No one is linked to Salon Ultimate yet. Link people on the Team page first.</p>
        ) : (
          <ul className="grid gap-x-4 sm:grid-cols-2">
            {people.map((p) => (
              <li key={p.userId} className="py-1.5">
                <label className="flex cursor-pointer items-center gap-2">
                  <input type="checkbox" className="h-5 w-5 shrink-0 accent-[var(--accent)]" checked={form.picked.includes(p.userId)}
                    onChange={() => toggle(p.userId)} />
                  <span className="break-words font-semibold">{p.name}</span>
                </label>
              </li>
            ))}
          </ul>
        )}
        <p className="mt-2 text-xs opacity-60">Only people linked to their Salon Ultimate name can play. Receptionists aren&apos;t included.</p>
      </fieldset>

      <div className="flex flex-wrap gap-2">
        <button type="submit" className="btn" disabled={busy}>{busy ? 'Starting…' : 'Start game'}</button>
        <button type="button" className="btn-ghost" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}
