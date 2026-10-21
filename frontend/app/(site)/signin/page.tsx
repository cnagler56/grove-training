'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useUser } from '@/src/lib/UserContext';

export default function SignInPage() {
  const { login, register } = useUser();

  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [teamCode, setTeamCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // The site layout redirects to the library as soon as a user is set.
  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      if (mode === 'login') await login({ email, password });
      else await register({ email, password, firstName, lastName, teamCode });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-sm">
      <h1 className="text-3xl font-extrabold tracking-tight">
        {mode === 'login' ? 'Sign in' : 'Join the team'}
      </h1>
      <p className="mt-1 text-sm opacity-70">
        {mode === 'login'
          ? 'Training videos for Grove Salons staff.'
          : 'You need the team code from your manager.'}
      </p>

      <form onSubmit={onSubmit} className="mt-6 space-y-4">
        {mode === 'register' && (
          <>
            <div className="grid grid-cols-2 gap-3">
              <Field label="First name" value={firstName} onChange={setFirstName} required />
              <Field label="Last name" value={lastName} onChange={setLastName} />
            </div>
            <Field label="Team code" value={teamCode} onChange={setTeamCode} required />
          </>
        )}
        <Field label="Email" type="email" value={email} onChange={setEmail} required />
        <Field
          label={mode === 'register' ? 'Password (8+ characters)' : 'Password'}
          type="password"
          value={password}
          onChange={setPassword}
          required
        />

        {error && <p className="text-sm text-[var(--danger)]">{error}</p>}

        <button type="submit" disabled={busy} className="btn w-full">
          {busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
        </button>
      </form>

      <p className="mt-4 text-sm opacity-70">
        {mode === 'login' ? 'New to the team? ' : 'Already have an account? '}
        <button
          className="underline underline-offset-4"
          onClick={() => {
            setMode(mode === 'login' ? 'register' : 'login');
            setError(null);
          }}
        >
          {mode === 'login' ? 'Create an account' : 'Sign in'}
        </button>
      </p>
      {mode === 'login' && (
        <p className="mt-2 text-xs opacity-50">Forgot your password? Ask your manager to reset it.</p>
      )}
      {mode === 'login' && (
        <p className="mt-6 rounded-xl bg-[var(--accent-soft)] px-4 py-3 text-sm">
          Not on the team yet? <Link href="/apply" className="font-bold text-[var(--accent-strong)] underline underline-offset-4">Apply to work at Grove</Link>
        </p>
      )}
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  type = 'text',
  required = false,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  type?: string;
  required?: boolean;
}) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block opacity-70">{label}</span>
      <input
        type={type}
        value={value}
        required={required}
        onChange={(e) => onChange(e.target.value)}
        className="input"
      />
    </label>
  );
}
