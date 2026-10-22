import type { UserRole } from './roles';

export type { UserRole };

// API calls go to this site's own address; next.config.ts forwards them to the
// backend (BACKEND_URL). Same address = first-party cookie, so sign-in works on iPhones.
const BASE = '';

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
  /** BUNNY = uploaded file; YOUTUBE = a linked YouTube video. */
  source: 'BUNNY' | 'YOUTUBE';
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
  phone: string | null;
  /** ISO date "YYYY-MM-DD". */
  hireDate: string | null;
  /** ISO date "YYYY-MM-DD". */
  birthday: string | null;
  role: UserRole;
  active: boolean;
  /** When the account was disabled; null while active. */
  disabledAt: string | null;
  /** Off the schedule (time off) since this date, "YYYY-MM-DD"; null = on the schedule. */
  inactiveSince: string | null;
  videosCompleted: number;
  createdAt: string | null;
}

/** What a manager can edit on an employee. Dates are "YYYY-MM-DD" or null. */
export interface EmployeeDetails {
  firstName: string | null;
  lastName: string | null;
  email: string;
  phone: string | null;
  hireDate: string | null;
  birthday: string | null;
}

/** An owner adding someone directly. `password` is temporary; hand it to them. */
export interface NewEmployee extends EmployeeDetails {
  role: UserRole;
  password: string;
}

/** One shift. Dates are "YYYY-MM-DD"; times are "HH:MM" or "HH:MM:SS". */
export interface ShiftView {
  id: number;
  userId: number;
  name: string;
  role: UserRole | null;
  date: string;
  /** WORK, or REQUESTED_OFF ("RO": the person asked for the day off; no times). */
  type: ShiftType;
  start: string | null;
  end: string | null;
  note: string | null;
}

export type ShiftType = 'WORK' | 'REQUESTED_OFF';

/** One row of the schedule's Name column. */
export interface RosterEntry {
  userId: number;
  name: string;
  role: UserRole;
  /** false only for a former employee who still has shifts that week. */
  active: boolean;
}

/**
 * A Saturday-to-Friday week. Staff get no shifts until it's posted. `roster` is
 * the Name column: stylists, leads, managers, and GMs (A–Z), then receptionists.
 */
export interface WeekSchedule {
  weekStart: string;
  weekEnd: string;
  published: boolean;
  publishedAt: string | null;
  /** DRAFT, PENDING (sent for approval), or POSTED. */
  state: 'DRAFT' | 'PENDING' | 'POSTED';
  /** Viewer can change this week (posted weeks: approvers only). */
  canEdit: boolean;
  /** Viewer can approve/post, take down, or send back. */
  canApprove: boolean;
  submittedByName: string | null;
  submittedToId: number | null;
  submittedToName: string | null;
  submittedAt: string | null;
  /** Why an approver sent it back. */
  returnNote: string | null;
  returnedByName: string | null;
  /** Who the viewer can send this week to for approval. */
  approvers: Person[];
  roster: RosterEntry[];
  shifts: ShiftView[];
}

export interface Person {
  userId: number;
  name: string;
  role: UserRole;
}

/** A week waiting for the viewer's approval. */
export interface PendingWeek {
  weekStart: string;
  submittedByName: string | null;
  submittedAt: string | null;
}

/** What the signed-in person can do with the schedule. */
export interface MyPermissions {
  viewSchedule: boolean;
  editSchedule: boolean;
  approveSchedule: boolean;
}

export type PermissionKey = 'VIEW_SCHEDULE' | 'EDIT_SCHEDULE' | 'APPROVE_SCHEDULE';

/** One permission on the owner's Settings page. */
export interface PermissionRule {
  permission: PermissionKey;
  label: string;
  description: string;
  roles: UserRole[];
}

export interface ShiftInput {
  userId: number;
  date: string;
  type: ShiftType;
  /** Omitted for REQUESTED_OFF. */
  start?: string;
  end?: string;
  note?: string | null;
}

/** A birthday or work anniversary. `years` is for anniversaries only (ages are never shared). */
export interface Celebration {
  userId: number;
  name: string;
  kind: 'BIRTHDAY' | 'ANNIVERSARY';
  date: string;
  years: number | null;
}

/** Today's (everyone on staff) and the next week's (managers and up). */
export interface Celebrations {
  today: string;
  todays: Celebration[];
  upcoming: Celebration[];
}

