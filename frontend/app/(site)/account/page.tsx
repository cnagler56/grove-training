'use client';

import { useState } from 'react';
import { api } from '@/src/lib/api';
import NotificationsCard from '@/src/components/NotificationsCard';

/** Anyone on staff changes their own password here. Linked from the site header. */
export default function AccountPage() {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [repeat, setRepeat] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setNotice(null);
    if (next !== repeat) {
      setError('The two new passwords don\'t match.');
      return;
    }
    setBusy(true);
    try {
      await api.changePassword(current, next);
      setCurrent('');
      setNext('');
      setRepeat('');
      setNotice('Your password is changed. Any other phones or computers you were signed in on will need the new one.');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-md">
      <h1 className="text-3xl font-extrabold tracking-tight">My account</h1>

      <form onSubmit={submit} className="card mt-6 space-y-4 p-5">
        <h2 className="text-lg font-bold">Change your password</h2>
        <Field label="Current password" autoComplete="current-password" value={current} onChange={setCurrent} />
        <Field label="New password (8+ characters)" autoComplete="new-password" value={next} onChange={setNext} />
        <Field label="New password again" autoComplete="new-password" value={repeat} onChange={setRepeat} />

        {error && <p className="text-sm text-[var(--danger)]">{error}</p>}
        {notice && <p className="text-sm text-[var(--success)]">{notice}</p>}

        <button type="submit" disabled={busy} className="btn w-full">
          {busy ? 'Please wait…' : 'Change password'}
        </button>
      </form>

      <section className="card mt-6 space-y-3 p-5">
        <h2 className="text-lg font-bold">Notifications on this device</h2>
        <NotificationsCard always />
      </section>
    </div>
  );
}

function Field({
  label,
  autoComplete,
  value,
  onChange,
}: {
  label: string;
  autoComplete: string;
  value: string;
  onChange: (v: string) => void;
}) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block opacity-70">{label}</span>
      <input
        type="password"
        autoComplete={autoComplete}
        value={value}
        required
        onChange={(e) => onChange(e.target.value)}
        className="input"
      />
    </label>
  );
}
