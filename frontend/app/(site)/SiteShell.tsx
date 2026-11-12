'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { api } from '@/src/lib/api';
import { UserProvider, useUser } from '@/src/lib/UserContext';
import { canManageSite, hasTodos, onSchedule } from '@/src/lib/roles';
import { clearDelivered } from '@/src/lib/push';
import TodoPopup from '@/src/components/TodoPopup';

/** One page in the menu. `count` shows as a small number beside it (new applicants). */
type MenuItem = { href: string; label: string; count?: number };
/** A menu across the top. With one page it's a plain link; with more it opens a list. */
type MenuGroup = { label: string; items: MenuItem[] };

/** Manage videos, Payroll, and Time off only match exactly; they're prefixes of other pages. */
const EXACT = ['/admin', '/payroll', '/time-off'];
const isOn = (pathname: string, href: string) => (EXACT.includes(href) ? pathname === href : pathname.startsWith(href));

const pill = (active: boolean) => `whitespace-nowrap rounded-full px-3.5 py-1.5 font-semibold transition-colors ${
  active ? 'bg-[var(--accent-soft)] text-[var(--accent-strong)]' : 'opacity-70 hover:bg-[var(--surface-2)] hover:opacity-100'
}`;

const headerPill = (active: boolean) =>
  `whitespace-nowrap rounded-full px-3.5 py-1.5 font-semibold ${active ? 'bg-white/45' : 'bg-white/25 hover:bg-white/35'}`;

function Count({ n, label, tone = 'accent' }: { n: number; label: string; tone?: 'accent' | 'danger' }) {
  if (n <= 0) return null;
  return (
    <span className={`ml-1.5 rounded-full px-1.5 py-0.5 text-xs font-bold text-white ${tone === 'danger' ? 'bg-[var(--danger)]' : 'bg-[var(--accent)]'}`}
      aria-label={`${n} ${label}`}>
      {n}
    </span>
  );
}

/** Pages with wide tables and card grids get a wider layout (header included, so it lines up). */
const WIDE_PAGES = ['/payroll', '/my-week', '/admin/stats', '/schedule/edit', '/time-off/calendar'];
const pageWidth = (pathname: string) => (WIDE_PAGES.some((p) => pathname.startsWith(p)) ? 'max-w-7xl' : 'max-w-5xl');

const hasMyWeek = (r: string | undefined) => !!r && !['CUSTOMER', 'OWNER', 'RECEPTIONIST'].includes(r);

/** The Schedule tab: anyone the owner lets see, build, or approve the schedule. */
const seesSchedule = (p: { viewSchedule: boolean; editSchedule: boolean; approveSchedule: boolean }) =>
  p.viewSchedule || p.editSchedule || p.approveSchedule;

