'use client';

import { useEffect, useState } from 'react';
import type { Celebration } from '@/src/lib/api';
import Fireworks from '@/src/components/Fireworks';

const TOAST_MS = 5000;

/** Once per person per day, so it doesn't replay on every visit to Home. */
const seenKey = (userId: number, day: string) => `grove.celebrated.${userId}.${day}`;

function alreadySeen(userId: number, day: string) {
  try { return localStorage.getItem(seenKey(userId, day)) === '1'; } catch { return false; }
}

function message(mine: Celebration[], firstName: string) {
  const birthday = mine.some((c) => c.kind === 'BIRTHDAY');
  const anniversary = mine.find((c) => c.kind === 'ANNIVERSARY');
  const years = anniversary?.years ? `${anniversary.years}-year ` : '';
  if (birthday && anniversary) {
    return { title: `Happy birthday and happy ${years}work anniversary, ${firstName}!`, body: 'A double celebration. Thank you for everything you do.' };
  }
  if (birthday) return { title: `Happy birthday, ${firstName}!`, body: 'The whole Grove Salons team is celebrating you today.' };
  return { title: `Happy ${years}work anniversary, ${firstName}!`, body: 'Thank you for everything you do. We\'re lucky to have you.' };
}

/**
 * On the viewer's own birthday or work anniversary: fireworks plus a toast
 * for about 5 seconds, the first time they open Home that day. People who
 * ask their phone for reduced motion just get the toast.
 */
export default function CelebrationMoment({ items, userId, firstName, today }: {
  items: Celebration[];
  userId: number;
  firstName: string;
  /** The salon's today, "YYYY-MM-DD" (from the server, so it matches the celebration). */
  today: string;
}) {
  const mine = items.filter((c) => c.userId === userId);
  // Decided once when Home first has the day's celebrations.
  const [play] = useState(() => mine.length > 0 && !alreadySeen(userId, today));
  const [calm] = useState(() => typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  const [fireworks, setFireworks] = useState(play && !calm);
  const [toast, setToast] = useState<'in' | 'out' | 'gone'>(play ? 'out' : 'gone');

  useEffect(() => {
    if (!play) return;
    try { localStorage.setItem(seenKey(userId, today), '1'); } catch { /* fine: it may show again */ }
    const show = requestAnimationFrame(() => setToast('in'));
    const hide = setTimeout(() => setToast('out'), TOAST_MS);
    const remove = setTimeout(() => setToast('gone'), TOAST_MS + 400);
    return () => { cancelAnimationFrame(show); clearTimeout(hide); clearTimeout(remove); };
  }, [play, userId, today]);

  if (!play) return null;
  const { title, body } = message(mine, firstName);

  return (
    <>
      {fireworks && <Fireworks onDone={() => setFireworks(false)} />}
      {toast !== 'gone' && (
        <div
          role="status"
          aria-live="polite"
          className="pointer-events-none fixed inset-x-0 top-20 z-50 flex justify-center px-4"
        >
          <div
            className="card w-full max-w-md border-2 border-[var(--accent)] p-5 shadow-xl transition-all duration-300"
            style={{ pointerEvents: toast === 'in' ? 'auto' : 'none', opacity: toast === 'in' ? 1 : 0, transform: toast === 'in' ? 'translateY(0)' : 'translateY(-12px)' }}
          >
            <div className="flex items-start justify-between gap-3">
              <div>
                <div className="text-lg font-extrabold text-[var(--accent-strong)]">{title}</div>
                <div className="mt-1 text-sm opacity-80">{body}</div>
              </div>
              <button className="text-sm font-semibold opacity-60 hover:opacity-100" onClick={() => setToast('out')}>
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
