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

/**
 * US phone with dashes as it's typed: "7633" → "763-3", "7633916664" → "763-391-6664".
 * A leading country code 1 is dropped. Dashes only appear once the next digit
 * is typed, so backspacing works normally.
 */
export function formatPhone(value: string): string {
  let d = value.replace(/\D/g, '');
  if (d.length === 11 && d.startsWith('1')) d = d.slice(1);
  d = d.slice(0, 10);
  if (d.length <= 3) return d;
  if (d.length <= 6) return `${d.slice(0, 3)}-${d.slice(3)}`;
  return `${d.slice(0, 3)}-${d.slice(3, 6)}-${d.slice(6)}`;
}

/** A saved phone shown with dashes; anything that isn't a 10-digit US number is shown as entered. */
export function displayPhone(phone: string | null): string {
  if (!phone) return '';
  const f = formatPhone(phone);
  return f.length === 12 ? f : phone;
}

/** "$1,403" (whole dollars). */
export const money = (n: number) => `$${Math.round(n).toLocaleString('en-US')}`;

/** "$2.58" (with cents, for small amounts like product per guest). */
export const moneyCents = (n: number) => `$${n.toFixed(2)}`;

/** "33.9" hours. */
export const hours = (n: number) => (Math.round(n * 10) / 10).toLocaleString('en-US');
