'use client';

import { Suspense, useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { api, type RosterEntry, type ShiftInput, type WeekSchedule } from '@/src/lib/api';
import { roleLabel } from '@/src/lib/roles';
import { SALON } from '@/src/lib/salon';
import {
  addDays, dayLabel, hhmm, hoursLabel, shiftHours, shiftLabel, startsReceptionists, todayIso, weekDates, weekRangeLabel, weekStartOf, weekday,
} from '@/src/lib/schedule';

type Cell = { start: string; end: string; note: string };
const key = (userId: number, date: string) => `${userId}|${date}`;

export default function EditSchedulePage() {
  // useSearchParams needs a Suspense boundary in the app router.
  return (
    <Suspense fallback={<p className="opacity-60">Loading…</p>}>
      <ScheduleEditor />
    </Suspense>
  );
}

/** Owners and general managers fill in a Saturday–Friday week and post it to staff. */
function ScheduleEditor() {
  const router = useRouter();
  const params = useSearchParams();
  const weekStart = weekStartOf(params.get('week') ?? todayIso());

  const [staff, setStaff] = useState<RosterEntry[] | null>(null);
  const [week, setWeek] = useState<WeekSchedule | null>(null);
  const [cells, setCells] = useState<Record<string, Cell>>({});
  const [editing, setEditing] = useState<string | null>(null);
  const [dirty, setDirty] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  // Load the week (its roster is the Name column) whenever the week changes.
  useEffect(() => {
    let cancelled = false;
    api.schedule(weekStart)
      .then((w) => {
        if (cancelled) return;
        // Former employees can't be saved onto a schedule, so they're left off the editor.
        const active = w.roster.filter((r) => r.active);
        const ids = new Set(active.map((m) => m.userId));
        const next: Record<string, Cell> = {};
        for (const s of w.shifts) {
          if (ids.has(s.userId)) next[key(s.userId, s.date)] = { start: hhmm(s.start), end: hhmm(s.end), note: s.note ?? '' };
        }
        setStaff(active);
        setWeek(w);
        setCells(next);
        setDirty(false);
        setEditing(null);
        setError(null);
        setNotice(null);
      })
      .catch((e) => { if (!cancelled) setError(e instanceof Error ? e.message : 'Could not load the schedule'); });
    return () => { cancelled = true; };
  }, [weekStart]);

  // Browser "leave site?" prompt while there are unsaved changes.
  useEffect(() => {
    if (!dirty) return;
    const handler = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [dirty]);

  function goToWeek(start: string) {
    if (dirty && !confirm('You have unsaved changes on this week. Leave without saving?')) return;
    router.replace(`/schedule/edit?week=${start}`);
  }

  function update(k: string, cell: Cell | null) {
    setCells((c) => {
      const next = { ...c };
      if (cell) next[k] = cell; else delete next[k];
      return next;
    });
    setDirty(true);
    setNotice(null);
  }

  function openCell(userId: number, date: string) {
    const k = key(userId, date);
    if (!cells[k]) {
      const [open, close] = SALON.openClose[weekday(date)];
      update(k, { start: open, end: close, note: '' });
    }
    setEditing(k);
  }

  async function copyLastWeek() {
    if (Object.keys(cells).length && !confirm('Replace this week with last week’s shifts?')) return;
    try {
      const prev = await api.schedule(addDays(weekStart, -7));
      const ids = new Set((staff ?? []).map((m) => m.userId));
      const next: Record<string, Cell> = {};
      for (const s of prev.shifts) {
        if (ids.has(s.userId)) next[key(s.userId, addDays(s.date, 7))] = { start: hhmm(s.start), end: hhmm(s.end), note: s.note ?? '' };
      }
      setCells(next);
      setDirty(true);
      setEditing(null);
      setNotice(prev.shifts.length ? 'Copied last week. Review it, then save.' : 'Last week had no shifts to copy.');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not copy last week');
    }
  }

  async function save(): Promise<boolean> {
    setBusy(true);
    setError(null);
    try {
      const shifts: ShiftInput[] = Object.entries(cells).map(([k, c]) => {
        const [userId, date] = k.split('|');
        return { userId: Number(userId), date, start: c.start, end: c.end, note: c.note || null };
      });
      setWeek(await api.saveSchedule(weekStart, shifts));
      setDirty(false);
      setEditing(null);
      setNotice('Saved.');
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save');
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function setPosted(published: boolean) {
    if (published && dirty && !(await save())) return;
    setBusy(true);
    try {
      setWeek(await api.publishSchedule(weekStart, published));
      setNotice(published ? 'Posted. Staff can see this week now.' : 'Taken down. Staff can’t see this week.');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not update');
    } finally {
      setBusy(false);
    }
  }

  const days = weekDates(weekStart);
  if (!staff || !week) return <p className="opacity-60">{error ?? 'Loading…'}</p>;

  return (
    <div>
      <Link href="/schedule" className="text-sm opacity-70 hover:opacity-100">Back to salon schedule</Link>
      <div className="mt-3 flex flex-wrap items-center gap-3">
        <h1 className="text-3xl font-extrabold tracking-tight">Edit schedule</h1>
        <span className={`rounded-full px-3 py-1 text-xs font-bold ${
          week.published ? 'bg-[var(--success-soft)] text-[var(--success)]' : 'bg-amber-100 text-[var(--warn)]'
        }`}>
          {week.published ? 'Posted' : 'Draft'}
        </span>
        {dirty && <span className="text-sm font-semibold text-[var(--warn)]">Unsaved changes</span>}
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-2">
        <button className="btn-ghost" onClick={() => goToWeek(addDays(weekStart, -7))}>Previous week</button>
        <button className="btn-ghost" onClick={() => goToWeek(addDays(weekStart, 7))}>Next week</button>
        <span className="ml-2 text-lg font-extrabold">{weekRangeLabel(weekStart)}</span>
      </div>

      <div className="mt-4 flex flex-wrap gap-2">
        <button className="btn-ghost" onClick={() => void copyLastWeek()} disabled={busy}>Copy last week</button>
        <button
          className="btn-ghost"
          onClick={() => { if (confirm('Clear every shift this week?')) { setCells({}); setDirty(true); setEditing(null); } }}
          disabled={busy}
        >
          Clear week
        </button>
        <span className="flex-1" />
        <button className="btn-ghost" onClick={() => void save()} disabled={busy || !dirty}>Save</button>
        {week.published ? (
          <button className="btn-ghost" onClick={() => void setPosted(false)} disabled={busy}>Take down</button>
        ) : (
          <button className="btn" onClick={() => void setPosted(true)} disabled={busy}>
            {dirty ? 'Save and post to staff' : 'Post to staff'}
          </button>
        )}
      </div>
      {week.published && dirty && (
        <p className="mt-2 text-sm opacity-70">This week is already posted. Staff will see your changes as soon as you save.</p>
      )}

      {error && <p className="mt-4 text-sm text-[var(--danger)]">{error}</p>}
      {notice && <p className="mt-4 text-sm font-semibold text-[var(--success)]">{notice}</p>}

      <div className="card mt-4 overflow-x-auto">
        <table className="w-full min-w-[900px] text-left text-sm">
          <thead className="border-b border-[var(--border)] text-xs uppercase tracking-wide opacity-60">
            <tr>
              <th className="px-4 py-3 font-semibold">Name</th>
              {days.map((d) => (
                <th key={d} className="px-2 py-3 font-semibold">
                  {dayLabel(d)}
                  <div className="font-normal normal-case">{SALON.hoursByDay[weekday(d)] || 'Closed'}</div>
                </th>
              ))}
              <th className="px-3 py-3 text-right font-semibold">Hours</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[var(--border)]">
            {staff.map((m, i) => {
              const total = days.reduce((h, d) => {
                const c = cells[key(m.userId, d)];
                return h + (c ? shiftHours(c.start, c.end) : 0);
              }, 0);
              return (
                <tr key={m.userId} className={startsReceptionists(staff, i) ? 'border-t-2 border-t-[var(--accent)]/40' : ''}>
                  <td className="px-4 py-2">
                    <div className="font-bold">{m.name}</div>
                    <div className="text-xs opacity-60">{roleLabel(m.role)}</div>
                  </td>
                  {days.map((d) => {
                    const k = key(m.userId, d);
                    const c = cells[k];
                    if (editing === k && c) {
                      return (
                        <td key={d} className="bg-[var(--surface-2)] px-2 py-2 align-top">
                          <div className="flex flex-col gap-1">
                            <input type="time" step={900} className="input px-2 py-1 text-xs" value={c.start}
                              aria-label="Start" onChange={(e) => update(k, { ...c, start: e.target.value })} />
                            <input type="time" step={900} className="input px-2 py-1 text-xs" value={c.end}
                              aria-label="End" onChange={(e) => update(k, { ...c, end: e.target.value })} />
                            <input className="input px-2 py-1 text-xs" value={c.note} placeholder="Note" maxLength={100}
                              aria-label="Note" onChange={(e) => update(k, { ...c, note: e.target.value })} />
                            <div className="flex gap-1">
                              <button className="flex-1 rounded-full bg-[var(--accent)] px-2 py-1 text-xs font-bold text-white"
                                onClick={() => setEditing(null)}>Done</button>
                              <button className="flex-1 rounded-full border border-[var(--border)] bg-[var(--surface)] px-2 py-1 text-xs font-bold"
                                onClick={() => { update(k, null); setEditing(null); }}>Off</button>
                            </div>
                          </div>
                        </td>
                      );
                    }
                    const invalid = c && shiftHours(c.start, c.end) <= 0;
                    return (
                      <td key={d} className="px-1 py-1 align-top">
                        <button
                          onClick={() => openCell(m.userId, d)}
                          className={`w-full rounded-lg px-2 py-2 text-left hover:bg-[var(--surface-2)] ${
                            c ? (invalid ? 'bg-red-50 text-[var(--danger)]' : 'bg-[var(--accent-soft)]') : ''
                          }`}
                        >
                          {c ? (
                            <>
                              <div className="whitespace-nowrap text-xs font-bold">{shiftLabel(c.start, c.end)}</div>
                              {c.note && <div className="truncate text-xs opacity-60">{c.note}</div>}
                            </>
                          ) : (
                            <span className="text-xs opacity-30">Off</span>
                          )}
                        </button>
                      </td>
                    );
                  })}
                  <td className="px-3 py-2 text-right font-semibold">{total ? hoursLabel(total) : ''}</td>
                </tr>
              );
            })}
          </tbody>
          <tfoot className="border-t border-[var(--border)] text-xs opacity-70">
            <tr>
              <td className="px-4 py-2 font-semibold">Working</td>
              {days.map((d) => (
                <td key={d} className="px-2 py-2">{staff.filter((m) => cells[key(m.userId, d)]).length}</td>
              ))}
              <td />
            </tr>
          </tfoot>
        </table>
      </div>
      <p className="mt-3 text-xs opacity-60">
        Click a day to add or change a shift. New shifts start with the salon&apos;s hours for that day.
      </p>
    </div>
  );
}
