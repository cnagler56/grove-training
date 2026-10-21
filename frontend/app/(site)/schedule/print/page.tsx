'use client';

import { Suspense, useEffect, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { api, type WeekSchedule } from '@/src/lib/api';
import { SALON } from '@/src/lib/salon';
import {
  hoursLabel, shiftHours, shortShift, startsReceptionists, todayIso, weekRangeLabel, weekStartOf, weekDates,
} from '@/src/lib/schedule';

const DAY_NAMES = ['Sat', 'Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri'];

export default function PrintSchedulePage() {
  // useSearchParams needs a Suspense boundary in the app router.
  return (
    <Suspense fallback={<p className="opacity-60">Loading…</p>}>
      <PrintSchedule />
    </Suspense>
  );
}

/**
 * Printable schedule: one Saturday–Friday week on a letter page, laid out
 * like the salon's paper form (names down the left, days across) but cleaner.
 */
function PrintSchedule() {
  const params = useSearchParams();
  const first = weekStartOf(params.get('week') ?? todayIso());
  const [week, setWeek] = useState<WeekSchedule | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.schedule(first)
      .then((w) => { if (!cancelled) setWeek(w); })
      .catch((e) => { if (!cancelled) setError(e instanceof Error ? e.message : 'Could not load the schedule'); });
    return () => { cancelled = true; };
  }, [first]);

  if (!week) return <p className="opacity-60">{error ?? 'Loading…'}</p>;

  return (
    <div>
      {/* Letter, portrait. `exact` keeps the header colors and row shading when printing. */}
      <style>{`
        @page { size: letter portrait; margin: 0.45in; }
        .print-sheet { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
      `}</style>

      <div className="mb-6 flex flex-wrap items-center justify-between gap-3 print:hidden">
        <Link href="/schedule" className="text-sm opacity-70 hover:opacity-100">Back to salon schedule</Link>
        <div className="flex items-center gap-3">
          <span className="text-sm opacity-70">Week of {weekRangeLabel(first)}</span>
          <button className="btn" onClick={() => window.print()}>Print</button>
        </div>
      </div>

      <div className="print-sheet mx-auto max-w-[7.6in] rounded-2xl bg-white p-8 text-[#1f2a33] shadow-lg print:max-w-none print:rounded-none print:p-0 print:shadow-none">
        <header className="flex items-end justify-between border-b-2 border-[#4682b4] pb-2">
          <div>
            <div className="text-xl font-extrabold tracking-tight text-[#2c5a82]">{SALON.name}</div>
            <div className="text-sm font-semibold text-gray-500">Staff schedule</div>
          </div>
          <div className="text-right text-xs text-gray-500">
            Printed {new Date().toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })}
          </div>
        </header>

        <PrintWeek week={week} />
      </div>
    </div>
  );
}

function PrintWeek({ week }: { week: WeekSchedule }) {
  const days = weekDates(week.weekStart);
  const cell = new Map(week.shifts.map((s) => [`${s.userId}|${s.date}`, s]));
  const status = week.published ? null : week.canEdit ? 'Draft' : 'Not posted yet';

  return (
    <section className="mt-6 break-inside-avoid">
      <div className="mb-2 flex items-center gap-3">
        <h2 className="text-base font-extrabold">
          Week of {weekRangeLabel(week.weekStart)}, {week.weekStart.slice(0, 4)}
        </h2>
        {status && (
          <span className="rounded-full bg-amber-100 px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wide text-amber-800">
            {status}
          </span>
        )}
      </div>

      <table className="w-full table-fixed border-collapse text-[13px]">
        <colgroup>
          <col className="w-[1.55in]" />
          {days.map((d) => <col key={d} />)}
          <col className="w-[0.5in]" />
        </colgroup>
        <thead>
          <tr className="bg-[#4682b4] text-white">
            <th className="rounded-tl-md px-2.5 py-1.5 text-left text-[11px] font-bold uppercase tracking-wide">Name</th>
            {days.map((d, i) => (
              <th key={d} className="px-1 py-1 text-center leading-tight">
                <div className="text-[11px] font-bold uppercase tracking-wide">{DAY_NAMES[i]}</div>
                <div className="text-[10px] font-semibold opacity-85">{Number(d.slice(5, 7))}/{Number(d.slice(8, 10))}</div>
              </th>
            ))}
            <th className="rounded-tr-md px-1 py-1.5 text-center text-[11px] font-bold uppercase tracking-wide">Hrs</th>
          </tr>
        </thead>
        <tbody>
          {week.roster.map((person, i) => {
            const shifts = days.map((d) => cell.get(`${person.userId}|${d}`));
            const total = shifts.reduce((h, s) => h + (s ? shiftHours(s.start, s.end) : 0), 0);
            return (
              <tr
                key={person.userId}
                className={`${i % 2 ? 'bg-[#f3f6f9]' : 'bg-white'} ${
                  startsReceptionists(week.roster, i) ? 'border-t-2 border-t-[#4682b4]' : ''
                }`}
              >
                <td className="h-[0.42in] truncate border-b border-[#d8dfe6] px-2.5 font-bold">{person.name}</td>
                {shifts.map((s, j) => (
                  <td key={days[j]} className="border-b border-l border-[#d8dfe6] px-1 text-center font-semibold">
                    {s ? shortShift(s.start, s.end) : ''}
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
      {week.roster.length === 0 && <p className="mt-2 text-sm text-gray-500">Nobody on staff to schedule yet.</p>}
    </section>
  );
}
