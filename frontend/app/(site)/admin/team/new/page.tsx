'use client';

import { Suspense, useEffect, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { api, type NewEmployee, type TeamMember, type UserRole } from '@/src/lib/api';
import { employeeName } from '@/src/lib/format';
import { ROLES } from '@/src/lib/roles';

const today = () => new Date().toLocaleDateString('en-CA'); // "YYYY-MM-DD" in local time

/** Applicant's position → starting role when creating their account. */
const POSITION_ROLE: Record<string, UserRole> = {
  Stylist: 'STYLIST', Receptionist: 'RECEPTIONIST', Lead: 'LEAD', Manager: 'MANAGER',
};

/** Easy-to-read temporary password, e.g. "grove-4827-tulip". No 0/O or 1/l confusion. */
function tempPassword(): string {
  const words = ['tulip', 'maple', 'cedar', 'river', 'sunny', 'coral', 'amber', 'olive', 'lilac', 'pearl'];
  const n = crypto.getRandomValues(new Uint32Array(2));
  return `grove-${2000 + (n[0] % 8000)}-${words[n[1] % words.length]}`;
}

export default function AddEmployeePage() {
  // useSearchParams needs a Suspense boundary in the app router.
  return (
    <Suspense fallback={<p className="opacity-60">Loading…</p>}>
      <AddEmployee />
    </Suspense>
  );
}

function AddEmployee() {
  const params = useSearchParams();
  const fromApplication = params.get('application');

  const blank = (): NewEmployee => ({
    firstName: '', lastName: '', email: '', phone: '', hireDate: today(), birthday: null,
    role: 'STYLIST', password: tempPassword(),
  });
  const [form, setForm] = useState<NewEmployee>(blank);
  const [created, setCreated] = useState<{ member: TeamMember; password: string } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // "Create their account" from a hired applicant: prefill from the application.
  useEffect(() => {
    if (!fromApplication) return;
    api.application(Number(fromApplication)).then((a) => setForm((f) => ({
      ...f,
      firstName: a.firstName, lastName: a.lastName, email: a.email, phone: a.phone,
      role: POSITION_ROLE[a.position] ?? 'STYLIST',
    }))).catch(() => {});
  }, [fromApplication]);

  const set = (k: keyof NewEmployee) => (v: string) => setForm({ ...form, [k]: v === '' && k !== 'firstName' && k !== 'email' && k !== 'password' ? null : v });

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const member = await api.createMember(form);
      setCreated({ member, password: form.password });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong');
    } finally {
      setBusy(false);
    }
  }

  if (created) {
    const { member, password } = created;
    const signin = `${window.location.origin}/signin`;
    const handout = `Welcome to the Grove Salons team site!\nSign in at ${signin}\nEmail: ${member.email}\nTemporary password: ${password}`;
    return (
      <div className="mx-auto max-w-xl">
        <div className="card p-6">
          <h1 className="text-2xl font-extrabold">{employeeName(member)}&apos;s account is ready</h1>
          <p className="mt-2 text-sm opacity-80">Give them these sign-in details. You won&apos;t see this password again.</p>
          <pre className="mt-4 whitespace-pre-wrap rounded-xl bg-[var(--surface-2)] p-4 font-sans text-sm">{handout}</pre>
          <div className="mt-4 flex flex-wrap gap-2">
            <button className="btn" onClick={() => void navigator.clipboard?.writeText(handout)}>Copy details</button>
            <Link href={`/admin/team/${member.userId}`} className="btn-ghost">View {member.firstName}</Link>
            <button className="btn-ghost" onClick={() => { setCreated(null); setForm(blank()); }}>Add another</button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl">
      <Link href="/admin/team" className="text-sm opacity-70 hover:opacity-100">Back to team</Link>
      <h1 className="mt-3 text-3xl font-extrabold tracking-tight">Add employee</h1>
      <p className="mt-1 text-sm opacity-70">
        Creates their account directly, so they don&apos;t need the team code.
      </p>

      <form onSubmit={onSubmit} className="card mt-6 space-y-5 p-5">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="First name" value={form.firstName} onChange={set('firstName')} required />
          <Field label="Last name" value={form.lastName} onChange={set('lastName')} />
          <Field label="Email" hint="What they'll sign in with" type="email" value={form.email} onChange={set('email')} required />
          <Field label="Phone" type="tel" value={form.phone} onChange={set('phone')} />
          <Field label="Hire date" type="date" value={form.hireDate} onChange={set('hireDate')} />
          <Field label="Birthday" type="date" value={form.birthday} onChange={set('birthday')} max={today()} />
          <label className="block text-sm">
            <span className="mb-1 block font-semibold opacity-80">Role</span>
            <select className="input" value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as UserRole })}>
              {ROLES.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
            </select>
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-semibold opacity-80">Temporary password</span>
            <div className="flex gap-2">
              <input
                className="input font-mono"
                value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })}
                minLength={8}
                required
              />
              <button type="button" className="btn-ghost whitespace-nowrap" onClick={() => setForm({ ...form, password: tempPassword() })}>
                New one
              </button>
            </div>
            <span className="mt-1 block text-xs opacity-60">8+ characters. You&apos;ll get a copy to hand them.</span>
          </label>
        </div>

        {error && <p className="text-sm text-[var(--danger)]">{error}</p>}
        <button type="submit" className="btn" disabled={busy}>{busy ? 'Creating…' : 'Create account'}</button>
      </form>
    </div>
  );
}

function Field({
  label, value, onChange, type = 'text', hint, required, max,
}: {
  label: string;
  value: string | null;
  onChange: (v: string) => void;
  type?: string;
  hint?: string;
  required?: boolean;
  max?: string;
}) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block font-semibold opacity-80">{label}</span>
      <input className="input" type={type} value={value ?? ''} onChange={(e) => onChange(e.target.value)} required={required} max={max} />
      {hint && <span className="mt-1 block text-xs opacity-60">{hint}</span>}
    </label>
  );
}
