'use client';

import { useEffect, useState } from 'react';
import { api, type MonthlyGoals } from '@/src/lib/api';
import { todayIso } from '@/src/lib/schedule';

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October',
  'November', 'December'];

/** "2026-11" plus n months. */
function addMonths(month: string, n: number) {
  const [y, m] = month.split('-').map(Number);
  const i = y * 12 + (m - 1) + n;
  return `${Math.floor(i / 12)}-${String((i % 12) + 1).padStart(2, '0')}`;
}
const monthLabel = (month: string) => `${MONTHS[Number(month.slice(5, 7)) - 1]} ${month.slice(0, 4)}`;

type Draft = Record<keyof MonthlyGoals, string>;
const FIELDS: { key: keyof MonthlyGoals; label: string; money: boolean; step: string; placeholder: string }[] = [
  { key: 'sales', label: 'Sales', money: true, step: '100', placeholder: '45000' },
  { key: 'waxes', label: 'Waxes', money: false, step: '1', placeholder: '60' },
  { key: 'treatments', label: 'Treatments', money: false, step: '1', placeholder: '80' },
  { key: 'ppg', label: 'Product per guest', money: true, step: '0.25', placeholder: '12.50' },
];

const toDraft = (g: MonthlyGoals): Draft => ({
  sales: g.sales == null ? '' : String(g.sales),
  waxes: g.waxes == null ? '' : String(g.waxes),
  treatments: g.treatments == null ? '' : String(g.treatments),
  ppg: g.ppg == null ? '' : String(g.ppg),
});

/**
 * Owner Settings: the team's goals for a month. Who sees the Home card is the
 * "See monthly goals" row in the table above.
 */
export default function MonthlyGoalsCard() {
  const thisMonth = todayIso().slice(0, 7);
  const options = [0, 1, 2].map((n) => addMonths(thisMonth, n));
  const [month, setMonth] = useState(thisMonth);
  const [saved, setSaved] = useState<Draft | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    setDraft(null);
    setNotice(null);
    setError(null);
    api.monthlyGoals(month).then((g) => {
      setSaved(toDraft(g));
      setDraft(toDraft(g));
    }).catch((e) => setError(e.message));
  }, [month]);

  const dirty = !!draft && !!saved && FIELDS.some((f) => draft[f.key] !== saved[f.key]);

  async function save() {
    if (!draft) return;
    const num = (s: string) => (s.trim() === '' ? null : Number(s));
    const input: MonthlyGoals = {
      sales: num(draft.sales), waxes: num(draft.waxes), treatments: num(draft.treatments), ppg: num(draft.ppg),
    };
    if (Object.values(input).some((v) => v !== null && (Number.isNaN(v) || v < 0))) {
      setError('Goals need to be numbers, 0 or more.');
      return;
    }
    if (input.waxes !== null) input.waxes = Math.round(input.waxes);
    if (input.treatments !== null) input.treatments = Math.round(input.treatments);
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const g = await api.setMonthlyGoals(month, input);
      setSaved(toDraft(g));
      setDraft(toDraft(g));
      setNotice(`Saved ${monthLabel(month)} goals.`);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save');
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card mt-8 p-5">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-lg font-extrabold">Monthly goals</h2>
        <select className="input w-auto cursor-pointer py-1" value={month} aria-label="Month"
          onChange={(e) => setMonth(e.target.value)}>
          {options.map((m) => <option key={m} value={m}>{monthLabel(m)}</option>)}
        </select>
      </div>
      <p className="mt-1 text-sm opacity-70">
        The team&apos;s goals for the month. Leave one blank for no goal. Who sees them on Home is the
        &quot;See monthly goals&quot; row in the table above.
      </p>
      {!draft ? (
        <p className="mt-3 text-sm opacity-60">{error ?? 'Loading…'}</p>
      ) : (
        <>
          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            {FIELDS.map((f) => (
              <label key={f.key} className="block text-sm">
                <span className="mb-1 block font-semibold opacity-80">{f.label}{f.money ? ' ($)' : ''}</span>
                <input className="input" type="number" inputMode="decimal" min={0} step={f.step} placeholder={f.placeholder}
                  value={draft[f.key]} onChange={(e) => { setDraft({ ...draft, [f.key]: e.target.value }); setNotice(null); }} />
              </label>
            ))}
          </div>
          <p className="mt-2 text-xs opacity-60">
            Sales are services after discounts plus retail. Product per guest is retail divided by guests.
          </p>
          {error && <p className="mt-3 text-sm text-[var(--danger)]">{error}</p>}
          {notice && <p className="mt-3 text-sm font-semibold text-[var(--success)]">{notice}</p>}
          <div className="mt-3 flex items-center gap-3">
            <button className="btn" disabled={busy || !dirty} onClick={() => void save()}>{busy ? 'Saving…' : 'Save goals'}</button>
            {dirty && <span className="text-sm font-semibold text-[var(--warn)]">Unsaved changes</span>}
          </div>
        </>
      )}
    </section>
  );
}
