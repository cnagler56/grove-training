/**
 * Salon roles, highest first. Mirrors backend Role.java. The server enforces
 * these rules; the frontend uses them only to hide controls you can't use.
 */
export const ROLES = [
  { value: 'OWNER', label: 'Owner', rank: 6 },
  { value: 'GENERAL_MANAGER', label: 'General manager', rank: 5 },
  { value: 'MANAGER', label: 'Manager', rank: 4 },
  { value: 'LEAD', label: 'Lead', rank: 3 },
  { value: 'STYLIST', label: 'Stylist', rank: 2 },
  { value: 'RECEPTIONIST', label: 'Receptionist', rank: 2 },
  { value: 'CUSTOMER', label: 'Customer', rank: 1 },
] as const;

export type UserRole = (typeof ROLES)[number]['value'];

const byValue = Object.fromEntries(ROLES.map((r) => [r.value, r])) as Record<UserRole, (typeof ROLES)[number]>;
const rank = (r: UserRole) => byValue[r]?.rank ?? 0;

export const roleLabel = (r: UserRole) => byValue[r]?.label ?? r;

/** Managers and up get the manager pages (upload videos, manage the team). */
export const canManageSite = (r: UserRole | undefined) => !!r && rank(r) >= rank('MANAGER');

/** Owners and general managers build and post the weekly schedule. */
export const canEditSchedule = (r: UserRole | undefined) => r === 'OWNER' || r === 'GENERAL_MANAGER';

/** You can change people ranked below you; an owner can change anyone. */
export const canManage = (actor: UserRole, target: UserRole) => actor === 'OWNER' || rank(actor) > rank(target);

/**
 * Roles to offer in a dropdown, highest first: those below your own (all of
 * them for an owner), plus the person's current role so it still shows.
 */
export const roleOptions = (actor: UserRole, current: UserRole) =>
  ROLES.filter((r) => r.value === current || actor === 'OWNER' || rank(actor) > r.rank);

/** Badge colors: managers in the brand color, staff neutral, customers muted. */
export function roleBadgeClass(r: UserRole): string {
  if (canManageSite(r)) return 'bg-[var(--accent-soft)] text-[var(--accent-strong)]';
  if (r === 'CUSTOMER') return 'bg-black/5 opacity-70';
  return 'bg-[var(--success-soft)] text-[var(--success)]';
}
