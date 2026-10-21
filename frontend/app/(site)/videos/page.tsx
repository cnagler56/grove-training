'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { api, type VideoSummary } from '@/src/lib/api';
import { useUser } from '@/src/lib/UserContext';
import VideoCard from '@/src/components/VideoCard';
import { canManageSite } from '@/src/lib/roles';

const UNCATEGORIZED = 'Other';

export default function LibraryPage() {
  const { user } = useUser();
  const [videos, setVideos] = useState<VideoSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');

  useEffect(() => {
    api.listVideos().then(setVideos).catch((e) => setError(e.message));
  }, []);

  const groups = useMemo(() => {
    const q = query.trim().toLowerCase();
    const map = new Map<string, VideoSummary[]>();
    for (const v of videos ?? []) {
      const hay = `${v.title} ${v.description ?? ''} ${v.category ?? ''}`.toLowerCase();
      if (q && !hay.includes(q)) continue;
      const key = v.category || UNCATEGORIZED;
      map.set(key, [...(map.get(key) ?? []), v]);
    }
    return [...map.entries()].sort(([a], [b]) =>
      a === UNCATEGORIZED ? 1 : b === UNCATEGORIZED ? -1 : a.localeCompare(b));
  }, [videos, query]);

  if (error) return <p className="text-[var(--danger)]">{error}</p>;
  if (!videos) return <p className="opacity-60">Loading videos…</p>;

  const ready = videos.filter((v) => v.status === 'READY');
  const watched = ready.filter((v) => v.completed).length;

  return (
    <div>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight">
            Training videos
          </h1>
          {ready.length > 0 && (
            <p className="mt-1 text-sm opacity-70">
              You&apos;ve watched {watched} of {ready.length} training video{ready.length === 1 ? '' : 's'}.
            </p>
          )}
        </div>
        {videos.length > 0 && (
          <input
            type="search"
            placeholder="Search videos"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            className="input max-w-xs"
          />
        )}
      </div>

      {videos.length === 0 ? (
        <div className="card mt-8 p-6 text-center">
          <p className="opacity-80">
            {user?.roles === 'CUSTOMER' ? 'Training videos are for the Grove Salons team.' : 'No training videos yet.'}
          </p>
          {canManageSite(user?.roles) && (
            <Link href="/admin" className="btn mt-4 inline-block">Upload the first one</Link>
          )}
        </div>
      ) : groups.length === 0 ? (
        <p className="mt-8 opacity-70">Nothing matches &ldquo;{query}&rdquo;.</p>
      ) : (
        groups.map(([category, list]) => (
          <section key={category} className="mt-8">
            <h2 className="mb-3 text-sm font-extrabold uppercase tracking-wide text-[var(--accent-strong)]">{category}</h2>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {list.map((v) => <VideoCard key={v.id} video={v} />)}
            </div>
          </section>
        ))
      )}
    </div>
  );
}
