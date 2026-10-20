'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { UserProvider, useUser } from '@/src/lib/UserContext';

const TABS = [{ href: '/', label: 'Videos' }];
const ADMIN_TABS = [
  { href: '/admin', label: 'Manage videos' },
  { href: '/admin/team', label: 'Team' },
];

function Header() {
  const { user, logout } = useUser();
  const pathname = usePathname();
  const tabs = user?.roles === 'ADMIN' ? [...TABS, ...ADMIN_TABS] : TABS;

  return (
    <header>
      <div className="bg-gradient-to-r from-[var(--header-from)] to-[var(--header-to)] text-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-4">
          <Link href="/" className="text-xl font-extrabold tracking-tight">
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
              const active = tab.href === '/'
                ? pathname === '/' || pathname.startsWith('/videos')
                : tab.href === '/admin'
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

  useEffect(() => {
    if (loading) return;
    if (!user && !isSignin) router.replace('/signin');
    else if (user && isSignin) router.replace('/');
    else if (user && needsAdmin && user.roles !== 'ADMIN') router.replace('/');
  }, [user, loading, isSignin, needsAdmin, router]);

  if (loading) return <p className="opacity-60">Loading…</p>;
  if (!user && !isSignin) return null;
  if (user && needsAdmin && user.roles !== 'ADMIN') return null;
  return <>{children}</>;
}

export default function SiteLayout({ children }: { children: React.ReactNode }) {
  return (
    <UserProvider>
      <Header />
      <main className="mx-auto max-w-5xl px-4 py-8">
        <Gate>{children}</Gate>
      </main>
    </UserProvider>
  );
}
