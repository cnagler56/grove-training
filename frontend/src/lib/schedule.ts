/**
 * Date/time helpers for the weekly schedule. Weeks run Saturday through
 * Friday. Dates stay as "YYYY-MM-DD" strings and are shifted with UTC math,
 * so time zones and daylight saving never move a shift to another day.
 */

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const DAY_SHORT = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

const toUtc = (iso: string) => {
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d));
};
const fromUtc = (dt: Date) => dt.toISOString().slice(0, 10);

/** Today in the viewer's local time, as "YYYY-MM-DD". */
export const todayIso = () => new Date().toLocaleDateString('en-CA');

export const addDays = (iso: string, n: number) => {
  const dt = toUtc(iso);
  dt.setUTCDate(dt.getUTCDate() + n);
  return fromUtc(dt);
};

/** 0 = Sunday … 6 = Saturday. */
export const weekday = (iso: string) => toUtc(iso).getUTCDay();

/** The Saturday on or before this date. */
export const weekStartOf = (iso: string) => addDays(iso, -((weekday(iso) + 1) % 7));

/** Saturday … Friday of the week starting on `start`. */
export const weekDates = (start: string) => Array.from({ length: 7 }, (_, i) => addDays(start, i));

/** "Sat 17". */
export const dayLabel = (iso: string) => `${DAY_SHORT[weekday(iso)]} ${Number(iso.slice(8, 10))}`;

/** "Sat, Oct 17". */
export const longDayLabel = (iso: string) => `${DAY_SHORT[weekday(iso)]}, ${MONTHS[Number(iso.slice(5, 7)) - 1]} ${Number(iso.slice(8, 10))}`;

/** "Oct 17 – 23" or "Oct 31 – Nov 6". */
export function weekRangeLabel(start: string) {
  const end = addDays(start, 6);
  const sm = MONTHS[Number(start.slice(5, 7)) - 1];
  const em = MONTHS[Number(end.slice(5, 7)) - 1];
  return `${sm} ${Number(start.slice(8, 10))} – ${sm === em ? '' : `${em} `}${Number(end.slice(8, 10))}`;
}

/** "10:00:00" → "10am", "13:30" → "1:30pm". */
export function timeLabel(t: string) {
  const [h, m] = t.split(':').map(Number);
  const suffix = h < 12 ? 'am' : 'pm';
  const h12 = h % 12 === 0 ? 12 : h % 12;
  return `${h12}${m ? `:${String(m).padStart(2, '0')}` : ''}${suffix}`;
}

export const shiftLabel = (start: string, end: string) => `${timeLabel(start)} – ${timeLabel(end)}`;

/** Paper-form style: "10–6", "9:30–5". No am/pm, like a handwritten schedule. */
export function shortShift(start: string, end: string) {
  const t = (s: string) => {
    const [h, m] = s.split(':').map(Number);
    const h12 = h % 12 === 0 ? 12 : h % 12;
    return `${h12}${m ? `:${String(m).padStart(2, '0')}` : ''}`;
  };
  return `${t(start)}–${t(end)}`;
}

/** Hours between two "HH:MM" times. */
export function shiftHours(start: string, end: string) {
  const mins = (t: string) => { const [h, m] = t.split(':').map(Number); return h * 60 + m; };
  return Math.max(0, mins(end) - mins(start)) / 60;
}

/** "7.5" → "7.5h", "8" → "8h". */
export const hoursLabel = (h: number) => `${Number.isInteger(h) ? h : h.toFixed(1)}h`;

/** "10:00:00" → "10:00" (what <input type="time"> expects). */
export const hhmm = (t: string) => t.slice(0, 5);

/** True on the first receptionist row, which gets a divider like the paper form's two groups. */
export function startsReceptionists(rows: { role: string }[], i: number) {
  return rows[i].role === 'RECEPTIONIST' && (i === 0 || rows[i - 1].role !== 'RECEPTIONIST');
}

/**
 * Who opens and who closes on a day: an opener starts at or before the salon's
 * opening time, a closer ends at or after closing. Times are "HH:MM[:SS]" and
 * compare as strings since they're zero-padded.
 */
export function dayCoverage(
  date: string,
  shifts: { name: string; start: string; end: string }[],
  openClose: [string, string][],
) {
  const [open, close] = openClose[weekday(date)];
  const first = (n: string) => n.split(' ')[0];
  return {
    openers: shifts.filter((s) => hhmm(s.start) <= open).map((s) => first(s.name)),
    closers: shifts.filter((s) => hhmm(s.end) >= close).map((s) => first(s.name)),
  };
}

/** Minutes between choices in the schedule editor's time dropdowns: every half hour. */
export const TIME_STEP_MINUTES = 30;

const toMinutes = (t: string) => { const [h, m] = t.split(':').map(Number); return h * 60 + m; };
const fromMinutes = (n: number) => `${String(Math.floor(n / 60)).padStart(2, '0')}:${String(n % 60).padStart(2, '0')}`;

/** "HH:MM" times from `from` through `to` (inclusive), every TIME_STEP_MINUTES. */
export function timeOptions(from: string, to: string): string[] {
  const out: string[] = [];
  for (let m = toMinutes(from); m <= toMinutes(to); m += TIME_STEP_MINUTES) out.push(fromMinutes(m));
  return out;
}

/** Shift a "HH:MM" time by some minutes. */
export const addMinutes = (t: string, n: number) => fromMinutes(toMinutes(t) + n);

type Entry = { type?: string; start: string | null; end: string | null };

/** An RO day: the person requested it off. */
export const isRequestedOff = (s: Entry) => s.type === 'REQUESTED_OFF' || !s.start || !s.end;

/** Hours worked for an entry; RO days count as 0. */
export const entryHours = (s: Entry) => (isRequestedOff(s) ? 0 : shiftHours(s.start!, s.end!));
