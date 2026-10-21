'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { api, type ApplicationRequest } from '@/src/lib/api';

/** Public job application. Lives outside the (site) group so no sign-in is needed. */
export default function ApplyPage() {
  const [positions, setPositions] = useState<string[]>(['Stylist', 'Receptionist', 'Lead', 'Manager', 'Other']);
  const [form, setForm] = useState<ApplicationRequest>({
    firstName: '', lastName: '', email: '', phone: '', position: '',
    licensed: null, licenseNumber: '', yearsExperience: null,
    availability: '', portfolio: '', message: '', website: '',
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sentTo, setSentTo] = useState<string | null>(null);

  useEffect(() => {
    api.positions().then(setPositions).catch(() => {});
  }, []);

  const set = <K extends keyof ApplicationRequest>(k: K) => (v: ApplicationRequest[K]) => setForm({ ...form, [k]: v });

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.apply(form);
      setSentTo(form.firstName);
      window.scrollTo({ top: 0 });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong. Please try again.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <header className="bg-gradient-to-r from-[var(--header-from)] to-[var(--header-to)] text-white">
        <div className="mx-auto max-w-2xl px-4 py-8">
          <p className="text-sm font-bold opacity-90">✂️ Grove Salons</p>
          <h1 className="mt-1 text-4xl font-extrabold tracking-tight">Join our team</h1>
          <p className="mt-2 max-w-lg opacity-95">
            We&apos;re always looking for talented, friendly people. Tell us a little about yourself and we&apos;ll be in touch.
          </p>
        </div>
      </header>

      <main className="mx-auto max-w-2xl px-4 py-8">
        {sentTo ? (
          <div className="card p-8 text-center">
            <p className="text-4xl">🎉</p>
            <h2 className="mt-2 text-2xl font-extrabold">Thanks, {sentTo}!</h2>
            <p className="mt-2 opacity-80">Your application is in. We&apos;ll reach out by phone or email soon.</p>
          </div>
        ) : (
          <form onSubmit={onSubmit} className="card space-y-6 p-6">
            <Section title="About you">
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="First name" value={form.firstName} onChange={set('firstName')} required autoComplete="given-name" />
                <Field label="Last name" value={form.lastName} onChange={set('lastName')} required autoComplete="family-name" />
                <Field label="Email" type="email" value={form.email} onChange={set('email')} required autoComplete="email" />
                <Field label="Phone" type="tel" value={form.phone} onChange={set('phone')} required autoComplete="tel" />
              </div>
            </Section>

            <Section title="The job">
              <div className="grid gap-4 sm:grid-cols-2">
                <label className="block text-sm">
                  <span className="mb-1 block font-semibold opacity-80">Position</span>
                  <select className="input" value={form.position} onChange={(e) => set('position')(e.target.value)} required>
                    <option value="" disabled>Choose one…</option>
                    {positions.map((p) => <option key={p} value={p}>{p}</option>)}
                  </select>
                </label>
                <Field
                  label="Years of experience"
                  type="number"
                  value={form.yearsExperience === null ? '' : String(form.yearsExperience)}
                  onChange={(v) => set('yearsExperience')(v === '' ? null : Math.max(0, Math.min(60, Number(v))))}
                  min="0"
                  max="60"
                />
              </div>

              <fieldset className="mt-4 text-sm">
                <legend className="mb-1 font-semibold opacity-80">Do you have a cosmetology license?</legend>
                <div className="flex gap-2">
                  {[{ v: true, l: 'Yes' }, { v: false, l: 'No' }].map(({ v, l }) => (
                    <button
                      key={l}
                      type="button"
                      onClick={() => set('licensed')(form.licensed === v ? null : v)}
                      className={`rounded-full border px-4 py-1.5 font-semibold ${
                        form.licensed === v
                          ? 'border-[var(--accent)] bg-[var(--accent-soft)] text-[var(--accent-strong)]'
                          : 'border-[var(--border)] bg-[var(--surface)]'
                      }`}
                      aria-pressed={form.licensed === v}
                    >
                      {l}
                    </button>
                  ))}
                </div>
              </fieldset>
              {form.licensed && (
                <div className="mt-4 sm:w-1/2">
                  <Field label="License number (optional)" value={form.licenseNumber ?? ''} onChange={set('licenseNumber')} />
                </div>
              )}
            </Section>

            <Section title="A bit more">
              <div className="space-y-4">
                <Field
                  label="When can you work?"
                  value={form.availability ?? ''}
                  onChange={set('availability')}
                  placeholder="e.g. Tue–Sat, evenings, weekends only"
                />
                <Field
                  label="Instagram or portfolio (optional)"
                  value={form.portfolio ?? ''}
                  onChange={set('portfolio')}
                  placeholder="@yourhandle or a link"
                />
                <label className="block text-sm">
                  <span className="mb-1 block font-semibold opacity-80">Anything you&apos;d like us to know?</span>
                  <textarea
                    className="input min-h-28"
                    value={form.message ?? ''}
                    onChange={(e) => set('message')(e.target.value)}
                    maxLength={4000}
                    placeholder="Your specialties, where you've worked, what you love about doing hair…"
                  />
                </label>
              </div>
            </Section>

            {/* Spam trap: hidden from people, but bots fill it in. */}
            <div aria-hidden="true" className="absolute -left-[9999px] h-0 w-0 overflow-hidden">
              <label>
                Website
                <input tabIndex={-1} autoComplete="off" value={form.website ?? ''} onChange={(e) => set('website')(e.target.value)} />
              </label>
            </div>

            {error && <p className="text-sm text-[var(--danger)]">{error}</p>}
            <button type="submit" className="btn w-full sm:w-auto" disabled={busy}>
              {busy ? 'Sending…' : 'Send application'}
            </button>
          </form>
        )}

        <p className="mt-6 text-center text-sm opacity-60">
          Already on the team? <Link href="/signin" className="underline underline-offset-4">Sign in</Link>
        </p>
      </main>
    </>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section>
      <h2 className="mb-3 text-lg font-extrabold">{title}</h2>
      {children}
    </section>
  );
}

function Field({
  label, value, onChange, type = 'text', required, placeholder, autoComplete, min, max,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  type?: string;
  required?: boolean;
  placeholder?: string;
  autoComplete?: string;
  min?: string;
  max?: string;
}) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block font-semibold opacity-80">{label}</span>
      <input
        className="input"
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        required={required}
        placeholder={placeholder}
        autoComplete={autoComplete}
        min={min}
        max={max}
      />
    </label>
  );
}
