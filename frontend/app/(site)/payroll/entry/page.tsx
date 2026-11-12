'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, type PayrollEntry, type PayrollPayWeek } from '@/src/lib/api';
import { formatDate, moneyCents } from '@/src/lib/format';
import { ADP_SIGN_IN_URL, NOTE_KIND, halfScreen, signedAmount } from '@/src/lib/payroll';
import { roleLabel } from '@/src/lib/roles';

const hrs = (n: number) => n.toFixed(2);
const spotKey = (checkDate: string) => `payrollEntrySpot:${checkDate}`;

/** Where they left off for this pay period; browser storage can be missing or blocked. */
function savedSpot(checkDate: string) {
  try {
    return Number(window.localStorage.getItem(spotKey(checkDate))) || 0;
  } catch {
    return 0;
  }
}

function saveSpot(checkDate: string, i: number) {
  try {
    window.localStorage.setItem(spotKey(checkDate), String(i));
  } catch {
    // Not saved; they start at the first person next time.
  }
}

/** Opens (or brings back) ADP's sign-in on the right half of the screen. */
function openAdp() {
  const w = window.open(ADP_SIGN_IN_URL, 'grove-adp', halfScreen('right').features);
  w?.focus();
}

/**
 * A small window for entering payroll into ADP: one person at a time with everything
 * to enter for them, and Next to move on. Opened from the Payroll tab on the left half
 * of the screen; ADP opens on the right half.
 */
