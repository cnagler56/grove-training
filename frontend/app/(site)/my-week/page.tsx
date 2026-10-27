'use client';

import { useEffect, useState } from 'react';
import { api, type MyStats, type StatLine } from '@/src/lib/api';
import { hours, money, moneyCents } from '@/src/lib/format';
import { weekRangeLabel } from '@/src/lib/schedule';

/** How many past weeks make up "your average". */
const AVERAGE_WEEKS = 8;

type Metric = {
  label: string;
  value: (s: StatLine) => number | null;
  format: (n: number) => string;
  hint?: string;
};

const METRICS: Metric[] = [
  { label: 'Pay rate', value: (s) => s.payRate, format: (n) => `${moneyCents(n)}/hr`, hint: 'This week, from your PPH' },
  { label: 'PPH', value: (s) => (s.floorHours > 0 ? s.pph : null), format: moneyCents, hint: 'Production per hour, as payroll figures it' },
  { label: 'Service sales', value: (s) => s.serviceSales, format: money, hint: 'Before discounts' },
  { label: 'Retail sales', value: (s) => s.retailSales, format: money },
  { label: 'Product per guest', value: (s) => s.productPerGuest, format: moneyCents },
  { label: 'Floor hours', value: (s) => s.floorHours, format: hours },
];

/**
 * Each employee's own numbers from Salon Ultimate, one Saturday–Friday week at
 * a time, compared with the week before and their recent average. Only they
 * (and managers, on the leaderboards) can see them.
 */
export default function MyWeekPage() {
  const [data, setData] = useState<MyStats | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [picked, setPicked] = useState(0);

  useEffect(() => {
    api.myStats().then(setData).catch((e) => setError(e.message));
  }, []);

  if (error) return <p className="text-sm text-[var(--danger)]">{error}</p>;
  if (!data) return <p className="opacity-60">Loading…</p>;

  const weeks = data.weeks;
  const week = weeks[picked];
  const before = weeks[picked + 1];
  const earlier = weeks.slice(picked + 1, picked + 1 + AVERAGE_WEEKS);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">My week</h1>
        <p className="mt-1 text-sm opacity-70">
          {week ? `Week of ${weekRangeLabel(week.weekStart)}` : 'Your numbers from Salon Ultimate, every Saturday.'}
        </p>
      </div>

      {!data.linked ? (
        <p className="card p-5 text-sm">
          Your numbers aren&apos;t connected yet. Ask a manager to link your Salon Ultimate name on the Team page.
        </p>
      ) : !week ? (
        <p className="card p-5 text-sm opacity-80">
          No weeks yet. Your numbers arrive every Saturday morning, after the week ends.
        </p>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {METRICS.filter((m) => m.label !== 'Pay rate' || week.payRate !== null).map((m) => {
              const now = m.value(week);
              const prev = before ? m.value(before) : null;
              const pastValues = earlier.map(m.value).filter((n): n is number => n !== null);
              const avg = pastValues.length ? pastValues.reduce((a, b) => a + b, 0) / pastValues.length : null;
              return (
                <section key={m.label} className="card p-5">
                  <div className="text-xs font-bold uppercase tracking-wide opacity-60">{m.label}</div>
                  <div className="mt-1 text-3xl font-extrabold">{now === null ? 'n/a' : m.format(now)}</div>
                  {m.hint && <div className="text-xs opacity-60">{m.hint}</div>}
                  <div className="mt-3 space-y-0.5 text-sm">
                    {now !== null && prev !== null && <Change now={now} then={prev} format={m.format} label="vs. the week before" />}
                    {now !== null && avg !== null && pastValues.length > 1 && (
                      <Change now={now} then={avg} format={m.format} label={`vs. your ${pastValues.length}-week average`} />
                    )}
                  </div>
                </section>
              );
            })}
          </div>

          <section className="card overflow-x-auto">
            <h2 className="px-5 pt-5 text-lg font-extrabold">Every week</h2>
            <p className="px-5 text-xs opacity-60">Pick a week to see it above.</p>
            <table className="mt-3 w-full text-left text-sm">
              <thead className="border-b border-[var(--border)] text-xs uppercase tracking-wide opacity-60">
                <tr>
                  <th className="px-5 py-2 font-semibold">Week of</th>
                  <th className="px-3 py-2 text-right font-semibold">Service</th>
                  <th className="px-3 py-2 text-right font-semibold">Retail</th>
                  <th className="px-3 py-2 text-right font-semibold">Per guest</th>
                  <th className="px-3 py-2 text-right font-semibold">PPH</th>
                  {weeks.some((w) => w.payRate !== null) && <th className="px-3 py-2 text-right font-semibold">Rate</th>}
                  <th className="px-5 py-2 text-right font-semibold">Hours</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--border)]">
                {weeks.map((w, i) => (
                  <tr
                    key={w.weekStart}
                    onClick={() => setPicked(i)}
                    className={`cursor-pointer ${i === picked ? 'bg-[var(--accent-soft)] font-semibold' : 'hover:bg-[var(--surface-2)]'}`}
                  >
                    <td className="whitespace-nowrap px-5 py-2">{weekRangeLabel(w.weekStart)}</td>
                    <td className="px-3 py-2 text-right">{money(w.serviceSales)}</td>
                    <td className="px-3 py-2 text-right">{money(w.retailSales)}</td>
                    <td className="px-3 py-2 text-right">{moneyCents(w.productPerGuest)}</td>
                    <td className="px-3 py-2 text-right">{w.floorHours > 0 ? moneyCents(w.pph) : ''}</td>
                    {weeks.some((x) => x.payRate !== null) && (
                      <td className="px-3 py-2 text-right">{w.payRate === null ? '' : moneyCents(w.payRate)}</td>
                    )}
                    <td className="px-5 py-2 text-right">{hours(w.floorHours)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        </>
      )}
    </div>
  );
}

/** "+$120 vs. the week before" in green, or "-$40 ..." in red. */
function Change({ now, then, format, label }: { now: number; then: number; format: (n: number) => string; label: string }) {
  const diff = now - then;
  const same = Math.abs(diff) < 0.005;
  return (
    <div>
      <span className={`font-semibold ${same ? 'opacity-60' : diff > 0 ? 'text-[var(--success)]' : 'text-[var(--danger)]'}`}>
        {same ? 'Same' : `${diff > 0 ? '+' : '-'}${format(Math.abs(diff))}`}
      </span>{' '}
      <span className="opacity-60">{label}</span>
    </div>
  );
}
