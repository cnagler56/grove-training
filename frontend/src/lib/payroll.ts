import type { PayrollNote, PayrollNoteKind } from './api';
import { formatDateTime, moneyCents } from './format';

/** Label and badge colors for each kind of payroll note. */
export const NOTE_KIND: Record<PayrollNoteKind, [string, string]> = {
  DEDUCTION: ['Deduction', 'bg-red-50 text-[var(--danger)]'],
  ADDITION: ['Addition', 'bg-[var(--success-soft)] text-[var(--success)]'],
  COMMENT: ['Comment', 'bg-[var(--surface-2)]'],
};

/** "-$25.50" / "+$40.00", or nothing for a comment. */
export const signedAmount = (n: Pick<PayrollNote, 'kind' | 'amount'>) =>
  n.amount == null || n.kind === 'COMMENT' ? '' : `${n.kind === 'DEDUCTION' ? '-' : '+'}${moneyCents(n.amount)}`;

/** Where a note stands, in words, with a text color. */
export function noteStatus(n: PayrollNote): [string, string] {
  const at = (iso: string | null) => (iso ? `, ${formatDateTime(iso)}` : '');
  if (n.status === 'FINISHED') {
    return [`Finished: entered into payroll by ${n.finishedByName ?? 'someone'}${at(n.finishedAt)}`, 'text-[var(--success)]'];
  }
  if (n.status === 'ACKNOWLEDGED') {
    return [`Acknowledged by ${n.acknowledgedByName ?? 'someone'}${at(n.acknowledgedAt)}`, 'text-[var(--accent-strong)]'];
  }
  return ['Waiting to be acknowledged', 'text-[var(--warn)]'];
}
