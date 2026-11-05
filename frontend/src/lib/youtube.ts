/**
 * The 11-character video id from a YouTube link (watch?v=, youtu.be/, shorts/,
 * embed/, live/) or a bare id. Mirrors the backend's YouTube.parseId, which
 * makes the real decision; this is only for the form's live preview.
 */
export function youTubeId(input: string): string | null {
  const s = input.trim();
  if (/^[A-Za-z0-9_-]{11}$/.test(s)) return s;
  let url: URL;
  try {
    url = new URL(/^https?:\/\//i.test(s) ? s : `https://${s}`);
  } catch {
    return null;
  }
  const host = url.hostname.toLowerCase().replace(/^(www|m|music)\./, '');
  let id: string | null = null;
  if (host === 'youtu.be') {
    id = url.pathname.split('/')[1] ?? null;
  } else if (host === 'youtube.com' || host === 'youtube-nocookie.com') {
    if (url.pathname === '/watch') id = url.searchParams.get('v');
    else {
      const [, kind, rest] = url.pathname.split('/');
      if (['shorts', 'embed', 'live', 'v'].includes(kind)) id = rest ?? null;
    }
  }
  return id && /^[A-Za-z0-9_-]{11}$/.test(id) ? id : null;
}

export const youTubeThumbnail = (id: string) => `https://i.ytimg.com/vi/${id}/hqdefault.jpg`;

export type LinkedSource = 'YOUTUBE' | 'INSTAGRAM' | 'TIKTOK';

/** Display names for linked videos' sites. */
export const SOURCE_NAME: Record<LinkedSource, string> = { YOUTUBE: 'YouTube', INSTAGRAM: 'Instagram', TIKTOK: 'TikTok' };

/**
 * Which site a video link is from, for the form's live check. Mirrors the
 * backend's YouTube and SocialVideo parsers, which make the real decision.
 * TikTok short links (vm.tiktok.com) count: the server follows them.
 */
export function videoLinkSource(input: string): LinkedSource | null {
  if (youTubeId(input)) return 'YOUTUBE';
  const s = input.trim();
  let url: URL;
  try {
    url = new URL(/^https?:\/\//i.test(s) ? s : `https://${s}`);
  } catch {
    return null;
  }
  const host = url.hostname.toLowerCase().replace(/^(www|m)\./, '');
  const parts = url.pathname.split('/');
  if (host === 'instagram.com' || host === 'instagr.am') {
    const at = parts.findIndex((p) => ['p', 'reel', 'reels', 'tv'].includes(p));
    return at >= 0 && /^[A-Za-z0-9_-]{5,40}$/.test(parts[at + 1] ?? '') ? 'INSTAGRAM' : null;
  }
  if (host === 'vm.tiktok.com' || host === 'vt.tiktok.com' || (host === 'tiktok.com' && parts[1] === 't')) return 'TIKTOK';
  if (host === 'tiktok.com') return parts.some((p) => /^\d{15,25}(\.html)?$/.test(p)) ? 'TIKTOK' : null;
  return null;
}
