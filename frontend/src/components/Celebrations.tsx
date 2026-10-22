import type { Celebration } from '@/src/lib/api';
import { longDayLabel } from '@/src/lib/schedule';

const firstName = (name: string) => name.split(' ')[0];

/** "3-year work anniversary" */
const anniversary = (years: number | null) => `${years}-year work anniversary`;

/** Today's birthdays and anniversaries, at the top of everyone's Home page. */
export function TodayCelebrations({ items, viewerId }: { items: Celebration[]; viewerId?: number }) {
  if (items.length === 0) return null;
  return (
    <section className="card border-2 border-[var(--accent)] bg-[var(--accent-soft)] p-5">
      <h2 className="text-lg font-extrabold text-[var(--accent-strong)]">Celebrating today</h2>
      <ul className="mt-2 space-y-1.5">
        {items.map((c) => (
          <li key={`${c.kind}-${c.userId}`} className="text-base">
            {c.userId === viewerId ? (
              c.kind === 'BIRTHDAY'
                ? <b>Happy birthday, {firstName(c.name)}! The whole team is celebrating you today.</b>
                : <b>Happy {anniversary(c.years)}, {firstName(c.name)}! Thank you for everything you do.</b>
            ) : c.kind === 'BIRTHDAY' ? (
              <>It&apos;s <b>{c.name}</b>&apos;s birthday today. Be sure to wish them a happy birthday!</>
            ) : (
              <>Today is <b>{c.name}</b>&apos;s {anniversary(c.years)} at Grove Salons. Congratulations!</>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}

/** The next week's birthdays and anniversaries (managers and up). */
export function UpcomingCelebrations({ items }: { items: Celebration[] }) {
  return (
    <section className="card p-5">
      <h2 className="text-lg font-extrabold">Birthdays &amp; anniversaries this week</h2>
      {items.length === 0 ? (
        <p className="mt-2 text-sm opacity-70">None in the next 7 days.</p>
      ) : (
        <ul className="mt-3 divide-y divide-[var(--border)] text-sm">
          {items.map((c) => (
            <li key={`${c.kind}-${c.userId}`} className="flex flex-wrap items-center justify-between gap-2 py-2">
              <span><b>{c.name}</b> <span className="opacity-70">· {c.kind === 'BIRTHDAY' ? 'Birthday' : anniversary(c.years)}</span></span>
              <span className="whitespace-nowrap font-semibold text-[var(--accent-strong)]">{longDayLabel(c.date)}</span>
            </li>
          ))}
        </ul>
      )}
      <p className="mt-3 text-xs opacity-60">From birthdays and hire dates on the Team page.</p>
    </section>
  );
}
