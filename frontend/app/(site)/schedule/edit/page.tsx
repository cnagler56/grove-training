'use client';

import { Suspense, useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { api, type RosterEntry, type ShiftInput, type WeekSchedule } from '@/src/lib/api';
import { roleLabel } from '@/src/lib/roles';
import CoverageRows from '@/src/components/CoverageRows';
import { SALON } from '@/src/lib/salon';
import { useUser } from '@/src/lib/UserContext';
import { useDialogs } from '@/src/components/Dialogs';
import { confirmLeave, useUnsavedChangesGuard } from '@/src/lib/leaveGuard';
import {
  addDays, addMinutes, dayLabel, hhmm, hoursLabel, shiftHours, shiftLabel, startsReceptionists, TIME_STEP_MINUTES, timeLabel,
  longDayLabel, timeOptions, todayIso, weekDates, weekRangeLabel, weekStartOf, weekday,
} from '@/src/lib/schedule';

/** A day for one person: a shift, or RO (requested off: no times). */
type Cell = { start: string; end: string; note: string; off?: boolean };
/** Start-dropdown value meaning "requested off". */
const RO = "__RO__";
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
  const [approverId, setApproverId] = useState<number | null>(null);
  const { user } = useUser();
  const dialogs = useDialogs();

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
          if (ids.has(s.userId)) next[key(s.userId, s.date)] = s.type === 'REQUESTED_OFF'
            ? { start: '', end: '', note: s.note ?? '', off: true }
            : { start: hhmm(s.start ?? ''), end: hhmm(s.end ?? ''), note: s.note ?? '' };
        }
        setStaff(active);
        setWeek(w);
        setCells(next);
        // Default "send to": whoever it was last sent to, or the only approver.
        setApproverId(w.submittedToId ?? (w.approvers.length === 1 ? w.approvers[0].userId : null));
        setDirty(false);
        setEditing(null);
        setError(null);
        setNotice(null);
      })
      .catch((e) => { if (!cancelled) setError(e instanceof Error ? e.message : 'Could not load the schedule'); });
    return () => { cancelled = true; };
  }, [weekStart]);

  // Our "leave without saving?" dialog for tabs, links, and Sign out.
  useUnsavedChangesGuard(dirty, { message: 'You have unsaved changes on this week.' });

  async function goToWeek(start: string) {
    if (!(await confirmLeave())) return;
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
    if (Object.keys(cells).length && !(await dialogs.confirm({
      title: 'Copy last week?',
      message: 'This replaces everything on this week with last week’s shifts.',
      confirmLabel: 'Replace',
    }))) return;
    try {
      const prev = await api.schedule(addDays(weekStart, -7));
      const ids = new Set((staff ?? []).map((m) => m.userId));
      const next: Record<string, Cell> = {};
      for (const s of prev.shifts) {
        // Time-off requests are for specific dates, so they aren't copied forward.
        if (ids.has(s.userId) && s.type !== 'REQUESTED_OFF' && s.start && s.end) {
          next[key(s.userId, addDays(s.date, 7))] = { start: hhmm(s.start), end: hhmm(s.end), note: s.note ?? '' };
        }
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
        return c.off
          ? { userId: Number(userId), date, type: 'REQUESTED_OFF' as const, note: c.note || null }
          : { userId: Number(userId), date, type: 'WORK' as const, start: c.start, end: c.end, note: c.note || null };
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

  /** Save (if needed) and send the week to the chosen approver. */
  async function sendForApproval() {
    if (!approverId) { setError('Pick who should approve it.'); return; }
    if (dirty && !(await save())) return;
    setBusy(true);
    setError(null);
    try {
      const w = await api.submitSchedule(weekStart, approverId);
      setWeek(w);
      setNotice(`Sent to ${w.submittedToName} for approval.`);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not send');
    } finally {
      setBusy(false);
    }
  }

  async function sendBack() {
    const note = await dialogs.prompt({
      title: 'Send back',
      message: 'What should they change? They’ll see this note.',
      placeholder: 'e.g. We need a closer on Monday',
      multiline: true,
      confirmLabel: 'Send back',
    });
    if (!note) return;
    setBusy(true);
    setError(null);
    try {
      setWeek(await api.sendBackSchedule(weekStart, note));
      setNotice('Sent back with your note.');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not send back');
    } finally {
      setBusy(false);
    }
  }

  const days = weekDates(weekStart);
  if (!staff || !week) return <p className="opacity-60">{error ?? 'Loading…'}</p>;

  const readOnly = !week.canEdit;
  const pending = week.state === 'PENDING';
  const waitingOnMe = pending && week.submittedToId === user?.userId;
  // Builders who can't approve send the week on; approvers post it themselves.
  const canSend = week.canEdit && !week.canApprove && !week.published;
  const STATE_BADGE = {
    DRAFT: ['Draft', 'bg-amber-100 text-[var(--warn)]'],
    PENDING: ['Waiting for approval', 'bg-[var(--accent-soft)] text-[var(--accent-strong)]'],
    POSTED: ['Posted', 'bg-[var(--success-soft)] text-[var(--success)]'],
  } as const;
  const [badgeText, badgeClass] = STATE_BADGE[week.state];

  return (
    <div>
      <Link href="/schedule" className="text-sm opacity-70 hover:opacity-100">Back to salon schedule</Link>
      <div className="mt-3 flex flex-wrap items-center gap-3">
        <h1 className="text-3xl font-extrabold tracking-tight">{readOnly ? 'Schedule' : 'Edit schedule'}</h1>
        <span className={`rounded-full px-3 py-1 text-xs font-bold ${badgeClass}`}>{badgeText}</span>
        {dirty && <span className="text-sm font-semibold text-[var(--warn)]">Unsaved changes</span>}
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-2">
        <button className="btn-ghost" onClick={() => goToWeek(addDays(weekStart, -7))}>Previous week</button>
        <button className="btn-ghost" onClick={() => goToWeek(addDays(weekStart, 7))}>Next week</button>
        <span className="ml-2 text-lg font-extrabold">{weekRangeLabel(weekStart)}</span>
      </div>

      {/* Where this week stands in the approval process. */}
      {waitingOnMe ? (
        <Banner tone="accent">
          <b>{week.submittedByName}</b> sent this week for your approval{week.submittedAt && ` on ${longDayLabel(week.submittedAt.slice(0, 10))}`}.
          Review it, then approve and post it, or send it back with a note.
        </Banner>
      ) : pending ? (
        <Banner tone="accent">
          Waiting for approval from <b>{week.submittedToName}</b>
          {week.submittedByName && <> (sent by {week.submittedByName})</>}. You can still make changes; they&apos;ll see the latest version.
        </Banner>
      ) : week.returnNote && !week.published ? (
        <Banner tone="warn">
          <b>{week.returnedByName ?? 'An approver'}</b> sent this back: &ldquo;{week.returnNote}&rdquo;
        </Banner>
      ) : week.published && readOnly ? (
        <Banner tone="muted">This week is posted. Only someone who can approve the schedule can change it now.</Banner>
      ) : null}

      {!readOnly && (
        <div className="mt-4 flex flex-wrap items-center gap-2">
          <button className="btn-ghost" onClick={() => void copyLastWeek()} disabled={busy}>Copy last week</button>
          <button
            className="btn-ghost"
            onClick={() => void dialogs.confirm({
              title: 'Clear this week?',
              message: 'This removes every shift and RO on this week. Nothing is saved until you click Save.',
              confirmLabel: 'Clear week',
              danger: true,
            }).then((ok) => { if (ok) { setCells({}); setDirty(true); setEditing(null); } })}
            disabled={busy}
          >
            Clear week
          </button>
          <span className="flex-1" />
          <button className="btn-ghost" onClick={() => void save()} disabled={busy || !dirty}>Save</button>

          {canSend && (
            <>
              <select
                className="input w-auto"
                aria-label="Send to"
                value={approverId ?? ''}
                onChange={(e) => setApproverId(e.target.value ? Number(e.target.value) : null)}
              >
                <option value="">Send to…</option>
                {week.approvers.map((p) => <option key={p.userId} value={p.userId}>{p.name}</option>)}
              </select>
              <button className="btn" onClick={() => void sendForApproval()} disabled={busy || !approverId}>
                {pending ? 'Send again' : dirty ? 'Save and send for approval' : 'Send for approval'}
              </button>
            </>
          )}

          {week.canApprove && pending && (
            <button className="btn-ghost" onClick={() => void sendBack()} disabled={busy}>Send back</button>
          )}
          {week.canApprove && (week.published ? (
            <button className="btn-ghost" onClick={() => void setPosted(false)} disabled={busy}>Take down</button>
          ) : (
            <button className="btn" onClick={() => void setPosted(true)} disabled={busy}>
              {pending ? 'Approve and post' : dirty ? 'Save and post to staff' : 'Post to staff'}
            </button>
          ))}
        </div>
      )}
      {canSend && week.approvers.length === 0 && (
        <p className="mt-2 text-sm text-[var(--danger)]">
          Nobody can approve the schedule yet. Ask the owner to give a role approval rights on the Settings page.
        </p>
      )}
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
                return h + (c && !c.off ? shiftHours(c.start, c.end) : 0);
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
                      // Only times the salon is open that day; the end always comes after the start.
                      const [open, close] = SALON.openClose[weekday(d)];
                      const startChoices = timeOptions(open, addMinutes(close, -TIME_STEP_MINUTES));
                      const endChoices = c.off ? [] : timeOptions(addMinutes(c.start, TIME_STEP_MINUTES), close);
                      return (
                        <td key={d} className="bg-[var(--surface-2)] px-2 py-2 align-top">
                          <div className="flex flex-col gap-1">
                            <TimeSelect label="Start" value={c.off ? RO : c.start} choices={startChoices} withRO
                              onChange={(v) => update(k, v === RO
                                ? { ...c, off: true }
                                : { ...c, off: false, start: v, end: !c.off && c.end > v ? c.end : close })} />
                            {!c.off && (
                              <TimeSelect label="End" value={c.end} choices={endChoices}
                                onChange={(end) => update(k, { ...c, end })} />
                            )}
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
                    const invalid = c && !c.off && shiftHours(c.start, c.end) <= 0;
                    return (
                      <td key={d} className="px-1 py-1 align-top">
                        <button
                          onClick={() => { if (!readOnly) openCell(m.userId, d); }}
                          disabled={readOnly}
                          className={`w-full rounded-lg px-2 py-2 text-left hover:bg-[var(--surface-2)] ${
                            c ? (c.off ? 'bg-amber-100 text-[var(--warn)]' : invalid ? 'bg-red-50 text-[var(--danger)]' : 'bg-[var(--accent-soft)]') : ''
                          }`}
                        >
                          {c ? (
                            <>
                              <div className="whitespace-nowrap text-xs font-bold">{c.off ? 'RO' : shiftLabel(c.start, c.end)}</div>
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
          <CoverageRows
            days={days}
            shiftsOn={(d) => staff.flatMap((m) => {
              const c = cells[key(m.userId, d)];
              return c && !c.off ? [{ name: m.name, start: c.start, end: c.end }] : [];
            })}
          />
        </table>
      </div>
      <p className="mt-3 text-xs opacity-60">
        Click a day to add or change a shift. New shifts start with the salon&apos;s hours for that day. Choose RO for a day someone requested off.
      </p>
    </div>
  );
}

/**
 * A time dropdown limited to the salon's open hours. A value outside them (say
 * a shift saved before the hours changed) stays listed so it isn't silently lost.
 */
function TimeSelect({ label, value, choices, onChange, withRO = false }: {
  label: string;
  value: string;
  choices: string[];
  onChange: (v: string) => void;
  /** Offer "RO" (requested off) above the times. */
  withRO?: boolean;
}) {
  const options = value === RO || choices.includes(value) ? choices : [value, ...choices];
  return (
    <select className="input px-2 py-1 text-xs" aria-label={label} value={value} onChange={(e) => onChange(e.target.value)}>
      {withRO && <option value={RO}>RO (requested off)</option>}
      {options.map((t) => <option key={t} value={t}>{timeLabel(t)}</option>)}
    </select>
  );
}

/** A one-line status message above the schedule grid. */
function Banner({ tone, children }: { tone: 'accent' | 'warn' | 'muted'; children: React.ReactNode }) {
  const style = {
    accent: 'border-[var(--accent)]/40 bg-[var(--accent-soft)]',
    warn: 'border-amber-300 bg-amber-50',
    muted: 'border-[var(--border)] bg-[var(--surface)] opacity-80',
  }[tone];
  return <div className={`mt-4 rounded-xl border px-4 py-3 text-sm ${style}`}>{children}</div>;
}
