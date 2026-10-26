'use client';

import { useState } from 'react';

/** "03151990" → "03/15/1990" as it's typed. Slashes only appear once the next digit is typed. */
function formatTyped(value: string): string {
  const d = value.replace(/\D/g, '').slice(0, 8);
  if (d.length <= 2) return d;
  if (d.length <= 4) return `${d.slice(0, 2)}/${d.slice(2)}`;
  return `${d.slice(0, 2)}/${d.slice(2, 4)}/${d.slice(4)}`;
}

/** "1990-03-15" → "03/15/1990". */
function toTyped(iso: string | null): string {
  if (!iso) return '';
  const [y, m, d] = iso.slice(0, 10).split('-');
  return y && m && d ? `${m}/${d}/${y}` : '';
}

/** "03/15/1990" → "1990-03-15", or null if it isn't a complete, real date. */
function toIso(typed: string): string | null {
  const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(typed);
  if (!m) return null;
  const [, mm, dd, yyyy] = m;
  const date = new Date(Number(yyyy), Number(mm) - 1, Number(dd));
  const real = date.getFullYear() === Number(yyyy) && date.getMonth() === Number(mm) - 1 && date.getDate() === Number(dd);
  return real ? `${yyyy}-${mm}-${dd}` : null;
}

/**
 * A date typed as MM/DD/YYYY (slashes added automatically). Works the same on
 * every browser and phone, unlike a date picker. `value` and `onChange` use
 * "YYYY-MM-DD" (or null when blank). A half-typed or impossible date blocks the
 * form from submitting with a message, rather than silently saving nothing.
 */
export default function DateField({
  label, value, onChange, max, hint,
}: {
  label: string;
  value: string | null;
  onChange: (iso: string | null) => void;
  /** Latest allowed date, "YYYY-MM-DD" (e.g. today for a birthday). */
  max?: string;
  hint?: string;
}) {
  const [text, setText] = useState(toTyped(value));
  const [shown, setShown] = useState(value);
  // The saved value changed from outside (the record loaded or was reset): show it.
  if (value !== shown) {
    setShown(value);
    if (value !== toIso(text)) setText(toTyped(value));
  }

  function problem(typed: string): string {
    if (!typed) return '';
    const iso = toIso(typed);
    if (!iso) return 'Enter a full date as MM/DD/YYYY.';
    if (max && iso > max) return `${label} can't be in the future.`;
    return '';
  }

  return (
    <label className="block text-sm">
      <span className="mb-1 block font-semibold opacity-80">{label}</span>
      <input
        className="input"
        inputMode="numeric"
        placeholder="MM/DD/YYYY"
        maxLength={10}
        value={text}
        onChange={(e) => {
          const typed = formatTyped(e.target.value);
          setText(typed);
          e.target.setCustomValidity(problem(typed));
          const iso = toIso(typed);
          if (!typed) { setShown(null); onChange(null); }
          else if (iso && !problem(typed)) { setShown(iso); onChange(iso); }
        }}
      />
      {hint && <span className="mt-1 block text-xs opacity-60">{hint}</span>}
    </label>
  );
}
