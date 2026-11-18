'use client';

import { useEffect, useState } from 'react';
import { api, type DailySalesDay, type DailySalesView } from '@/src/lib/api';
import { money } from '@/src/lib/format';
import { addDays, longDayLabel, todayIso } from '@/src/lib/schedule';

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October',
  'November', 'December'];

/** "8:30 PM" from "2026-11-18T20:30:00". */
const clock = (iso: string) => new Date(iso).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });

/**
 * Owners' Home page: the latest day's business, this month so far with a projection,
 * and the last 30 days of sales. Numbers are pulled from Salon Ultimate 30 minutes
 * after closing each day.
 */
export default function OwnerBusinessCard() {
  const [data, setData] = useState<DailySalesView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showTable, setShowTable] = useState(false);

  useEffect(() => {
    api.dailySales().then(setData).catch((e) => setError(e.message));
  }, []);

  if (error) return <section className="card p-5 text-sm text-[var(--danger)]">{error}</section>;
  if (!data) return <section className="card p-5 text-sm opacity-60">Loading business…</section>;

  const today = todayIso();
  const month = MONTHS[Number(today.slice(5, 7)) - 1];
  const latest = data.latest;
  const blockedToday = data.today && data.today.status === 'BLOCKED' ? data.today : null;

  return (
    <section className="card space-y-4 p-5">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-lg font-extrabold">Business</h2>
        <span className="text-xs opacity-60">
          {data.configured
            ? (data.today ? 'Today’s numbers are in' : `Today’s numbers come in at ${clock(data.todayPullsAt)}`)
            : 'Salon Ultimate sign-in isn’t set up on the server'}
        </span>
      </div>

      {blockedToday && (
        <p className="rounded-lg bg-amber-50 px-3 py-2 text-sm font-semibold text-[var(--warn)]">
          Today&apos;s numbers are waiting on an open timecard: {blockedToday.message}
        </p>
      )}

      {latest ? (
        <div>
          <div className="text-xs font-semibold uppercase tracking-wide opacity-60">
            {latest.date === today ? 'Today' : longDayLabel(latest.date)}
          </div>
          <div className="text-3xl font-extrabold tabular-nums">{money(latest.total ?? 0)}</div>
          <div className="text-sm opacity-70">
            {money(latest.netServiceSales ?? 0)} services · {money(latest.retailSales ?? 0)} retail
            {latest.clients != null && <> · {latest.clients} clients</>}
          </div>
        </div>
      ) : (
        <p className="text-sm opacity-70">No days saved yet. They fill in a few at a time, starting with the past month.</p>
      )}

      <div className="grid grid-cols-3 gap-2">
        <Tile label={`${month} so far`} value={money(data.monthToDate)} />
        <Tile label="Average open day" value={money(data.averagePerOpenDay)} />
        <Tile label={`Projected ${month}`} value={data.openDaysSoFar > 0 ? money(data.projectedMonth) : '-'}
          note={data.openDaysSoFar > 0 ? `${data.openDaysSoFar} of ${data.openDaysInMonth} open days` : undefined} />
      </div>

      <div>
        <div className="mb-1 flex items-baseline justify-between gap-2">
          <h3 className="text-sm font-bold">Sales, last 30 days</h3>
          <button className="text-xs font-semibold hover:underline" onClick={() => setShowTable(!showTable)}>
            {showTable ? 'Show chart' : 'Show as a table'}
          </button>
        </div>
        {showTable ? <SalesTable days={data.days} /> : <SalesChart days={data.days} today={today} />}
      </div>

      <p className="text-xs opacity-50">Sales are services after discounts plus retail, from Salon Ultimate.</p>
    </section>
  );
}

function Tile({ label, value, note }: { label: string; value: string; note?: string }) {
  return (
    <div className="rounded-lg bg-[var(--surface-2)] p-2.5">
      <div className="text-[11px] font-semibold uppercase leading-tight tracking-wide opacity-60">{label}</div>
      <div className="mt-0.5 text-lg font-extrabold tabular-nums">{value}</div>
      {note && <div className="text-[11px] opacity-60">{note}</div>}
    </div>
  );
}

