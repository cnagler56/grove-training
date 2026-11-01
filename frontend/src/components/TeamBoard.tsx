'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, type TeamPost } from '@/src/lib/api';
import { canManageSite } from '@/src/lib/roles';
import { useUser } from '@/src/lib/UserContext';
import { useDialogs } from '@/src/components/Dialogs';

const MAX_PHOTOS = 6;
/** Longest side of a posted photo. Keeps uploads small and quick on phones. */
const MAX_PHOTO_SIDE = 1600;

/** Any photo the browser can open, as a smaller JPEG. */
async function shrinkPhoto(file: File): Promise<Blob> {
  const url = URL.createObjectURL(file);
  try {
    const img = await new Promise<HTMLImageElement>((resolve, reject) => {
      const el = new Image();
      el.onload = () => resolve(el);
      el.onerror = () => reject(new Error(`Couldn't read ${file.name}. Try a JPEG or PNG photo.`));
      el.src = url;
    });
    const scale = Math.min(1, MAX_PHOTO_SIDE / Math.max(img.naturalWidth, img.naturalHeight));
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(img.naturalWidth * scale);
    canvas.height = Math.round(img.naturalHeight * scale);
    canvas.getContext('2d')!.drawImage(img, 0, 0, canvas.width, canvas.height);
    return await new Promise<Blob>((resolve, reject) => {
      canvas.toBlob((b) => (b ? resolve(b) : reject(new Error(`Couldn't read ${file.name}.`))), 'image/jpeg', 0.82);
    });
  } finally {
    URL.revokeObjectURL(url);
  }
}

