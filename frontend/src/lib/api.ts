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
  /** BUNNY = uploaded file; the others are linked videos that play in that site's player. */
  source: 'BUNNY' | 'YOUTUBE' | 'INSTAGRAM' | 'TIKTOK';
  status: VideoStatus;
  durationSeconds: number | null;
  thumbnailUrl: string | null;
  completed: boolean;
  createdAt: string;
  /** Shown only to the people it's assigned to (managers always see it). */
  assignedOnly: boolean;
  /** The viewer is one of the people it's assigned to. */
  assignedToMe: boolean;
}

/** Who can see a video: everyone on staff, or only these people. */
export interface VideoAudience {
  everyone: boolean;
  userIds: number[];
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
  /** Their name as Salon Ultimate shows it; links their weekly numbers. Null = not linked. */
  salonUltimateName: string | null;
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
  /** Every day this week someone is approved off (already in shifts as RO unless the day has a shift). */
  timeOff: { userId: number; name: string; date: string }[];
  /** Private notes by employee id. Only sent to people who can build or approve the schedule. */
  builderNotes: Record<number, string>;
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
  approveTimeOff: boolean;
  postUpdates: boolean;
}

export interface TeamComment {
  id: number;
  authorName: string;
  body: string;
  createdAt: string;
  canDelete: boolean;
}

/** A post on the Team board (staff Home page). */
export interface TeamPost {
  id: number;
  authorName: string;
  body: string | null;
  pinned: boolean;
  createdAt: string;
  imageIds: number[];
  likes: number;
  likedByMe: boolean;
  comments: TeamComment[];
  canDelete: boolean;
}

export interface TeamFeed {
  posts: TeamPost[];
  hasMore: boolean;
}

/** A request for days off. Dates are "YYYY-MM-DD"; endDate is the last day off. */
export interface TimeOffRequest {
  id: number;
  userId: number;
  name: string;
  startDate: string;
  endDate: string;
  note: string | null;
  status: 'PENDING' | 'APPROVED' | 'SENT_BACK';
  /** The approver's message back. */
  responseNote: string | null;
  decidedByName: string | null;
  decidedAt: string | null;
  createdAt: string | null;
}

/** What an approver sees. */
export interface TimeOffQueue {
  waiting: TimeOffRequest[];
  /** Approved time off that's still to come. */
  approved: TimeOffRequest[];
}

/** A request for paid time off hours. checkDate is "YYYY-MM-DD". */
export interface VacationPayRequest {
  id: number;
  userId: number;
  name: string;
  hours: number;
  checkDate: string;
  status: 'PENDING' | 'APPROVED' | 'SENT_BACK';
  responseNote: string | null;
  decidedByName: string | null;
  decidedAt: string | null;
  createdAt: string | null;
}

/** What an owner sees. */
export interface VacationPayQueue {
  waiting: VacationPayRequest[];
  /** Approved requests whose check hasn't gone out yet. */
  approved: VacationPayRequest[];
}

export type PermissionKey = 'VIEW_SCHEDULE' | 'EDIT_SCHEDULE' | 'APPROVE_SCHEDULE' | 'APPROVE_TIME_OFF' | 'POST_UPDATES';

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

/** One week of someone's numbers from Salon Ultimate. Money in dollars. */
export interface StatLine {
  weekStart: string;
  /** Service sales before discounts. */
  serviceSales: number;
  netServiceSales: number;
  retailSales: number;
  productPerGuest: number;
  floorHours: number;
  offFloorHours: number;
  /** Service sales per floor hour; null with no floor hours. */
  serviceSalesPerHour: number | null;
  /** Production per hour as payroll figures it: (after-discount sales + 20% of discounts) ÷ floor hours. */
  pph: number;
  /** That week's hourly rate. Only on the employee's own My week; null elsewhere or before pay rules are loaded. */
  payRate: number | null;
  /** Also only on the employee's own My week (null elsewhere, or for weeks pulled before tips were saved). */
  tips: number | null;
  /** Tips ÷ floor hours. */
  tipsPerHour: number | null;
  /** Pay rate + tips per hour. */
  totalPerHour: number | null;
  /** Services done and clients seen (null for weeks pulled before these were saved). */
  services: number | null;
  clients: number | null;
  /** Color, treatment, and waxing services (null for weeks pulled before these were saved). */
  colors: number | null;
  treatments: number | null;
  waxes: number | null;
}

