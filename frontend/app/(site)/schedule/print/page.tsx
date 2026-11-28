'use client';

import { Suspense, useEffect, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { api, type WeekSchedule } from '@/src/lib/api';
import { SALON } from '@/src/lib/salon';
import ScheduleSheet from '@/src/components/ScheduleSheet';
import { scheduledThisWeek, todayIso, weekRangeLabel, weekStartOf } from '@/src/lib/schedule';

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
  const people = scheduledThisWeek(week.roster, week.shifts);
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

      <ScheduleSheet week={week} />
      {people.length === 0 && <p className="mt-2 text-sm text-gray-500">No one is on the schedule this week yet.</p>}
    </section>
  );
}
