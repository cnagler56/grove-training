'use client';

import { useEffect, useState } from 'react';
import { api, type TimeOffCalendar, type TimeOffRequest } from '@/src/lib/api';
import { addDays, longDayLabel, timeOffRange, todayIso, weekday } from '@/src/lib/schedule';

const MONTH_NAMES = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October',
  'November', 'December'];
const DAY_HEADS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

/** Names shown in a day before "+2 more"; the day's panel lists everyone. */
const MAX_NAMES = 3;

/** Blacked-out days are dark so they stand out from the rest of the month. */
const DARK = 'bg-slate-800 text-white hover:bg-slate-700';

/** "2026-11" → the first day "2026-11-01". */
const firstOf = (month: string) => `${month}-01`;
const shiftMonth = (month: string, n: number) => {
  const [y, m] = month.split('-').map(Number);
  const total = y * 12 + (m - 1) + n;
  return `${Math.floor(total / 12)}-${String((total % 12) + 1).padStart(2, '0')}`;
};

/** The Sunday the month's six-week grid starts on. */
function gridStart(month: string) {
  const first = firstOf(month);
  return addDays(first, -weekday(first));
}

/**
 * A month calendar of everyone's time off, for the people who approve it. Click a day to
 * write a note on it, or to black it out so no one can ask for it off.
 */
export default function TimeOffCalendarPage() {
  const today = todayIso();
  const [month, setMonth] = useState(today.slice(0, 7));
  const [cal, setCal] = useState<TimeOffCalendar | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const from = gridStart(month);
  const to = addDays(from, 41);
  const days = Array.from({ length: 42 }, (_, i) => addDays(from, i));

  // Reloads when the month changes, and after each change (version goes up).
  const [version, setVersion] = useState(0);
  const load = () => setVersion((v) => v + 1);
  useEffect(() => {
    api.timeOffCalendar(from, to).then(setCal).catch((e) => setError(e.message));
  }, [from, to, version]);

  const blackout = (d: string) => cal?.blackouts.find((b) => b.date === d) ?? null;
  const noteOn = (d: string) => cal?.notes.find((n) => n.date === d)?.text ?? null;
  const offOn = (d: string) => (cal?.timeOff ?? []).filter((r) => r.startDate <= d && r.endDate >= d);

  const [y, m] = month.split('-').map(Number);

  return (
    // Wide screens: controls in a column on the left, the month filling the rest of the
    // screen below the menu bar. Phones: stacked, and the month scrolls.
    <div className="space-y-4 lg:flex lg:h-[calc(100dvh-8.5rem)] lg:gap-5 lg:space-y-0">
      <aside className="space-y-4 lg:w-56 lg:shrink-0 lg:overflow-y-auto">
        <div>
          <h1 className="text-2xl font-extrabold tracking-tight">Time off calendar</h1>
          <p className="mt-1 text-sm opacity-70">
            Click a day to add a note, or to black it out so no one can ask for it off.
          </p>
        </div>

        <div className="space-y-2">
          <h2 className="text-xl font-extrabold">{MONTH_NAMES[m - 1]} {y}</h2>
          <div className="flex flex-wrap gap-2">
            <button className="btn-ghost px-3 py-1.5" onClick={() => setMonth(shiftMonth(month, -1))}>Previous</button>
            <button className="btn-ghost px-3 py-1.5" onClick={() => setMonth(shiftMonth(month, 1))}>Next</button>
            {month !== today.slice(0, 7) && (
              <button className="btn-ghost px-3 py-1.5" onClick={() => setMonth(today.slice(0, 7))}>This month</button>
            )}
          </div>
        </div>

        <div className="flex flex-wrap gap-x-4 gap-y-1.5 text-xs lg:flex-col">
          <span className="flex items-center gap-1.5"><span className="h-3 w-3 rounded bg-[var(--accent)]" />Approved</span>
          <span className="flex items-center gap-1.5"><span className="h-3 w-3 rounded border-2 border-dashed border-[var(--warn)]" />Waiting for an answer</span>
          <span className="flex items-center gap-1.5"><span className="h-3 w-3 rounded bg-slate-800" />Blacked out</span>
        </div>

        {error && <p className="text-sm text-[var(--danger)]">{error}</p>}
        {notice && <p className="text-sm font-semibold text-[var(--success)]">{notice}</p>}
        {!cal && !error && <p className="text-sm opacity-60">Loading…</p>}
      </aside>

      <div className="card flex flex-col overflow-hidden p-0 lg:min-w-0 lg:flex-1">
        <div className="grid grid-cols-7 border-b border-[var(--border)] text-center text-xs font-bold uppercase tracking-wide opacity-60">
          {DAY_HEADS.map((d) => <div key={d} className="py-1.5">{d}</div>)}
        </div>
        <div className="grid grid-cols-7 lg:min-h-0 lg:flex-1 lg:grid-rows-6">
          {days.map((d) => {
            const inMonth = d.slice(0, 7) === month;
            const b = blackout(d);
            const note = noteOn(d);
            const off = offOn(d);
            const isToday = d === today;
            return (
              <button key={d} type="button" onClick={() => { setNotice(null); setError(null); setSelected(d); }}
                className={`flex min-h-24 min-w-0 flex-col justify-start overflow-hidden border-b border-r border-[var(--border)] p-1.5 text-left transition-colors sm:min-h-28 lg:min-h-0 ${
                  b ? DARK : 'hover:bg-[var(--surface-2)]'} ${inMonth ? '' : 'opacity-40'}`}>
                <div className="flex items-center justify-between gap-1">
                  <span className={`text-sm font-bold ${isToday ? 'rounded-full bg-[var(--accent)] px-1.5 text-white' : ''}`}>
                    {Number(d.slice(8, 10))}
                  </span>
                  {b && <span className="truncate text-[10px] font-bold uppercase tracking-wide">Blacked out</span>}
                </div>
                {b?.note && <div className="mt-0.5 truncate text-[11px] font-semibold opacity-80">{b.note}</div>}
                {note && (
                  <div className={`mt-0.5 truncate rounded px-1 text-[11px] font-semibold ${b ? 'bg-white/15' : 'bg-[var(--surface-2)]'}`}
                    title={note}>
                    {note}
                  </div>
                )}
                <ul className="mt-0.5 space-y-px">
                  {off.slice(0, MAX_NAMES).map((r) => <Person key={r.id} r={r} />)}
                  {off.length > MAX_NAMES && (
                    <li className="text-[11px] font-semibold leading-4 opacity-70">+{off.length - MAX_NAMES} more</li>
                  )}
                </ul>
              </button>
            );
          })}
        </div>
      </div>

      {selected && (
        <DayPanel
          key={selected}
          date={selected}
          blackoutNote={blackout(selected)?.note ?? null}
          blackedOut={!!blackout(selected)}
          note={noteOn(selected)}
          off={offOn(selected)}
          onClose={() => setSelected(null)}
          onSaved={(message) => { setNotice(message); setSelected(null); load(); }}
        />
      )}
    </div>
  );
}