/** The whole team's services by type for one week. */
export interface TeamServiceWeek {
  weekStart: string;
  colors: number | null;
  treatments: number | null;
  waxes: number | null;
}

/** The pay matrix and codes, as loaded from the payroll skill's files (owners only). */
export interface PayRules {
  matrix: { pph: number; rate: number }[];
  codes: { name: string; code: number | null; premium: number | null; flatRate: number | null }[];
}

/** The skill's two files, as-is: matrix.json and employee_codes.json. */
export interface PayRulesUpload {
  matrix: { pph: number; rate: number }[];
  codes: Record<string, { code?: number; premium?: number; flat_rate?: number }>;
}

export interface StatsImport {
  weekStart: string;
  status: 'OK' | 'BLOCKED' | 'FAILED' | 'NOT_CONFIGURED';
  message: string | null;
  rowCount: number | null;
  attemptedAt: string;
}

/** The signed-in person's weeks, newest first. */
export interface MyStats {
  linked: boolean;
  salonUltimateName: string | null;
  weeks: StatLine[];
  teamWeeks: TeamServiceWeek[];
}

export interface TeamStatRow {
  suName: string;
  /** The linked site account, if any. */
  userId: number | null;
  name: string;
  stats: StatLine;
}

/** Everyone's numbers for one week (managers and up). */
export interface TeamStats {
  weekStart: string;
  latestCompleteWeek: string;
  weeks: string[];
  configured: boolean;
  lastImport: StatsImport | null;
  rows: TeamStatRow[];
  teamWeeks: TeamServiceWeek[];
}

export interface SalonUltimateName {
  name: string;
  linkedUserId: number | null;
  linkedTo: string | null;
}

/** Someone who'll sit for a haircut (the Models page, managers and up). Dates are "YYYY-MM-DD". */
export interface HaircutModel {
  id: number;
  name: string;
  source: string | null;
  phone: string | null;
  notes: string | null;
  lastVisit: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface ModelInput {
  name: string;
  source: string | null;
  phone: string | null;
  notes: string | null;
  lastVisit: string | null;
}

/** A number the business calls (the Contacts page, managers and up). */
export interface BusinessContact {
  id: number;
  name: string;
  phone: string;
  /** Who they are: vendor, landlord... */
  type: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface ContactInput {
  name: string;
  phone: string;
  type: string | null;
}

/** One to-do on one person's list. Times are the salon's local time. */
export interface Todo {
  id: number;
  text: string;
  assigneeId: number;
  assigneeName: string;
  assigneeRole: UserRole | null;
  assignedByName: string;
  assignedAt: string;
  /** Optional, "YYYY-MM-DD". */
  dueDate: string | null;
  /** Null until they mark it done. */
  completedAt: string | null;
}

/** A group you can assign to: its people, and its own items assigned before. */
export interface TodoAssignGroup {
  role: UserRole;
  people: { userId: number; name: string }[];
  items: { id: number; text: string }[];
}

export interface TodoAssignOptions {
  groups: TodoAssignGroup[];
}

export type ContestMetric = 'TREATMENTS' | 'COLORS' | 'WAXES';

/** HOURS: fewer hours worked, more points per service. RATE: a lower usual rate per guest, more points. BOTH: the two together. */
export type ContestHandicap = 'NONE' | 'HOURS' | 'RATE' | 'BOTH';

/** A contest as everyone on staff sees it. Places are null until the first check; tied people share one. */
export interface Contest {
  id: number;
  name: string;
  metric: ContestMetric;
  /** "treatments", "colors", "waxes". */
  metricLabel: string;
  handicap: ContestHandicap;
  startDate: string;
  endDate: string;
  prize: string | null;
  /** ENDED: the last day is over and the final check hasn't run yet. */
  status: 'UPCOMING' | 'LIVE' | 'ENDED' | 'FINISHED';
  lastCheckedAt: string | null;
  /** Why the last Salon Ultimate check failed. Managers only. */
  checkProblem: string | null;
  createdByName: string;
  /** `total` is points (the count, without a handicap). */
  teams: { position: number; name: string; total: number; rank: number | null; members: number }[];
  /** `worth` is points per service with a handicap, set at the first check; null otherwise. */
  standings: {
    userId: number; name: string; count: number | null; points: number | null; worth: number | null;
    rank: number | null; team: number | null;
  }[];
}

export interface ContestInput {
  name: string;
  metric: ContestMetric;
  handicap: ContestHandicap;
  startDate: string | null;
  endDate: string | null;
  prize: string | null;
  /** Empty for everyone for themselves. */
  teams: string[];
  /** `team` is a position in `teams`. */
  people: { userId: number; team: number | null }[];
}

/** Something the app told this person. `url` is the page it opens. */
export interface AppNotification {
  id: number;
  title: string;
  body: string | null;
  url: string;
  createdAt: string;
  read: boolean;
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
  changePassword: (currentPassword: string, newPassword: string) =>
    send<void>('/api/account/password', 'POST', { currentPassword, newPassword }),

