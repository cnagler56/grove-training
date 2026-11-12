'use client';

import Link from 'next/link';
import { Fragment, useCallback, useEffect, useState } from 'react';
import { api, type PayrollPayWeek, type PayrollReport } from '@/src/lib/api';
import { formatDate, formatDateTime, moneyCents } from '@/src/lib/format';
import { addDays, todayIso } from '@/src/lib/schedule';

const hrs = (n: number) => n.toFixed(2);
const weekRange = (start: string) => `${formatDate(start, false)} – ${formatDate(addDays(start, 6), false)}`;

/**
 * The payroll skill's ADP report, built on the site: each person's rate, floor and
 * off-floor hours, tips, and retail commission for both weeks of the pay period,
 * from the weeks pulled from Salon Ultimate every Saturday.
 */
export default function PayrollHoursCard({ checkDate }: { checkDate: string }) {
  const [report, setReport] = useState<PayrollReport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pulling, setPulling] = useState<string | null>(null);

  const load = useCallback(() => {
    api.payrollReport(checkDate).then(setReport).catch((e) => setError(e.message));
  }, [checkDate]);
  useEffect(load, [load]);

  async function pull(week: string) {
    setError(null);
    setPulling(week);
    try {
      const result = await api.pullPayrollWeek(week);
      if (result.status !== 'OK' && result.message) setError(result.message);
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not pull that week');
    } finally {
      setPulling(null);
    }
  }

  if (!report) {
    return <section className="card p-5 text-sm opacity-60">{error ?? 'Loading hours and pay…'}</section>;
  }

  return (
    <section className="card p-0">
      <div className="space-y-2 p-5 pb-3">
        <h2 className="text-lg font-extrabold">Hours and pay</h2>
        <p className="text-sm opacity-70">From Salon Ultimate, figured the same way as the payroll skill.</p>

        <ul className="space-y-1 text-sm print:hidden">
          {report.weeks.map((w, i) => {
            const over = addDays(w.weekStart, 6) < todayIso();
            const ok = w.pull?.status === 'OK';
            return (
              <li key={w.weekStart} className="flex flex-wrap items-center gap-x-3 gap-y-1">
                <span><b>Week {i + 1}</b> {weekRange(w.weekStart)}:</span>
                <span className={ok ? 'opacity-70' : 'font-semibold text-[var(--warn)]'}>
                  {!over ? 'Not over yet'
                    : !w.pull ? 'Not pulled yet'
                    : ok ? `Pulled ${formatDateTime(w.pull.attemptedAt)}`
                    : w.pull.message ?? 'The last pull didn\'t work'}
                </span>
                {over && report.salonUltimateConfigured && (
                  <button className="text-xs font-semibold underline" disabled={pulling !== null} onClick={() => void pull(w.weekStart)}>
                    {pulling === w.weekStart ? 'Pulling…' : ok ? 'Pull again' : 'Pull now'}
                  </button>
                )}
              </li>
            );
          })}
        </ul>
        {!report.salonUltimateConfigured && (
          <p className="text-sm font-semibold text-[var(--warn)]">The server has no Salon Ultimate sign-in, so weeks can&apos;t be pulled.</p>
        )}
        {!report.ratesLoaded && (
          <p className="text-sm font-semibold text-[var(--warn)]">
            Rates are blank until the pay matrix is loaded on <Link href="/admin/settings" className="underline">Settings</Link>.
          </p>
        )}
        {error && <p className="text-sm text-[var(--danger)]">{error}</p>}
      </div>

      {report.rows.length === 0 ? (
        <p className="px-5 pb-5 text-sm opacity-70">No hours for this pay period yet.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[680px] text-sm tabular-nums">
            <thead>
              <tr className="border-y border-[var(--border)] text-left text-xs uppercase tracking-wide opacity-70">
                <th className="px-4 py-2.5">Name</th>
                <th className="px-2 py-2.5 text-center">Wk</th>
                <th className="px-3 py-2.5 text-right">Rate</th>
                <th className="px-3 py-2.5 text-right">Floor hrs</th>
                <th className="px-3 py-2.5 text-right">Off-floor</th>
                <th className="px-3 py-2.5 text-right">Tips</th>
                <th className="px-4 py-2.5 text-right">Commission</th>
              </tr>
            </thead>
            <tbody>
              {report.rows.map((r) => (
                <Fragment key={r.suName}>
                  {[r.week1, r.week2].map((w, i) => (
                    <tr key={i} className={i === 1 ? 'border-b border-[var(--border)]' : ''}>
                      {i === 0 && (
                        <td rowSpan={2} className="px-4 py-1.5 align-top">
                          <div className="font-bold">{r.name}</div>
                          <div className="flex flex-wrap gap-1.5 text-xs">
                            {(r.role === 'LEAD' || r.role === 'MANAGER') && (
                              <span className="font-semibold text-[var(--accent-strong)]">{r.role === 'LEAD' ? 'Lead' : 'Manager'}</span>
                            )}
                            {r.flatRate != null && <span className="opacity-60">Flat {moneyCents(r.flatRate)}</span>}
                          </div>
                          {r.problem && <div className="mt-0.5 max-w-56 text-xs font-semibold text-[var(--warn)] print:hidden">{r.problem}</div>}
                        </td>
                      )}
                      <td className="px-2 py-1.5 text-center opacity-60">{i + 1}</td>
                      <WeekCells w={w} />
                    </tr>
                  ))}
                </Fragment>
              ))}
              <tr className="font-bold">
                <td className="px-4 py-2.5" colSpan={3}>Total</td>
                <td className="px-3 py-2.5 text-right">{hrs(report.floorHours)}</td>
                <td className="px-3 py-2.5 text-right">{hrs(report.offFloorHours)}</td>
                <td className="px-3 py-2.5 text-right">{moneyCents(report.tips)}</td>
                <td className="px-4 py-2.5 text-right">{moneyCents(report.commission)}</td>
              </tr>
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function WeekCells({ w }: { w: PayrollPayWeek | null }) {
  if (!w) return <td colSpan={5} className="px-3 py-1.5 text-right text-xs opacity-40">Not in this week&apos;s report</td>;
  return (
    <>
      <td className="px-3 py-1.5 text-right font-semibold">{w.rate == null ? '-' : moneyCents(w.rate)}</td>
      <td className="px-3 py-1.5 text-right">{hrs(w.floorHours)}</td>
      <td className="px-3 py-1.5 text-right">{hrs(w.offFloorHours)}</td>
      <td className="px-3 py-1.5 text-right">{moneyCents(w.tips)}</td>
      <td className="px-4 py-1.5 text-right">{w.commission > 0 ? moneyCents(w.commission) : <span className="opacity-40">-</span>}</td>
    </>
  );
}
