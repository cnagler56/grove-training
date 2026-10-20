'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { api, type EmployeeDetails, type TeamMember } from '@/src/lib/api';
import { employeeName, formatDate, tenure } from '@/src/lib/format';
import { useUser } from '@/src/lib/UserContext';

const today = () => new Date().toLocaleDateString('en-CA'); // "YYYY-MM-DD" in local time

export default function EmployeePage() {
  const { id } = useParams<{ id: string }>();
  const userId = Number(id);
  const { user } = useUser();
  const self = user?.userId === userId;

  const [member, setMember] = useState<TeamMember | null>(null);
  const [form, setForm] = useState<EmployeeDetails | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const apply = useCallback((m: TeamMember) => {
    setMember(m);
    setForm({
      firstName: m.firstName, lastName: m.lastName, email: m.email,
      phone: m.phone, hireDate: m.hireDate, birthday: m.birthday,
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

  function resetPassword() {
    if (!member) return;
    const pw = prompt(`New temporary password for ${employeeName(member)} (8+ characters). Tell them in person.`);
    if (!pw) return;
    void run(() => api.resetPassword(userId, pw), `Password reset for ${employeeName(member)}.`);
  }

  function setActive(active: boolean) {
    if (!member) return;
    if (!active && !confirm(
      `Disable ${employeeName(member)}'s account?\n\n` +
      `They'll be signed out and can't sign in again. Their details and watch history are kept, ` +
      `and you can re-enable them later.\n\nRemember to change the team code too.`)) return;
    void run(() => api.updateMember(userId, { active }),
      active ? `${employeeName(member)} can sign in again.` : `${employeeName(member)}'s account is disabled.`);
  }

  if (!member || !form) return <p className="opacity-60">{error ?? 'Loading…'}</p>;
  const set = (k: keyof EmployeeDetails) => (v: string) => setForm({ ...form, [k]: v === '' ? null : v });

  return (
    <div className="mx-auto max-w-2xl">
      <Link href="/admin/team" className="text-sm opacity-70 hover:opacity-100">← Team</Link>

      <div className="mt-3 flex flex-wrap items-center gap-3">
        <h1 className="text-3xl font-extrabold tracking-tight">{employeeName(member)}</h1>
        {!member.active && (
          <span className="rounded-full bg-[var(--danger)] px-2.5 py-0.5 text-xs font-bold text-white">Disabled</span>
        )}
        {member.role === 'ADMIN' && (
          <span className="rounded-full bg-[var(--accent-soft)] px-2.5 py-0.5 text-xs font-bold text-[var(--accent-strong)]">Manager</span>
        )}
      </div>
      <p className="mt-1 text-sm opacity-70">
        {member.videosCompleted} video{member.videosCompleted === 1 ? '' : 's'} watched
        {member.hireDate && tenure(member.hireDate) && ` · with Grove ${tenure(member.hireDate)}`}
        {member.createdAt && ` · joined the site ${formatDate(member.createdAt)}`}
      </p>

      {error && <p className="mt-4 text-sm text-[var(--danger)]">{error}</p>}
      {notice && <p className="mt-4 text-sm font-semibold text-[var(--success)]">{notice}</p>}

      <form onSubmit={save} className="card mt-6 space-y-4 p-5">
        <h2 className="text-lg font-extrabold">Details</h2>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="First name" value={form.firstName} onChange={set('firstName')} />
          <Field label="Last name" value={form.lastName} onChange={set('lastName')} />
          <Field
            label="Email"
            hint="Also what they sign in with"
            type="email"
            value={form.email}
            onChange={(v) => setForm({ ...form, email: v })}
            required
          />
          <Field label="Phone" type="tel" value={form.phone} onChange={set('phone')} placeholder="(555) 555-0100" />
          <Field label="Hire date" type="date" value={form.hireDate} onChange={set('hireDate')} />
          <Field label="Birthday" type="date" value={form.birthday} onChange={set('birthday')} max={today()} />
        </div>
        <button type="submit" className="btn" disabled={saving}>{saving ? 'Saving…' : 'Save details'}</button>
      </form>

      {!self && (
        <section className="card mt-6 space-y-4 p-5">
          <h2 className="text-lg font-extrabold">Account</h2>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="text-sm">
              <div className="font-bold">Role: {member.role === 'ADMIN' ? 'Manager' : 'Stylist'}</div>
              <div className="opacity-60">Managers can upload videos and manage the team.</div>
            </div>
            <button
              className="btn-ghost"
              onClick={() => void run(
                () => api.updateMember(userId, { role: member.role === 'ADMIN' ? 'USER' : 'ADMIN' }),
                'Role updated.')}
            >
              {member.role === 'ADMIN' ? 'Make stylist' : 'Make manager'}
            </button>
          </div>
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-[var(--border)] pt-4">
            <div className="text-sm">
              <div className="font-bold">Password</div>
              <div className="opacity-60">Set a temporary one if they forgot theirs.</div>
            </div>
            <button className="btn-ghost" onClick={resetPassword}>Reset password</button>
          </div>
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-[var(--border)] pt-4">
            {member.active ? (
              <>
                <div className="text-sm">
                  <div className="font-bold">Employee left?</div>
                  <div className="opacity-60">Disabling signs them out and blocks sign-in. History is kept.</div>
                </div>
                <button
                  className="rounded-full bg-[var(--danger)] px-4 py-2 text-sm font-bold text-white hover:opacity-90"
                  onClick={() => setActive(false)}
                >
                  Disable account
                </button>
              </>
            ) : (
              <>
                <div className="text-sm">
                  <div className="font-bold">Disabled{member.disabledAt && ` on ${formatDate(member.disabledAt)}`}</div>
                  <div className="opacity-60">They can&apos;t sign in.</div>
                </div>
                <button className="btn" onClick={() => setActive(true)}>Re-enable</button>
              </>
            )}
          </div>
        </section>
      )}
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
