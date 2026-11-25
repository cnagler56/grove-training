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
  // The day being pulled again, and how the last try went.
  const [retrying, setRetrying] = useState<string | null>(null);
  const [retryResult, setRetryResult] = useState<{ ok: boolean; text: string } | null>(null);

  useEffect(() => {
    api.dailySales().then(setData).catch((e) => setError(e.message));
  }, []);

  async function retry(date: string) {
    setRetrying(date);
    setRetryResult(null);
    try {
      const next = await api.retryDailySales(date);
      setData(next);
      const day = next.days.find((d) => d.date === date);
      setRetryResult(day?.status === 'OK'
        ? { ok: true, text: `${longDayLabel(date)} is in now.` }
        : { ok: false, text: `${longDayLabel(date)} still didn't come in${day?.message ? `: ${day.message}` : '.'}` });
    } catch (e) {
      setRetryResult({ ok: false, text: e instanceof Error ? e.message : 'Couldn’t try again' });
    } finally {
      setRetrying(null);
    }
  }

  if (error) return <section className="card p-5 text-sm text-[var(--danger)]">{error}</section>;
  if (!data) return <section className="card p-5 text-sm opacity-60">Loading business…</section>;

  const today = todayIso();
  const month = MONTHS[Number(today.slice(5, 7)) - 1];
  const latest = data.latest;
  // Days that didn't come in (open timecard, or Salon Ultimate couldn't be reached), newest first.
  const missed = data.days.filter((d) => d.status !== 'OK').reverse();

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

      {missed.length > 0 && data.configured && (
        <ul className="space-y-1.5 rounded-lg bg-amber-50 px-3 py-2 text-sm">
          {missed.map((d) => (
            <li key={d.date} className="flex flex-wrap items-center justify-between gap-2">
              <span className="min-w-0 font-semibold text-[var(--warn)]">
                {d.date === today ? 'Today' : longDayLabel(d.date)}
                {d.status === 'BLOCKED' ? ' is waiting on an open timecard' : ' didn’t come in'}
                {d.message ? `: ${d.message}` : ''}
              </span>
              <button className="btn-ghost" disabled={retrying !== null} onClick={() => void retry(d.date)}>
                {retrying === d.date ? 'Trying…' : 'Try again'}
              </button>
            </li>
          ))}
          <li className="text-xs opacity-70">Once the timecard is closed in Salon Ultimate, press Try again.</li>
        </ul>
      )}
      {retryResult && (
        <p className={`text-sm font-semibold ${retryResult.ok ? 'text-[var(--success)]' : 'text-[var(--danger)]'}`}>
          {retryResult.text}
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
        <div className="mb-1 flex flex-wrap items-baseline justify-between gap-2">
          <h3 className="text-sm font-bold">Sales, last 15 days</h3>
          <button className="text-xs font-semibold hover:underline" onClick={() => setShowTable(!showTable)}>
            {showTable ? 'Show chart' : 'Show as a table'}
          </button>
        </div>
        <Legend />
        <Comparison days={data.days} yearAgo={data.yearAgo} />
        {showTable
          ? <SalesTable days={data.days} yearAgo={data.yearAgo} />
          : <SalesChart days={data.days} yearAgo={data.yearAgo} today={today} />}
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

/** This year and a year ago: the same measure, so one hue, darker for now. */
const NOW_COLOR = '#3f74a3';
const YEAR_AGO_COLOR = '#8ab4d9';
/** 52 weeks back: the same weekday a year earlier. */
const yearAgoOf = (iso: string) => addDays(iso, -364);
const withYear = (iso: string) => `${longDayLabel(iso)}, ${iso.slice(0, 4)}`;
const sales = (d: DailySalesDay | undefined) => (d?.open && d.total != null ? d.total : null);

/** "up 12%", "down 4%", or "same". Ink, not red/green: the words carry the direction. */
function change(now: number, before: number) {
  if (before <= 0) return null;
  const pct = Math.round(((now - before) / before) * 100);
  return pct === 0 ? 'same' : pct > 0 ? `up ${pct}%` : `down ${-pct}%`;
}

function Legend() {
  return (
    <div className="mb-1 flex flex-wrap gap-x-4 gap-y-1 text-xs">
      <span className="flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-sm" style={{ background: NOW_COLOR }} />This year</span>
      <span className="flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-sm" style={{ background: YEAR_AGO_COLOR }} />A year ago, same weekday</span>
    </div>
  );
}

/** The 15 days against their year-ago days, counting only days with numbers on both sides. */
function Comparison({ days, yearAgo }: { days: DailySalesDay[]; yearAgo: DailySalesDay[] }) {
  const before = new Map(yearAgo.map((d) => [d.date, d]));
  let now = 0;
  let then = 0;
  let paired = 0;
  for (const d of days) {
    const a = sales(d);
    const b = sales(before.get(yearAgoOf(d.date)));
    if (a == null || b == null) continue;
    now += a;
    then += b;
    paired++;
  }
  if (paired === 0) {
    return <p className="mb-2 text-xs opacity-60">Year-ago days are still filling in from Salon Ultimate, a few at a time.</p>;
  }
  const c = change(now, then);
  return (
    <p className="mb-2 text-sm">
      <b className="tabular-nums">{money(now)}</b> over {paired} {paired === 1 ? 'day' : 'days'} vs{' '}
      <span className="tabular-nums">{money(then)}</span> on the same weekdays a year ago{c ? <>, <b>{c}</b></> : null}.
    </p>
  );
}

/** Two bars per day, a year ago then this year, oldest day on the left. Closed days and days that didn't save have no bar. */
function SalesChart({ days, yearAgo, today }: { days: DailySalesDay[]; yearAgo: DailySalesDay[]; today: string }) {
  const [hover, setHover] = useState<string | null>(null);
  const byDate = new Map(days.map((d) => [d.date, d]));
  const before = new Map(yearAgo.map((d) => [d.date, d]));
  const dates = Array.from({ length: 15 }, (_, i) => addDays(today, i - 14));
  const max = Math.max(1, ...[...days, ...yearAgo].map((d) => sales(d) ?? 0));
  const height = (v: number | null) => (v ? Math.max(2, (v / max) * 100) : 0);
  const hovered = hover ? byDate.get(hover) : undefined;
  const hoveredBefore = hover ? before.get(yearAgoOf(hover)) : undefined;
  const nowSales = sales(hovered);
  const thenSales = sales(hoveredBefore);
  const hoverChange = nowSales != null && thenSales != null ? change(nowSales, thenSales) : null;

  return (
    <div>
      <div className="relative">
        {/* Recessive guide: the top of the scale, the best day (its amount is under the chart). */}
        <div className="pointer-events-none absolute inset-x-0 top-0 border-t border-dashed border-[var(--border)]" />
        <div className="flex h-36 items-end gap-1.5 border-b border-[var(--border)]" onMouseLeave={() => setHover(null)}>
          {dates.map((date) => {
            const now = sales(byDate.get(date));
            const then = sales(before.get(yearAgoOf(date)));
            const on = hover === date;
            return (
              // The hit area is the whole column, taller and wider than the bars.
              <div key={date} className={`flex h-full min-w-0 flex-1 cursor-default items-end gap-[2px] rounded-t ${on ? 'bg-[var(--surface-2)]' : ''}`}
                onMouseEnter={() => setHover(date)} onClick={() => setHover(date)}
                aria-label={`${longDayLabel(date)}: ${now != null ? money(now) : statusWord(byDate.get(date), date, today)}; a year ago ${then != null ? money(then) : 'no numbers'}`}>
                <div className="w-1/2 rounded-t" style={{ height: `${height(then)}%`, background: YEAR_AGO_COLOR }} />
                <div className="w-1/2 rounded-t" style={{ height: `${height(now)}%`, background: NOW_COLOR }} />
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
      <div className="mt-1 min-h-14 rounded-lg bg-[var(--surface-2)] px-2.5 py-1.5 text-xs">
        {hover ? (
          <>
            <div>
              <b>{longDayLabel(hover)}</b>{' '}
              {nowSales != null && hovered ? (
                <>
                  <span className="font-semibold tabular-nums">{money(nowSales)}</span>
                  <span className="opacity-70">
                    {' '}· {money(hovered.netServiceSales ?? 0)} services · {money(hovered.retailSales ?? 0)} retail
                    {hovered.clients != null && <> · {hovered.clients} clients</>}
                  </span>
                </>
              ) : (
                <span className="opacity-70">{statusWord(hovered, hover, today)}</span>
              )}
            </div>
            <div className="opacity-80">
              A year ago, {withYear(yearAgoOf(hover))}:{' '}
              {thenSales != null && hoveredBefore ? (
                <>
                  <span className="tabular-nums">{money(thenSales)}</span>
                  {hoveredBefore.clients != null && <> · {hoveredBefore.clients} clients</>}
                  {hoverChange && <b> · {hoverChange}</b>}
                </>
              ) : (
                <span className="opacity-70">{hoveredBefore ? statusWord(hoveredBefore, '', today) : 'not pulled yet'}</span>
              )}
            </div>
          </>
        ) : (
          <span className="opacity-60">Point at a day to compare it with the same weekday a year ago.</span>
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

function SalesTable({ days, yearAgo }: { days: DailySalesDay[]; yearAgo: DailySalesDay[] }) {
  const before = new Map(yearAgo.map((d) => [d.date, d]));
  const rows = [...days].reverse();
  if (rows.length === 0) return <p className="text-sm opacity-70">No days saved yet.</p>;
  return (
    <div className="max-h-64 overflow-y-auto">
      <table className="w-full text-sm tabular-nums">
        <thead className="sticky top-0 bg-[var(--surface)]">
          <tr className="text-left text-xs uppercase tracking-wide opacity-60">
            <th className="py-1">Day</th>
            <th className="py-1 text-right">Sales</th>
            <th className="py-1 text-right">A year ago</th>
            <th className="py-1 text-right">Change</th>
            <th className="py-1 text-right">Clients</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-[var(--border)]">
          {rows.map((d) => {
            const prev = before.get(yearAgoOf(d.date));
            const now = sales(d);
            const then = sales(prev);
            return (
              <tr key={d.date}>
                <td className="py-1">{longDayLabel(d.date)}</td>
                <td className="py-1 text-right font-semibold">
                  {now != null ? money(now) : <span className="font-normal opacity-60">{statusWord(d, d.date, '')}</span>}
                </td>
                <td className="py-1 text-right" title={withYear(yearAgoOf(d.date))}>{then != null ? money(then) : '-'}</td>
                <td className="py-1 text-right">{now != null && then != null ? change(now, then) ?? '-' : '-'}</td>
                <td className="py-1 text-right">
                  {d.clients ?? '-'}{prev?.clients != null && <span className="opacity-60"> / {prev.clients}</span>}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      <p className="mt-1 text-[11px] opacity-60">A year ago is the same weekday 52 weeks earlier. Clients: this year / a year ago.</p>
    </div>
  );
}
