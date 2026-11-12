'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { api, type PayPeriods, type PayrollSummary } from '@/src/lib/api';
import { moneyCents } from '@/src/lib/format';
import { NOTE_KIND, signedAmount } from '@/src/lib/payroll';
import { longDayLabel } from '@/src/lib/schedule';
import { useUser } from '@/src/lib/UserContext';
import PayPeriodPicker, { workRange } from '@/src/components/PayPeriodPicker';
import PayrollHoursCard from '@/src/components/PayrollHoursCard';

const hrs = (h: number) => `${Number(h.toFixed(2))}`;

/**
 * Everything for one pay period in one place: approved paid time off hours on
 * that check, plus every deduction, addition, and comment from Payroll notes.
 * Opens on the newest period that's finished.
 */
export default function PayrollPage() {
  const { perms } = useUser();
  const [periods, setPeriods] = useState<PayPeriods | null>(null);
  const [checkDate, setCheckDate] = useState('');
  const [summary, setSummary] = useState<PayrollSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.payPeriods().then((p) => { setPeriods(p); setCheckDate(p.defaultCheckDate); }).catch((e) => setError(e.message));
  }, []);

  useEffect(() => {
    if (!checkDate) return;
    api.payrollSummary(checkDate).then(setSummary).catch((e) => setError(e.message));
  }, [checkDate]);

  const [busy, setBusy] = useState(false);

  /** Acknowledge / Finished / Reopen, then show the period again. */
  async function act(action: () => Promise<unknown>) {
    setError(null);
    setBusy(true);
    try {
      await action();
      setSummary(await api.payrollSummary(checkDate));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong');
    } finally {
      setBusy(false);
    }
  }

  function pick(d: string) {
    setSummary(null);
    setCheckDate(d);
  }

  const period = summary?.period;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight">Payroll</h1>
          <p className="mt-1 text-sm opacity-70">
            Hours, pay rates, paid time off, and payroll notes for the pay period, all in one place.
          </p>
        </div>
        <div className="flex gap-2 print:hidden">
          {perms.payrollNotes && <Link href="/payroll-notes" className="btn-ghost">Add a payroll note</Link>}
          <button className="btn-ghost" onClick={() => window.print()} disabled={!summary}>Print</button>
        </div>
      </div>

      {periods && (
        <div className="max-w-xl print:hidden">
          <PayPeriodPicker periods={periods.periods} value={checkDate} defaultCheckDate={periods.defaultCheckDate} onChange={pick} />
        </div>
      )}

      {error && <p className="text-sm text-[var(--danger)]">{error}</p>}

      {!summary ? (
        !error && <p className="text-sm opacity-60">Loading…</p>
      ) : (
        <>
          {period && (
            <p className="text-sm">
              <b>Work {workRange(period)}</b>
              <span className="opacity-70"> · check {longDayLabel(period.checkDate)}</span>
              {summary.employees.some((e) => e.notes.length > 0 || e.ptoHours > 0) && (() => {
                const open = summary.openNotes + summary.openPto;
                return (
                  <span className={`ml-2 font-semibold print:hidden ${open > 0 ? 'text-[var(--warn)]' : 'text-[var(--success)]'}`}>
                    · {open > 0 ? `${open} ${open === 1 ? 'item' : 'items'} not finished yet` : 'Everything finished'}
                  </span>
                );
              })()}
            </p>
          )}

          {summary.pendingPtoRequests > 0 && (
            <div className="rounded-xl border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-[var(--warn)] print:hidden">
              <b>{summary.pendingPtoRequests} paid time off {summary.pendingPtoRequests === 1 ? 'request is' : 'requests are'}</b> for
              this check and still waiting for an answer. They aren&apos;t counted below until approved.{' '}
              <Link href="/paid-time-off" className="font-semibold underline">Answer them</Link>
            </div>
          )}

          <PayrollHoursCard key={checkDate} checkDate={checkDate} />

          <h2 className="pt-2 text-lg font-extrabold">Paid time off and payroll notes</h2>
          <div className="grid gap-3 sm:grid-cols-3">
            <Tile label="Paid time off" value={`${hrs(summary.ptoHours)} hours`} />
            <Tile label="Additions" value={moneyCents(summary.additions)} />
            <Tile label="Deductions" value={moneyCents(summary.deductions)} />
          </div>

          {summary.employees.length === 0 ? (
            <section className="card p-5 text-sm opacity-70">
              Nothing for this pay period: no paid time off and no payroll notes.
            </section>
          ) : (
            <section className="card overflow-x-auto p-0">
              <table className="w-full min-w-[640px] text-sm">
                <thead>
                  <tr className="border-b border-[var(--border)] text-left text-xs uppercase tracking-wide opacity-70">
                    <th className="px-4 py-3">Employee</th>
                    <th className="px-4 py-3 text-right">PTO hours</th>
                    <th className="px-4 py-3 text-right">Additions</th>
                    <th className="px-4 py-3 text-right">Deductions</th>
                    <th className="px-4 py-3">Notes</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--border)]">
                  {summary.employees.map((e) => (
                    <tr key={e.userId} className="align-top">
                      <td className="px-4 py-3 font-bold">{e.name}</td>
                      <td className="px-4 py-3 text-right tabular-nums">
                        {e.pto.length === 0 ? <span className="opacity-40">-</span> : (
                          <ul className="space-y-1.5">
                            {e.pto.map((p) => (
                              <li key={p.id} className={p.finishedAt ? 'opacity-50' : ''}>
                                {p.status === 'PENDING' ? (
                                  <span className="text-xs font-semibold text-[var(--warn)]">{hrs(p.hours)} waiting for approval</span>
                                ) : (
                                  <div className="flex items-center justify-end gap-2">
                                    <b>{hrs(p.hours)}</b>
                                    <span className="print:hidden">
                                      {p.finishedAt ? (
                                        <>
                                          <span className="text-xs font-bold text-[var(--success)]">Finished</span>
                                          <button className="ml-2 text-xs font-semibold hover:underline" disabled={busy}
                                            onClick={() => void act(() => api.reopenPto(p.id))}>Reopen</button>
                                        </>
                                      ) : (
                                        <button className="btn px-3 py-1 text-xs" disabled={busy}
                                          title="Entered into payroll; tells them and closes the request"
                                          onClick={() => void act(() => api.finishPto(p.id))}>Finished</button>
                                      )}
                                    </span>
                                  </div>
                                )}
                              </li>
                            ))}
                          </ul>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right tabular-nums">
                        {e.additions > 0 ? moneyCents(e.additions) : <span className="opacity-40">-</span>}
                      </td>
                      <td className="px-4 py-3 text-right tabular-nums">
                        {e.deductions > 0 ? moneyCents(e.deductions) : <span className="opacity-40">-</span>}
                      </td>
                      <td className="px-4 py-3">
                        {e.notes.length === 0 ? <span className="opacity-40">-</span> : (
                          <ul className="space-y-1.5">
                            {e.notes.map((n) => (
                              <li key={n.id} className={`flex flex-wrap items-start justify-between gap-2 ${n.status === 'FINISHED' ? 'opacity-50' : ''}`}>
                                <div className="min-w-0">
                                  <span className={`mr-1.5 rounded-full px-2 py-0.5 text-xs font-bold ${NOTE_KIND[n.kind][1]}`}>
                                    {NOTE_KIND[n.kind][0]}
                                  </span>
                                  {signedAmount(n) && <b className="mr-1.5">{signedAmount(n)}</b>}
                                  <span className="whitespace-pre-line">{n.note}</span>
                                  {n.createdByName && <span className="ml-1.5 text-xs opacity-50">({n.createdByName})</span>}
                                </div>
                                <div className="flex shrink-0 items-center gap-2 print:hidden">
                                  {n.status === 'NEW' && (
                                    <button className="btn px-3 py-1 text-xs" disabled={busy}
                                      title="Lets the person who added it know you've seen it"
                                      onClick={() => void act(() => api.acknowledgePayrollNote(n.id))}>Acknowledge</button>
                                  )}
                                  {n.status === 'ACKNOWLEDGED' && (
                                    <button className="btn px-3 py-1 text-xs" disabled={busy}
                                      title="Entered into payroll; closes the note"
                                      onClick={() => void act(() => api.finishPayrollNote(n.id))}>Finished</button>
                                  )}
                                  {n.status === 'FINISHED' && (
                                    <>
                                      <span className="text-xs font-bold text-[var(--success)]">Finished</span>
                                      <button className="text-xs font-semibold hover:underline" disabled={busy}
                                        onClick={() => void act(() => api.reopenPayrollNote(n.id))}>Reopen</button>
                                    </>
                                  )}
                                </div>
                              </li>
                            ))}
                          </ul>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
          )}
        </>
      )}
    </div>
  );
}

function Tile({ label, value }: { label: string; value: string }) {
  return (
    <div className="card p-4">
      <div className="text-xs font-semibold uppercase tracking-wide opacity-60">{label}</div>
      <div className="mt-1 text-2xl font-extrabold tabular-nums">{value}</div>
    </div>
  );
}
