'use client';

import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import { api, formatDuration, type CompletionReport, type VideoDetail } from '@/src/lib/api';
import { useUser } from '@/src/lib/UserContext';

export default function VideoPage() {
  const { id } = useParams<{ id: string }>();
  const videoId = Number(id);
  const { user } = useUser();
  const isAdmin = user?.roles === 'ADMIN';

  const [detail, setDetail] = useState<VideoDetail | null>(null);
  const [report, setReport] = useState<CompletionReport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const load = useCallback(() => {
    api.getVideo(videoId).then(setDetail).catch((e) => setError(e.message));
    if (isAdmin) api.completions(videoId).then(setReport).catch(() => setReport(null));
  }, [videoId, isAdmin]);

  useEffect(load, [load]);

  async function toggleWatched() {
    if (!detail) return;
    setSaving(true);
    try {
      await api.setCompleted(videoId, !detail.video.completed);
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save');
    } finally {
      setSaving(false);
    }
  }

  if (error) return <p className="text-[var(--danger)]">{error}</p>;
  if (!detail) return <p className="opacity-60">Loading…</p>;

  const { video, embedUrl } = detail;
  const watchedCount = report?.employees.filter((e) => e.completedAt).length ?? 0;

  return (
    <div>
      <Link href="/" className="text-sm opacity-70 hover:opacity-100">← All videos</Link>

      <div className="mt-4 overflow-hidden rounded-2xl bg-black text-white shadow-lg">
        {embedUrl ? (
          <div className="relative aspect-video">
            <iframe
              src={embedUrl}
              title={video.title}
              className="absolute inset-0 h-full w-full border-0"
              allow="accelerometer; gyroscope; autoplay; encrypted-media; picture-in-picture; fullscreen"
              allowFullScreen
            />
          </div>
        ) : (
          <div className="flex aspect-video items-center justify-center p-6 text-center opacity-70">
            {video.status === 'FAILED'
              ? 'This upload failed. Delete it from Manage videos and try again.'
              : 'This video is still processing. Check back in a few minutes.'}
          </div>
        )}
      </div>

      <div className="mt-5 flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          {video.category && (
            <p className="text-xs font-bold uppercase tracking-wide text-[var(--accent-strong)]">{video.category}</p>
          )}
          <h1 className="text-3xl font-extrabold tracking-tight">{video.title}</h1>
          {video.durationSeconds ? (
            <p className="mt-1 text-sm opacity-60">{formatDuration(video.durationSeconds)}</p>
          ) : null}
        </div>
        {video.status === 'READY' && (
          <button
            onClick={() => void toggleWatched()}
            disabled={saving}
            className={video.completed ? 'btn-ghost' : 'btn'}
          >
            {video.completed ? '✓ Watched — undo' : 'Mark as watched'}
          </button>
        )}
      </div>

      {video.description && (
        <p className="mt-4 whitespace-pre-line leading-relaxed opacity-90">{video.description}</p>
      )}

      {isAdmin && report && (
        <section className="card mt-8 p-5">
          <h2 className="font-semibold">
            Who&apos;s watched it <span className="font-normal opacity-60">({watchedCount} of {report.employees.length})</span>
          </h2>
          <ul className="mt-3 divide-y divide-[var(--border)] text-sm">
            {report.employees.map((e) => (
              <li key={e.userId} className="flex justify-between gap-4 py-2">
                <span>{e.name}</span>
                <span className={e.completedAt ? 'text-[var(--success)]' : 'opacity-50'}>
                  {e.completedAt ? `✓ ${new Date(e.completedAt).toLocaleDateString()}` : 'Not yet'}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