export type ApplicationStatus = 'NEW' | 'CONTACTED' | 'INTERVIEW' | 'HIRED' | 'NOT_A_FIT';

/** What the public /apply form sends. `website` is a hidden spam trap; leave it empty. */
export interface ApplicationRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  position: string;
  licensed: boolean | null;
  licenseNumber?: string;
  yearsExperience: number | null;
  availability?: string;
  portfolio?: string;
  message?: string;
  website?: string;
}

/** A job application as the owner sees it. */
export interface JobApplication {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  position: string;
  licensed: boolean | null;
  licenseNumber: string | null;
  yearsExperience: number | null;
  availability: string | null;
  portfolio: string | null;
  message: string | null;
  status: ApplicationStatus;
  ownerNotes: string | null;
  createdAt: string;
}

export interface AdminStatus {
  bunnyConfigured: boolean;
  signupOpen: boolean;
}

/**
 * Every call goes to this site's own address (forwarded to the backend by
 * next.config.ts) and sends the session cookie. Errors surface the server's
 * message, e.g. "That team code isn't right".
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
  // Some responses have no body: 204 No Content (logout), 201 Created (apply).
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
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
  addYouTube: (r: VideoRequest & { url: string }) => send<VideoSummary>('/api/admin/videos/youtube', 'POST', r),
  uploadTicket: (id: number) => send<UploadTicket>(`/api/admin/videos/${id}/upload-ticket`, 'POST'),
  updateVideo: (id: number, r: VideoRequest) => send<VideoSummary>(`/api/admin/videos/${id}`, 'PUT', r),
  deleteVideo: (id: number) => send<void>(`/api/admin/videos/${id}`, 'DELETE'),
  completions: (id: number) => send<CompletionReport>(`/api/admin/videos/${id}/completions`),

  schedule: (date?: string) => send<WeekSchedule>(`/api/schedule${date ? `?date=${date}` : ''}`),
  mySchedule: () => send<ShiftView[]>('/api/schedule/mine'),
  saveSchedule: (weekStart: string, shifts: ShiftInput[]) =>
    send<WeekSchedule>(`/api/schedule/${weekStart}`, 'PUT', { shifts }),
  publishSchedule: (weekStart: string, published: boolean) =>
    send<WeekSchedule>(`/api/schedule/${weekStart}/published`, 'PUT', { published }),
  submitSchedule: (weekStart: string, approverId: number) =>
    send<WeekSchedule>(`/api/schedule/${weekStart}/submit`, 'POST', { approverId }),
  sendBackSchedule: (weekStart: string, note: string) =>
    send<WeekSchedule>(`/api/schedule/${weekStart}/send-back`, 'POST', { note }),
  waitingSchedules: () => send<PendingWeek[]>('/api/schedule/waiting'),

  myPermissions: () => send<MyPermissions>('/api/me/permissions'),
  celebrations: () => send<Celebrations>('/api/celebrations'),
  permissionRules: () => send<PermissionRule[]>('/api/owner/permissions'),
  updatePermissions: (roles: Partial<Record<PermissionKey, UserRole[]>>) =>
    send<PermissionRule[]>('/api/owner/permissions', 'PUT', { roles }),

  positions: () => send<string[]>('/api/apply/positions'),
  apply: (a: ApplicationRequest) => send<void>('/api/apply', 'POST', a),
  applications: () => send<JobApplication[]>('/api/owner/applications'),
  newApplicationCount: () => send<number>('/api/owner/applications/new-count'),
  application: (id: number) => send<JobApplication>(`/api/owner/applications/${id}`),
  updateApplication: (id: number, u: { status?: ApplicationStatus; ownerNotes?: string }) =>
    send<JobApplication>(`/api/owner/applications/${id}`, 'PUT', u),
  deleteApplication: (id: number) => send<void>(`/api/owner/applications/${id}`, 'DELETE'),

  team: () => send<TeamMember[]>('/api/admin/team'),
  member: (userId: number) => send<TeamMember>(`/api/admin/team/${userId}`),
  createMember: (n: NewEmployee) => send<TeamMember>('/api/admin/team', 'POST', n),
  deleteMember: (userId: number) => send<void>(`/api/admin/team/${userId}`, 'DELETE'),
  updateDetails: (userId: number, d: EmployeeDetails) =>
    send<TeamMember>(`/api/admin/team/${userId}/details`, 'PUT', d),
  updateMember: (userId: number, change: { active?: boolean; role?: UserRole; inactive?: boolean }) =>
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