  listVideos: () => send<VideoSummary[]>('/api/videos'),
  getVideo: (id: number) => send<VideoDetail>(`/api/videos/${id}`),
  setCompleted: (id: number, done: boolean) =>
    send<void>(`/api/videos/${id}/completed`, done ? 'PUT' : 'DELETE'),

  adminStatus: () => send<AdminStatus>('/api/admin/status'),
  categories: () => send<string[]>('/api/admin/categories'),
  createVideo: (r: VideoRequest) => send<UploadTicket>('/api/admin/videos', 'POST', r),
  addVideoLink: (r: VideoRequest & { url: string }) => send<VideoSummary>('/api/admin/videos/link', 'POST', r),
  uploadTicket: (id: number) => send<UploadTicket>(`/api/admin/videos/${id}/upload-ticket`, 'POST'),
  updateVideo: (id: number, r: VideoRequest) => send<VideoSummary>(`/api/admin/videos/${id}`, 'PUT', r),
  deleteVideo: (id: number) => send<void>(`/api/admin/videos/${id}`, 'DELETE'),
  videoAudience: (id: number) => send<VideoAudience>(`/api/admin/videos/${id}/audience`),
  setVideoAudience: (id: number, a: VideoAudience) => send<VideoAudience>(`/api/admin/videos/${id}/audience`, 'PUT', a),
  completions: (id: number) => send<CompletionReport>(`/api/admin/videos/${id}/completions`),

  schedule: (date?: string) => send<WeekSchedule>(`/api/schedule${date ? `?date=${date}` : ''}`),
  mySchedule: () => send<ShiftView[]>('/api/schedule/mine'),
  saveSchedule: (weekStart: string, shifts: ShiftInput[], notes: Record<number, string>) =>
    send<WeekSchedule>(`/api/schedule/${weekStart}`, 'PUT', { shifts, notes }),
  publishSchedule: (weekStart: string, published: boolean) =>
    send<WeekSchedule>(`/api/schedule/${weekStart}/published`, 'PUT', { published }),
  submitSchedule: (weekStart: string, approverId: number) =>
    send<WeekSchedule>(`/api/schedule/${weekStart}/submit`, 'POST', { approverId }),
  sendBackSchedule: (weekStart: string, note: string) =>
    send<WeekSchedule>(`/api/schedule/${weekStart}/send-back`, 'POST', { note }),
  waitingSchedules: () => send<PendingWeek[]>('/api/schedule/waiting'),

