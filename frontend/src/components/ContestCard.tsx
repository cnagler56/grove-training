'use client';

import type { Contest } from '@/src/lib/api';
import { formatDate, formatDateTime, ordinal } from '@/src/lib/format';

const today = () => new Date().toLocaleDateString('en-CA');

/** Whole days from today to an ISO date (0 = today). */
function daysUntil(iso: string): number {
  const [y, m, d] = iso.split('-').map(Number);
  const [ty, tm, td] = today().split('-').map(Number);
  return Math.round((Date.UTC(y, m - 1, d) - Date.UTC(ty, tm - 1, td)) / 86400000);
}

/** "On now, 4 days left", "Starts Nov 5", ... */
export function contestTiming(c: Contest): string {
  if (c.status === 'FINISHED') return `Finished ${formatDate(c.endDate, false)}`;
  if (c.status === 'ENDED') return 'Over. Final results come with the next check.';
  if (c.status === 'UPCOMING') return `Starts ${formatDate(c.startDate, false)}`;
  const left = daysUntil(c.endDate);
  return left === 0 ? 'On now, last day' : `On now, ${left + 1} days left`;
}

/** "2nd", or "Tied 2nd" when someone else has the same place. */
function placeLabel(c: Contest, rank: number | null): string {
  if (rank === null) return '';
  const tied = c.standings.filter((s) => s.rank === rank).length > 1;
  return `${tied ? 'Tied ' : ''}${ordinal(rank)}`;
}

/** One contest: its teams, then everyone's place and count, with the viewer's own row marked. */
export default function ContestCard({ contest: c, viewerId, actions }: {
  contest: Contest;
  viewerId: number | undefined;
  /** Manager buttons, shown under the title. */
  actions?: React.ReactNode;
}) {
  const handicapped = c.handicap !== 'NONE';
  const best = Math.max(1, ...c.standings.map((s) => s.points ?? 0));
  const bestTeam = Math.max(1, ...c.teams.map((t) => t.total));
  const teamName = (position: number | null) => c.teams.find((t) => t.position === position)?.name;
  const checked = c.lastCheckedAt !== null;
  const teamUnit = handicapped ? 'points' : c.metricLabel;

  return (
    <section className="card space-y-4 p-5">
      <div>
        <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
          <h2 className="text-xl font-extrabold">{c.name}</h2>
          <span className={`text-sm font-bold ${c.status === 'LIVE' ? 'text-[var(--success)]' : 'opacity-70'}`}>{contestTiming(c)}</span>
        </div>
        <p className="mt-1 text-sm opacity-80">
          Most {c.metricLabel}, {formatDate(c.startDate, false)} to {formatDate(c.endDate, false)}
          {c.teams.length > 0 && `, ${c.teams.length} teams`}
        </p>
        {handicapped && (
          <p className="mt-1 text-sm">
            <b>Handicap:</b> {c.handicap === 'HOURS' ? 'hours worked' : `usual ${c.metricLabel} per guest`}.
            {' '}Places go by points: each person&apos;s services are worth 100 to 200 points.
          </p>
        )}
        {c.prize && <p className="mt-1 text-sm"><b>Prize:</b> {c.prize}</p>}
        <p className="mt-1 text-xs opacity-60">
          {checked
            ? `Standings as of ${formatDateTime(c.lastCheckedAt)}. They update every 4 hours from Salon Ultimate.`
            : c.status === 'UPCOMING' ? 'Standings start once it begins.' : 'Not checked yet. Standings update every 4 hours from Salon Ultimate.'}
        </p>
        {c.checkProblem && <p className="mt-2 text-sm text-[var(--danger)]">Last check didn&apos;t work: {c.checkProblem}</p>}
        {actions && <div className="mt-3 flex flex-wrap gap-2">{actions}</div>}
      </div>

      {c.teams.length > 0 && (
        <div className="grid gap-3 sm:grid-cols-2">
          {c.teams.map((t) => {
            const leading = checked && t.rank === 1 && c.teams.filter((o) => o.rank === 1).length === 1;
            return (
              <div key={t.position}
                className={`rounded-xl p-4 ${leading ? 'border-2 border-[var(--accent)] bg-[var(--accent-soft)]' : 'border border-[var(--border)]'}`}>
                <div className="flex items-baseline justify-between gap-2">
                  <span className="text-lg font-extrabold">{t.name}</span>
                  {checked && t.rank !== null && <span className="text-sm font-bold">{leading ? 'In the lead' : ordinal(t.rank)}</span>}
                </div>
                <div className="mt-1 text-3xl font-extrabold">{checked ? t.total.toLocaleString('en-US') : '-'}</div>
                <div className="text-sm opacity-70">{teamUnit}, {t.members} {t.members === 1 ? 'person' : 'people'}</div>
                <div className="mt-2 h-2 rounded-full bg-[var(--surface-2)]">
                  <div className="h-2 rounded-full bg-[var(--accent)]" style={{ width: `${(t.total / bestTeam) * 100}%` }} />
                </div>
              </div>
            );
          })}
        </div>
      )}

      <ol className="divide-y divide-[var(--border)]">
        {c.standings.map((s) => {
          const me = s.userId === viewerId;
          return (
            <li key={s.userId} className={`flex items-center gap-3 px-2 py-2.5 ${me ? 'rounded-lg bg-[var(--accent-soft)]' : ''}`}>
              <span className="w-14 shrink-0 text-sm font-extrabold">{placeLabel(c, s.rank)}</span>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-baseline gap-x-2">
                  <span className="break-words font-bold">{s.name}{me && ' (you)'}</span>
                  {teamName(s.team) && <span className="text-xs font-semibold opacity-60">{teamName(s.team)}</span>}
                </div>
                {handicapped && checked && s.count !== null && (
                  <div className="text-xs opacity-70">
                    {s.count} {s.count === 1 ? c.metricLabel.replace(/(es|s)$/, '') : c.metricLabel}
                    {s.worth !== null && `, worth ${s.worth} each`}
                  </div>
                )}
                {checked && (
                  <div className="mt-1 h-1.5 rounded-full bg-[var(--surface-2)]">
                    <div className="h-1.5 rounded-full bg-[var(--accent)]" style={{ width: `${((s.points ?? 0) / best) * 100}%` }} />
                  </div>
                )}
              </div>
              <span className="w-16 shrink-0 text-right text-lg font-extrabold">
                {s.points === null ? '-' : s.points.toLocaleString('en-US')}
              </span>
            </li>
          );
        })}
      </ol>
    </section>
  );
}
