'use client';

import type { PayPeriod } from '@/src/lib/api';
import { formatDate } from '@/src/lib/format';
import { longDayLabel } from '@/src/lib/schedule';

/** "Oct 24 – Nov 6": the two weeks a check pays for. */
export const workRange = (p: PayPeriod) => `${formatDate(p.workStart, false)} – ${formatDate(p.workEnd)}`;

/** "Check Fri, Nov 13 (work Oct 24 – Nov 6, 2026)". */
export const periodLabel = (p: PayPeriod) => `Check ${longDayLabel(p.checkDate)} (work ${workRange(p)})`;

/** Picks a pay period by check date. The newest finished period is the default. */
export default function PayPeriodPicker({ periods, value, defaultCheckDate, onChange }: {
  periods: PayPeriod[];
  value: string;
  defaultCheckDate: string;
  onChange: (checkDate: string) => void;
}) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block font-semibold opacity-80">Pay period</span>
      <select className="input" value={value} onChange={(e) => onChange(e.target.value)}>
        {periods.map((p) => (
          <option key={p.checkDate} value={p.checkDate}>
            {periodLabel(p)}{p.checkDate === defaultCheckDate ? ' - current' : ''}
          </option>
        ))}
      </select>
    </label>
  );
}
