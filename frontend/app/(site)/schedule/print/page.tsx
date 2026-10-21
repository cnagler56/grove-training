'use client';

import { Suspense, useEffect, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { api, type WeekSchedule } from '@/src/lib/api';
import { SALON } from '@/src/lib/salon';
import { addDays, shortShift, startsReceptionists, todayIso, weekRangeLabel, weekStartOf, weekDates } from '@/src/lib/schedule';

const DAY_HEADERS = ['SAT', 'SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI'];
/** Empty lines under each week for write-ins, like the paper form. */
const BLANK_ROWS = 2;

export default function PrintSchedulePage() {
  // useSearchParams needs a Suspense boundary in the app router.
  return (
    <Suspense fallback={<p className="opacity-60">Loading…</p>}>
      <PrintSchedule />
    </Suspense>
  );
}

/**
 * Printable copy of the salon's paper schedule form: two Saturday–Friday weeks
 * on one letter page, names filled in, plain black grid.
 */
function PrintSchedule() {
  const params = useSearchParams();
  const first = weekStartOf(params.get('week') ?? todayIso());
  const [weeks, setWeeks] = useState<WeekSchedule[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.all([api.schedule(first), api.schedule(addDays(first, 7))])
      .then((w) => { if (!cancelled) setWeeks(w); })
      .catch((e) => { if (!cancelled) setError(e instanceof Error ? e.message : 'Could not load the schedule'); });
    return () => { cancelled = true; };
  }, [first]);

  if (!weeks) return <p className="opacity-60">{error ?? 'Loading…'}</p>;

  return (
    <div>
      {/* Letter, portrait, half-inch margins: matches the paper form. */}
      <style>{'@page { size: letter portrait; margin: 0.5in; }'}</style>

      <div className="mb-6 flex flex-wrap items-center justify-between gap-3 print:hidden">
        <Link href="/schedule" className="text-sm opacity-70 hover:opacity-100">Back to salon schedule</Link>
        <div className="flex items-center gap-3">
          <span className="text-sm opacity-70">This week and next, on one page.</span>
          <button className="btn" onClick={() => window.print()}>Print</button>
        </div>
      </div>

      <div className="mx-auto max-w-[7.5in] bg-white p-6 text-black shadow print:max-w-none print:p-0 print:shadow-none">
        {weeks.map((w, i) => (
          <FormWeek key={w.weekStart} week={w} spaced={i > 0} />
        ))}
        <p className="mt-4 text-right text-[10px] text-gray-500">{SALON.name} · printed {new Date().toLocaleDateString()}</p>
      </div>
    </div>
  );
}

function FormWeek({ week, spaced }: { week: WeekSchedule; spaced: boolean }) {
  const days = weekDates(week.weekStart);
  const cell = new Map(week.shifts.map((s) => [`${s.userId}|${s.date}`, s]));
  const hidden = !week.published && !week.canEdit;

  return (
    <section className={`break-inside-avoid ${spaced ? 'mt-8' : ''}`}>
      <div className="mb-1 ml-[1.1in] flex items-end gap-2 text-sm font-bold">
        <span>Week</span>
        <span className="min-w-[2in] border-b border-black px-2 font-normal">
          {weekRangeLabel(week.weekStart)}, {week.weekStart.slice(0, 4)}
        </span>
        {!week.published && (
          <span className="ml-2 text-xs font-normal italic text-gray-500">
            {hidden ? 'not posted yet' : 'draft'}
          </span>
        )}
      </div>
      <table className="w-full table-fixed border-collapse border border-black text-[11px]">
        <colgroup>
          <col className="w-[1.5in]" />
          {days.map((d) => <col key={d} />)}
        </colgroup>
        <thead>
          <tr>
            <th className="border border-black py-0.5 text-center font-bold italic">NAME</th>
            {DAY_HEADERS.map((h) => (
              <th key={h} className="border border-black py-0.5 text-center font-bold italic">{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {week.roster.map((person, i) => (
            <tr key={person.userId} className={startsReceptionists(week.roster, i) ? 'border-t-2 border-t-black' : ''}>
              <td className="h-[0.32in] truncate border border-black px-1.5 font-semibold">{person.name}</td>
              {days.map((d) => {
                const s = cell.get(`${person.userId}|${d}`);
                return (
                  <td key={d} className="border border-black px-1 text-center">
                    {s ? shortShift(s.start, s.end) : ''}
                  </td>
                );
              })}
            </tr>
          ))}
          {Array.from({ length: BLANK_ROWS }, (_, i) => (
            <tr key={`blank-${i}`}>
              <td className="h-[0.32in] border border-black" />
              {days.map((d) => <td key={d} className="border border-black" />)}
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
