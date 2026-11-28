'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, type AdpFile } from '@/src/lib/api';
import { moneyCents } from '@/src/lib/format';
import { adpLineText, handLineText } from '@/src/lib/payroll';

/**
 * The file to import into ADP (Home, Time Sheet Import) for this check, and what's
 * still left to type by hand. ADP pays imported hours at the rate on each person's
 * ADP profile, so the file only carries hours at that rate; everything else is listed.
 */
export default function AdpFileCard({ checkDate }: { checkDate: string }) {
  const [file, setFile] = useState<AdpFile | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [downloaded, setDownloaded] = useState(false);

  const load = useCallback(() => {
    api.adpFile(checkDate).then((f) => { setFile(f); setError(null); }).catch((e) => setError(e.message));
  }, [checkDate]);
  useEffect(load, [load]);

  async function download() {
    if (!file) return;
    setBusy(true);
    setError(null);
    try {
      await api.downloadAdpFile(checkDate, file.fileName);
      setDownloaded(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not make the file');
    } finally {
      setBusy(false);
    }
  }

  if (!file) {
    return <section className="card p-5 text-sm opacity-60">{error ?? 'Loading the ADP file…'}</section>;
  }

  const ready = file.problems.length === 0;
  const listed = file.people.filter((p) => p.lines.length > 0 || p.byHand.length > 0);

  return (
    <section className="card p-0 print:hidden">
      <div className="space-y-3 p-5 pb-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 className="text-lg font-extrabold">ADP import file</h2>
            <p className="mt-1 max-w-2xl text-sm opacity-70">
              Brings regular hours paid at each person&apos;s ADP rate, vacation, cash tips, product commission, and
              monthly bonuses into ADP. ADP can&apos;t import other rates, so hours at any other rate, additions, and
              deductions are listed under Type in ADP.
            </p>
          </div>
          <div className="flex gap-2">
            <button className="btn-ghost" disabled={busy} onClick={load}>Check again</button>
            <button className="btn" disabled={busy || !ready} onClick={() => void download()}>
              {busy ? 'Making the file…' : 'Download file'}
            </button>
          </div>
        </div>

        {error && <p className="text-sm text-[var(--danger)]">{error}</p>}

        {!ready && (
          <div className="rounded-xl border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-[var(--warn)]">
            <b>Fix these before downloading:</b>
            <ul className="mt-1 list-disc space-y-0.5 pl-5">
              {file.problems.map((p) => <li key={p}>{p}</li>)}
            </ul>
          </div>
        )}
        {file.notices.length > 0 && (
          <ul className="list-disc space-y-0.5 pl-5 text-sm opacity-80">
            {file.notices.map((n) => <li key={n}>{n}</li>)}
          </ul>
        )}

        <ol className={`list-decimal space-y-0.5 pl-5 text-sm ${downloaded ? '' : 'opacity-70'}`}>
          <li>Download the file{downloaded && <span className="font-semibold text-[var(--success)]"> (done: {file.fileName})</span>}.</li>
          <li>In ADP, go to Home, then Time Sheet Import. Select the file and click Import timesheet.</li>
          <li>
            On ADP&apos;s review screen, check each person&apos;s pay rate and hours against In the file below. If anything
            is off, click Import a different timesheet to throw it away.
          </li>
          <li>Continue to payroll, then type everything under Type in ADP. Open with ADP shows it one person at a time.</li>
        </ol>
        {file.lastCheckOfMonth && file.bonus > 0 && (
          <p className="text-sm">Last check of the month: monthly bonuses are in the file.</p>
        )}
      </div>

      {listed.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[760px] text-sm">
            <thead>
              <tr className="border-y border-[var(--border)] text-left text-xs uppercase tracking-wide opacity-70">
                <th className="px-4 py-2">Name</th>
                <th className="px-2 py-2">ADP ID</th>
                <th className="px-2 py-2">ADP pay rate</th>
                <th className="px-2 py-2">In the file</th>
                <th className="px-4 py-2">Type in ADP</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[var(--border)]">
              {listed.map((p) => (
                <tr key={p.name} className="align-top">
                  <td className="px-4 py-2 font-semibold">{p.name}</td>
                  <td className="px-2 py-2 tabular-nums">
                    {p.employeeId ?? <span className="font-semibold text-[var(--warn)]">Missing</span>}
                  </td>
                  <td className="whitespace-nowrap px-2 py-2 tabular-nums">
                    {p.salaried ? 'Fixed pay' : p.adpRate != null ? moneyCents(p.adpRate) : <span className="opacity-40">-</span>}
                  </td>
                  <td className="px-2 py-2 tabular-nums">
                    {p.lines.length === 0 ? <span className="opacity-40">-</span> : (
                      <ul className="space-y-0.5">{p.lines.map((l) => <li key={l.earningsCode}>{adpLineText(l)}</li>)}</ul>
                    )}
                  </td>
                  <td className="px-4 py-2 tabular-nums">
                    {p.byHand.length === 0 ? <span className="opacity-40">-</span> : (
                      <ul className="space-y-0.5 font-semibold">
                        {p.byHand.map((h, i) => <li key={i}>{handLineText(h)}</li>)}
                      </ul>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr className="border-t border-[var(--border)] align-top text-xs">
                <td className="px-4 py-2 font-bold uppercase tracking-wide opacity-70" colSpan={3}>File totals</td>
                <td className="px-2 py-2 tabular-nums" colSpan={2}>
                  Regular {file.regularHours.toFixed(2)} hrs · Vacation {file.vacationHours.toFixed(2)} hrs · Cash tips{' '}
                  {moneyCents(file.tips)} · Commission {moneyCents(file.commission)} · Bonus {moneyCents(file.bonus)}
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </section>
  );
}
