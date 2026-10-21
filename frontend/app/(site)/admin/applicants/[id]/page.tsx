'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { api, type ApplicationStatus, type JobApplication } from '@/src/lib/api';
import { APPLICATION_STATUSES, statusBadge, statusLabel } from '@/src/lib/applications';
import { formatDate } from '@/src/lib/format';

export default function ApplicantPage() {
  const { id } = useParams<{ id: string }>();
  const appId = Number(id);
  const router = useRouter();
  const [app, setApp] = useState<JobApplication | null>(null);
  const [notes, setNotes] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    api.application(appId)
      .then((a) => { setApp(a); setNotes(a.ownerNotes ?? ''); })
      .catch((e) => setError(e.message));
  }, [appId]);

  async function update(u: { status?: ApplicationStatus; ownerNotes?: string }, done: string) {
    setError(null);
    setNotice(null);
    try {
      const a = await api.updateApplication(appId, u);
      setApp(a);
      setNotice(done);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong');
    }
  }

  async function remove() {
    if (!app || !confirm(`Delete ${app.firstName} ${app.lastName}'s application? This can't be undone.`)) return;
    try {
      await api.deleteApplication(appId);
      router.push('/admin/applicants');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not delete');
    }
  }

  if (!app) return <p className="opacity-60">{error ?? 'Loading…'}</p>;
  const portfolioHref = app.portfolio
    ? app.portfolio.startsWith('http') ? app.portfolio
      : app.portfolio.startsWith('@') ? `https://instagram.com/${app.portfolio.slice(1)}` : null
    : null;

  return (
    <div className="mx-auto max-w-2xl">
      <Link href="/admin/applicants" className="text-sm opacity-70 hover:opacity-100">← Applicants</Link>

      <div className="mt-3 flex flex-wrap items-center gap-3">
        <h1 className="text-3xl font-extrabold tracking-tight">{app.firstName} {app.lastName}</h1>
        <span className={`rounded-full px-3 py-1 text-xs font-bold ${statusBadge(app.status)}`}>{statusLabel(app.status)}</span>
      </div>
      <p className="mt-1 text-sm opacity-70">Applied for {app.position} · {formatDate(app.createdAt)}</p>

      {error && <p className="mt-4 text-sm text-[var(--danger)]">{error}</p>}
      {notice && <p className="mt-4 text-sm font-semibold text-[var(--success)]">{notice}</p>}

      <section className="card mt-6 p-5">
        <div className="flex flex-wrap gap-2">
          <a href={`tel:${app.phone}`} className="btn">📞 {app.phone}</a>
          <a href={`mailto:${app.email}`} className="btn-ghost">✉️ {app.email}</a>
        </div>
        <dl className="mt-5 grid gap-4 text-sm sm:grid-cols-2">
          <Item label="Licensed">
            {app.licensed === null ? '—' : app.licensed ? `Yes${app.licenseNumber ? ` (#${app.licenseNumber})` : ''}` : 'No'}
          </Item>
          <Item label="Experience">
            {app.yearsExperience === null ? '—' : `${app.yearsExperience} year${app.yearsExperience === 1 ? '' : 's'}`}
          </Item>
          <Item label="Availability">{app.availability || '—'}</Item>
          <Item label="Instagram / portfolio">
            {portfolioHref ? (
              <a href={portfolioHref} target="_blank" rel="noreferrer noopener" className="text-[var(--accent-strong)] underline underline-offset-4">
                {app.portfolio}
              </a>
            ) : (app.portfolio || '—')}
          </Item>
        </dl>
        {app.message && (
          <div className="mt-5">
            <dt className="text-xs font-bold uppercase tracking-wide opacity-60">In their words</dt>
            <p className="mt-1 whitespace-pre-line leading-relaxed">{app.message}</p>
          </div>
        )}
      </section>

      <section className="card mt-6 space-y-4 p-5">
        <h2 className="text-lg font-extrabold">Hiring</h2>
        <div className="flex flex-wrap gap-2">
          {APPLICATION_STATUSES.map((s) => (
            <button
              key={s.value}
              onClick={() => void update({ status: s.value }, `Marked as ${s.label.toLowerCase()}.`)}
              className={`rounded-full px-3.5 py-1.5 text-sm font-semibold ${
                app.status === s.value ? s.badge : 'border border-[var(--border)] bg-[var(--surface)] opacity-70 hover:opacity-100'
              }`}
              aria-pressed={app.status === s.value}
            >
              {s.label}
            </button>
          ))}
        </div>
        <label className="block text-sm">
          <span className="mb-1 block font-semibold opacity-80">Private notes</span>
          <textarea
            className="input min-h-24"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            maxLength={4000}
            placeholder="Interview impressions, follow-ups…"
          />
        </label>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <button className="btn" onClick={() => void update({ ownerNotes: notes }, 'Notes saved.')}>Save notes</button>
          <button className="text-sm font-semibold text-[var(--danger)] hover:underline" onClick={() => void remove()}>
            Delete application
          </button>
        </div>
        {app.status === 'HIRED' && (
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-[var(--success-soft)] px-4 py-3 text-sm">
            <span>🎉 Ready to give {app.firstName} access to training videos?</span>
            <Link href={`/admin/team/new?application=${app.id}`} className="btn">Create their account</Link>
          </div>
        )}
      </section>
    </div>
  );
}

function Item({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs font-bold uppercase tracking-wide opacity-60">{label}</dt>
      <dd className="mt-0.5">{children}</dd>
    </div>
  );
}
