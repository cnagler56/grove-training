'use client';

import { useState } from 'react';
import { api, type PayPerson, type PayRules } from '@/src/lib/api';
import { moneyCents } from '@/src/lib/format';
import { roleLabel } from '@/src/lib/roles';

type Setting = 'MINIMUM' | 'LEAD' | 'MANAGER';
const SETTINGS: { key: Setting; label: string }[] = [
  { key: 'MINIMUM', label: 'Stylist minimum ($ per hour)' },
  { key: 'LEAD', label: 'Lead bump ($ per hour)' },
  { key: 'MANAGER', label: 'Manager bump ($ per hour)' },
];

/**
 * Owner Settings: how everyone is paid. Stylists get the matrix rate or the minimum,
 * whichever is higher, plus the lead or manager bump for their role. Anyone with a
 * flat rate (every receptionist) gets exactly that instead.
 */
export default function PayPeopleCard({ rules, onChange }: { rules: PayRules; onChange: (r: PayRules) => void }) {
  const saved: Record<Setting, number> = { MINIMUM: rules.minimumRate, LEAD: rules.leadBump, MANAGER: rules.managerBump };
  const [values, setValues] = useState<Record<Setting, string>>({
    MINIMUM: String(rules.minimumRate), LEAD: String(rules.leadBump), MANAGER: String(rules.managerBump),
  });
  const [flats, setFlats] = useState<Record<string, string>>({});
  const [newName, setNewName] = useState('');
  const [newRate, setNewRate] = useState('');
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

  function saveSetting(key: Setting) {
    const amount = Number(values[key]);
    const label = SETTINGS.find((s) => s.key === key)!.label.replace(' ($ per hour)', '').toLowerCase();
    void run(() => (key === 'MINIMUM' ? api.setMinimumRate(amount) : api.setBump(key, amount)), `Saved the ${label}.`);
  }

  async function saveFlat(p: PayPerson, value: string | null) {
    const flatRate = value == null || value.trim() === '' ? null : Number(value);
    const ok = await run(() => api.setFlatRate({ name: p.name, flatRate }),
      flatRate == null ? `${p.name} is back on the matrix.` : `Saved ${p.name}'s flat rate.`);
    if (ok) {
      setFlats((all) => {
        const rest = { ...all };
        delete rest[p.name];
        return rest;
      });
    }
  }

  async function addPerson() {
    const name = newName.trim();
    if (!name || !newRate) return;
    const ok = await run(() => api.setFlatRate({ name, flatRate: Number(newRate) }), `Added ${name}.`);
    if (ok) {
      setNewName('');
      setNewRate('');
    }
  }

  /** What they're paid, in words. Null: a receptionist with no flat rate yet. */
  function payLabel(p: PayPerson) {
    if (p.flatRate != null) return `${moneyCents(p.flatRate)} flat`;
    if (p.role === 'RECEPTIONIST') return null;
    const bump = p.role === 'LEAD' ? rules.leadBump : p.role === 'MANAGER' ? rules.managerBump : 0;
    return `Matrix, at least ${moneyCents(rules.minimumRate)}${bump > 0 ? ` + ${moneyCents(bump)}` : ''}`;
  }

  return (
    <section className="card mt-8 p-0">
      <div className="p-5 pb-3">
        <h2 className="text-lg font-extrabold">How everyone is paid</h2>
        <p className="mt-1 text-sm opacity-70">
          Stylists get the matrix rate or the minimum, whichever is higher. Leads and managers (their role on the
          Team page) get their bump on top. Anyone with a flat rate, like receptionists, gets exactly that instead.
        </p>
        <div className="mt-3 flex flex-wrap items-end gap-x-6 gap-y-3">
          {SETTINGS.map(({ key, label }) => (
            <div key={key} className="flex items-end gap-2">
              <label className="block text-sm">
                <span className="mb-1 block font-semibold opacity-80">{label}</span>
                <input className="input w-28" type="number" step="0.01" min="0" value={values[key]}
                  onChange={(e) => setValues({ ...values, [key]: e.target.value })} />
              </label>
              <button className="btn" disabled={busy || values[key] === '' || Number(values[key]) === saved[key]}
                onClick={() => saveSetting(key)}>Save</button>
            </div>
          ))}
        </div>
        {error && <p className="mt-3 text-sm text-[var(--danger)]">{error}</p>}
        {notice && <p className="mt-3 text-sm font-semibold text-[var(--success)]">{notice}</p>}
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[560px] text-sm">
          <thead>
            <tr className="border-y border-[var(--border)] text-left text-xs uppercase tracking-wide opacity-70">
              <th className="px-4 py-2">Name</th>
              <th className="px-2 py-2">Paid</th>
              <th className="px-2 py-2">Flat rate</th>
              <th className="px-4 py-2" />
            </tr>
          </thead>
          <tbody className="divide-y divide-[var(--border)]">
            {rules.people.map((p) => {
              const savedFlat = p.flatRate == null ? '' : String(p.flatRate);
              const value = flats[p.name] ?? savedFlat;
              const changed = value.trim() === '' ? p.flatRate != null : Number(value) !== p.flatRate;
              const pay = payLabel(p);
              return (
                <tr key={p.name}>
                  <td className="px-4 py-2">
                    <div className="font-semibold">{p.name}</div>
                    <div className="text-xs">
                      {p.role
                        ? <span className="opacity-60">{roleLabel(p.role)}</span>
                        : <span className="font-semibold text-[var(--warn)]">Not linked to a site account (Team page)</span>}
                    </div>
                  </td>
                  <td className="px-2 py-2">
                    {pay ?? <span className="font-semibold text-[var(--warn)]">Needs a flat rate</span>}
                  </td>
                  <td className="px-2 py-2">
                    <input className="input w-24 py-1" type="number" step="0.01" min="0" placeholder="None"
                      value={value} onChange={(e) => setFlats({ ...flats, [p.name]: e.target.value })} />
                  </td>
                  <td className="whitespace-nowrap px-4 py-2 text-right">
                    {changed && (
                      <button className="btn px-3 py-1 text-xs" disabled={busy} onClick={() => void saveFlat(p, value)}>
                        Save
                      </button>
                    )}
                    {!changed && p.flatRate != null && (
                      <button className="text-xs font-semibold hover:underline" disabled={busy} onClick={() => void saveFlat(p, null)}>
                        Use matrix
                      </button>
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
          <span className="mb-1 block font-semibold opacity-80">Add a flat rate for someone not listed (name as Salon Ultimate shows it)</span>
          <input className="input w-64" placeholder="First Last" value={newName} onChange={(e) => setNewName(e.target.value)} />
        </label>
        <input className="input w-24" type="number" step="0.01" min="0" placeholder="$ / hr" aria-label="Flat rate"
          value={newRate} onChange={(e) => setNewRate(e.target.value)} />
        <button className="btn-ghost" disabled={busy || !newName.trim() || !newRate} onClick={() => void addPerson()}>Add</button>
      </div>
    </section>
  );
}
