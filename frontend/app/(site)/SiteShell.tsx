'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { api } from '@/src/lib/api';
import { UserProvider, useUser } from '@/src/lib/UserContext';
import { canManageSite } from '@/src/lib/roles';

const TABS = [{ href: '/home', label: 'Home' }];
const STAFF_TABS = [{ href: '/videos', label: 'Videos' }];
const SCHEDULE_TABS = [{ href: '/schedule', label: 'Schedule' }];
/** Their own numbers and pay rate from Salon Ultimate. Not for owners (not in it) or receptionists. */
const MY_WEEK_TABS = [{ href: '/my-week', label: 'My week' }];
const ADMIN_TABS = [
  { href: '/admin/stats', label: 'Leaderboards' },
  { href: '/admin', label: 'Manage videos' },
  { href: '/admin/team', label: 'Team' },
];
const OWNER_TABS = [
  { href: '/admin/applicants', label: 'Applicants' },
  { href: '/admin/settings', label: 'Settings' },
];

/** Pages with wide tables and card grids get a wider layout (header included, so it lines up). */
const WIDE_PAGES = ['/my-week', '/admin/stats'];
const pageWidth = (pathname: string) => (WIDE_PAGES.some((p) => pathname.startsWith(p)) ? 'max-w-7xl' : 'max-w-5xl');

const hasMyWeek = (r: string | undefined) => !!r && !['CUSTOMER', 'OWNER', 'RECEPTIONIST'].includes(r);

/** The Schedule tab: anyone the owner lets see, build, or approve the schedule. */
const seesSchedule = (p: { viewSchedule: boolean; editSchedule: boolean; approveSchedule: boolean }) =>
  p.viewSchedule || p.editSchedule || p.approveSchedule;

function Header() {
  const { user, perms, logout } = useUser();
  const pathname = usePathname();
  const isOwner = user?.roles === 'OWNER';
  const tabs = [
    ...TABS,
    ...(user && user.roles !== 'CUSTOMER' ? STAFF_TABS : []),
    ...(seesSchedule(perms) ? SCHEDULE_TABS : []),
    ...(hasMyWeek(user?.roles) ? MY_WEEK_TABS : []),
    ...(canManageSite(user?.roles) ? ADMIN_TABS : []),
    ...(isOwner ? OWNER_TABS : []),
  ];

  // Owners see how many applications are still "New"; refreshed on every page change.
  const [newApplicants, setNewApplicants] = useState(0);
  useEffect(() => {
    if (!isOwner) return;
    api.newApplicationCount().then(setNewApplicants).catch(() => {});
  }, [isOwner, pathname]);

  return (
    <header className="print:hidden">
      <div className="bg-gradient-to-r from-[var(--header-from)] to-[var(--header-to)] text-white">
        <div className={`mx-auto flex ${pageWidth(pathname)} items-center justify-between px-4 py-4`}>
          <Link href={user ? '/home' : '/'} className="text-xl font-extrabold tracking-tight">
            Grove Salons
          </Link>
          {user && (
            <div className="flex items-center gap-3 text-sm">
              <span className="hidden font-semibold sm:inline">{user.firstName || user.email}</span>
              <button
                onClick={() => void logout()}
                className="rounded-full bg-white/25 px-3.5 py-1.5 font-semibold hover:bg-white/35"
              >
                Sign out
              </button>
            </div>
          )}
        </div>
      </div>

      {user && (
        <nav className="border-b border-[var(--border)] bg-[var(--surface)]">
          <div className={`mx-auto flex ${pageWidth(pathname)} items-center gap-1.5 overflow-x-auto px-3 py-2 text-sm`}>
            {tabs.map((tab) => {
              const active = tab.href === '/admin'
                  ? pathname === '/admin'
                  : pathname.startsWith(tab.href);
              return (
                <Link
                  key={tab.href}
                  href={tab.href}
                  className={`whitespace-nowrap rounded-full px-3.5 py-1.5 font-semibold transition-colors ${
                    active
                      ? 'bg-[var(--accent-soft)] text-[var(--accent-strong)]'
                      : 'opacity-70 hover:bg-[var(--surface-2)] hover:opacity-100'
                  }`}
                >
                  {tab.label}
                  {tab.href === '/admin/applicants' && isOwner && newApplicants > 0 && (
                    <span
                      className="ml-1.5 rounded-full bg-[var(--accent)] px-1.5 py-0.5 text-xs font-bold text-white"
                      aria-label={`${newApplicants} new`}
                    >
                      {newApplicants}
                    </span>
                  )}
                </Link>
              );
            })}
          </div>
        </nav>
      )}
    </header>
  );
}

/** Everything but /signin needs a signed-in user; /admin pages need a manager. */
function Gate({ children }: { children: React.ReactNode }) {
  const { user, perms, loading } = useUser();
  const pathname = usePathname();
  const router = useRouter();
  const isSignin = pathname === '/signin';
  const needsAdmin = pathname.startsWith('/admin');
  // Applicants, adding accounts, and Settings are for owners only.
  const needsOwner = pathname.startsWith('/admin/applicants') || pathname === '/admin/team/new'
    || pathname.startsWith('/admin/settings');
  const needsStaff = pathname.startsWith('/videos');
  const needsMyWeek = pathname.startsWith('/my-week');
  // Schedule access follows the owner's Settings.
  const needsScheduleView = pathname.startsWith('/schedule');
  const needsScheduleEdit = pathname.startsWith('/schedule/edit');
  const blocked = !!user && (
    (needsAdmin && !canManageSite(user.roles))
    || (needsOwner && user.roles !== 'OWNER')
    || (needsStaff && user.roles === 'CUSTOMER')
    || (needsMyWeek && !hasMyWeek(user.roles))
    || (needsScheduleView && !seesSchedule(perms))
    || (needsScheduleEdit && !perms.editSchedule && !perms.approveSchedule));

  useEffect(() => {
    if (loading) return;
    if (!user && !isSignin) router.replace('/signin');
    else if (user && isSignin) router.replace('/home');
    else if (blocked) router.replace('/home');
  }, [user, loading, isSignin, blocked, router]);

  if (loading) return <p className="opacity-60">Loading…</p>;
  if (!user && !isSignin) return null;
  if (blocked) return null;
  return <>{children}</>;
}

function Main({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  return (
    <main className={`mx-auto ${pageWidth(pathname)} px-4 py-8 print:max-w-none print:p-0`}>
      <Gate>{children}</Gate>
    </main>
  );
}

export default function SiteShell({ children }: { children: React.ReactNode }) {
  return (
    <UserProvider>
      <Header />
      <Main>{children}</Main>
    </UserProvider>
  );
}
