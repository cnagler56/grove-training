'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { api, formatDuration, type AdminStatus, type UploadTicket, type VideoSummary } from '@/src/lib/api';
import { uploadToBunny } from '@/src/lib/upload';

const STATUS_STYLE: Record<VideoSummary['status'], string> = {
  UPLOADING: 'text-amber-200',
  PROCESSING: 'text-amber-200',
  READY: 'text-[var(--accent)]',
  FAILED: 'text-red-300',
};
const STATUS_LABEL: Record<VideoSummary['status'], string> = {
  UPLOADING: 'Waiting for upload',
  PROCESSING: 'Processing',
  READY: 'Live',
  FAILED: 'Failed',
};

export default function AdminVideosPage() {
  const [status, setStatus] = useState<AdminStatus | null>(null);
  const [videos, setVideos] = useState<VideoSummary[]>([]);
  const [categories, setCategories] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    api.adminStatus().then(setStatus).catch((e) => setError(e.message));
    api.listVideos().then(setVideos).catch((e) => setError(e.message));
    api.categories().then(setCategories).catch(() => {});
  }, []);
  useEffect(load, [load]);

  // While anything is still processing on Bunny, re-check every 15s.
  const pending = videos.some((v) => v.status === 'PROCESSING');
  useEffect(() => {
    if (!pending) return;
    const t = setInterval(() => api.listVideos().then(setVideos).catch(() => {}), 15000);
    return () => clearInterval(t);
  }, [pending]);

  return (
    <div className="space-y-8">
      <h1 className="text-2xl font-semibold tracking-tight">Manage videos</h1>
      {error && <p className="text-red-300">{error}</p>}

      {status && !status.bunnyConfigured ? (
        <SetupHelp />
      ) : (
        <UploadForm categories={categories} onDone={load} />
      )}

      <section>
        <h2 className="mb-3 font-semibold">All videos ({videos.length})</h2>
        {videos.length === 0 ? (
          <p className="text-sm opacity-60">Nothing uploaded yet.</p>
        ) : (
          <ul className="space-y-3">
            {videos.map((v) => (
              <VideoRow key={v.id} video={v} categories={categories} onChange={load} />
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

function SetupHelp() {
  return (
    <section className="card space-y-2 p-5 text-sm">
      <h2 className="text-base font-semibold">Connect video hosting first</h2>
      <p className="opacity-80">
        Uploads go to Bunny Stream. Once these are set on the server, the upload form shows up here.
      </p>
      <ol className="list-decimal space-y-1 pl-5 opacity-80">
        <li>At bunny.net, go to Stream and create a video library called &ldquo;Grove Training&rdquo;.</li>
        <li>Open the library&apos;s <b>API</b> page and copy the Library ID, API key, and CDN hostname.</li>
        <li>Under <b>Security</b>, turn on embed view token authentication and copy that key too.</li>
        <li>Set <code>BUNNY_LIBRARY_ID</code>, <code>BUNNY_API_KEY</code>, <code>BUNNY_CDN_HOSTNAME</code>, and <code>BUNNY_TOKEN_KEY</code> on the backend, then restart it.</li>
      </ol>
    </section>
  );
}

function UploadForm({ categories, onDone }: { categories: string[]; onDone: () => void }) {
  const [title, setTitle] = useState('');
  const [category, setCategory] = useState('');
  const [description, setDescription] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [progress, setProgress] = useState<number | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const uploading = progress !== null;

  useWarnOnLeave(uploading);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!file) return;
    setError(null);
    setMessage(null);
    setProgress(0);
    try {
      const ticket = await api.createVideo({ title, category, description });
      onDone(); // show the new "waiting for upload" row right away
      await uploadToBunny(file, ticket, title, setProgress).promise;
      setMessage(`"${title}" uploaded. Bunny is processing it now. It goes live for the team in a few minutes.`);
      setTitle('');
      setDescription('');
      setFile(null);
      if (fileInput.current) fileInput.current.value = '';
      onDone();
    } catch (err) {
      setError(`Upload failed: ${err instanceof Error ? err.message : String(err)}. You can retry from the list below.`);
      onDone();
    } finally {
      setProgress(null);
    }
  }

  return (
    <form onSubmit={onSubmit} className="card space-y-4 p-5">
      <h2 className="font-semibold">Upload a training video</h2>
      <div className="grid gap-4 sm:grid-cols-2">
        <label className="block text-sm">
          <span className="mb-1 block opacity-70">Title</span>
          <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} required disabled={uploading} />
        </label>
        <label className="block text-sm">
          <span className="mb-1 block opacity-70">Category</span>
          <input
            className="input"
            list="categories"
            placeholder="e.g. Color, Cuts, Front desk"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            disabled={uploading}
          />
          <datalist id="categories">
            {categories.map((c) => <option key={c} value={c} />)}
          </datalist>
        </label>
      </div>
      <label className="block text-sm">
        <span className="mb-1 block opacity-70">Notes for the team (optional)</span>
        <textarea
          className="input min-h-20"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          disabled={uploading}
        />
      </label>
      <label className="block text-sm">
        <span className="mb-1 block opacity-70">Video file</span>
        <input
          ref={fileInput}
          type="file"
          accept="video/*"
          required
          disabled={uploading}
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          className="block w-full text-sm file:mr-3 file:rounded-md file:border-0 file:bg-white/10 file:px-3 file:py-1.5 file:text-[var(--foreground)]"
        />
        {file && <span className="mt-1 block text-xs opacity-60">{(file.size / 1024 / 1024).toFixed(0)} MB</span>}
      </label>

      {uploading && <ProgressBar fraction={progress} />}
      {message && <p className="text-sm text-[var(--accent)]">{message}</p>}
      {error && <p className="text-sm text-red-300">{error}</p>}

      <button type="submit" className="btn" disabled={uploading || !file || !title.trim()}>
        {uploading ? `Uploading… ${Math.round((progress ?? 0) * 100)}%` : 'Upload'}
      </button>
      {uploading && <p className="text-xs opacity-60">Keep this tab open until the upload finishes.</p>}
    </form>
  );
}

function VideoRow({ video, categories, onChange }: { video: VideoSummary; categories: string[]; onChange: () => void }) {
  const [editing, setEditing] = useState(false);
  const [title, setTitle] = useState(video.title);
  const [category, setCategory] = useState(video.category ?? '');
  const [description, setDescription] = useState(video.description ?? '');
  const [progress, setProgress] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const retryInput = useRef<HTMLInputElement>(null);

  useWarnOnLeave(progress !== null);

  async function save() {
    setError(null);
    try {
      await api.updateVideo(video.id, { title, category, description });
      setEditing(false);
      onChange();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save');
    }
  }

  async function remove() {
    if (!confirm(`Delete "${video.title}"? This removes it from Bunny too, along with everyone's watch history for it.`)) return;
    try {
      await api.deleteVideo(video.id);
      onChange();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not delete');
    }
  }

  async function retry(file: File) {
    setError(null);
    setProgress(0);
    try {
      const ticket: UploadTicket = await api.uploadTicket(video.id);
      await uploadToBunny(file, ticket, video.title, setProgress).promise;
      onChange();
    } catch (e) {
      setError(`Upload failed: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setProgress(null);
    }
  }

  return (
    <li className="card p-4">
      {editing ? (
        <div className="space-y-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} />
            <input className="input" list="categories" value={category} placeholder="Category" onChange={(e) => setCategory(e.target.value)} />
          </div>
          <datalist id="categories">
            {categories.map((c) => <option key={c} value={c} />)}
          </datalist>
          <textarea className="input min-h-16" value={description} placeholder="Notes" onChange={(e) => setDescription(e.target.value)} />
          <div className="flex gap-2">
            <button className="btn" onClick={() => void save()}>Save</button>
            <button className="btn-ghost" onClick={() => setEditing(false)}>Cancel</button>
          </div>
        </div>
      ) : (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="min-w-0">
            <Link href={`/videos/${video.id}`} className="font-medium hover:underline">{video.title}</Link>
            <p className="text-xs opacity-70">
              <span className={STATUS_STYLE[video.status]}>{STATUS_LABEL[video.status]}</span>
              {video.category && ` · ${video.category}`}
              {video.durationSeconds ? ` · ${formatDuration(video.durationSeconds)}` : ''}
              {` · added ${new Date(video.createdAt).toLocaleDateString()}`}
            </p>
          </div>
          <div className="flex gap-2">
            {video.status === 'UPLOADING' && progress === null && (
              <>
                <input
                  ref={retryInput}
                  type="file"
                  accept="video/*"
                  className="hidden"
                  onChange={(e) => e.target.files?.[0] && void retry(e.target.files[0])}
                />
                <button className="btn-ghost" onClick={() => retryInput.current?.click()}>Upload file</button>
              </>
            )}
            <button className="btn-ghost" onClick={() => setEditing(true)}>Edit</button>
            <button className="btn-ghost text-red-300" onClick={() => void remove()}>Delete</button>
          </div>
        </div>
      )}
      {progress !== null && <div className="mt-3"><ProgressBar fraction={progress} /></div>}
      {error && <p className="mt-2 text-sm text-red-300">{error}</p>}
    </li>
  );
}

function ProgressBar({ fraction }: { fraction: number | null }) {
  return (
    <div className="h-2 overflow-hidden rounded-full bg-white/10">
      <div className="h-full bg-[var(--accent)] transition-all" style={{ width: `${Math.round((fraction ?? 0) * 100)}%` }} />
    </div>
  );
}

/** Browser "leave site?" prompt while an upload is running. */
function useWarnOnLeave(active: boolean) {
  useEffect(() => {
    if (!active) return;
    const handler = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [active]);
}
