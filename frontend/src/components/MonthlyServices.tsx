import type { StatLine, TeamServiceWeek } from '@/src/lib/api';
import { addDays } from '@/src/lib/schedule';

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
const TYPES = [
  { key: 'colors', label: 'Colors' },
  { key: 'treatments', label: 'Treatments' },
  { key: 'waxes', label: 'Waxes' },
] as const;

type Tally = { colors: number; treatments: number; waxes: number };
const empty = (): Tally => ({ colors: 0, treatments: 0, waxes: 0 });

/** A Saturday–Friday week counts in the month it ends: "2026-10". */
const monthOf = (weekStart: string) => addDays(weekStart, 6).slice(0, 7);
const monthLabel = (month: string) => `${MONTHS[Number(month.slice(5)) - 1]} ${month.slice(0, 4)}`;

/**
 * Colors, treatments, and waxes added up by month from the saved weeks: the
 * whole team, plus the viewer's own when `mine` is given. Weeks pulled before
 * these numbers were saved are left out.
 */
export default function MonthlyServices({ teamWeeks, mine }: { teamWeeks: TeamServiceWeek[]; mine?: StatLine[] }) {
  const counted = teamWeeks.filter((w) => w.colors !== null);
  if (counted.length === 0) return null;

  const months = new Map<string, { weeks: number; team: Tally; you: Tally }>();
  for (const w of counted) {
    const m = months.get(monthOf(w.weekStart)) ?? { weeks: 0, team: empty(), you: empty() };
    m.weeks += 1;
    for (const { key } of TYPES) m.team[key] += w[key] ?? 0;
    months.set(monthOf(w.weekStart), m);
  }
  for (const w of mine ?? []) {
    const m = months.get(monthOf(w.weekStart));
    if (m) for (const { key } of TYPES) m.you[key] += w[key] ?? 0;
  }
  const rows = [...months.entries()].sort((a, b) => b[0].localeCompare(a[0]));

  return (
    <section className="card overflow-x-auto">
      <h2 className="px-5 pt-5 text-lg font-extrabold">By month</h2>
      <p className="px-5 text-xs opacity-60">Weeks count in the month they end. Months only include the weeks pulled so far.</p>
      <table className="mt-3 w-full text-left text-sm">
        <thead className="border-b border-[var(--border)] text-xs uppercase tracking-wide opacity-60">
          <tr>
            <th className="px-5 py-2 font-semibold">Month</th>
            <th className="px-3 py-2 text-right font-semibold">Weeks</th>
            {TYPES.map((t) => (mine ? (
              <th key={t.key} colSpan={2} className="px-3 py-2 text-right font-semibold">{t.label}: you / team</th>
            ) : (
              <th key={t.key} className="px-3 py-2 text-right font-semibold">Team {t.label.toLowerCase()}</th>
            )))}
          </tr>
        </thead>
        <tbody className="divide-y divide-[var(--border)]">
          {rows.map(([month, m]) => (
            <tr key={month}>
              <td className="whitespace-nowrap px-5 py-2 font-semibold">{monthLabel(month)}</td>
              <td className="px-3 py-2 text-right">{m.weeks}</td>
              {TYPES.map((t) => (mine ? (
                <td key={t.key} colSpan={2} className="px-3 py-2 text-right"><b>{m.you[t.key]}</b> / {m.team[t.key]}</td>
              ) : (
                <td key={t.key} className="px-3 py-2 text-right font-semibold">{m.team[t.key]}</td>
              )))}
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
