import type { PayrollNote, PayrollNoteKind } from './api';
import { formatDateTime, moneyCents } from './format';

/** Where the owner signs in to ADP (RUN) to enter payroll. */
export const ADP_SIGN_IN_URL = 'https://online.adp.com/signin/v1/?APPID=RUN&productId=80e309c3-70c3-bae1-e053-3505430b5495'
  + '&Action=Login&Mode=BROWSER&Stc=False&ssru=branded';

/**
 * Window features for half the screen. Browsers only place and size windows a page opens
 * itself, so this is for window.open, and for the entry window moving itself into place.
 */
export function halfScreen(side: 'left' | 'right') {
  const s = window.screen as Screen & { availLeft?: number; availTop?: number };
  const width = Math.floor(s.availWidth / 2);
  const height = s.availHeight;
  const left = (s.availLeft ?? 0) + (side === 'right' ? width : 0);
  const top = s.availTop ?? 0;
  return { width, height, left, top, features: `popup,width=${width},height=${height},left=${left},top=${top}` };
}

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
