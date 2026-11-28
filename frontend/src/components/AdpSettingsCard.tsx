'use client';

import { useEffect, useState } from 'react';
import { api, type AdpPerson, type AdpSettings } from '@/src/lib/api';
import { moneyCents } from '@/src/lib/format';
import { roleLabel } from '@/src/lib/roles';

/** A row's unsaved changes. */
type Draft = { id: string; salaried: boolean; bonus: string };

const savedDraft = (p: AdpPerson): Draft => ({
  id: p.employeeId ?? '',
  salaried: p.salaried,
  bonus: p.monthlyBonus != null ? String(p.monthlyBonus) : '',
});

/**
 * Owner Settings: what the ADP import file on the Payroll tab needs. ADP's company
 * code, and for each person their ADP employee ID, whether ADP pays them a fixed
 * amount, and any bonus paid on the last check of every month.
 */
export default function AdpSettingsCard() {
  const [settings, setSettings] = useState<AdpSettings | null>(null);
  const [company, setCompany] = useState('');
  const [drafts, setDrafts] = useState<Record<string, Draft>>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    api.adpSettings().then((s) => { setSettings(s); setCompany(s.companyCode ?? ''); }).catch((e) => setError(e.message));
  }, []);

  async function run(action: () => Promise<AdpSettings>, done: string) {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const s = await action();
      setSettings(s);
      setNotice(done);
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save');
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function saveRow(p: AdpPerson, d: Draft) {
    const ok = await run(() => api.setAdpPerson({
      name: p.name,
      employeeId: d.id.trim() || null,
      salaried: d.salaried,
      monthlyBonus: d.bonus.trim() === '' ? null : Number(d.bonus),
    }), `Saved ${p.name}'s ADP details.`);
    if (ok) {
      setDrafts((all) => {
        const rest = { ...all };
        delete rest[p.name];
        return rest;
      });
    }
  }

  if (!settings) {
    return <section className="card mt-8 p-5 text-sm opacity-60">{error ?? 'Loading ADP settings…'}</section>;
  }

  return (
    <section className="card mt-8 p-0">
      <div className="p-5 pb-3">
        <h2 className="text-lg font-extrabold">ADP import file</h2>
        <p className="mt-1 text-sm opacity-70">
          For the ADP file on the Payroll tab. Each person needs their ADP employee ID, from ADP&apos;s Employee Codes
          report (in ADP Reports, search for employee codes). Fixed pay means ADP pays them a set amount, so the file
          only sends their hours. A monthly bonus goes in the file on the last check of each month. ADP pays imported
          hours at the rate in each person&apos;s ADP profile, so that rate should match the Vacation and off-floor rate here.
        </p>
        <div className="mt-3 flex flex-wrap items-end gap-2">
          <label className="block text-sm">
            <span className="mb-1 block font-semibold opacity-80">ADP company code</span>
            <input className="input w-40" value={company} onChange={(e) => setCompany(e.target.value)}
              placeholder="From ADP's Client ID" />
          </label>
          <button className="btn" disabled={busy || company.trim() === (settings.companyCode ?? '')}
            onClick={() => void run(() => api.setAdpCompany(company.trim()), 'Saved the company code.')}>Save</button>
        </div>
        {error && <p className="mt-3 text-sm text-[var(--danger)]">{error}</p>}
        {notice && <p className="mt-3 text-sm font-semibold text-[var(--success)]">{notice}</p>}
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[680px] text-sm">
          <thead>
            <tr className="border-y border-[var(--border)] text-left text-xs uppercase tracking-wide opacity-70">
              <th className="px-4 py-2">Name</th>
              <th className="px-2 py-2">ADP employee ID</th>
              <th className="px-2 py-2">Fixed pay</th>
              <th className="px-2 py-2">Monthly bonus</th>
              <th className="px-2 py-2">Vacation and off-floor rate</th>
              <th className="px-4 py-2" />
            </tr>
          </thead>
          <tbody className="divide-y divide-[var(--border)]">
            {settings.people.map((p) => {
              const was = savedDraft(p);
              const d = drafts[p.name] ?? was;
              const set = (patch: Partial<Draft>) => setDrafts({ ...drafts, [p.name]: { ...d, ...patch } });
              // A blank bonus and $0 both mean none.
              const changed = d.id.trim() !== was.id || d.salaried !== was.salaried
                || Number(d.bonus || 0) !== Number(was.bonus || 0);
              return (
                <tr key={p.name}>
                  <td className="px-4 py-2">
                    <div className="font-semibold">{p.name}</div>
                    {p.role && <div className="text-xs opacity-60">{roleLabel(p.role)}</div>}
                  </td>
                  <td className="px-2 py-2">
                    <input className="input w-24 py-1" aria-label={`${p.name}'s ADP employee ID`} value={d.id}
                      onChange={(e) => set({ id: e.target.value })} />
                  </td>
                  <td className="px-2 py-2">
                    <label className="flex items-center gap-1.5">
                      <input type="checkbox" checked={d.salaried} onChange={(e) => set({ salaried: e.target.checked })} />
                      <span className="text-xs">Fixed</span>
                    </label>
                  </td>
                  <td className="px-2 py-2">
                    <input className="input w-24 py-1" type="number" step="1" min="0" placeholder="None"
                      aria-label={`${p.name}'s monthly bonus`} value={d.bonus} onChange={(e) => set({ bonus: e.target.value })} />
                  </td>
                  <td className="whitespace-nowrap px-2 py-2 tabular-nums">
                    {d.salaried ? <span className="opacity-40">-</span> : moneyCents(p.standardRate)}
                  </td>
                  <td className="whitespace-nowrap px-4 py-2 text-right">
                    {changed && (
                      <button className="btn px-3 py-1 text-xs" disabled={busy} onClick={() => void saveRow(p, d)}>Save</button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}
