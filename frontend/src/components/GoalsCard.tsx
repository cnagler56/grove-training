'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { api, type GoalDayNeed, type GoalLine, type GoalsView } from '@/src/lib/api';
import { money, moneyCents } from '@/src/lib/format';
import { longDayLabel, todayIso } from '@/src/lib/schedule';

const count = (n: number) => (Math.round(n * 10) / 10).toLocaleString('en-US');

/**
 * Home: this month's goals, how much is done, and what's left. Each remaining day's
 * share is weighted by stylist hours (scheduled, or that weekday's usual), so busy
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
  const next = data.days.find((d) => d.hours > 0) ?? null;
  const counts = data.lines.filter((l) => l.key !== 'PPG');
  const ppg = data.lines.find((l) => l.key === 'PPG') ?? null;

  return (
    <section className="card space-y-4 p-5">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-lg font-extrabold">{data.monthLabel} goals</h2>
        <span className="text-xs opacity-60">
          {data.daysLeft === 0 ? 'No open days left' : `${data.daysLeft} open ${data.daysLeft === 1 ? 'day' : 'days'} left`}
        </span>
      </div>

      <div className="space-y-3">
        {counts.map((l) => <Line key={l.key} line={l} />)}
        {ppg && (
          <div>
            <div className="flex flex-wrap items-baseline justify-between gap-2 text-sm">
              <span className="font-bold">{ppg.label}</span>
              <span className="tabular-nums">
                <b>{ppg.done == null ? '-' : moneyCents(ppg.done)}</b> <span className="opacity-60">so far, goal {moneyCents(ppg.goal)}</span>
              </span>
            </div>
            <Bar value={ppg.done ?? 0} goal={ppg.goal} met={ppg.met} />
            <p className="mt-1 text-xs opacity-70">
              {ppg.perDay == null
                ? 'Not enough history yet to say what the rest of the month needs.'
                : ppg.perDay === 0 ? 'Met: retail so far already covers the goal.'
                : `The rest of the month needs ${moneyCents(ppg.perDay)} in retail per guest.`}
            </p>
          </div>
        )}
      </div>

      {next && counts.some((l) => !l.met) && (
        <div className="rounded-lg bg-[var(--accent-soft)] px-3 py-2 text-sm">
          <b>{next.date === today ? 'Today' : longDayLabel(next.date)} needs</b>{' '}
          {needs(next, counts).join(', ')}
          <span className="block text-xs opacity-70">
            Weighted by {next.scheduled ? 'scheduled' : 'usual'} stylist hours ({count(next.hours)}), so busier days carry more.
          </span>
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

      {(data.daysNotIn > 0 || data.daysCountsPending > 0) && (
        <p className="text-xs opacity-60">
          {data.daysNotIn > 0 && `${data.daysNotIn} earlier ${data.daysNotIn === 1 ? 'day isn’t' : 'days aren’t'} in yet, so done may be low. `}
          {data.daysCountsPending > 0 && `Waxes and treatments for ${data.daysCountsPending} ${data.daysCountsPending === 1 ? 'day are' : 'days are'} still filling in.`}
        </p>
      )}
    </section>
  );
}

function Line({ line }: { line: GoalLine }) {
  const fmt = (n: number) => (line.money ? money(n) : count(n));
  return (
    <div>
      <div className="flex flex-wrap items-baseline justify-between gap-2 text-sm">
        <span className="font-bold">{line.label}</span>
        <span className="tabular-nums">
          <b>{fmt(line.done ?? 0)}</b> <span className="opacity-60">of {fmt(line.goal)}</span>
        </span>
      </div>
      <Bar value={line.done ?? 0} goal={line.goal} met={line.met} />
      <p className="mt-1 text-xs opacity-70">
        {line.met ? 'Goal met.'
          : line.perDay == null ? `${fmt(line.left ?? 0)} left, no open days left this month.`
          : `${fmt(line.left ?? 0)} left, about ${fmt(line.perDay)} per open day.`}
      </p>
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

function needs(d: GoalDayNeed, lines: GoalLine[]) {
  const out: string[] = [];
  for (const l of lines) {
    if (l.met) continue;
    if (l.key === 'SALES' && d.sales != null) out.push(`${money(d.sales)} in sales`);
    if (l.key === 'WAXES' && d.waxes != null) out.push(`${d.waxes} ${d.waxes === 1 ? 'wax' : 'waxes'}`);
    if (l.key === 'TREATMENTS' && d.treatments != null) out.push(`${d.treatments} ${d.treatments === 1 ? 'treatment' : 'treatments'}`);
  }
  return out;
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
