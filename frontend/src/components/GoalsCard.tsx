'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { api, type GoalDayNeed, type GoalLine, type GoalsView } from '@/src/lib/api';
import { money, moneyCents } from '@/src/lib/format';
import { addDays, longDayLabel, todayIso } from '@/src/lib/schedule';

const count = (n: number) => (Math.round(n * 10) / 10).toLocaleString('en-US');
const WEEKDAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const weekday = (iso: string) => WEEKDAYS[new Date(`${iso}T12:00:00`).getDay()];

/**
 * Home: today's goal first, big, then the month so far. Each remaining day's share
 * is weighted by stylist hours (scheduled, or that weekday's usual), so busy
 * Saturdays carry more than Sundays. Who sees it is set in Settings.
 */
export default function GoalsCard({ isOwner }: { isOwner: boolean }) {
  const [data, setData] = useState<GoalsView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showDays, setShowDays] = useState(false);

  useEffect(() => {
    api.goals().then(setData).catch((e) => setError(e.message));
  }, []);

  if (error) return isOwner ? <section className="card p-5 text-sm text-[var(--danger)]">{error}</section> : null;
  if (!data) return null;
  if (!data.hasGoals) {
    return isOwner ? (
      <section className="card p-5 text-sm">
        <h2 className="text-lg font-extrabold">{data.monthLabel} goals</h2>
        <p className="mt-1 opacity-70">No goals set for {data.monthLabel}. <Link href="/admin/settings" className="font-semibold underline">Set them in Settings</Link>.</p>
      </section>
    ) : null;
  }

  const today = todayIso();
  const day = data.days.find((d) => d.hours > 0) ?? null;
  const counts = data.lines.filter((l) => l.key !== 'PPG');
  const ppg = data.lines.find((l) => l.key === 'PPG') ?? null;
  const dayName = !day ? null : day.date === today ? 'Today' : day.date === addDays(today, 1) ? 'Tomorrow' : weekday(day.date);

  return (
    <section className="card overflow-hidden p-0">
      {/* The day's goal: the first thing anyone sees. */}
      {day ? (
        <div className="bg-[var(--accent)] px-5 py-4 text-[var(--accent-ink)]">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 className="text-lg font-extrabold">{dayName}&apos;s goal</h2>
            <span className="text-sm font-semibold opacity-80">{longDayLabel(day.date)}</span>
          </div>
          <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-4">
            {counts.map((l) => (
              <DayTile key={l.key} label={l.label} met={l.met}
                value={l.key === 'SALES' ? (day.sales == null ? '-' : money(day.sales))
                  : l.key === 'WAXES' ? String(day.waxes ?? '-') : String(day.treatments ?? '-')} />
            ))}
            {ppg && (
              <DayTile label="Product per guest" met={ppg.perDay === 0}
                value={ppg.perDay == null ? moneyCents(ppg.goal) : moneyCents(Math.max(ppg.perDay, 0))} />
            )}
          </div>
          <p className="mt-2 text-xs opacity-80">
            Based on {count(day.hours)} {day.scheduled ? 'scheduled' : 'usual'} stylist {day.hours === 1 ? 'hour' : 'hours'} that day, so busier days carry more.
          </p>
        </div>
      ) : (
        <div className="bg-[var(--accent)] px-5 py-4 text-[var(--accent-ink)]">
          <h2 className="text-lg font-extrabold">{data.monthLabel} goals</h2>
          <p className="mt-1 text-sm opacity-80">No open days left this month.</p>
        </div>
      )}

      <div className="space-y-3 p-5">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h3 className="text-sm font-bold">{data.monthLabel} so far</h3>
          <span className="text-xs opacity-60">
            {data.daysLeft} open {data.daysLeft === 1 ? 'day' : 'days'} left
          </span>
        </div>
        {counts.map((l) => <Line key={l.key} line={l} />)}
        {ppg && (
          <div>
            <div className="flex flex-wrap items-baseline justify-between gap-2 text-sm">
              <span className="font-semibold">{ppg.label}</span>
              <span className="tabular-nums">
                <b>{ppg.done == null ? '-' : moneyCents(ppg.done)}</b> <span className="opacity-60">so far, goal {moneyCents(ppg.goal)}</span>
              </span>
            </div>
            <Bar value={ppg.done ?? 0} goal={ppg.goal} met={ppg.met} />
          </div>
        )}

        {data.days.length > 0 && counts.length > 0 && (
          <div>
            <button className="text-xs font-semibold hover:underline" onClick={() => setShowDays(!showDays)}>
              {showDays ? 'Hide each day' : 'Show each day left'}
            </button>
            {showDays && <DaysTable days={data.days} lines={counts} today={today} />}
          </div>
        )}

        <p className="text-xs opacity-60">
          {data.countsThrough == null
            ? 'Waxes and treatments are loading from Salon Ultimate; check back in a minute. '
            : `Waxes and treatments through ${longDayLabel(data.countsThrough)}. `}
          {data.daysNotIn > 0 && `${data.daysNotIn} earlier ${data.daysNotIn === 1 ? 'day isn’t' : 'days aren’t'} in yet, so sales so far may be low.`}
        </p>
      </div>
    </section>
  );
}

