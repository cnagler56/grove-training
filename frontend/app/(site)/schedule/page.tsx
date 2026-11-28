'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { api, type WeekSchedule } from '@/src/lib/api';
import ScheduleSheet from '@/src/components/ScheduleSheet';
import { addDays, scheduledThisWeek, todayIso, weekRangeLabel, weekStartOf } from '@/src/lib/schedule';
import { useUser } from '@/src/lib/UserContext';

/**
 * The full salon schedule, one Saturday–Friday week at a time. Looks like the
 * printed schedule: every scheduled person down the left, days across the top.
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

  // Only people with a shift or RO this week; the editor still lists everyone.
  const people = useMemo(() => (current ? scheduledThisWeek(current.roster, current.shifts) : []), [current]);

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
        <div className="card mt-6 p-6 text-center opacity-80">No one is on the schedule this week yet.</div>
      ) : (
        // Same sheet as the printed schedule, plus your own row shaded, today marked, and shift notes.
        <div className="mt-6 overflow-x-auto rounded-2xl bg-white p-4 text-[#1f2a33] shadow-lg sm:p-6">
          <ScheduleSheet week={current} youId={user?.userId} today={today} notes />
        </div>
      )}
    </div>
  );
}