  pushKey: () => send<{ publicKey: string }>('/api/push/key'),
  pushSubscribe: (s: { endpoint: string; p256dh: string; auth: string }) => send<void>('/api/push/subscribe', 'POST', s),
  pushUnsubscribe: (endpoint: string) => send<void>('/api/push/unsubscribe', 'POST', { endpoint }),
  pushTest: () => send<{ service: string; status: number; answer: string }[]>('/api/push/test', 'POST'),

  teamBoard: (page: number) => send<TeamFeed>(`/api/team-board?page=${page}`),
  createTeamPost: (p: { body: string; withPhotos: boolean }) => send<{ id: number }>('/api/team-board', 'POST', p),
  /** One shrunk JPEG at a time, so a post with many photos never hits a size limit. */
  uploadTeamPhoto: async (id: number, photo: Blob) => {
    const res = await fetch(`${BASE}/api/team-board/${id}/images`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'image/jpeg' },
      body: photo,
    });
    if (!res.ok) {
      const j = await res.json().catch(() => null);
      throw new Error(j?.message || 'A photo could not be uploaded');
    }
  },
  deleteTeamPost: (id: number) => send<void>(`/api/team-board/${id}`, 'DELETE'),
  pinTeamPost: (id: number, pinned: boolean) => send<void>(`/api/team-board/${id}/pinned`, 'PUT', { pinned }),
  likeTeamPost: (id: number, liked: boolean) => send<void>(`/api/team-board/${id}/like`, liked ? 'PUT' : 'DELETE'),
  commentOnTeamPost: (id: number, body: string) => send<void>(`/api/team-board/${id}/comments`, 'POST', { body }),
  deleteTeamComment: (id: number) => send<void>(`/api/team-board/comments/${id}`, 'DELETE'),

  myTimeOff: () => send<TimeOffRequest[]>('/api/time-off/mine'),
  requestTimeOff: (r: { startDate: string; endDate: string; note: string | null }) =>
    send<TimeOffRequest>('/api/time-off', 'POST', r),
  cancelTimeOff: (id: number) => send<void>(`/api/time-off/${id}`, 'DELETE'),
  timeOffQueue: () => send<TimeOffQueue>('/api/time-off/queue'),
  approveTimeOff: (id: number) => send<TimeOffRequest>(`/api/time-off/${id}/approve`, 'PUT', {}),
  sendBackTimeOff: (id: number, message: string) =>
    send<TimeOffRequest>(`/api/time-off/${id}/send-back`, 'PUT', { message }),

  vacationPayCheckDates: () => send<string[]>('/api/vacation-pay/check-dates'),
  myVacationPay: () => send<VacationPayRequest[]>('/api/vacation-pay/mine'),
  requestVacationPay: (r: { hours: number; checkDate: string }) =>
    send<VacationPayRequest>('/api/vacation-pay', 'POST', r),
  cancelVacationPay: (id: number) => send<void>(`/api/vacation-pay/${id}`, 'DELETE'),
  vacationPayQueue: () => send<VacationPayQueue>('/api/vacation-pay/queue'),
  approveVacationPay: (id: number) => send<VacationPayRequest>(`/api/vacation-pay/${id}/approve`, 'PUT', {}),
  sendBackVacationPay: (id: number, message: string) =>
    send<VacationPayRequest>(`/api/vacation-pay/${id}/send-back`, 'PUT', { message }),

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

  models: () => send<HaircutModel[]>('/api/admin/models'),
  createModel: (m: ModelInput) => send<HaircutModel>('/api/admin/models', 'POST', m),
  updateModel: (id: number, m: ModelInput) => send<HaircutModel>(`/api/admin/models/${id}`, 'PUT', m),
  deleteModel: (id: number) => send<void>(`/api/admin/models/${id}`, 'DELETE'),

