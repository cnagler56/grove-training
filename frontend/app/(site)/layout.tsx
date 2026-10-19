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
    <header className="border-b border-white/10">
      <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
        <Link href="/" className="text-lg font-semibold tracking-tight">
          🌿 Grove Salons <span className="font-normal opacity-60">Training</span>
        </Link>
        {user && (
          <div className="flex items-center gap-4 text-sm">
            <span className="hidden opacity-80 sm:inline">{user.firstName || user.email}</span>
            <button onClick={() => void logout()} className="btn-ghost">
              Sign out
            </button>
          </div>
        )}
      </div>

      {user && (
        <nav className="border-t border-white/10">
          <div className="mx-auto flex max-w-5xl items-center gap-1 overflow-x-auto px-2 text-sm">
            {tabs.map((tab) => {
              const active = tab.href === '/'
                ? pathname === '/' || pathname.startsWith('/videos')
                : pathname === tab.href;
              return (
                <Link
                  key={tab.href}
                  href={tab.href}
                  className={`whitespace-nowrap border-b-2 px-3 py-2.5 transition-colors ${
                    active
                      ? 'border-[var(--accent)] font-medium text-[var(--accent)]'
                      : 'border-transparent opacity-70 hover:opacity-100'
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