function DayTile({ label, value, met }: { label: string; value: string; met: boolean }) {
  return (
    <div className="rounded-lg bg-[var(--surface)] px-3 py-2 text-[var(--foreground)]">
      <div className="text-[11px] font-semibold uppercase leading-tight tracking-wide opacity-60">{label}</div>
      <div className="mt-0.5 text-2xl font-extrabold tabular-nums">{met ? 'Met' : value}</div>
    </div>
  );
}

function Line({ line }: { line: GoalLine }) {
  const fmt = (n: number) => (line.money ? money(n) : count(n));
  return (
    <div>
      <div className="flex flex-wrap items-baseline justify-between gap-2 text-sm">
        <span className="font-semibold">{line.label}</span>
        <span className="tabular-nums">
          <b>{fmt(line.done ?? 0)}</b> <span className="opacity-60">of {fmt(line.goal)}</span>
          {!line.met && line.left != null && <span className="opacity-60">, {fmt(line.left)} left</span>}
          {line.met && <span className="font-semibold text-[var(--success)]"> Met</span>}
        </span>
      </div>
      <Bar value={line.done ?? 0} goal={line.goal} met={line.met} />
    </div>
  );
}

function Bar({ value, goal, met }: { value: number; goal: number; met: boolean }) {
  const pct = goal > 0 ? Math.min(100, (value / goal) * 100) : 0;
  return (
    <div className="mt-1 h-2 overflow-hidden rounded-full bg-[var(--surface-2)]">
      <div className={`h-full rounded-full ${met ? 'bg-[var(--success)]' : 'bg-[var(--accent)]'}`} style={{ width: `${pct}%` }} />
    </div>
  );
}

function DaysTable({ days, lines, today }: { days: GoalDayNeed[]; lines: GoalLine[]; today: string }) {
  const has = (k: GoalLine['key']) => lines.some((l) => l.key === k);
  return (
    <div className="mt-2 max-h-72 overflow-y-auto">
      <table className="w-full text-sm tabular-nums">
        <thead className="sticky top-0 bg-[var(--surface)]">
          <tr className="text-left text-xs uppercase tracking-wide opacity-60">
            <th className="py-1">Day</th>
            <th className="py-1 text-right">Hours</th>
            {has('SALES') && <th className="py-1 text-right">Sales</th>}
            {has('WAXES') && <th className="py-1 text-right">Waxes</th>}
            {has('TREATMENTS') && <th className="py-1 text-right">Treatments</th>}
          </tr>
        </thead>
        <tbody className="divide-y divide-[var(--border)]">
          {days.map((d) => (
            <tr key={d.date} className={d.hours === 0 ? 'opacity-50' : ''}>
              <td className="py-1">{d.date === today ? 'Today' : longDayLabel(d.date)}</td>
              <td className="py-1 text-right">
                {d.hours === 0 ? 'Closed' : count(d.hours)}
                {d.hours > 0 && <span className="ml-1 text-[10px] opacity-60">{d.scheduled ? 'scheduled' : 'usual'}</span>}
              </td>
              {has('SALES') && <td className="py-1 text-right">{d.sales == null ? '-' : money(d.sales)}</td>}
              {has('WAXES') && <td className="py-1 text-right">{d.waxes ?? '-'}</td>}
              {has('TREATMENTS') && <td className="py-1 text-right">{d.treatments ?? '-'}</td>}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
