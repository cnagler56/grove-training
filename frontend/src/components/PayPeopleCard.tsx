'use client';

import { useState } from 'react';
import { api, type PayPerson, type PayRules } from '@/src/lib/api';
import { moneyCents } from '@/src/lib/format';
import { roleLabel } from '@/src/lib/roles';
import { useDialogs } from '@/src/components/Dialogs';

type Setting = 'MINIMUM' | 'RECEPTIONIST' | 'LEAD' | 'MANAGER';
const SETTINGS: { key: Setting; label: string; name: string }[] = [
  { key: 'MINIMUM', label: 'Stylist rate', name: 'stylist rate' },
  { key: 'RECEPTIONIST', label: 'Receptionist rate', name: 'receptionist rate' },
  { key: 'LEAD', label: 'Lead bump', name: 'lead bump' },
  { key: 'MANAGER', label: 'Manager bump', name: 'manager bump' },
];

/**
 * Owner Settings: how everyone is paid. Each person has a base rate (the stylist or
 * receptionist rate unless changed just for them). Stylists get the matrix rate or their
 * base, whichever is higher, plus the lead or manager bump; receptionists get their base.
 */
export default function PayPeopleCard({ rules, onChange }: { rules: PayRules; onChange: (r: PayRules) => void }) {
  const saved: Record<Setting, number> = {
    MINIMUM: rules.minimumRate, RECEPTIONIST: rules.receptionistRate, LEAD: rules.leadBump, MANAGER: rules.managerBump,
  };
  const [values, setValues] = useState<Record<Setting, string>>({
    MINIMUM: String(rules.minimumRate), RECEPTIONIST: String(rules.receptionistRate),
    LEAD: String(rules.leadBump), MANAGER: String(rules.managerBump),
  });
  const [bases, setBases] = useState<Record<string, string>>({});
  const [newName, setNewName] = useState('');
  const [newRate, setNewRate] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const dialogs = useDialogs();

  async function remove(p: PayPerson) {
    if (!(await dialogs.confirm({
      title: `Remove ${p.name} from the pay list?`,
      message: 'Any rate set just for them is cleared. Their past hours stay on old pay periods, and they come back '
        + 'on their own if Salon Ultimate reports them again.',
      confirmLabel: 'Remove',
      danger: true,
    }))) return;
    await run(() => api.removePayPerson(p.name), `Removed ${p.name}.`);
  }

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
    const name = SETTINGS.find((s) => s.key === key)!.name;
    void run(() => (key === 'MINIMUM' ? api.setMinimumRate(amount)
      : key === 'RECEPTIONIST' ? api.setReceptionistRate(amount)
      : api.setBump(key, amount)), `Saved the ${name}.`);
  }

  async function saveBase(p: PayPerson, value: string | null) {
    const baseRate = value == null || value.trim() === '' ? null : Number(value);
    const ok = await run(() => api.setBaseRate({ name: p.name, baseRate }),
      baseRate == null || baseRate === p.defaultRate ? `${p.name} is back on the default rate.` : `Saved ${p.name}'s rate.`);
    if (ok) {
      setBases((all) => {
        const rest = { ...all };
        delete rest[p.name];
        return rest;
      });
    }
  }

  async function addPerson() {
    const name = newName.trim();
    if (!name || !newRate) return;
    const ok = await run(() => api.setBaseRate({ name, baseRate: Number(newRate) }), `Added ${name}.`);
    if (ok) {
      setNewName('');
      setNewRate('');
    }
  }

  return (
    <section className="card mt-8 p-0">
      <div className="p-5 pb-3">
        <h2 className="text-lg font-extrabold">How everyone is paid</h2>
        <p className="mt-1 text-sm opacity-70">
          Everyone starts at the stylist or receptionist rate, which you can change for one person below. Stylists
          get the matrix rate when it&apos;s higher. Leads and managers (their role on the Team page) get their bump
          on top. Paid shows the least each person makes an hour.
        </p>
        <div className="mt-3 flex flex-wrap items-end gap-x-6 gap-y-3">
          {SETTINGS.map(({ key, label }) => (
            <div key={key} className="flex items-end gap-2">
              <label className="block text-sm">
                <span className="mb-1 block font-semibold opacity-80">{label} ($/hr)</span>
                <input className="input w-28" type="number" step="0.25" min="0" value={values[key]}
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
        <table className="w-full min-w-[520px] text-sm">
          <thead>
            <tr className="border-y border-[var(--border)] text-left text-xs uppercase tracking-wide opacity-70">
              <th className="px-4 py-2">Name</th>
              <th className="px-2 py-2">Base rate</th>
              <th className="px-2 py-2">Paid</th>
              <th className="px-4 py-2" />
            </tr>
          </thead>
          <tbody className="divide-y divide-[var(--border)]">
            {rules.people.map((p) => {
              const savedBase = String(p.ownRate ?? p.defaultRate);
              const value = bases[p.name] ?? savedBase;
              const changed = value.trim() !== '' && Number(value) !== Number(savedBase);
              const bump = p.paidRate - (p.ownRate ?? p.defaultRate);
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
                    <input className="input w-24 py-1" type="number" step="0.25" min="0" aria-label={`${p.name}'s base rate`}
                      value={value} onChange={(e) => setBases({ ...bases, [p.name]: e.target.value })} />
                  </td>
                  <td className="px-2 py-2">
                    <b>{moneyCents(p.paidRate)}</b>
                    {bump > 0.001 && <span className="ml-1 text-xs opacity-60">incl. {moneyCents(bump)} bump</span>}
                  </td>
                  <td className="whitespace-nowrap px-4 py-2 text-right">
                    {changed && (
                      <button className="btn px-3 py-1 text-xs" disabled={busy} onClick={() => void saveBase(p, value)}>
                        Save
                      </button>
                    )}
                    {!changed && p.ownRate != null && (
                      <button className="text-xs font-semibold hover:underline" disabled={busy} onClick={() => void saveBase(p, null)}>
                        Use default ({moneyCents(p.defaultRate)})
                      </button>
                    )}
                    <button className="ml-3 text-xs font-semibold text-[var(--danger)] hover:underline" disabled={busy}
                      onClick={() => void remove(p)}>Remove</button>
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
        <input className="input w-24" type="number" step="0.25" min="0" placeholder="$ / hr" aria-label="Base rate"
          value={newRate} onChange={(e) => setNewRate(e.target.value)} />
        <button className="btn-ghost" disabled={busy || !newName.trim() || !newRate} onClick={() => void addPerson()}>Add</button>
      </div>
    </section>
  );
}
