'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { api, type StatLine, type TeamStatRow, type TeamStats } from '@/src/lib/api';
import { formatDate, hours, money, moneyCents } from '@/src/lib/format';
import { addDays, weekRangeLabel } from '@/src/lib/schedule';

/** Below this many floor hours, "per hour" is too noisy to rank. */
const MIN_HOURS_FOR_RATE = 5;
/** How many people each leaderboard card shows. */
const BOARD_SIZE = 5;

type Column = {
  key: string;
  label: string;
  value: (s: StatLine) => number | null;
  format: (n: number) => string;
};

const COLUMNS: Column[] = [
  { key: 'service', label: 'Service', value: (s) => s.serviceSales, format: money },
  { key: 'net', label: 'After discounts', value: (s) => s.netServiceSales, format: money },
  { key: 'retail', label: 'Retail', value: (s) => s.retailSales, format: money },
  { key: 'ppg', label: 'Per guest', value: (s) => s.productPerGuest, format: moneyCents },
  { key: 'rate', label: 'Per hour', value: (s) => s.serviceSalesPerHour, format: moneyCents },
  { key: 'hours', label: 'Floor hrs', value: (s) => s.floorHours, format: hours },
  { key: 'off', label: 'Off floor', value: (s) => s.offFloorHours, format: hours },
];

const BOARDS: { title: string; column: string; note?: string }[] = [
  { title: 'Service sales', column: 'service' },
  { title: 'Retail sales', column: 'retail' },
  { title: 'Product per guest', column: 'ppg' },
  { title: 'Service sales per hour', column: 'rate', note: `${MIN_HOURS_FOR_RATE}+ floor hours` },
];

const column = (key: string) => COLUMNS.find((c) => c.key === key)!;

/**
 * Everyone's numbers from Salon Ultimate for one Saturday–Friday week:
 * top-five leaderboards plus the full table. Managers and up only.
 */
