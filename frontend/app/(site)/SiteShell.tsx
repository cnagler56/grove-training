'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { api } from '@/src/lib/api';
import { UserProvider, useUser } from '@/src/lib/UserContext';
import { canManageSite } from '@/src/lib/roles';

const TABS = [{ href: '/videos', label: 'Videos' }];
const ADMIN_TABS = [
  { href: '/admin', label: 'Manage videos' },
  { href: '/admin/team', label: 'Team' },
];
const OWNER_TABS = [{ href: '/admin/applicants', label: 'Applicants' }];

function Header() {
  const { user, logout } = useUser();
  const pathname = usePathname();
  const isOwner = user?.roles === 'OWNER';
  const tabs = [
    ...TABS,
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
    <header>
      <div className="bg-gradient-to-r from-[var(--header-from)] to-[var(--header-to)] text-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-4">
          <Link href={user ? '/videos' : '/'} className="text-xl font-extrabold tracking-tight">
            ✂️ Grove Salons <span className="font-semibold opacity-85">Training</span>
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
          <div className="mx-auto flex max-w-5xl items-center gap-1.5 overflow-x-auto px-3 py-2 text-sm">
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
  const { user, loading } = useUser();
  const pathname = usePathname();
  const router = useRouter();
  const isSignin = pathname === '/signin';
  const needsAdmin = pathname.startsWith('/admin');
  // Applicants' personal details, and adding accounts, are for owners only.
  const needsOwner = pathname.startsWith('/admin/applicants') || pathname === '/admin/team/new';
  const blocked = !!user && ((needsAdmin && !canManageSite(user.roles)) || (needsOwner && user.roles !== 'OWNER'));

  useEffect(() => {
    if (loading) return;
    if (!user && !isSignin) router.replace('/signin');
    else if (user && isSignin) router.replace('/videos');
    else if (blocked) router.replace('/videos');
  }, [user, loading, isSignin, blocked, router]);

  if (loading) return <p className="opacity-60">Loading…</p>;
  if (!user && !isSignin) return null;
  if (blocked) return null;
  return <>{children}</>;
}

export default function SiteShell({ children }: { children: React.ReactNode }) {
  return (
    <UserProvider>
      <Header />
      <main className="mx-auto max-w-5xl px-4 py-8">
        <Gate>{children}</Gate>
      </main>
    </UserProvider>
  );
}
