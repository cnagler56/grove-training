'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { api, type ShiftView, type WeekSchedule } from '@/src/lib/api';
import { roleLabel } from '@/src/lib/roles';
import {
  addDays, dayLabel, entryHours, hoursLabel, isRequestedOff, shiftLabel, startsReceptionists, todayIso, weekDates, weekRangeLabel, weekStartOf,
  workingThisWeek,
} from '@/src/lib/schedule';
import { useUser } from '@/src/lib/UserContext';

/**
 * The full salon schedule, one Saturday–Friday week at a time. Laid out like
 * the paper form: every scheduled person down the left, days across the top.
 */
export default function SalonSchedulePage() {
  const { user } = useUser();
  const [weekStart, setWeekStart] = useState(() => weekStartOf(todayIso()));
  const [week, setWeek] = useState<WeekSchedule | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.schedule(weekStart).then(setWeek).catch((e) => setError(e.message));
  }, [weekStart]);
  // While a newly picked week loads, the old one is still in state, so show it as loading.
  const current = week && week.weekStart === weekStart ? week : null;

  const shiftsByCell = useMemo(() => {
    const m = new Map<string, ShiftView>();
    for (const s of current?.shifts ?? []) m.set(`${s.userId}|${s.date}`, s);
    return m;
  }, [current]);

  // Only people with hours this week; the editor still lists everyone.
  const people = useMemo(() => (current ? workingThisWeek(current.roster, current.shifts) : []), [current]);

  const days = weekDates(weekStart);
  const today = todayIso();
  const thisWeek = weekStartOf(today);
  // Builders and approvers see drafts and can open the editor.
  const manages = !!current && (current.canEdit || current.canApprove);
  const visible = current && (current.published || manages);
  const status = current && ({
    DRAFT: ['Draft (staff can’t see it yet)', 'bg-amber-100 text-[var(--warn)]'],
    PENDING: [`Waiting for approval${current.submittedToName ? ` from ${current.submittedToName}` : ''}`, 'bg-[var(--accent-soft)] text-[var(--accent-strong)]'],
    POSTED: ['Posted', 'bg-[var(--success-soft)] text-[var(--success)]'],
  } as const)[current.state];

  return (
    <div>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight">Salon schedule</h1>
          <p className="mt-1 text-sm opacity-70">Weeks run Saturday through Friday.</p>
        </div>
        <div className="flex gap-2">
          {visible && (
            <Link href={`/schedule/print?week=${weekStart}`} className="btn">Print schedule</Link>
          )}
          {manages && (
            <Link href={`/schedule/edit?week=${weekStart}`} className="btn-ghost">
              {current?.state === 'PENDING' && current.canApprove ? 'Review this week' : 'Edit this week'}
            </Link>
          )}
        </div>
      </div>

      <div className="mt-6 flex flex-wrap items-center gap-2">
        <button className="btn-ghost" onClick={() => setWeekStart(addDays(weekStart, -7))}>Previous week</button>
        <button className="btn-ghost" onClick={() => setWeekStart(thisWeek)} disabled={weekStart === thisWeek}>This week</button>
        <button className="btn-ghost" onClick={() => setWeekStart(addDays(weekStart, 7))}>Next week</button>
        <span className="ml-2 text-lg font-extrabold">Week of {weekRangeLabel(weekStart)}</span>
        {manages && status && (
          <span className={`rounded-full px-3 py-1 text-xs font-bold ${status[1]}`}>{status[0]}</span>
        )}
      </div>

      {error && <p className="mt-4 text-sm text-[var(--danger)]">{error}</p>}

      {!current ? (
        <p className="mt-6 opacity-60">Loading…</p>
      ) : !visible ? (
        <div className="card mt-6 p-6 text-center opacity-80">This week’s schedule hasn’t been posted yet.</div>
      ) : people.length === 0 ? (
        <div className="card mt-6 p-6 text-center opacity-80">No one has hours this week yet.</div>
      ) : (
        <div className="card mt-6 overflow-x-auto">
          <table className="w-full min-w-[760px] text-left text-sm">
            <thead className="border-b border-[var(--border)] text-xs uppercase tracking-wide">
              <tr>
                <th className="px-4 py-3 font-bold opacity-70">Name</th>
                {days.map((d) => (
                  <th key={d} className={`px-2 py-3 text-center font-bold ${d === today ? 'text-[var(--accent-strong)]' : 'opacity-70'}`}>
                    {dayLabel(d)}
                  </th>
                ))}
                <th className="px-3 py-3 text-right font-bold opacity-70">Hours</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[var(--border)]">
              {people.map((person, i) => {
                const mine = person.userId === user?.userId;
                const total = days.reduce((h, d) => {
                  const s = shiftsByCell.get(`${person.userId}|${d}`);
                  return h + (s ? entryHours(s) : 0);
                }, 0);
                return (
                  <tr
                    key={person.userId}
                    className={`${mine ? 'bg-[var(--accent-soft)]' : ''} ${
                      startsReceptionists(people, i) ? 'border-t-2 border-t-[var(--accent)]/40' : ''
                    }`}
                  >
                    <td className="px-4 py-3">
                      <div className="font-bold">{person.name}{mine && <span className="font-normal opacity-60"> (you)</span>}</div>
                      <div className="text-xs opacity-60">{person.active ? roleLabel(person.role) : 'No longer on staff'}</div>
                    </td>
                    {days.map((d) => {
                      const s = shiftsByCell.get(`${person.userId}|${d}`);
                      return (
                        <td key={d} className={`px-2 py-3 text-center align-top ${d === today ? 'bg-[var(--surface-2)]/60' : ''}`}>
                          {s ? (
                            <>
                              {isRequestedOff(s) ? (
                                <span className="rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-bold text-[var(--warn)]" title="Requested off">RO</span>
                              ) : (
                                <div className="whitespace-nowrap font-semibold">{shiftLabel(s.start!, s.end!)}</div>
                              )}
                              {s.note && <div className="text-xs opacity-60">{s.note}</div>}
                            </>
                          ) : (
                            <span className="opacity-25">Off</span>
                          )}
                        </td>
                      );
                    })}
                    <td className="px-3 py-3 text-right font-semibold">{total ? hoursLabel(total) : ''}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
