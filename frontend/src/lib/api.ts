const BASE = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8084';

export type UserRole = 'USER' | 'ADMIN';

export interface User {
  userId: number;
  firstName: string | null;
  lastName: string | null;
  email: string;
  roles: UserRole;
  active: boolean;
}

export interface Credentials {
  email: string;
  password: string;
  firstName?: string;
  lastName?: string;
  teamCode?: string;
}

export type VideoStatus = 'UPLOADING' | 'PROCESSING' | 'READY' | 'FAILED';

export interface VideoSummary {
  id: number;
  title: string;
  description: string | null;
  category: string | null;
  status: VideoStatus;
  durationSeconds: number | null;
  thumbnailUrl: string | null;
  completed: boolean;
  createdAt: string;
}

export interface VideoDetail {
  video: VideoSummary;
  /** Signed Bunny player URL; null until the video is READY. */
  embedUrl: string | null;
}

export interface VideoRequest {
  title: string;
  description?: string;
  category?: string;
}

/** Pre-signed credentials for uploading one file straight to Bunny over TUS. */
export interface UploadTicket {
  videoId: number;
  endpoint: string;
  libraryId: string;
  bunnyVideoId: string;
  expires: number;
  signature: string;
}

export interface CompletionRow {
  userId: number;
  name: string;
  email: string;
  completedAt: string | null;
}

export interface CompletionReport {
  videoId: number;
  employees: CompletionRow[];
}

export interface TeamMember {
  userId: number;
  firstName: string | null;
  lastName: string | null;
  email: string;
  role: UserRole;
  active: boolean;
  videosCompleted: number;
  createdAt: string | null;
}

export interface AdminStatus {
  bunnyConfigured: boolean;
  signupOpen: boolean;
}

/**
 * Every call sends `credentials: 'include'` so the session cookie rides
 * cross-origin (Next.js on :3001 → GroveServer on :8084). Errors surface the
 * server's message, e.g. "That team code isn't right".
 */
async function send<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    method,
    credentials: 'include',
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!res.ok) {
    let msg = `${res.status} ${res.statusText}`;
    try { const j = await res.json(); msg = j.message || j.error || msg; } catch { /* ignore */ }
    throw new Error(msg);
  }
  // 204 No Content (e.g. logout) has an empty body.
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export const api = {
  getMe: () => send<User>('/me'),
  login: (c: Credentials) => send<User>('/login', 'POST', c),
  register: (c: Credentials) => send<User>('/register', 'POST', c),
  logout: () => send<void>('/logout', 'POST'),

  listVideos: () => send<VideoSummary[]>('/api/videos'),
  getVideo: (id: number) => send<VideoDetail>(`/api/videos/${id}`),
  setCompleted: (id: number, done: boolean) =>
    send<void>(`/api/videos/${id}/completed`, done ? 'PUT' : 'DELETE'),

  adminStatus: () => send<AdminStatus>('/api/admin/status'),
  categories: () => send<string[]>('/api/admin/categories'),
  createVideo: (r: VideoRequest) => send<UploadTicket>('/api/admin/videos', 'POST', r),
  uploadTicket: (id: number) => send<UploadTicket>(`/api/admin/videos/${id}/upload-ticket`, 'POST'),
  updateVideo: (id: number, r: VideoRequest) => send<VideoSummary>(`/api/admin/videos/${id}`, 'PUT', r),
  deleteVideo: (id: number) => send<void>(`/api/admin/videos/${id}`, 'DELETE'),
  completions: (id: number) => send<CompletionReport>(`/api/admin/videos/${id}/completions`),

  team: () => send<TeamMember[]>('/api/admin/team'),
  updateMember: (userId: number, change: { active?: boolean; role?: UserRole }) =>
    send<TeamMember>(`/api/admin/team/${userId}`, 'PUT', change),
  resetPassword: (userId: number, password: string) =>
    send<void>(`/api/admin/team/${userId}/password`, 'POST', { password }),
};

/** "12:34" / "1:02:03" from seconds; empty when unknown. */
export function formatDuration(seconds: number | null): string {
  if (!seconds) return '';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  const mm = h ? String(m).padStart(2, '0') : String(m);
  return `${h ? h + ':' : ''}${mm}:${String(s).padStart(2, '0')}`;
}
