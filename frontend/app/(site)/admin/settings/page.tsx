'use client';

import { useEffect, useState } from 'react';
import PayRulesCard from '@/src/components/PayRulesCard';
import { api, type PermissionKey, type PermissionRule, type UserRole } from '@/src/lib/api';
import { ROLES } from '@/src/lib/roles';

/** Roles that can be given schedule permissions (customers can't). */
const STAFF_ROLES = ROLES.filter((r) => r.value !== 'CUSTOMER');

/** Owner-only: decide which roles can see, build, and approve the schedule. */
export default function SettingsPage() {
  const [rules, setRules] = useState<PermissionRule[] | null>(null);
  const [draft, setDraft] = useState<Record<PermissionKey, Set<UserRole>> | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const apply = (r: PermissionRule[]) => {
    setRules(r);
    setDraft(Object.fromEntries(r.map((p) => [p.permission, new Set(p.roles)])) as Record<PermissionKey, Set<UserRole>>);
  };

  useEffect(() => {
    api.permissionRules().then(apply).catch((e) => setError(e.message));
  }, []);

  if (!rules || !draft) return <p className="opacity-60">{error ?? 'Loading settings…'}</p>;

  const dirty = rules.some((r) => {
    const now = draft[r.permission];
    return now.size !== r.roles.length || r.roles.some((role) => !now.has(role));
  });

  function toggle(p: PermissionKey, role: UserRole) {
    if (role === 'OWNER' || !draft) return; // owners always keep every permission
    const next = new Set(draft[p]);
    if (next.has(role)) next.delete(role); else next.add(role);
    setDraft({ ...draft, [p]: next });
    setNotice(null);
  }

  async function save() {
    if (!draft) return;
    setSaving(true);
    setError(null);
    try {
      apply(await api.updatePermissions(
        Object.fromEntries(Object.entries(draft).map(([k, v]) => [k, [...v]])) as Record<PermissionKey, UserRole[]>,
      ));
      setNotice('Saved. Changes apply the next time each person loads a page.');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="text-3xl font-extrabold tracking-tight">Settings</h1>
      <p className="mt-1 text-sm opacity-70">Decide which roles can see, build, and approve the weekly schedule.</p>

      <div className="mt-6 space-y-4">
        {rules.map((rule) => (
          <section key={rule.permission} className="card p-5">
            <h2 className="text-lg font-extrabold">{rule.label}</h2>
            <p className="text-sm opacity-70">{rule.description}</p>
            <div className="mt-4 flex flex-wrap gap-2">
              {STAFF_ROLES.map((r) => {
                const on = draft[rule.permission].has(r.value);
                const locked = r.value === 'OWNER';
                return (
                  <label
                    key={r.value}
                    className={`flex cursor-pointer select-none items-center gap-2 rounded-full border px-3.5 py-1.5 text-sm font-semibold ${
                      on ? 'border-[var(--accent)] bg-[var(--accent-soft)] text-[var(--accent-strong)]' : 'border-[var(--border)] bg-[var(--surface)]'
                    } ${locked ? 'cursor-default opacity-70' : ''}`}
                    title={locked ? 'Owners always have every permission' : undefined}
                  >
                    <input
                      type="checkbox"
                      className="accent-[var(--accent)]"
                      checked={on}
                      disabled={locked}
                      onChange={() => toggle(rule.permission, r.value)}
                    />
                    {r.label}
                  </label>
                );
              })}
            </div>
          </section>
        ))}
      </div>

      {error && <p className="mt-4 text-sm text-[var(--danger)]">{error}</p>}
      {notice && <p className="mt-4 text-sm font-semibold text-[var(--success)]">{notice}</p>}
      <div className="mt-6 flex items-center gap-3">
        <button className="btn" onClick={() => void save()} disabled={saving || !dirty}>
          {saving ? 'Saving…' : 'Save settings'}
        </button>
        {dirty && <span className="text-sm font-semibold text-[var(--warn)]">Unsaved changes</span>}
      </div>
      <p className="mt-4 text-xs opacity-60">
        Owners always have every permission. Customers can&apos;t be given schedule access.
      </p>

      <PayRulesCard />
    </div>
  );
}
