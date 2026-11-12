'use client';

import { useState } from 'react';
import { api, type BumpKind, type PayPerson, type PayRules } from '@/src/lib/api';
import { moneyCents } from '@/src/lib/format';
import { roleLabel } from '@/src/lib/roles';
import { useDialogs } from '@/src/components/Dialogs';

type Setting = 'MINIMUM' | 'RECEPTIONIST' | 'LEAD' | 'LEAD_PROD' | 'MANAGER';
const SETTINGS: { key: Setting; label: string; name: string }[] = [
  { key: 'MINIMUM', label: 'Stylist rate', name: 'stylist rate' },
  { key: 'RECEPTIONIST', label: 'Receptionist rate', name: 'receptionist rate' },
  { key: 'LEAD', label: 'Lead bump', name: 'lead bump' },
  { key: 'LEAD_PROD', label: 'Lead/prod bump', name: 'lead/prod bump' },
  { key: 'MANAGER', label: 'Manager bump', name: 'manager bump' },
];

/** A row's unsaved changes: base rate, which bump, and the amount for Other. */
type Draft = { base: string; kind: BumpKind; custom: string };

/**
 * Owner Settings: how everyone is paid. Each person has a base rate (the stylist or
 * receptionist rate unless changed just for them) and a bump (their role's, or one picked
 * for them). Stylists get the matrix rate or their base, whichever is higher; the bump goes on top.
 */
export default function PayPeopleCard({ rules, onChange }: { rules: PayRules; onChange: (r: PayRules) => void }) {
  const saved: Record<Setting, number> = {
    MINIMUM: rules.minimumRate, RECEPTIONIST: rules.receptionistRate,
    LEAD: rules.leadBump, LEAD_PROD: rules.leadProdBump, MANAGER: rules.managerBump,
  };
  const [values, setValues] = useState<Record<Setting, string>>(
    Object.fromEntries(Object.entries(saved).map(([k, v]) => [k, String(v)])) as Record<Setting, string>);
  const [drafts, setDrafts] = useState<Record<string, Draft>>({});
  const [newName, setNewName] = useState('');
  const [newRate, setNewRate] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const dialogs = useDialogs();

  const savedDraft = (p: PayPerson): Draft => ({
    base: String(p.ownRate ?? p.defaultRate),
    kind: p.bumpKind ?? 'ROLE',
    custom: p.bumpKind === 'CUSTOM' ? String(p.bump) : '',
  });

  /** The dropdown's choices, with today's amounts. */
  function bumpOptions(p: PayPerson): { value: BumpKind; label: string }[] {
    const roleBump = p.role === 'LEAD' ? rules.leadBump : p.role === 'MANAGER' ? rules.managerBump : 0;
    return [
      { value: 'ROLE', label: `By role (${roleBump > 0 ? moneyCents(roleBump) : 'none'})` },
      { value: 'NONE', label: 'None' },
      { value: 'LEAD', label: `Lead ${moneyCents(rules.leadBump)}` },
      { value: 'LEAD_PROD', label: `Lead/prod ${moneyCents(rules.leadProdBump)}` },
      { value: 'MANAGER', label: `Manager ${moneyCents(rules.managerBump)}` },
      { value: 'CUSTOM', label: 'Other amount' },
    ];
  }

  async function remove(p: PayPerson) {
    if (!(await dialogs.confirm({
      title: `Remove ${p.name} from the pay list?`,
      message: 'Any rate or bump set just for them is cleared. Their past hours stay on old pay periods, and they come back '
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

  /** Save whatever changed on one row: the base rate, the bump, or both. */
  async function saveRow(p: PayPerson, d: Draft) {
    const was = savedDraft(p);
    const ok = await run(async () => {
      let rules: PayRules | null = null;
      if (Number(d.base) !== Number(was.base)) rules = await api.setBaseRate({ name: p.name, baseRate: Number(d.base) });
      if (d.kind !== was.kind || (d.kind === 'CUSTOM' && Number(d.custom) !== Number(was.custom))) {
        rules = await api.setPersonBump({ name: p.name, kind: d.kind, amount: d.kind === 'CUSTOM' ? Number(d.custom) : null });
      }
      return rules ?? api.payRules();
    }, `Saved ${p.name}'s pay.`);
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
          get the matrix rate when it&apos;s higher. Each person&apos;s bump goes on top: by role, leads and managers get
          theirs, or pick one for them. Paid shows the least each person makes an hour.
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
        <table className="w-full min-w-[680px] text-sm">
          <thead>
            <tr className="border-y border-[var(--border)] text-left text-xs uppercase tracking-wide opacity-70">
              <th className="px-4 py-2">Name</th>
              <th className="px-2 py-2">Base rate</th>
              <th className="px-2 py-2">Bump</th>
              <th className="px-2 py-2">Paid</th>
              <th className="px-4 py-2" />
            </tr>
          </thead>
          <tbody className="divide-y divide-[var(--border)]">
            {rules.people.map((p) => {
              const was = savedDraft(p);
              const d = drafts[p.name] ?? was;
              const set = (patch: Partial<Draft>) => setDrafts({ ...drafts, [p.name]: { ...d, ...patch } });
              const changed = (d.base.trim() !== '' && Number(d.base) !== Number(was.base))
                || d.kind !== was.kind
                || (d.kind === 'CUSTOM' && d.custom.trim() !== '' && Number(d.custom) !== Number(was.custom));
              const incomplete = d.base.trim() === '' || (d.kind === 'CUSTOM' && d.custom.trim() === '');
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
                      value={d.base} onChange={(e) => set({ base: e.target.value })} />
                  </td>
                  <td className="px-2 py-2">
                    <div className="flex items-center gap-1.5">
                      <select className="input w-40 py-1" aria-label={`${p.name}'s bump`} value={d.kind}
                        onChange={(e) => set({ kind: e.target.value as BumpKind })}>
                        {bumpOptions(p).map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                      </select>
                      {d.kind === 'CUSTOM' && (
                        <input className="input w-20 py-1" type="number" step="0.25" min="0" placeholder="$/hr"
                          aria-label={`${p.name}'s bump amount`} value={d.custom} onChange={(e) => set({ custom: e.target.value })} />
                      )}
                    </div>
                  </td>
                  <td className="whitespace-nowrap px-2 py-2"><b>{moneyCents(p.paidRate)}</b></td>
                  <td className="whitespace-nowrap px-4 py-2 text-right">
                    {changed && (
                      <button className="btn px-3 py-1 text-xs" disabled={busy || incomplete} onClick={() => void saveRow(p, d)}>
                        Save
                      </button>
                    )}
                    {!changed && p.ownRate != null && (
                      <button className="text-xs font-semibold hover:underline" disabled={busy}
                        onClick={() => void run(() => api.setBaseRate({ name: p.name, baseRate: null }),
                          `${p.name} is back on the default rate.`)}>
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
