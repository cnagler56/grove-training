'use client';

import { useEffect, useState } from 'react';
import PayRulesCard from '@/src/components/PayRulesCard';
import { api, type PermissionKey, type PermissionRule, type UserRole } from '@/src/lib/api';
import { ROLES } from '@/src/lib/roles';

/** Roles that can be given permissions. Owners always have all of them; customers can't have any. */
const ROLE_COLUMNS = ROLES.filter((r) => r.value !== 'CUSTOMER' && r.value !== 'OWNER');

/** Owner-only: decide which roles can do what. */
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
      <p className="mt-1 text-sm opacity-70">Which roles can do each thing. Owners can always do everything.</p>

      <section className="card mt-6 overflow-x-auto p-0">
        <table className="w-full min-w-[560px] text-sm">
          <thead>
            <tr className="border-b border-[var(--border)] text-xs font-semibold opacity-70">
              <th className="px-4 py-2.5 text-left" />
              {ROLE_COLUMNS.map((r) => <th key={r.value} className="px-2 py-2.5 text-center">{r.label}</th>)}
            </tr>
          </thead>
          <tbody className="divide-y divide-[var(--border)]">
            {rules.map((rule) => (
              <tr key={rule.permission}>
                <td className="px-4 py-2.5 font-semibold">{rule.label}</td>
                {ROLE_COLUMNS.map((r) => (
                  <td key={r.value} className="px-2 py-2.5 text-center">
                    <input
                      type="checkbox"
                      className="h-4 w-4 cursor-pointer accent-[var(--accent)]"
                      aria-label={`${r.label}: ${rule.label}`}
                      checked={draft[rule.permission].has(r.value)}
                      onChange={() => toggle(rule.permission, r.value)}
                    />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {error && <p className="mt-4 text-sm text-[var(--danger)]">{error}</p>}
      {notice && <p className="mt-4 text-sm font-semibold text-[var(--success)]">{notice}</p>}
      <div className="mt-4 flex items-center gap-3">
        <button className="btn" onClick={() => void save()} disabled={saving || !dirty}>
          {saving ? 'Saving…' : 'Save settings'}
        </button>
        {dirty && <span className="text-sm font-semibold text-[var(--warn)]">Unsaved changes</span>}
      </div>

      <PayRulesCard />
    </div>
  );
}
