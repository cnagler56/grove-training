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

const perHour = (n: number) => `${moneyCents(n)}/hr`;

const PAY: Metric[] = [
  { label: 'Total per hour', value: (s) => s.totalPerHour, format: perHour, hint: 'Pay rate plus tips per hour' },
  { label: 'Pay rate', value: (s) => s.payRate, format: perHour, hint: 'This week, from your PPH' },
  { label: 'Tips per hour', value: (s) => s.tipsPerHour, format: perHour, hint: 'Tips ÷ floor hours' },
  { label: 'Tips', value: (s) => s.tips, format: money, hint: 'This week' },
];

const PRODUCTION: Metric[] = [
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
  const showPay = weeks.some((w) => w.payRate !== null);
  const showTips = weeks.some((w) => w.tips !== null);

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
          {PAY.some((m) => m.value(week) !== null) && (
            <Group title="Pay" metrics={PAY} week={week} before={before} earlier={earlier} columns="sm:grid-cols-2 lg:grid-cols-4" />
          )}
          <Group title="Production" metrics={PRODUCTION} week={week} before={before} earlier={earlier} columns="sm:grid-cols-2 lg:grid-cols-5" />

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
                  {showPay && <th className="px-3 py-2 text-right font-semibold">Rate</th>}
                  {showTips && <th className="px-3 py-2 text-right font-semibold">Tips</th>}
                  {showTips && <th className="px-3 py-2 text-right font-semibold">Tips/hr</th>}
                  {showPay && showTips && <th className="px-3 py-2 text-right font-semibold">Total/hr</th>}
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
                    {showPay && <td className="px-3 py-2 text-right">{w.payRate === null ? '' : moneyCents(w.payRate)}</td>}
                    {showTips && <td className="px-3 py-2 text-right">{w.tips === null ? '' : money(w.tips)}</td>}
                    {showTips && <td className="px-3 py-2 text-right">{w.tipsPerHour === null ? '' : moneyCents(w.tipsPerHour)}</td>}
                    {showPay && showTips && (
                      <td className="px-3 py-2 text-right font-semibold">{w.totalPerHour === null ? '' : moneyCents(w.totalPerHour)}</td>
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

/** A titled row of metric cards for the picked week, each compared with the week before and their average. */
function Group({ title, metrics, week, before, earlier, columns }: {
  title: string;
  metrics: Metric[];
  week: StatLine;
  before: StatLine | undefined;
  earlier: StatLine[];
  columns: string;
}) {
  return (
    <section>
      <h2 className="mb-2 text-sm font-bold uppercase tracking-wide opacity-60">{title}</h2>
      <div className={`grid gap-4 ${columns}`}>
        {metrics.map((m) => {
          const now = m.value(week);
          const prev = before ? m.value(before) : null;
          const pastValues = earlier.map(m.value).filter((n): n is number => n !== null);
          const avg = pastValues.length ? pastValues.reduce((a, b) => a + b, 0) / pastValues.length : null;
          return (
            <div key={m.label} className="card p-5">
              <div className="text-xs font-bold uppercase tracking-wide opacity-60">{m.label}</div>
              <div className="mt-1 text-3xl font-extrabold">{now === null ? 'n/a' : m.format(now)}</div>
              {m.hint && <div className="text-xs opacity-60">{m.hint}</div>}
              <div className="mt-3 space-y-0.5 text-sm">
                {now !== null && prev !== null && <Change now={now} then={prev} format={m.format} label="vs. the week before" />}
                {now !== null && avg !== null && pastValues.length > 1 && (
                  <Change now={now} then={avg} format={m.format} label={`vs. your ${pastValues.length}-week average`} />
                )}
              </div>
            </div>
          );
        })}
      </div>
    </section>
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
