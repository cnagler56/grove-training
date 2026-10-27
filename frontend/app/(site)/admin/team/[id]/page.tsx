'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { api, type EmployeeDetails, type SalonUltimateName, type TeamMember, type UserRole } from '@/src/lib/api';
import { canManage, canManageSite, roleOptions, roleBadgeClass, roleLabel } from '@/src/lib/roles';
import { displayPhone, employeeName, formatDate, formatPhone, tenure } from '@/src/lib/format';
import { useUser } from '@/src/lib/UserContext';
import DateField from '@/src/components/DateField';
import { useDialogs } from '@/src/components/Dialogs';

const today = () => new Date().toLocaleDateString('en-CA'); // "YYYY-MM-DD" in local time

export default function EmployeePage() {
  const { id } = useParams<{ id: string }>();
  const userId = Number(id);
  const { user } = useUser();
  const router = useRouter();
  const dialogs = useDialogs();
  const self = user?.userId === userId;

  async function remove() {
    if (!member) return;
    const name = employeeName(member);
    if (!(await dialogs.confirm({
      title: `Permanently delete ${name}'s account?`,
      message: "This erases their account and watch history and can't be undone. To just stop them signing in, use Disable account instead.",
      confirmLabel: 'Delete account',
      danger: true,
    }))) return;
    try {
      await api.deleteMember(userId);
      router.push('/admin/team');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not delete');
    }
  }

  const [member, setMember] = useState<TeamMember | null>(null);
  const [form, setForm] = useState<EmployeeDetails | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  // Names Salon Ultimate has reported, for linking this person to their weekly numbers.
  const [suNames, setSuNames] = useState<SalonUltimateName[]>([]);
  const managesSite = canManageSite(user?.roles);
  useEffect(() => {
    if (managesSite) api.salonUltimateNames().then(setSuNames).catch(() => {});
  }, [managesSite]);

  const apply = useCallback((m: TeamMember) => {
    setMember(m);
    setForm({
      firstName: m.firstName, lastName: m.lastName, email: m.email,
      phone: m.phone && displayPhone(m.phone), hireDate: m.hireDate, birthday: m.birthday,
    });
  }, []);

  useEffect(() => {
    api.member(userId).then(apply).catch((e) => setError(e.message));
  }, [userId, apply]);

  async function run(action: () => Promise<TeamMember | void>, done: string) {
    setError(null);
    setNotice(null);
    try {
      const updated = await action();
      if (updated) apply(updated);
      setNotice(done);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong');
    }
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (!form) return;
    setSaving(true);
    await run(() => api.updateDetails(userId, form), 'Saved.');
    setSaving(false);
  }

  async function resetPassword() {
    if (!member) return;
    const pw = await dialogs.prompt({
      title: `Reset ${employeeName(member)}'s password`,
      message: 'Set a temporary password and tell them in person.',
      placeholder: 'New temporary password',
      hint: 'At least 8 characters.',
      minLength: 8,
      confirmLabel: 'Reset password',
    });
    if (!pw) return;
    void run(() => api.resetPassword(userId, pw), `Password reset for ${employeeName(member)}.`);
  }

  async function setActive(active: boolean) {
    if (!member) return;
    if (!active && !(await dialogs.confirm({
      title: `Disable ${employeeName(member)}'s account?`,
      message: "They'll be signed out and can't sign in again. Their details and watch history are kept, "
        + 'and you can re-enable them later.\n\nRemember to change the team code too.',
      confirmLabel: 'Disable account',
      danger: true,
    }))) return;
    void run(() => api.updateMember(userId, { active }),
      active ? `${employeeName(member)} can sign in again.` : `${employeeName(member)}'s account is disabled.`);
  }

  if (!member || !form) return <p className="opacity-60">{error ?? 'Loading…'}</p>;
  const editable = self || (!!user && canManage(user.roles, member.role));
  const set = (k: keyof EmployeeDetails) => (v: string) => setForm({ ...form, [k]: v === '' ? null : v });

  return (
    <div>
      <Link href="/admin/team" className="text-sm opacity-70 hover:opacity-100">Back to team</Link>

      <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1">
        <h1 className="text-3xl font-extrabold tracking-tight">{employeeName(member)}</h1>
        {!member.active && (
          <span className="rounded-full bg-[var(--danger)] px-2.5 py-0.5 text-xs font-bold text-white">Disabled</span>
        )}
        {member.active && member.inactiveSince && (
          <span className="rounded-full bg-[var(--surface-2)] px-2.5 py-0.5 text-xs font-bold opacity-80">Inactive</span>
        )}
        <span className={`rounded-full px-2.5 py-0.5 text-xs font-bold ${roleBadgeClass(member.role)}`}>
          {roleLabel(member.role)}
        </span>
        <span className="text-sm opacity-70">
          {member.videosCompleted} video{member.videosCompleted === 1 ? '' : 's'} watched
          {member.hireDate && tenure(member.hireDate) && ` · with Grove ${tenure(member.hireDate)}`}
          {member.createdAt && ` · joined the site ${formatDate(member.createdAt)}`}
        </span>
      </div>

      {error && <p className="mt-3 text-sm text-[var(--danger)]">{error}</p>}
      {notice && <p className="mt-3 text-sm font-semibold text-[var(--success)]">{notice}</p>}
      {!editable && (
        <p className="mt-3 text-sm opacity-70">
          Only someone ranked above {article(roleLabel(member.role))} {roleLabel(member.role).toLowerCase()} can change this account.
        </p>
      )}

      <div className="mt-4 grid items-start gap-4 lg:grid-cols-2">
        <form onSubmit={save} className="card space-y-3 p-5">
          <h2 className="text-lg font-extrabold">Details</h2>
          <fieldset disabled={!editable} className="grid gap-3 disabled:opacity-60 sm:grid-cols-2">
            <Field label="First name" value={form.firstName} onChange={set('firstName')} />
            <Field label="Last name" value={form.lastName} onChange={set('lastName')} />
            <Field
              label="Email (sign-in)"
              type="email"
              value={form.email}
              onChange={(v) => setForm({ ...form, email: v })}
              required
            />
            <Field label="Phone" type="tel" value={form.phone} onChange={(v) => set('phone')(formatPhone(v))} placeholder="555-555-0100" />
            <DateField label="Hire date" value={form.hireDate} onChange={(hireDate) => setForm({ ...form, hireDate })} />
            <DateField label="Birthday" value={form.birthday} onChange={(birthday) => setForm({ ...form, birthday })} max={today()} />
          </fieldset>
          {editable && (
            <button type="submit" className="btn" disabled={saving}>{saving ? 'Saving…' : 'Save details'}</button>
          )}
        </form>

        {!self && editable && user && (
          <section className="card p-5">
            <h2 className="text-lg font-extrabold">Account</h2>
            <div className="mt-1 divide-y divide-[var(--border)]">
              <Row title="Role" hint="Managers and up can manage videos and the team.">
                <select
                  aria-label="Role"
                  value={member.role}
                  onChange={(e) => {
                    const role = e.target.value as UserRole;
                    void run(() => api.updateMember(userId, { role }), `Role changed to ${roleLabel(role)}.`);
                  }}
                  className="input w-auto cursor-pointer py-1.5 font-semibold"
                >
                  {roleOptions(user.roles, member.role).map(({ value: r }) => (
                    <option key={r} value={r}>{roleLabel(r)}</option>
                  ))}
                </select>
              </Row>
              <Row
                title="Salon Ultimate"
                hint={suNames.length === 0 && !member.salonUltimateName
                  ? 'Names show up here after the first weekly pull.'
                  : 'Links them to their weekly numbers.'}
              >
                <select
                  aria-label="Salon Ultimate name"
                  value={member.salonUltimateName ?? ''}
                  onChange={(e) => {
                    const name = e.target.value || null;
                    void run(() => api.linkSalonUltimate(userId, name),
                      name ? `Linked to ${name} in Salon Ultimate.` : 'No longer linked to Salon Ultimate.');
                  }}
                  className="input w-auto max-w-[14rem] cursor-pointer py-1.5"
                >
                  <option value="">Not linked</option>
                  {member.salonUltimateName && !suNames.some((n) => n.name === member.salonUltimateName) && (
                    <option value={member.salonUltimateName}>{member.salonUltimateName}</option>
                  )}
                  {suNames.map((n) => {
                    const takenBy = n.linkedUserId !== null && n.linkedUserId !== userId ? n.linkedTo : null;
                    return (
                      <option key={n.name} value={n.name} disabled={!!takenBy}>
                        {n.name}{takenBy ? ` (${takenBy})` : ''}
                      </option>
                    );
                  })}
                </select>
              </Row>
              {member.active && (member.inactiveSince ? (
                <Row title={`Inactive since ${formatDate(member.inactiveSince)}`} hint="Off the schedule. Can still sign in.">
                  <button className="btn" onClick={() => void run(() => api.updateMember(userId, { inactive: false }),
                    `${employeeName(member)} is back on the schedule.`)}>Mark active</button>
                </Row>
              ) : (
                <Row title="Active" hint="Taking time off? Inactive leaves them off the schedule.">
                  <button className="btn-ghost" onClick={() => void run(() => api.updateMember(userId, { inactive: true }),
                    `${employeeName(member)} is off the schedule until you mark them active.`)}>Mark inactive</button>
                </Row>
              ))}
              <Row title="Password" hint="Set a temporary one if they forgot theirs.">
                <button className="btn-ghost" onClick={() => void resetPassword()}>Reset password</button>
              </Row>
              {member.active ? (
                <Row title="Employee left?" hint="Blocks sign-in. History is kept.">
                  <button
                    className="rounded-full bg-[var(--danger)] px-4 py-2 text-sm font-bold text-white hover:opacity-90"
                    onClick={() => void setActive(false)}
                  >
                    Disable account
                  </button>
                </Row>
              ) : (
                <Row title={`Disabled${member.disabledAt ? ` on ${formatDate(member.disabledAt)}` : ''}`} hint="They can't sign in.">
                  <button className="btn" onClick={() => void setActive(true)}>Re-enable</button>
                </Row>
              )}
              {user.roles === 'OWNER' && (
                <Row title="Delete permanently" hint="Erases the account and watch history.">
                  <button className="text-sm font-bold text-[var(--danger)] hover:underline" onClick={() => void remove()}>
                    Delete account
                  </button>
                </Row>
              )}
            </div>
          </section>
        )}
      </div>
    </div>
  );
}

/** One setting in the Account card: label and hint on the left, its control on the right. */
function Row({ title, hint, children }: { title: string; hint: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3 py-3 text-sm">
      <div className="min-w-0">
        <div className="font-bold">{title}</div>
        <div className="opacity-60">{hint}</div>
      </div>
      <div className="shrink-0">{children}</div>
    </div>
  );
}

function Field({
  label, value, onChange, type = 'text', hint, required, placeholder, max,
}: {
  label: string;
  value: string | null;
  onChange: (v: string) => void;
  type?: string;
  hint?: string;
  required?: boolean;
  placeholder?: string;
  max?: string;
}) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block font-semibold opacity-80">{label}</span>
      <input
        className="input"
        type={type}
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value)}
        required={required}
        placeholder={placeholder}
        max={max}
      />
      {hint && <span className="mt-1 block text-xs opacity-60">{hint}</span>}
    </label>
  );
}

function article(word: string) {
  return /^[aeiou]/i.test(word) ? 'an' : 'a';
}
