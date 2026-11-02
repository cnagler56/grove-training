'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { api, type Contest } from '@/src/lib/api';
import { ordinal } from '@/src/lib/format';
import { contestTiming } from './ContestCard';

/** On Home while a contest is on: where you stand and who's leading. Nothing otherwise. */
export default function ContestHomeCard({ userId }: { userId: number }) {
  const [live, setLive] = useState<Contest[]>([]);

  useEffect(() => {
    api.contests().then((all) => setLive(all.filter((c) => c.status === 'LIVE' || c.status === 'ENDED'))).catch(() => {});
  }, []);

  if (live.length === 0) return null;
  return (
    <section className="card border-2 border-[var(--accent)] p-5">
      <div className="flex items-end justify-between gap-2">
        <h2 className="text-lg font-extrabold">{live.length === 1 ? 'Contest on now' : 'Contests on now'}</h2>
        <Link href="/contests" className="text-sm font-semibold text-[var(--accent-strong)] hover:underline">See standings</Link>
      </div>
      <ul className="mt-2 divide-y divide-[var(--border)] text-sm">
        {live.map((c) => {
          const mine = c.standings.find((s) => s.userId === userId);
          const leaders = c.standings.filter((s) => s.rank === 1);
          const leadingTeam = c.teams.filter((t) => t.rank === 1);
          return (
            <li key={c.id} className="py-2">
              <div className="font-bold">{c.name}</div>
              <div className="opacity-70">{contestTiming(c)}</div>
              {c.lastCheckedAt === null ? (
                <div className="mt-0.5">Standings show up after the first check.</div>
              ) : (
                <>
                  {mine && mine.rank !== null && (
                    <div className="mt-0.5 font-semibold">You&apos;re {ordinal(mine.rank)} with {mine.count} {c.metricLabel}.</div>
                  )}
                  {leadingTeam.length === 1 ? (
                    <div className="mt-0.5">{leadingTeam[0].name} leads with {leadingTeam[0].total}.</div>
                  ) : leaders.length > 0 && !(mine && mine.rank === 1 && leaders.length === 1) && (
                    <div className="mt-0.5">Leading: {leaders.map((l) => l.name).join(', ')} with {leaders[0].count}.</div>
                  )}
                </>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
