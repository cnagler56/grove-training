import type { WeekSchedule } from '@/src/lib/api';
import {
  entryHours, hoursLabel, isRequestedOff, scheduledThisWeek, shortShift, startsReceptionists, weekDates,
} from '@/src/lib/schedule';

const DAY_NAMES = ['Sat', 'Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri'];

/** "Jamie Rivera" as "Jamie R." so a name fits a day's narrow column. */
function shortName(name: string) {
  const [first, ...rest] = name.trim().split(/\s+/);
  const last = rest.at(-1);
  return last ? `${first} ${last[0]}.` : first;
}

/**
 * One week laid out like the printed schedule: steel-blue day header, the MOD row,
 * then everyone with a shift or RO that week (receptionists last, below a line).
 * The print view and the Schedule page both use it so they always look the same.
 * On screen it can also shade the viewer's own row, mark today, and show shift notes.
 */
export default function ScheduleSheet({ week, youId, today, notes = false }: {
  week: WeekSchedule;
  /** Shade this person's row and add "(you)". */
  youId?: number;
  /** Darken this date's day header. */
  today?: string;
  /** Show each shift's note under its times. */
  notes?: boolean;
}) {
  const days = weekDates(week.weekStart);
  const cell = new Map(week.shifts.map((s) => [`${s.userId}|${s.date}`, s]));
  // Only people with a shift or RO this week; the editor still lists everyone.
  const people = scheduledThisWeek(week.roster, week.shifts);
  // The MOD row, shown above everyone when any day has a manager on duty.
  const mods = new Map((week.managersOnDuty ?? []).map((m) => [m.date, m.name]));

  return (
    <table className="w-full min-w-[680px] table-fixed border-collapse text-[13px]">
      <colgroup>
        <col className="w-[1.55in]" />
        {days.map((d) => <col key={d} />)}
        <col className="w-[0.5in]" />
      </colgroup>
      <thead>
        <tr className="bg-[#4682b4] text-white">
          <th className="rounded-tl-md px-2.5 py-1.5 text-left text-[11px] font-bold uppercase tracking-wide">Name</th>
          {days.map((d, i) => (
            <th key={d} className={`px-1 py-1 text-center leading-tight ${d === today ? 'bg-[#2c5a82]' : ''}`}>
              <div className="text-[11px] font-bold uppercase tracking-wide">{DAY_NAMES[i]}</div>
              <div className="text-[10px] font-semibold opacity-85">
                {d === today ? 'Today' : `${Number(d.slice(5, 7))}/${Number(d.slice(8, 10))}`}
              </div>
            </th>
          ))}
          <th className="rounded-tr-md px-1 py-1.5 text-center text-[11px] font-bold uppercase tracking-wide">Hrs</th>
        </tr>
      </thead>
      <tbody>
        {mods.size > 0 && (
          <tr className="border-b-2 border-b-[#4682b4] bg-[#dce7f1]">
            <td className="h-[0.42in] border-b border-[#d8dfe6] px-2.5 leading-tight">
              <div className="font-extrabold text-[#2c5a82]">MOD</div>
              <div className="text-[10px] font-semibold text-gray-500">Manager on duty</div>
            </td>
            {days.map((d) => (
              <td key={d} className="border-b border-l border-[#d8dfe6] px-1 text-center text-[12px] font-bold leading-tight">
                {mods.has(d) ? shortName(mods.get(d)!) : ''}
              </td>
            ))}
            <td className="border-b border-l border-[#d8dfe6]" />
          </tr>
        )}
        {people.map((person, i) => {
          const shifts = days.map((d) => cell.get(`${person.userId}|${d}`));
          const total = shifts.reduce((h, s) => h + (s ? entryHours(s) : 0), 0);
          const mine = person.userId === youId;
          return (
            <tr
              key={person.userId}
              className={`${mine ? 'bg-[#fff4cc]' : i % 2 ? 'bg-[#f3f6f9]' : 'bg-white'} ${
                startsReceptionists(people, i) ? 'border-t-2 border-t-[#4682b4]' : ''
              }`}
            >
              <td className="h-[0.42in] truncate border-b border-[#d8dfe6] px-2.5 font-bold">
                {person.name}
                {mine && <span className="text-[11px] font-semibold text-gray-500"> (you)</span>}
              </td>
              {shifts.map((s, j) => (
                <td key={days[j]} className="border-b border-l border-[#d8dfe6] px-1 text-center font-semibold leading-tight">
                  {s ? (isRequestedOff(s) ? <span className="font-bold" title="Requested off">RO</span> : shortShift(s.start!, s.end!)) : ''}
                  {notes && s?.note && <div className="truncate text-[10px] font-normal text-gray-500" title={s.note}>{s.note}</div>}
                </td>
              ))}
              <td className="border-b border-l border-[#d8dfe6] px-1 text-center text-[11px] text-gray-500">
                {total ? hoursLabel(total) : ''}
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
