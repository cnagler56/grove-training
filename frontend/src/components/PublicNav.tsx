'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { api } from '@/src/lib/api';
import { SALON } from '@/src/lib/salon';

/**
 * Navigation bar for the public pages (home, apply). Shows "Sign in", or
 * "My home" when someone is already signed in.
 */
export default function PublicNav() {
  const [signedIn, setSignedIn] = useState(false);
  useEffect(() => {
    api.getMe().then(() => setSignedIn(true)).catch(() => {});
  }, []);

  return (
    <nav className="sticky top-0 z-10 border-b border-[var(--border)] bg-[var(--surface)]/90 backdrop-blur">
      <div className="mx-auto flex max-w-5xl items-center justify-between gap-3 px-4 py-3">
        <Link href="/" className="text-lg font-extrabold tracking-tight">{SALON.name}</Link>
        <div className="flex items-center gap-1 text-sm sm:gap-2">
          <Link href="/#services" className="hidden rounded-full px-3 py-1.5 font-semibold opacity-75 hover:bg-[var(--surface-2)] hover:opacity-100 sm:inline">
            Services
          </Link>
          <Link href="/#visit" className="hidden rounded-full px-3 py-1.5 font-semibold opacity-75 hover:bg-[var(--surface-2)] hover:opacity-100 sm:inline">
            Visit
          </Link>
          <Link href="/apply" className="rounded-full px-3 py-1.5 font-semibold opacity-75 hover:bg-[var(--surface-2)] hover:opacity-100">
            Join our team
          </Link>
          <Link href={signedIn ? '/home' : '/signin'} className="btn">
            {signedIn ? 'My home' : 'Sign in'}
          </Link>
        </div>
      </div>
    </nav>
  );
}