function Header() {
  const { user, perms, logout } = useUser();
  const pathname = usePathname();
  const role = user?.roles;
  const isOwner = role === 'OWNER';
  const isStaff = !!user && role !== 'CUSTOMER';
  const manages = canManageSite(role);

  // Owners see how many applications are still "New"; refreshed on every page change.
  const [newApplicants, setNewApplicants] = useState(0);
  useEffect(() => {
    if (!isOwner) return;
    api.newApplicationCount().then(setNewApplicants).catch(() => {});
  }, [isOwner, pathname]);

  // Unread notifications. Opening a page marks what pointed to it read, so this
  // runs on every page change, and again when the app comes back into view.
  const [unread, setUnread] = useState(0);
  useEffect(() => {
    if (!isStaff) return;
    const refresh = () => {
      api.notificationsSeen(window.location.pathname).then((r) => setUnread(r.unread)).catch(() => {});
      clearDelivered().catch(() => {});
    };
    refresh();
    const onVisible = () => { if (document.visibilityState === 'visible') refresh(); };
    // The Notifications page says when it's changed what's read.
    window.addEventListener('notifications-changed', refresh);
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      window.removeEventListener('notifications-changed', refresh);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [isStaff, pathname]);

  // The menus, each holding only the pages this person can open. Empty ones are dropped.
  const page = (show: boolean, href: string, label: string, count?: number): MenuItem[] => (show ? [{ href, label, count }] : []);
  const groups: MenuGroup[] = [
    { label: 'Home', items: page(true, '/home', 'Home') },
    { label: 'Schedule', items: [
      ...page(seesSchedule(perms), '/schedule', 'Schedule'),
      ...page(onSchedule(role) || perms.approveTimeOff, '/time-off', 'Time off'),
      ...page(perms.approveTimeOff, '/time-off/calendar', 'Time off calendar'),
      ...page(onSchedule(role) || isOwner, '/paid-time-off', 'Paid Time Off'),
    ] },
    { label: 'Training', items: [
      ...page(isStaff, '/videos', 'Videos'),
      ...page(isStaff, '/contests', 'Contests'),
      ...page(manages, '/admin', 'Manage videos'),
    ] },
    { label: 'Numbers', items: [
      ...page(hasMyWeek(role), '/my-week', 'My week'),
      ...page(manages, '/admin/stats', 'Leaderboards'),
    ] },
    { label: 'Payroll', items: [
      ...page(perms.viewPayroll, '/payroll', 'Payroll'),
      ...page(perms.payrollNotes, '/payroll-notes', 'Payroll notes'),
    ] },
    { label: 'People', items: [
      ...page(manages, '/admin/team', 'Team'),
      ...page(isOwner, '/admin/applicants', 'Applicants', newApplicants),
      ...page(manages, '/admin/models', 'Models'),
      ...page(manages, '/admin/contacts', 'Contacts'),
    ] },
    { label: 'Settings', items: page(isOwner, '/admin/settings', 'Settings') },
  ].filter((g) => g.items.length > 0);

  // Which menu is open on wide screens, and whether the phone menu is open.
  const [open, setOpen] = useState<string | null>(null);
  const [phoneMenu, setPhoneMenu] = useState(false);
  const closeAll = () => { setOpen(null); setPhoneMenu(false); };
  const navRef = useRef<HTMLElement>(null);
  useEffect(() => {
    if (!open && !phoneMenu) return;
    const onDown = (e: MouseEvent) => {
      if (navRef.current && !navRef.current.contains(e.target as Node)) closeAll();
    };
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') closeAll(); };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open, phoneMenu]);

  const showTodos = hasTodos(role);

  return (
    <header className="print:hidden" ref={navRef}>
      <div className="bg-gradient-to-r from-[var(--header-from)] to-[var(--header-to)] text-white">
        <div className={`mx-auto flex ${pageWidth(pathname)} items-center justify-between gap-2 px-4 py-4`}>
          <Link href={user ? '/home' : '/'} className="text-xl font-extrabold tracking-tight" onClick={closeAll}>
            Grove Salons
          </Link>
          {user && (
            <div className="flex items-center gap-2 text-sm">
              <span className="hidden font-semibold lg:inline">{user.firstName || user.email}</span>
              {isStaff && (
                <Link href="/notifications" className={headerPill(isOn(pathname, '/notifications'))} onClick={closeAll}>
                  Notifications
                  <Count n={unread} label="unread" tone="danger" />
                </Link>
              )}
              {/* Wide screens: everything in the header. Phones: the rest is under Menu. */}
              {showTodos && (
                <Link href="/todos" className={`hidden md:inline-block ${headerPill(isOn(pathname, '/todos'))}`}>To-do</Link>
              )}
              {isStaff && (
                <Link href="/account" className={`hidden md:inline-block ${headerPill(isOn(pathname, '/account'))}`}>My account</Link>
              )}
              <button onClick={() => void logout()} className={`hidden md:inline-block ${headerPill(false)}`}>Sign out</button>
              <button className={`md:hidden ${headerPill(phoneMenu)}`} aria-expanded={phoneMenu}
                onClick={() => setPhoneMenu(!phoneMenu)}>
                {phoneMenu ? 'Close' : 'Menu'}
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Wide screens: one row of menus. */}
      {user && (
        <nav className="hidden border-b border-[var(--border)] bg-[var(--surface)] md:block" aria-label="Pages">
          <div className={`mx-auto flex ${pageWidth(pathname)} flex-wrap items-center gap-1.5 px-3 py-2 text-sm`}>
            {groups.map((g) => {
              const active = g.items.some((i) => isOn(pathname, i.href));
              if (g.items.length === 1) {
                const only = g.items[0];
                return (
                  <Link key={g.label} href={only.href} className={pill(active)} onClick={closeAll}>
                    {only.label}
                    <Count n={only.count ?? 0} label="new" />
                  </Link>
                );
              }
              const isOpen = open === g.label;
              const count = g.items.reduce((n, i) => n + (i.count ?? 0), 0);
              return (
                <div key={g.label} className="relative">
                  <button className={pill(active || isOpen)} aria-haspopup="true" aria-expanded={isOpen}
                    onClick={() => setOpen(isOpen ? null : g.label)}>
                    {g.label}
                    <Count n={count} label="new" />
                  </button>
                  {isOpen && (
                    <div className="absolute left-0 top-full z-30 mt-1.5 min-w-48 rounded-xl border border-[var(--border)] bg-[var(--surface)] p-1.5 shadow-lg">
                      {g.items.map((i) => (
                        <Link key={i.href} href={i.href} onClick={closeAll}
                          className={`block whitespace-nowrap rounded-lg px-3 py-2 font-semibold ${
                            isOn(pathname, i.href) ? 'bg-[var(--accent-soft)] text-[var(--accent-strong)]' : 'hover:bg-[var(--surface-2)]'
                          }`}>
                          {i.label}
                          <Count n={i.count ?? 0} label="new" />
                        </Link>
                      ))}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </nav>
      )}

      {/* Phones: the same menus as one list under the Menu button. */}
      {user && phoneMenu && (
        <nav className="border-b border-[var(--border)] bg-[var(--surface)] px-3 py-2 text-sm md:hidden" aria-label="Pages">
          {groups.map((g) => (
            <div key={g.label} className="py-1">
              {g.items.length > 1 && (
                <div className="px-3 pb-0.5 pt-2 text-xs font-bold uppercase tracking-wide opacity-50">{g.label}</div>
              )}
              {g.items.map((i) => (
                <Link key={i.href} href={i.href} onClick={closeAll}
                  className={`block rounded-lg px-3 py-2.5 font-semibold ${
                    isOn(pathname, i.href) ? 'bg-[var(--accent-soft)] text-[var(--accent-strong)]' : ''
                  }`}>
                  {i.label}
                  <Count n={i.count ?? 0} label="new" />
                </Link>
              ))}
            </div>
          ))}
          <div className="mt-1 border-t border-[var(--border)] py-1">
            {showTodos && <Link href="/todos" onClick={closeAll} className="block rounded-lg px-3 py-2.5 font-semibold">To-do</Link>}
            {isStaff && <Link href="/account" onClick={closeAll} className="block rounded-lg px-3 py-2.5 font-semibold">My account</Link>}
            <button onClick={() => { closeAll(); void logout(); }} className="block w-full rounded-lg px-3 py-2.5 text-left font-semibold">
              Sign out
            </button>
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
  const needsStaff = pathname.startsWith('/videos') || pathname.startsWith('/account') || pathname.startsWith('/contests')
    || pathname.startsWith('/notifications');
  const needsMyWeek = pathname.startsWith('/my-week');
  const needsTimeOff = pathname.startsWith('/time-off');
  const needsTimeOffCalendar = pathname.startsWith('/time-off/calendar');
  const needsVacationPay = pathname.startsWith('/paid-time-off');
  const needsTodos = pathname.startsWith('/todos');
  const needsPayrollNotes = pathname.startsWith('/payroll-notes');
  const needsPayroll = pathname === '/payroll' || pathname.startsWith('/payroll/');
  // Schedule access follows the owner's Settings.
  const needsScheduleView = pathname.startsWith('/schedule');
  const needsScheduleEdit = pathname.startsWith('/schedule/edit');
  const blocked = !!user && (
    (needsAdmin && !canManageSite(user.roles))
    || (needsOwner && user.roles !== 'OWNER')
    || (needsStaff && user.roles === 'CUSTOMER')
    || (needsMyWeek && !hasMyWeek(user.roles))
    || (needsTimeOff && !onSchedule(user.roles) && !perms.approveTimeOff)
    || (needsTimeOffCalendar && !perms.approveTimeOff)
    || (needsVacationPay && !onSchedule(user.roles) && user.roles !== 'OWNER')
    || (needsTodos && !hasTodos(user.roles))
    || (needsPayrollNotes && !perms.payrollNotes)
    || (needsPayroll && !perms.viewPayroll)
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

/** Pages shown in a small window of their own, without the header, tabs, or pop-ups. */
const isBare = (pathname: string) => pathname.startsWith('/payroll/entry');

/** Pages that fill the screen, so they sit right under the menu bar instead of the usual top gap. */
const isTight = (pathname: string) => pathname.startsWith('/time-off/calendar');

function Main({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  if (isBare(pathname)) {
    return <main className="px-3 py-3"><Gate>{children}</Gate></main>;
  }
  return (
    <main className={`mx-auto ${pageWidth(pathname)} px-4 ${isTight(pathname) ? 'py-3' : 'py-8'} print:max-w-none print:p-0`}>
      <Gate>{children}</Gate>
    </main>
  );
}

export default function SiteShell({ children }: { children: React.ReactNode }) {
  const bare = isBare(usePathname());
  return (
    <UserProvider>
      {!bare && <Header />}
      <Main>{children}</Main>
      {!bare && <TodoPopup />}
    </UserProvider>
  );
}
