'use client';

import { useState } from 'react';
import { api, type PayPerson, type PayRules } from '@/src/lib/api';
import { moneyCents } from '@/src/lib/format';
import { roleLabel } from '@/src/lib/roles';

type Kind = 'MATRIX' | 'FLAT';
type Draft = { kind: Kind; amount: string; code: string };

const draftOf = (p: PayPerson): Draft => ({
  kind: p.flatRate != null ? 'FLAT' : 'MATRIX',
  amount: p.flatRate != null ? String(p.flatRate) : p.premium ? String(p.premium) : '',
  code: p.code != null ? String(p.code) : '',
});

const same = (a: Draft, b: Draft) => a.kind === b.kind && a.code === b.code && Number(a.amount || 0) === Number(b.amount || 0);

/**
 * Owner Settings: each person's pay (the matrix plus a premium, or a flat hourly
 * rate), and the bumps leads and managers get on top of the matrix. Payroll and My week
 * both use these.
 */
export default function PayPeopleCard({ rules, onChange }: { rules: PayRules; onChange: (r: PayRules) => void }) {
  const [drafts, setDrafts] = useState<Record<string, Draft>>({});
  const [bumps, setBumps] = useState({ LEAD: String(rules.leadBump), MANAGER: String(rules.managerBump) });
  const [newName, setNewName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  async function run(action: () => Promise<PayRules>, done: string) {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      onChange(await action());
      setNotice(done);
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save');
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function save(p: PayPerson, d: Draft) {
    const amount = d.amount.trim() === '' ? null : Number(d.amount);
    const ok = await run(() => api.savePayPerson({
      name: p.name,
      code: d.code.trim() === '' ? null : Number(d.code),
      premium: d.kind === 'MATRIX' ? amount ?? 0 : null,
      flatRate: d.kind === 'FLAT' ? amount : null,
    }), `Saved ${p.name}'s pay.`);
    if (ok) {
      setDrafts((all) => {
        const rest = { ...all };
        delete rest[p.name];
        return rest;
      });
    }
  }

  async function addPerson() {
    const name = newName.trim();
    if (!name) return;
    const ok = await run(() => api.savePayPerson({ name, code: null, premium: 0, flatRate: null }), `Added ${name}.`);
    if (ok) setNewName('');
  }

  const savedBump = { LEAD: rules.leadBump, MANAGER: rules.managerBump };
  const bumpFor = (role: string | null) => (role === 'LEAD' ? rules.leadBump : role === 'MANAGER' ? rules.managerBump : 0);

  return (
    <section className="card mt-8 p-0">
      <div className="p-5 pb-3">
        <h2 className="text-lg font-extrabold">Individual pay rates</h2>
        <p className="mt-1 text-sm opacity-70">
          Each person gets the matrix rate plus their premium, or a flat hourly rate instead.
          Leads and managers (their role on the Team page) also get their bump, except on a flat rate.
        </p>
        <div className="mt-3 flex flex-wrap items-end gap-x-6 gap-y-3">
          {(['LEAD', 'MANAGER'] as const).map((role) => {
            const label = role === 'LEAD' ? 'Lead' : 'Manager';
            const value = bumps[role];
            return (
              <div key={role} className="flex items-end gap-2">
                <label className="block text-sm">
                  <span className="mb-1 block font-semibold opacity-80">{label} bump ($ per hour)</span>
                  <input className="input w-28" type="number" step="0.01" min="0" max="10" value={value}
                    onChange={(e) => setBumps({ ...bumps, [role]: e.target.value })} />
                </label>
                <button className="btn" disabled={busy || value === '' || Number(value) === savedBump[role]}
                  onClick={() => void run(() => api.setBump(role, Number(value)), `Saved the ${label.toLowerCase()} bump.`)}>Save</button>
              </div>
            );
          })}
        </div>
        {error && <p className="mt-3 text-sm text-[var(--danger)]">{error}</p>}
        {notice && <p className="mt-3 text-sm font-semibold text-[var(--success)]">{notice}</p>}
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[620px] text-sm">
          <thead>
            <tr className="border-y border-[var(--border)] text-left text-xs uppercase tracking-wide opacity-70">
              <th className="px-4 py-2">Name</th>
              <th className="px-2 py-2">Pay</th>
              <th className="px-2 py-2">Amount</th>
              <th className="px-2 py-2">Code</th>
              <th className="px-4 py-2" />
            </tr>
          </thead>
          <tbody className="divide-y divide-[var(--border)]">
            {rules.people.map((p) => {
              const saved = draftOf(p);
              const d = drafts[p.name] ?? saved;
              const set = (patch: Partial<Draft>) => setDrafts({ ...drafts, [p.name]: { ...d, ...patch } });
              const changed = !same(d, saved) || !p.hasCode;
              const bump = bumpFor(p.role);
              return (
                <tr key={p.name}>
                  <td className="px-4 py-2">
                    <div className="font-semibold">{p.name}</div>
                    <div className="flex flex-wrap gap-x-2 text-xs">
                      {p.role && <span className={bump > 0 ? 'font-semibold text-[var(--accent-strong)]' : 'opacity-60'}>{roleLabel(p.role)}</span>}
                      {!p.hasCode && <span className="font-semibold text-[var(--warn)]">No pay code yet</span>}
                      {bump > 0 && p.flatRate == null && (p.premium ?? 0) > 0 && (
                        <span className="font-semibold text-[var(--warn)]">
                          Premium plus {moneyCents(bump)} {roleLabel(p.role!).toLowerCase()} bump. Make sure it isn&apos;t counted twice.
                        </span>
                      )}
                    </div>
                  </td>
                  <td className="px-2 py-2">
                    <select className="input w-32 py-1" value={d.kind} onChange={(e) => set({ kind: e.target.value as Kind, amount: '' })}>
                      <option value="MATRIX">Matrix +</option>
                      <option value="FLAT">Flat rate</option>
                    </select>
                  </td>
                  <td className="px-2 py-2">
                    <input className="input w-24 py-1" type="number" step="0.01" placeholder={d.kind === 'MATRIX' ? '0.00' : '$ / hr'}
                      value={d.amount} onChange={(e) => set({ amount: e.target.value })} />
                  </td>
                  <td className="px-2 py-2">
                    <input className="input w-16 py-1" type="number" step="1" min="0" value={d.code} onChange={(e) => set({ code: e.target.value })} />
                  </td>
                  <td className="whitespace-nowrap px-4 py-2 text-right">
                    {changed && (
                      <button className="btn px-3 py-1 text-xs" disabled={busy || (d.kind === 'FLAT' && !d.amount)}
                        onClick={() => void save(p, d)}>Save</button>
                    )}
                    {p.hasCode && !changed && (
                      <button className="text-xs font-semibold text-[var(--danger)] hover:underline" disabled={busy}
                        onClick={() => void run(() => api.removePayPerson(p.name), `Removed ${p.name}'s pay code.`)}>Remove</button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <div className="flex flex-wrap items-end gap-3 p-5 pt-3">
        <label className="block text-sm">
          <span className="mb-1 block font-semibold opacity-80">Add someone not listed (name as Salon Ultimate shows it)</span>
          <input className="input w-64" placeholder="First Last" value={newName} onChange={(e) => setNewName(e.target.value)} />
        </label>
        <button className="btn-ghost" disabled={busy || !newName.trim()} onClick={() => void addPerson()}>Add</button>
      </div>
    </section>
  );
}