/** One bar per day, oldest on the left. Closed days and days that didn't save have no bar. */
function SalesChart({ days, today }: { days: DailySalesDay[]; today: string }) {
  const [hover, setHover] = useState<string | null>(null);
  const byDate = new Map(days.map((d) => [d.date, d]));
  const dates = Array.from({ length: 30 }, (_, i) => addDays(today, i - 29));
  const max = Math.max(1, ...days.map((d) => d.total ?? 0));
  const hovered = hover ? byDate.get(hover) ?? null : null;

  return (
    <div>
      <div className="relative">
        {/* Recessive guide: the top of the scale, the best day (its amount is under the chart). */}
        <div className="pointer-events-none absolute inset-x-0 top-0 border-t border-dashed border-[var(--border)]" />
        <div className="flex h-36 items-end gap-[2px] border-b border-[var(--border)]" onMouseLeave={() => setHover(null)}>
          {dates.map((date) => {
            const d = byDate.get(date);
            const h = d?.open && d.total ? Math.max(2, (d.total / max) * 100) : 0;
            return (
              // The hit area is the whole column, taller than the bar.
              <div key={date} className="flex h-full min-w-0 flex-1 cursor-default items-end"
                onMouseEnter={() => setHover(date)} onClick={() => setHover(date)}
                aria-label={`${longDayLabel(date)}: ${d?.open && d.total != null ? money(d.total) : statusWord(d, date, today)}`}>
                <div className={`w-full rounded-t ${hover === date ? 'bg-[var(--accent-strong)]' : 'bg-[var(--accent)]'}`}
                  style={{ height: `${h}%` }} />
              </div>
            );
          })}
        </div>
      </div>
      <div className="mt-1 flex justify-between gap-2 text-[10px] opacity-60">
        <span>{longDayLabel(dates[0])}</span>
        <span className="tabular-nums">Dashed line: best day, {money(max)}</span>
        <span>Today</span>
      </div>
      <div className="mt-1 min-h-10 rounded-lg bg-[var(--surface-2)] px-2.5 py-1.5 text-xs">
        {hover ? (
          <>
            <b>{longDayLabel(hover)}</b>{' '}
            {hovered?.open && hovered.total != null ? (
              <>
                <span className="tabular-nums">{money(hovered.total)}</span>
                <span className="opacity-70">
                  {' '}· {money(hovered.netServiceSales ?? 0)} services · {money(hovered.retailSales ?? 0)} retail
                  {hovered.clients != null && <> · {hovered.clients} clients</>}
                </span>
              </>
            ) : (
              <span className="opacity-70">{statusWord(hovered ?? undefined, hover, today)}</span>
            )}
          </>
        ) : (
          <span className="opacity-60">Point at a day to see its numbers.</span>
        )}
      </div>
    </div>
  );
}

function statusWord(d: DailySalesDay | undefined, date: string, today: string) {
  if (!d) return date === today ? 'Not in yet' : 'Not pulled yet';
  if (d.status === 'BLOCKED') return 'Waiting on an open timecard';
  if (d.status === 'FAILED') return 'Couldn’t pull this day';
  return 'Closed';
}

function SalesTable({ days }: { days: DailySalesDay[] }) {
  const rows = [...days].reverse();
  if (rows.length === 0) return <p className="text-sm opacity-70">No days saved yet.</p>;
  return (
    <div className="max-h-64 overflow-y-auto">
      <table className="w-full text-sm tabular-nums">
        <thead className="sticky top-0 bg-[var(--surface)]">
          <tr className="text-left text-xs uppercase tracking-wide opacity-60">
            <th className="py-1">Day</th>
            <th className="py-1 text-right">Sales</th>
            <th className="py-1 text-right">Services</th>
            <th className="py-1 text-right">Retail</th>
            <th className="py-1 text-right">Clients</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-[var(--border)]">
          {rows.map((d) => (
            <tr key={d.date}>
              <td className="py-1">{longDayLabel(d.date)}</td>
              {d.open && d.total != null ? (
                <>
                  <td className="py-1 text-right font-semibold">{money(d.total)}</td>
                  <td className="py-1 text-right">{money(d.netServiceSales ?? 0)}</td>
                  <td className="py-1 text-right">{money(d.retailSales ?? 0)}</td>
                  <td className="py-1 text-right">{d.clients ?? '-'}</td>
                </>
              ) : (
                <td colSpan={4} className="py-1 text-right opacity-60">{statusWord(d, d.date, '')}</td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
