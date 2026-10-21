import type { ApplicationStatus } from './api';

/** Hiring steps in order, with the badge style for each. */
export const APPLICATION_STATUSES: { value: ApplicationStatus; label: string; badge: string }[] = [
  { value: 'NEW', label: 'New', badge: 'bg-[var(--accent)] text-white' },
  { value: 'CONTACTED', label: 'Contacted', badge: 'bg-[var(--accent-soft)] text-[var(--accent-strong)]' },
  { value: 'INTERVIEW', label: 'Interview', badge: 'bg-amber-100 text-[var(--warn)]' },
  { value: 'HIRED', label: 'Hired', badge: 'bg-[var(--success-soft)] text-[var(--success)]' },
  { value: 'NOT_A_FIT', label: 'Not a fit', badge: 'bg-black/5 opacity-70' },
];

const byValue = Object.fromEntries(APPLICATION_STATUSES.map((s) => [s.value, s]));

export const statusLabel = (s: ApplicationStatus) => byValue[s]?.label ?? s;
export const statusBadge = (s: ApplicationStatus) => byValue[s]?.badge ?? '';