export default function LeaderboardsPage() {
  const [week, setWeek] = useState<string | undefined>(undefined);
  const [data, setData] = useState<TeamStats | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pulling, setPulling] = useState(false);
  const [sort, setSort] = useState('service');

  const load = useCallback((w?: string) => {
    api.teamStats(w).then((d) => { setData(d); setError(null); }).catch((e) => setError(e.message));
  }, []);
  useEffect(() => { load(week); }, [week, load]);

  const ranked = useMemo(() => {
    const c = column(sort);
    return [...(data?.rows ?? [])].sort((a, b) => (c.value(b.stats) ?? -1) - (c.value(a.stats) ?? -1));
  }, [data, sort]);

  async function pull() {
    if (!data) return;
    setPulling(true);
    setError(null);
    try {
      await api.pullStats(data.weekStart);
      load(data.weekStart);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not pull the week');
    } finally {
      setPulling(false);
    }
  }

  if (!data) return <p className="opacity-60">{error ?? 'Loading…'}</p>;

  const shown = data.weekStart;
  const isLatest = shown === data.latestCompleteWeek;
  const rows = data.rows;
  const last = data.lastImport;
  const unlinked = rows.filter((r) => r.userId === null);
  const total = (key: string) => rows.reduce((sum, r) => sum + (column(key).value(r.stats) ?? 0), 0);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight">Leaderboards</h1>
          <p className="mt-1 text-sm opacity-70">From Salon Ultimate. Each week is pulled automatically on Saturday morning.</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <button className="btn-ghost" onClick={() => setWeek(addDays(shown, -7))}>Previous week</button>
          <button className="btn-ghost" disabled={isLatest} onClick={() => setWeek(addDays(shown, 7))}>Next week</button>
          {!isLatest && <button className="btn-ghost" onClick={() => setWeek(undefined)}>Latest</button>}
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-xl font-extrabold">Week of {weekRangeLabel(shown)}</h2>
        {data.configured && (
          <button className="btn" onClick={() => void pull()} disabled={pulling}>
            {pulling ? 'Pulling from Salon Ultimate…' : rows.length ? 'Pull again' : 'Pull this week'}
          </button>
        )}
      </div>

      {error && <p className="text-sm text-[var(--danger)]">{error}</p>}
      {!data.configured && (
        <p className="card p-4 text-sm">
          Salon Ultimate sign-in isn&apos;t set up on the server yet, so weeks can&apos;t be pulled.
        </p>
      )}
      {last && last.status !== 'OK' && (
        <p className="card border-2 border-[var(--danger)] p-4 text-sm">
          <b>Last try {formatDate(last.attemptedAt)}:</b> {last.message}
        </p>
      )}

      {rows.length === 0 ? (
        <p className="card p-5 text-sm opacity-80">
          No numbers for this week{data.configured ? '. Use Pull this week to get them from Salon Ultimate.' : '.'}
        </p>
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-2">
            {BOARDS.map((b) => {
              const c = column(b.column);
              const eligible = rows.filter((r) => c.value(r.stats) !== null
                && (b.column !== 'rate' || r.stats.floorHours >= MIN_HOURS_FOR_RATE));
              const top = eligible.sort((x, y) => c.value(y.stats)! - c.value(x.stats)!).slice(0, BOARD_SIZE);
              return (
                <section key={b.title} className="card p-5">
                  <div className="flex items-baseline justify-between gap-2">
                    <h3 className="text-lg font-extrabold">{b.title}</h3>
                    {b.note && <span className="text-xs opacity-60">{b.note}</span>}
                  </div>
                  <ol className="mt-2 divide-y divide-[var(--border)] text-sm">
                    {top.map((r, i) => (
                      <li key={r.suName} className="flex items-center justify-between gap-3 py-1.5">
                        <span className="min-w-0 truncate">
                          <span className="inline-block w-6 font-bold text-[var(--accent-strong)]">{i + 1}</span>
                          <Name row={r} />
                        </span>
                        <span className="font-semibold">{c.format(c.value(r.stats)!)}</span>
                      </li>
                    ))}
                  </ol>
                </section>
              );
            })}
          </div>

          <section className="card overflow-x-auto">
            <div className="px-5 pt-5">
              <h3 className="text-lg font-extrabold">Everyone</h3>
              <p className="text-xs opacity-60">Click a column to rank by it.</p>
            </div>
            <table className="mt-3 w-full text-left text-sm">
              <thead className="border-b border-[var(--border)] text-xs uppercase tracking-wide">
                <tr>
                  <th className="px-5 py-2 font-semibold opacity-60">Name</th>
                  {COLUMNS.map((c) => (
                    <th key={c.key} className="px-3 py-2 text-right font-semibold">
                      <button
                        onClick={() => setSort(c.key)}
                        className={`uppercase tracking-wide ${sort === c.key ? 'text-[var(--accent-strong)]' : 'opacity-60 hover:opacity-100'}`}
                      >
                        {c.label}
                      </button>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--border)]">
                {ranked.map((r) => (
                  <tr key={r.suName} className="hover:bg-[var(--surface-2)]">
                    <td className="whitespace-nowrap px-5 py-2"><Name row={r} /></td>
                    {COLUMNS.map((c) => {
                      const v = c.value(r.stats);
                      return (
                        <td key={c.key} className={`px-3 py-2 text-right ${sort === c.key ? 'font-semibold' : ''}`}>
                          {v === null ? '' : c.format(v)}
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
              <tfoot className="border-t-2 border-[var(--border)] font-bold">
                <tr>
                  <td className="px-5 py-2">Salon total</td>
                  <td className="px-3 py-2 text-right">{money(total('service'))}</td>
                  <td className="px-3 py-2 text-right">{money(total('net'))}</td>
                  <td className="px-3 py-2 text-right">{money(total('retail'))}</td>
                  <td className="px-3 py-2" />
                  <td className="px-3 py-2 text-right">
                    {total('hours') > 0 ? moneyCents(total('service') / total('hours')) : ''}
                  </td>
                  <td className="px-3 py-2 text-right">{hours(total('hours'))}</td>
                  <td className="px-3 py-2 text-right">{hours(total('off'))}</td>
                </tr>
              </tfoot>
            </table>
          </section>

          {unlinked.length > 0 && (
            <p className="text-sm opacity-70">
              Not linked to a site account yet: {unlinked.map((r) => r.suName).join(', ')}. Link them from their page under Team so they see
              their own numbers on My week.
            </p>
          )}
        </>
      )}
    </div>
  );
}

function Name({ row }: { row: TeamStatRow }) {
  return row.userId === null
    ? <span className="opacity-70">{row.name}</span>
    : <Link href={`/admin/team/${row.userId}`} className="font-semibold hover:underline">{row.name}</Link>;
}
