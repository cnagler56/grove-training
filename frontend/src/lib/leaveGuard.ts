'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useDialogs } from '@/src/components/Dialogs';

/**
 * Warns before leaving a page with unsaved work, using the site's own dialog
 * for everything inside the site: tabs, links, Sign out, and buttons that call
 * confirmLeave(). Closing or reloading the browser tab can only show the
 * browser's built-in warning; no site can replace that one.
 */

/** The active page's "are you sure?" check, if it has unsaved work. */
let blocker: (() => Promise<boolean>) | null = null;
/** Set right before we navigate on purpose, so the browser warning doesn't also fire. */
let leavingOnPurpose = false;

/** Ask (with our dialog) whether it's OK to leave. True when nothing is unsaved. */
export async function confirmLeave(): Promise<boolean> {
  return blocker ? blocker() : true;
}

/** Call just before a full-page navigation the user already agreed to (e.g. sign out). */
export function leavingNow() {
  leavingOnPurpose = true;
}

export function useUnsavedChangesGuard(
  active: boolean,
  { title = 'Leave without saving?', message = 'You have unsaved changes on this page.', confirmLabel = 'Leave' } = {},
) {
  const dialogs = useDialogs();
  const router = useRouter();

  useEffect(() => {
    if (!active) return;
    const ask = () => dialogs.confirm({ title, message, confirmLabel, cancelLabel: 'Stay', danger: true });
    blocker = ask;

    // Closing/reloading the tab: only the browser's own warning is allowed here.
    const onUnload = (e: BeforeUnloadEvent) => { if (!leavingOnPurpose) e.preventDefault(); };

    // Links inside the site (tabs, "Back to…", etc.): ask with our dialog first.
    const onClick = (e: MouseEvent) => {
      if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
      const a = (e.target as Element | null)?.closest?.('a[href]') as HTMLAnchorElement | null;
      if (!a || a.target === '_blank' || a.hasAttribute('download')) return;
      const url = new URL(a.href, window.location.href);
      if (url.origin !== window.location.origin) return;
      if (url.pathname === window.location.pathname && url.search === window.location.search) return;
      e.preventDefault();
      e.stopPropagation();
      void ask().then((ok) => { if (ok) { blocker = null; router.push(url.pathname + url.search + url.hash); } });
    };

    window.addEventListener('beforeunload', onUnload);
    document.addEventListener('click', onClick, true);
    return () => {
      blocker = null;
      window.removeEventListener('beforeunload', onUnload);
      document.removeEventListener('click', onClick, true);
    };
  }, [active, router, dialogs, title, message, confirmLabel]);
}
