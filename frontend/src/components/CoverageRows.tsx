import { SALON } from '@/src/lib/salon';
import { dayCoverage } from '@/src/lib/schedule';

type DayShift = { name: string; start: string; end: string };

/**
 * Table footer with Openers and Closers rows: how many people are there at
 * opening and at closing each day, with a red 0 where nobody is. Fits a table
 * whose columns are Name, the seven days, then Hours.
 */
export default function CoverageRows({ days, shiftsOn }: { days: string[]; shiftsOn: (date: string) => DayShift[] }) {
  const coverage = days.map((d) => dayCoverage(d, shiftsOn(d), SALON.openClose));
  const rows: { label: string; pick: (c: (typeof coverage)[number]) => string[] }[] = [
    { label: 'Openers', pick: (c) => c.openers },
    { label: 'Closers', pick: (c) => c.closers },
  ];

  return (
    <tfoot className="border-t-2 border-[var(--border)] text-xs">
      {rows.map((row) => (
        <tr key={row.label} className="border-t border-[var(--border)]">
          <td className="px-4 py-2 font-bold uppercase tracking-wide opacity-70">{row.label}</td>
          {coverage.map((c, i) => {
            const count = row.pick(c).length;
            return (
              <td key={days[i]} className="px-2 py-2 text-center">
                {count ? (
                  <span className="text-sm font-bold">{count}</span>
                ) : (
                  <span className="rounded-full bg-red-50 px-2.5 py-0.5 text-sm font-bold text-[var(--danger)]">0</span>
                )}
              </td>
            );
          })}
          <td />
        </tr>
      ))}
    </tfoot>
  );
}