/** The panel for one day: who's off, its note, and its blackout. */
function DayPanel({ date, blackedOut, blackoutNote, note, off, onClose, onSaved }: {
  date: string;
  blackedOut: boolean;
  blackoutNote: string | null;
  note: string | null;
  off: TimeOffRequest[];
  onClose: () => void;
  onSaved: (message: string) => void;
}) {
  const [noteText, setNoteText] = useState(note ?? '');
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const label = longDayLabel(date);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  async function act(action: () => Promise<unknown>, message: string) {
    setBusy(true);
    setError(null);
    try {
      await action();
      onSaved(message);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong');
      setBusy(false);
    }
  }

  const noteChanged = noteText.trim() !== (note ?? '');

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-4" onMouseDown={onClose}>
      <div role="dialog" aria-label={label} className="card w-full max-w-md space-y-4 p-5" onMouseDown={(e) => e.stopPropagation()}>
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-xl font-extrabold">{label}</h2>
          <button className="text-sm font-semibold hover:underline" onClick={onClose}>Close</button>
        </div>

        <section>
          <h3 className="text-xs font-bold uppercase tracking-wide opacity-60">Time off</h3>
          {off.length === 0 ? (
            <p className="mt-1 text-sm opacity-70">No one is off this day.</p>
          ) : (
            <ul className="mt-1 space-y-1 text-sm">
              {off.map((r) => (
                <li key={r.id}>
                  <b>{r.name}</b> · {timeOffRange(r.startDate, r.endDate)}
                  {r.status === 'PENDING' && <span className="font-semibold text-[var(--warn)]"> (waiting for an answer)</span>}
                  {r.note && <div className="text-xs opacity-70">{r.note}</div>}
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="space-y-2">
          <h3 className="text-xs font-bold uppercase tracking-wide opacity-60">Note</h3>
          <textarea className="input min-h-20" maxLength={500} value={noteText} placeholder="Staff meeting 9am, inventory, etc."
            onChange={(e) => setNoteText(e.target.value)} />
          <div className="flex gap-2">
            <button className="btn" disabled={busy || !noteChanged}
              onClick={() => void act(() => api.setDayNote(date, noteText.trim() || null),
                noteText.trim() ? `Saved the note on ${label}.` : `Removed the note on ${label}.`)}>
              Save note
            </button>
            {note && (
              <button className="btn-ghost" disabled={busy}
                onClick={() => void act(() => api.setDayNote(date, null), `Removed the note on ${label}.`)}>
                Remove note
              </button>
            )}
          </div>
        </section>

        <section className="space-y-2 border-t border-[var(--border)] pt-4">
          <h3 className="text-xs font-bold uppercase tracking-wide opacity-60">Blackout</h3>
          {blackedOut ? (
            <>
              <p className="text-sm">
                Blacked out{blackoutNote ? `: ${blackoutNote}` : ''}. No one can ask for this day off.
              </p>
              <button className="btn-ghost" disabled={busy}
                onClick={() => void act(() => api.clearBlackout(date), `${label} is open for time off requests again.`)}>
                Clear blackout
              </button>
            </>
          ) : (
            <>
              <p className="text-sm opacity-70">
                Black out this day so no one can ask for it off.
                {off.length > 0 && ' Time off already requested for it stays as it is.'}
              </p>
              <input className="input" maxLength={200} value={reason} placeholder='Reason staff will see (optional), like "Holiday rush"'
                onChange={(e) => setReason(e.target.value)} />
              <button className="btn bg-slate-800 hover:bg-slate-700" disabled={busy}
                onClick={() => void act(() => api.blackOutDay(date, reason.trim() || null), `${label} is blacked out.`)}>
                Black out this day
              </button>
            </>
          )}
        </section>

        {error && <p className="text-sm text-[var(--danger)]">{error}</p>}
      </div>
    </div>
  );
}

function Person({ r }: { r: TimeOffRequest }) {
  const waiting = r.status === 'PENDING';
  return (
    <li title={`${r.name}: ${timeOffRange(r.startDate, r.endDate)}${waiting ? ' (waiting for an answer)' : ''}${r.note ? ` - ${r.note}` : ''}`}
      className={`truncate rounded px-1 text-[11px] font-semibold leading-4 ${
        waiting
          ? 'border border-dashed border-[var(--warn)] text-[var(--warn)]'
          : 'bg-[var(--accent)] text-white'
      }`}>
      {r.name}
    </li>
  );
}