  myTodos: () => send<Todo[]>('/api/todos/mine'),
  setTodoDone: (id: number, done: boolean) => send<Todo>(`/api/todos/${id}/${done ? 'done' : 'not-done'}`, 'PUT', {}),
  assignedTodos: () => send<Todo[]>('/api/todos/assigned'),
  todoAssignOptions: () => send<TodoAssignOptions>('/api/todos/assign-options'),
  assignTodo: (text: string, userIds: number[], dueDate: string | null) =>
    send<Todo[]>('/api/todos', 'POST', { text, userIds, dueDate }),
  removeTodo: (id: number) => send<void>(`/api/todos/${id}`, 'DELETE'),
  removeSavedTodoItem: (id: number) => send<void>(`/api/todos/items/${id}`, 'DELETE'),

  notifications: () => send<{ unread: number; notifications: AppNotification[] }>('/api/notifications'),
  /** Marks anything pointing to this page read; returns how many are still unread. */
  notificationsSeen: (path: string) => send<{ unread: number }>('/api/notifications/seen', 'POST', { path }),
  readNotification: (id: number) => send<void>(`/api/notifications/${id}/read`, 'PUT', {}),
  readAllNotifications: () => send<void>('/api/notifications/read-all', 'PUT', {}),

  contests: () => send<Contest[]>('/api/contests'),
  contestPeople: () => send<{ userId: number; name: string }[]>('/api/contests/people'),
  createContest: (c: ContestInput) => send<Contest>('/api/contests', 'POST', c),
  updateContest: (id: number, c: ContestInput) => send<Contest>(`/api/contests/${id}`, 'PUT', c),
  deleteContest: (id: number) => send<void>(`/api/contests/${id}`, 'DELETE'),
  checkContest: (id: number) => send<Contest>(`/api/contests/${id}/check`, 'POST', {}),
  /** What each person's service would be worth with this handicap (100 for anyone at the average or better). */
  previewContestWorth: (p: { metric: ContestMetric; handicap: ContestHandicap; startDate: string | null; userIds: number[] }) =>
    send<{ userId: number; worth: number }[]>('/api/contests/worth-preview', 'POST', p),

  contacts: () => send<BusinessContact[]>('/api/admin/contacts'),
  createContact: (c: ContactInput) => send<BusinessContact>('/api/admin/contacts', 'POST', c),
  updateContact: (id: number, c: ContactInput) => send<BusinessContact>(`/api/admin/contacts/${id}`, 'PUT', c),
  deleteContact: (id: number) => send<void>(`/api/admin/contacts/${id}`, 'DELETE'),

  team: () => send<TeamMember[]>('/api/admin/team'),
  member: (userId: number) => send<TeamMember>(`/api/admin/team/${userId}`),
  createMember: (n: NewEmployee) => send<TeamMember>('/api/admin/team', 'POST', n),
  deleteMember: (userId: number) => send<void>(`/api/admin/team/${userId}`, 'DELETE'),
  updateDetails: (userId: number, d: EmployeeDetails) =>
    send<TeamMember>(`/api/admin/team/${userId}/details`, 'PUT', d),
  updateMember: (userId: number, change: { active?: boolean; role?: UserRole; inactive?: boolean }) =>
    send<TeamMember>(`/api/admin/team/${userId}`, 'PUT', change),
  linkSalonUltimate: (userId: number, name: string | null) =>
    send<TeamMember>(`/api/admin/team/${userId}/salon-ultimate`, 'PUT', { name }),

  myStats: () => send<MyStats>('/api/stats/mine'),
  teamStats: (week?: string) => send<TeamStats>(`/api/stats/team${week ? `?week=${week}` : ''}`),
  pullStats: (week: string) => send<StatsImport>(`/api/stats/import?week=${week}`, 'POST'),
  payRules: () => send<PayRules>('/api/owner/pay-rules'),
  loadPayRules: (u: PayRulesUpload) => send<PayRules>('/api/owner/pay-rules', 'PUT', u),
  salonUltimateNames: () => send<SalonUltimateName[]>('/api/stats/names'),

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
