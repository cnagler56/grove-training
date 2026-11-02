import { SALON } from '@/src/lib/salon';
import { dayCoverage, hoursLabel, maxAtOnce, shiftHours } from '@/src/lib/schedule';

type DayShift = { name: string; role?: string; start: string; end: string };

/**
 * Table footer with Openers and Closers rows (how many stylists are there at
 * opening and at closing each day, with a red 0 where nobody is), the most
 * stylists on at once (red over the salon's stations), then each day's total
 * scheduled hours and the week's total. Receptionists count toward hours only. Fits a table whose columns
 * are Name, the seven days, then Hours.
 */
export default function CoverageRows({ days, shiftsOn, extraColumn = false }: {
  days: string[];
  shiftsOn: (date: string) => DayShift[];
  /** The table has one more column after Hours. */
  extraColumn?: boolean;
}) {
  const byDay = days.map((d) => shiftsOn(d));
  // Openers, closers, and stations count everyone on the floor, not receptionists.
  const onFloor = byDay.map((shifts) => shifts.filter((s) => s.role !== 'RECEPTIONIST'));
  const coverage = days.map((d, i) => dayCoverage(d, onFloor[i], SALON.openClose));
  const dayHours = byDay.map((shifts) => shifts.reduce((h, s) => h + shiftHours(s.start, s.end), 0));
  const weekHours = dayHours.reduce((a, b) => a + b, 0);
  const maxStylists = onFloor.map(maxAtOnce);
  const rows: { label: string; pick: (c: (typeof coverage)[number]) => string[] }[] = [
    { label: 'Openers', pick: (c) => c.openers },
    { label: 'Closers', pick: (c) => c.closers },
  ];

  return (
    <tfoot className="sticky bottom-0 z-20 text-xs">
      {rows.map((row) => (
        <tr key={row.label} className="[&>td]:bg-[var(--surface)] [&>td]:shadow-[0_-1px_0_var(--border)]">
          <td className="sticky left-0 z-30 px-4 py-1 font-bold uppercase tracking-wide"><span className="opacity-70">{row.label}</span></td>
          {coverage.map((c, i) => {
            const count = row.pick(c).length;
            return (
              <td key={days[i]} className="px-2 py-1 text-center">
                {count ? (
                  <span className="text-sm font-bold">{count}</span>
                ) : (
                  <span className="rounded-full bg-red-50 px-2.5 py-0.5 text-sm font-bold text-[var(--danger)]">0</span>
                )}
              </td>
            );
          })}
          <td />
          {extraColumn && <td />}
        </tr>
      ))}
      <tr className="[&>td]:bg-[var(--surface)] [&>td]:shadow-[0_-1px_0_var(--border)]">
        <td className="sticky left-0 z-30 whitespace-nowrap px-4 py-1 font-bold uppercase tracking-wide">
          <span className="opacity-70">Max stylists</span>
        </td>
        {maxStylists.map((n, i) => (
          <td key={days[i]} className="px-2 py-1 text-center">
            {n > SALON.stations ? (
              <span className="rounded-full bg-[var(--danger)] px-2.5 py-0.5 text-sm font-bold text-white"
                title={`Over the ${SALON.stations} stations`}>{n}</span>
            ) : (
              <span className="text-sm font-bold">{n || '–'}</span>
            )}
          </td>
        ))}
        <td className="whitespace-nowrap px-3 py-1 text-right"><span className="opacity-60">of {SALON.stations}</span></td>
        {extraColumn && <td />}
      </tr>
      <tr className="[&>td]:bg-[var(--surface-2)] [&>td]:shadow-[0_-1px_0_var(--border)]">
        <td className="sticky left-0 z-30 px-4 py-1 font-bold uppercase tracking-wide"><span className="opacity-70">Total hours</span></td>
        {dayHours.map((h, i) => (
          <td key={days[i]} className="px-2 py-1 text-center text-sm font-bold">{h ? hoursLabel(h) : '–'}</td>
        ))}
        <td className="px-3 py-1 text-right text-sm font-extrabold">{hoursLabel(weekHours)}</td>
        {extraColumn && <td />}
      </tr>
    </tfoot>
  );
}
