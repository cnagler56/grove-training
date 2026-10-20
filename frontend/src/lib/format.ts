const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/**
 * "2025-03-01" → "Mar 1, 2025". Parsed by hand: `new Date("2025-03-01")` is
 * UTC midnight, which shows as the previous day in US time zones.
 */
export function formatDate(iso: string | null, withYear = true): string {
  if (!iso) return '';
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number);
  if (!y || !m || !d) return '';
  return `${MONTHS[m - 1]} ${d}${withYear ? `, ${y}` : ''}`;
}

/** Birthdays show month and day only. */
export function formatBirthday(iso: string | null): string {
  return formatDate(iso, false);
}

/** "2 yrs 3 mos" since a hire date; empty if unknown or in the future. */
export function tenure(hireDate: string | null): string {
  if (!hireDate) return '';
  const [y, m] = hireDate.split('-').map(Number);
  const now = new Date();
  const months = (now.getFullYear() - y) * 12 + (now.getMonth() + 1 - m);
  if (months < 0) return '';
  if (months < 1) return 'new';
  const yrs = Math.floor(months / 12);
  const mos = months % 12;
  const parts = [];
  if (yrs) parts.push(`${yrs} yr${yrs === 1 ? '' : 's'}`);
  if (mos) parts.push(`${mos} mo${mos === 1 ? '' : 's'}`);
  return parts.join(' ');
}

export function employeeName(m: { firstName: string | null; lastName: string | null; email: string }): string {
  return `${m.firstName ?? ''} ${m.lastName ?? ''}`.trim() || m.email;
}
