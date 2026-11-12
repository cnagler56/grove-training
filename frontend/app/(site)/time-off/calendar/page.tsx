'use client';

import { useEffect, useState } from 'react';
import { api, type TimeOffCalendar, type TimeOffRequest } from '@/src/lib/api';
import { addDays, longDayLabel, timeOffRange, todayIso, weekday } from '@/src/lib/schedule';
import { useDialogs } from '@/src/components/Dialogs';

const MONTH_NAMES = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October',
  'November', 'December'];
const DAY_HEADS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

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
 * A month calendar of everyone's time off, for the people who approve it. Days can be
 * blacked out here; no one can ask for a blacked-out day off.
 */
export default function TimeOffCalendarPage() {
  const dialogs = useDialogs();
  const today = todayIso();
  const [month, setMonth] = useState(today.slice(0, 7));
  const [cal, setCal] = useState<TimeOffCalendar | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const from = gridStart(month);
  const to = addDays(from, 41);
  const days = Array.from({ length: 42 }, (_, i) => addDays(from, i));

  // Reloads when the month changes, and after each blackout change (version goes up).
  const [version, setVersion] = useState(0);
  const load = () => setVersion((v) => v + 1);
  useEffect(() => {
    api.timeOffCalendar(from, to).then(setCal).catch((e) => setError(e.message));
  }, [from, to, version]);

  const blackout = (d: string) => cal?.blackouts.find((b) => b.date === d) ?? null;
  const offOn = (d: string) => (cal?.timeOff ?? []).filter((r) => r.startDate <= d && r.endDate >= d);

  async function clickDay(d: string) {
    setError(null);
    setNotice(null);
    const b = blackout(d);
    const off = offOn(d);
    try {
      if (b) {
        if (!(await dialogs.confirm({
          title: `Clear the blackout on ${longDayLabel(d)}?`,
          message: 'Staff will be able to ask for this day off again.',
          confirmLabel: 'Clear blackout',
        }))) return;
        await api.clearBlackout(d);
        setNotice(`${longDayLabel(d)} is open for time off requests again.`);
      } else {
        const note = await dialogs.prompt({
          title: `Black out ${longDayLabel(d)}?`,
          message: off.length > 0
            ? `No one will be able to ask for this day off. Already requested: ${off.map((r) => r.name).join(', ')}. Those stay as they are.`
            : 'No one will be able to ask for this day off.',
          placeholder: 'Reason staff will see (optional), like "Holiday rush"',
          minLength: 0,
          confirmLabel: 'Black out',
          danger: true,
        });
        if (note === null) return;
        await api.blackOutDay(d, note.trim() || null);
        setNotice(`${longDayLabel(d)} is blacked out.`);
      }
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong');
    }
  }

  const [y, m] = month.split('-').map(Number);

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Time off calendar</h1>
        <p className="mt-1 text-sm opacity-70">
          Everyone&apos;s time off by day. Click a day to black it out so no one can ask for it off, or to clear a blackout.
        </p>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <button className="btn-ghost px-3 py-1.5" onClick={() => setMonth(shiftMonth(month, -1))}>Previous</button>
        <h2 className="min-w-44 text-center text-xl font-extrabold">{MONTH_NAMES[m - 1]} {y}</h2>
        <button className="btn-ghost px-3 py-1.5" onClick={() => setMonth(shiftMonth(month, 1))}>Next</button>
        {month !== today.slice(0, 7) && (
          <button className="btn-ghost px-3 py-1.5" onClick={() => setMonth(today.slice(0, 7))}>This month</button>
        )}
        <div className="ml-auto flex flex-wrap gap-3 text-xs">
          <span className="flex items-center gap-1.5"><span className="h-3 w-3 rounded bg-[var(--accent)]" />Approved</span>
          <span className="flex items-center gap-1.5"><span className="h-3 w-3 rounded border-2 border-dashed border-[var(--warn)]" />Waiting for an answer</span>
          <span className="flex items-center gap-1.5"><span className="h-3 w-3 rounded bg-red-100 ring-1 ring-red-300" />Blacked out</span>
        </div>
      </div>

      {error && <p className="text-sm text-[var(--danger)]">{error}</p>}
      {notice && <p className="text-sm font-semibold text-[var(--success)]">{notice}</p>}

      <div className="card overflow-hidden p-0">
        <div className="grid grid-cols-7 border-b border-[var(--border)] text-center text-xs font-bold uppercase tracking-wide opacity-60">
          {DAY_HEADS.map((d) => <div key={d} className="py-2">{d}</div>)}
        </div>
        <div className="grid grid-cols-7">
          {days.map((d) => {
            const inMonth = d.slice(0, 7) === month;
            const b = blackout(d);
            const off = offOn(d);
            const isToday = d === today;
            return (
              <button key={d} type="button" onClick={() => void clickDay(d)}
                title={b ? `Blacked out${b.note ? `: ${b.note}` : ''}. Click to clear.` : 'Click to black out this day'}
                className={`flex min-h-24 min-w-0 flex-col justify-start border-b border-r border-[var(--border)] p-1.5 text-left transition-colors hover:bg-[var(--surface-2)] sm:min-h-28 ${
                  b ? 'bg-red-50 hover:bg-red-100' : ''} ${inMonth ? '' : 'opacity-40'}`}>
                <div className="flex items-center justify-between gap-1">
                  <span className={`text-sm font-bold ${isToday ? 'rounded-full bg-[var(--accent)] px-1.5 text-white' : ''}`}>
                    {Number(d.slice(8, 10))}
                  </span>
                  {b && <span className="truncate text-[10px] font-bold uppercase text-[var(--danger)]">Blacked out</span>}
                </div>
                {b?.note && <div className="mt-0.5 truncate text-[11px] font-semibold text-[var(--danger)]">{b.note}</div>}
                <ul className="mt-1 space-y-0.5">
                  {off.map((r) => <Person key={r.id} r={r} />)}
                </ul>
              </button>
            );
          })}
        </div>
      </div>

      {!cal && !error && <p className="text-sm opacity-60">Loading…</p>}
    </div>
  );
}

function Person({ r }: { r: TimeOffRequest }) {
  const waiting = r.status === 'PENDING';
  return (
    <li title={`${r.name}: ${timeOffRange(r.startDate, r.endDate)}${waiting ? ' (waiting for an answer)' : ''}${r.note ? ` - ${r.note}` : ''}`}
      className={`truncate rounded px-1 text-[11px] font-semibold leading-5 ${
        waiting
          ? 'border border-dashed border-[var(--warn)] text-[var(--warn)]'
          : 'bg-[var(--accent)] text-white'
      }`}>
      {r.name}
    </li>
  );
}
