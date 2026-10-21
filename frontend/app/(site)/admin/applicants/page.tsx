'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { api, type ApplicationStatus, type JobApplication } from '@/src/lib/api';
import { APPLICATION_STATUSES, statusBadge, statusLabel } from '@/src/lib/applications';
import { formatDate } from '@/src/lib/format';

type Filter = ApplicationStatus | 'OPEN' | 'ALL';
const OPEN: ApplicationStatus[] = ['NEW', 'CONTACTED', 'INTERVIEW'];

export default function ApplicantsPage() {
  const [apps, setApps] = useState<JobApplication[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<Filter>('OPEN');

  useEffect(() => {
    api.applications().then(setApps).catch((e) => setError(e.message));
  }, []);

  const shown = useMemo(() => (apps ?? []).filter((a) =>
    filter === 'ALL' ? true : filter === 'OPEN' ? OPEN.includes(a.status) : a.status === filter), [apps, filter]);

  if (!apps) return <p className="opacity-60">{error ?? 'Loading applicants…'}</p>;
  const count = (f: Filter) => apps.filter((a) =>
    f === 'ALL' ? true : f === 'OPEN' ? OPEN.includes(a.status) : a.status === f).length;
  const chips: { value: Filter; label: string }[] = [
    { value: 'OPEN', label: 'Open' },
    ...APPLICATION_STATUSES.map((s) => ({ value: s.value as Filter, label: s.label })),
    { value: 'ALL', label: 'All' },
  ];

  return (
    <div>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight">Applicants</h1>
          <p className="mt-1 text-sm opacity-70">
            People who applied through your public page. Only owners can see this.
          </p>
        </div>
        <ShareLink />
      </div>

      <div className="mt-6 flex flex-wrap gap-2">
        {chips.map((c) => (
          <button
            key={c.value}
            onClick={() => setFilter(c.value)}
            className={`rounded-full px-3.5 py-1.5 text-sm font-semibold ${
              filter === c.value ? 'bg-[var(--accent-soft)] text-[var(--accent-strong)]' : 'bg-[var(--surface)] opacity-70 hover:opacity-100'
            }`}
          >
            {c.label} <span className="opacity-60">{count(c.value)}</span>
          </button>
        ))}
      </div>

      {shown.length === 0 ? (
        <div className="card mt-6 p-6 text-center opacity-80">
          {apps.length === 0 ? 'No applications yet. Share your apply link to get some!' : 'Nobody in this list.'}
        </div>
      ) : (
        <ul className="mt-6 space-y-3">
          {shown.map((a) => (
            <li key={a.id}>
              <Link href={`/admin/applicants/${a.id}`} className="card flex flex-wrap items-center justify-between gap-3 p-4">
                <div className="min-w-0">
                  <div className="font-bold">
                    {a.firstName} {a.lastName}
                    <span className="ml-2 font-normal opacity-60">· {a.position}</span>
                  </div>
                  <div className="text-xs opacity-60">
                    Applied {formatDate(a.createdAt)}
                    {a.yearsExperience !== null && ` · ${a.yearsExperience} yr${a.yearsExperience === 1 ? '' : 's'} experience`}
                    {a.licensed === true && ' · licensed'}
                  </div>
                </div>
                <span className={`rounded-full px-3 py-1 text-xs font-bold ${statusBadge(a.status)}`}>{statusLabel(a.status)}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** The public apply URL, with a copy button to paste into job posts or Instagram. */
function ShareLink() {
  const [copied, setCopied] = useState(false);
  const url = typeof window === 'undefined' ? '/apply' : `${window.location.origin}/apply`;
  return (
    <div className="flex items-center gap-2 text-sm">
      <a href="/apply" target="_blank" rel="noreferrer" className="font-semibold text-[var(--accent-strong)] underline underline-offset-4">
        View apply page
      </a>
      <button
        className="btn-ghost"
        onClick={() => {
          void navigator.clipboard?.writeText(url).then(() => {
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
          });
        }}
      >
        {copied ? 'Copied!' : 'Copy link'}
      </button>
    </div>
  );
}