export default function PayrollEntryPage() {
  const [entry, setEntry] = useState<PayrollEntry | null>(null);
  const [checkDate, setCheckDate] = useState('');
  const [index, setIndex] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // A window this page opened itself can be moved; one opened from the Payroll tab can too.
    try {
      const { width, height, left, top } = halfScreen('left');
      window.resizeTo(width, height);
      window.moveTo(left, top);
    } catch {
      // The browser said no; it stays where it is.
    }
    const asked = new URLSearchParams(window.location.search).get('checkDate');
    const load = (d: string) => api.payrollEntry(d).then((e) => {
      setEntry(e);
      setCheckDate(d);
      setIndex(Math.min(savedSpot(d), Math.max(0, e.people.length - 1)));
    });
    (asked ? load(asked) : api.payPeriods().then((p) => load(p.defaultCheckDate)))
      .catch((e) => setError(e.message));
  }, []);

  const go = useCallback((to: number) => {
    if (!entry) return;
    const next = Math.max(0, Math.min(entry.people.length - 1, to));
    setIndex(next);
    saveSpot(checkDate, next);
  }, [entry, checkDate]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.target as HTMLElement)?.tagName === 'SELECT') return;
      if (e.key === 'ArrowRight') go(index + 1);
      if (e.key === 'ArrowLeft') go(index - 1);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [go, index]);

  if (error) return <p className="text-sm text-[var(--danger)]">{error}</p>;
  if (!entry) return <p className="text-sm opacity-60">Loading…</p>;

  const p = entry.people[index];
  const last = index === entry.people.length - 1;

  return (
    <div className="mx-auto max-w-md space-y-3 text-sm">
      <div className="flex items-center justify-between gap-2">
        <div className="text-xs opacity-70">
          Work {formatDate(entry.period.workStart, false)} – {formatDate(entry.period.workEnd, false)}
          {' · '}check {formatDate(entry.period.checkDate, false)}
        </div>
        <button className="btn-ghost px-3 py-1 text-xs" onClick={openAdp}>Open ADP</button>
      </div>

      {!entry.ratesLoaded && (
        <p className="text-xs font-semibold text-[var(--warn)]">Rates are blank until the pay matrix is loaded on Settings.</p>
      )}

      {entry.people.length === 0 ? (
        <p className="card p-4 opacity-70">No one to enter for this pay period yet.</p>
      ) : (
        <>
          <div className="flex items-center gap-2">
            <button className="btn-ghost px-3 py-1.5" disabled={index === 0} onClick={() => go(index - 1)}>Previous</button>
            <select className="input min-w-0 flex-1 py-1.5" aria-label="Jump to someone" value={index}
              onChange={(e) => go(Number(e.target.value))}>
              {entry.people.map((x, i) => <option key={x.name} value={i}>{i + 1}. {x.name}</option>)}
            </select>
            <button className="btn px-4 py-1.5" disabled={last} onClick={() => go(index + 1)}>Next</button>
          </div>

          <section className="card space-y-3 p-4">
            <div>
              <div className="flex items-baseline justify-between gap-2">
                <h1 className="text-xl font-extrabold">{p.name}</h1>
                <span className="text-xs opacity-60">{index + 1} of {entry.people.length}</span>
              </div>
              {p.role && <div className="text-xs opacity-60">{roleLabel(p.role)}</div>}
              {p.problem && <div className="mt-1 text-xs font-semibold text-[var(--warn)]">{p.problem}</div>}
            </div>

            {(p.week1 || p.week2) && (
              <table className="w-full tabular-nums">
                <thead>
                  <tr className="text-xs uppercase tracking-wide opacity-60">
                    <th className="py-1 text-left font-semibold" />
                    <th className="py-1 text-right font-semibold">Week 1</th>
                    <th className="py-1 text-right font-semibold">Week 2</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--border)]">
                  <Line label="Rate" w1={p.week1} w2={p.week2} show={(w) => (w.rate == null ? '-' : moneyCents(w.rate))} strong />
                  <Line label="Floor hours" w1={p.week1} w2={p.week2} show={(w) => hrs(w.floorHours)} />
                  <Line label="Off-floor hours" w1={p.week1} w2={p.week2} show={(w) => hrs(w.offFloorHours)} />
                  <Line label="Tips" w1={p.week1} w2={p.week2} show={(w) => moneyCents(w.tips)} />
                  <Line label="Commission" w1={p.week1} w2={p.week2} show={(w) => moneyCents(w.commission)} />
                </tbody>
              </table>
            )}

            <dl className="grid grid-cols-3 gap-2 text-center">
              <Total label="Paid time off" value={`${hrs(p.ptoHours)} hrs`} on={p.ptoHours > 0} />
              <Total label="Additions" value={moneyCents(p.additions)} on={p.additions > 0} />
              <Total label="Deductions" value={moneyCents(p.deductions)} on={p.deductions > 0} />
            </dl>
            {p.pendingPtoHours > 0 && (
              <p className="text-xs font-semibold text-[var(--warn)]">
                {hrs(p.pendingPtoHours)} more paid time off hours are waiting for approval and aren&apos;t counted.
              </p>
            )}

            {p.notes.length > 0 && (
              <ul className="space-y-1 border-t border-[var(--border)] pt-2">
                {p.notes.map((n) => (
                  <li key={n.id}>
                    <span className={`mr-1.5 rounded-full px-2 py-0.5 text-xs font-bold ${NOTE_KIND[n.kind][1]}`}>{NOTE_KIND[n.kind][0]}</span>
                    {signedAmount(n) && <b className="mr-1.5">{signedAmount(n)}</b>}
                    <span className="whitespace-pre-line">{n.note}</span>
                  </li>
                ))}
              </ul>
            )}
          </section>

          {last && <p className="text-center text-xs font-semibold text-[var(--success)]">That&apos;s everyone for this pay period.</p>}
          <p className="text-center text-xs opacity-50">The left and right arrow keys also move between people.</p>
        </>
      )}
    </div>
  );
}

function Line({ label, w1, w2, show, strong }: {
  label: string;
  w1: PayrollPayWeek | null;
  w2: PayrollPayWeek | null;
  show: (w: PayrollPayWeek) => string;
  strong?: boolean;
}) {
  return (
    <tr className={strong ? 'font-bold' : ''}>
      <td className="py-1 opacity-80">{label}</td>
      <td className="py-1 text-right">{w1 ? show(w1) : <span className="opacity-40">-</span>}</td>
      <td className="py-1 text-right">{w2 ? show(w2) : <span className="opacity-40">-</span>}</td>
    </tr>
  );
}

function Total({ label, value, on }: { label: string; value: string; on: boolean }) {
  return (
    <div className={`rounded-lg p-2 ${on ? 'bg-[var(--accent-soft)]' : 'bg-[var(--surface-2)] opacity-60'}`}>
      <dt className="text-[11px] font-semibold uppercase tracking-wide opacity-70">{label}</dt>
      <dd className="font-extrabold tabular-nums">{value}</dd>
    </div>
  );
}