/** "Oct 31, 9:05 PM". Times come from the server in salon time. */
function when(iso: string) {
  return new Date(iso).toLocaleString('en-US', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' });
}

/** Text with its web addresses as links. */
function Linked({ text }: { text: string }) {
  return (
    <>
      {text.split(/(https?:\/\/[^\s]+)/g).map((part, i) => (/^https?:\/\//.test(part)
        ? <a key={i} href={part} target="_blank" rel="noopener noreferrer" className="break-all text-[var(--accent-strong)] underline">{part}</a>
        : part))}
    </>
  );
}

/**
 * The Team board on the staff Home page: posts with photos, comments, likes,
 * and pinned notices. The owner picks who can post on Settings; everyone on
 * staff can comment and like.
 */
export default function TeamBoard() {
  const { user, perms } = useUser();
  const dialogs = useDialogs();
  const [posts, setPosts] = useState<TeamPost[] | null>(null);
  const [pages, setPages] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [body, setBody] = useState('');
  const [photos, setPhotos] = useState<File[]>([]);
  const [posting, setPosting] = useState(false);

  /** Fetch every page shown so far, so actions never lose your place. */
  const load = useCallback(async (count: number) => {
    const results = await Promise.all(Array.from({ length: count }, (_, i) => api.teamBoard(i)));
    setPosts(results.flatMap((r) => r.posts));
    setHasMore(results[results.length - 1].hasMore);
    setPages(count);
  }, []);

  useEffect(() => {
    api.teamBoard(0)
      .then((r) => { setPosts(r.posts); setHasMore(r.hasMore); })
      .catch((e) => setError(e.message));
  }, []);

  async function act(action: () => Promise<unknown>) {
    setError(null);
    try {
      await action();
      await load(pages);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong');
    }
  }

  function addPhotos(files: FileList | null) {
    if (!files) return;
    setError(null);
    const next = [...photos, ...Array.from(files)];
    if (next.length > MAX_PHOTOS) setError(`A post can have up to ${MAX_PHOTOS} photos.`);
    setPhotos(next.slice(0, MAX_PHOTOS));
  }

  async function publish(e: React.FormEvent) {
    e.preventDefault();
    if (!body.trim() && photos.length === 0) return;
    setPosting(true);
    setError(null);
    let created: number | null = null;
    try {
      const shrunk = await Promise.all(photos.map(shrinkPhoto));
      created = (await api.createTeamPost({ body, withPhotos: shrunk.length > 0 })).id;
      for (const photo of shrunk) await api.uploadTeamPhoto(created, photo);
      setBody('');
      setPhotos([]);
      await load(1);
    } catch (err) {
      // Don't leave half a post behind: take it down and keep what they typed.
      if (created !== null) await api.deleteTeamPost(created).catch(() => {});
      setError(err instanceof Error ? err.message : 'Could not post');
    } finally {
      setPosting(false);
    }
  }

  async function removePost(p: TeamPost) {
    if (await dialogs.confirm({
      title: 'Delete this post?',
      message: 'Its photos, comments, and likes go with it. This can\'t be undone.',
      confirmLabel: 'Delete post',
      danger: true,
    })) void act(() => api.deleteTeamPost(p.id));
  }

  async function removeComment(id: number) {
    if (await dialogs.confirm({ title: 'Delete this comment?', message: 'This can\'t be undone.', confirmLabel: 'Delete', danger: true })) {
      void act(() => api.deleteTeamComment(id));
    }
  }

  return (
    <section className="space-y-3 md:col-span-2">
      <h2 className="text-lg font-extrabold">Team board</h2>
      {error && <p className="text-sm text-[var(--danger)]">{error}</p>}

      {perms.postUpdates && (
        <form onSubmit={publish} className="card space-y-3 p-4">
          <textarea
            className="input min-h-16"
            placeholder="Share something with the team"
            aria-label="New post"
            maxLength={5000}
            value={body}
            onChange={(e) => setBody(e.target.value)}
          />
          {photos.length > 0 && (
            <ul className="flex flex-wrap gap-2 text-xs">
              {photos.map((f, i) => (
                <li key={`${f.name}-${i}`} className="flex items-center gap-2 rounded-full bg-[var(--surface-2)] px-3 py-1">
                  <span className="max-w-40 truncate">{f.name}</span>
                  <button type="button" className="font-bold text-[var(--danger)]"
                    onClick={() => setPhotos(photos.filter((_, j) => j !== i))}>Remove</button>
                </li>
              ))}
            </ul>
          )}
          <div className="flex flex-wrap items-center gap-2">
            <button type="submit" className="btn" disabled={posting || (!body.trim() && photos.length === 0)}>
              {posting ? 'Posting…' : 'Post'}
            </button>
            <label className={`btn-ghost cursor-pointer ${photos.length >= MAX_PHOTOS ? 'pointer-events-none opacity-50' : ''}`}>
              Add photos
              <input type="file" accept="image/*" multiple className="hidden"
                onChange={(e) => { addPhotos(e.target.files); e.target.value = ''; }} />
            </label>
          </div>
        </form>
      )}

      {posts === null ? (
        <p className="text-sm opacity-60">Loading…</p>
      ) : posts.length === 0 ? (
        <p className="card p-5 text-sm opacity-70">Nothing posted yet.</p>
      ) : (
        <div className="space-y-3">
          {posts.map((p) => (
            <PostCard
              key={p.id}
              post={p}
              canPin={canManageSite(user?.roles)}
              onLike={() => act(() => api.likeTeamPost(p.id, !p.likedByMe))}
              onPin={() => act(() => api.pinTeamPost(p.id, !p.pinned))}
              onDelete={() => removePost(p)}
              onComment={(text) => act(() => api.commentOnTeamPost(p.id, text))}
              onDeleteComment={removeComment}
            />
          ))}
          {hasMore && (
            <button className="btn-ghost" onClick={() => void load(pages + 1).catch((e) => setError(e.message))}>Show more</button>
          )}
        </div>
      )}
    </section>
  );
}

function PostCard({ post, canPin, onLike, onPin, onDelete, onComment, onDeleteComment }: {
  post: TeamPost;
  canPin: boolean;
  onLike: () => void;
  onPin: () => void;
  onDelete: () => void;
  onComment: (text: string) => Promise<void>;
  onDeleteComment: (id: number) => void;
}) {
  const [comment, setComment] = useState('');
  const [sending, setSending] = useState(false);

  async function send(e: React.FormEvent) {
    e.preventDefault();
    if (!comment.trim()) return;
    setSending(true);
    await onComment(comment);
    setComment('');
    setSending(false);
  }

  const single = post.imageIds.length === 1;
  return (
    <article className={`card p-4 ${post.pinned ? 'border-2 border-[var(--accent)]' : ''}`}>
      <div className="flex flex-wrap items-baseline gap-x-2">
        <span className="font-bold">{post.authorName}</span>
        <span className="text-xs opacity-60">{when(post.createdAt)}</span>
        {post.pinned && (
          <span className="rounded-full bg-[var(--accent-soft)] px-2 py-0.5 text-xs font-bold text-[var(--accent-strong)]">Pinned</span>
        )}
      </div>

      {post.body && <p className="mt-1 whitespace-pre-line break-words text-sm"><Linked text={post.body} /></p>}

      {post.imageIds.length > 0 && (
        <div className={`mt-2 grid gap-1.5 ${single ? 'grid-cols-1' : 'grid-cols-2 sm:grid-cols-3'}`}>
          {post.imageIds.map((id) => (
            <a key={id} href={`/api/team-board/images/${id}`} target="_blank" rel="noopener noreferrer">
              {/* Served from our own API, already sized when it was posted. */}
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src={`/api/team-board/images/${id}`}
                alt=""
                loading="lazy"
                className={`w-full rounded-lg bg-[var(--surface-2)] ${single ? 'max-h-96 object-contain' : 'aspect-square object-cover'}`}
              />
            </a>
          ))}
        </div>
      )}

      <div className="mt-2 flex flex-wrap items-center gap-3 text-sm">
        <button
          className={`font-semibold hover:underline ${post.likedByMe ? 'text-[var(--accent-strong)]' : 'opacity-70'}`}
          aria-pressed={post.likedByMe}
          onClick={onLike}
        >
          {post.likedByMe ? 'Liked' : 'Like'}{post.likes > 0 ? ` (${post.likes})` : ''}
        </button>
        <span className="flex-1" />
        {canPin && (
          <button className="text-xs font-semibold opacity-70 hover:underline" onClick={onPin}>{post.pinned ? 'Unpin' : 'Pin to top'}</button>
        )}
        {post.canDelete && (
          <button className="text-xs font-semibold text-[var(--danger)] hover:underline" onClick={onDelete}>Delete</button>
        )}
      </div>

      {post.comments.length > 0 && (
        <ul className="mt-2 space-y-1.5 border-t border-[var(--border)] pt-2 text-sm">
          {post.comments.map((c) => (
            <li key={c.id}>
              <b>{c.authorName}</b> <span className="whitespace-pre-line break-words"><Linked text={c.body} /></span>
              <span className="ml-2 text-xs opacity-50">{when(c.createdAt)}</span>
              {c.canDelete && (
                <button className="ml-2 text-xs font-semibold text-[var(--danger)] hover:underline" onClick={() => onDeleteComment(c.id)}>Delete</button>
              )}
            </li>
          ))}
        </ul>
      )}

      <form onSubmit={send} className="mt-2 flex gap-2">
        <input
          className="input py-1.5 text-sm"
          placeholder="Write a comment"
          aria-label={`Comment on ${post.authorName}'s post`}
          maxLength={1000}
          value={comment}
          onChange={(e) => setComment(e.target.value)}
        />
        <button type="submit" className="btn-ghost shrink-0" disabled={sending || !comment.trim()}>Send</button>
      </form>
    </article>
  );
}
