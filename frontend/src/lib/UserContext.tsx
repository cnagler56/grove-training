'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react';
import { api, type Credentials, type MyPermissions, type User } from './api';
import { confirmLeave, leavingNow } from './leaveGuard';

const NO_PERMISSIONS: MyPermissions = { viewSchedule: false, editSchedule: false, approveSchedule: false };

interface UserContextValue {
  user: User | null;
  /** What the owner's Settings let this person do with the schedule. */
  perms: MyPermissions;
  loading: boolean;
  login: (c: Credentials) => Promise<void>;
  register: (c: Credentials) => Promise<void>;
  logout: () => Promise<void>;
}

const UserContext = createContext<UserContextValue | undefined>(undefined);

/**
 * Holds the signed-in user (from the session cookie) and their schedule
 * permissions. A 401 from /me just means "logged out" and leaves user null.
 */
export function UserProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [perms, setPerms] = useState<MyPermissions>(NO_PERMISSIONS);
  const [loading, setLoading] = useState(true);

  /** Set the user and load what they're allowed to do. */
  const signedIn = useCallback(async (u: User) => {
    const p = await api.myPermissions().catch(() => NO_PERMISSIONS);
    setUser(u);
    setPerms(p);
  }, []);

  useEffect(() => {
    const check = () => api.getMe()
      .then(signedIn)
      .catch(() => { setUser(null); setPerms(NO_PERMISSIONS); })
      .finally(() => setLoading(false));
    void check();
    // Sign-in is shared by every tab. If someone signs in as a different person
    // (or out) in another tab, or the owner changes permissions, re-check when
    // this tab comes back into view so it doesn't show stale pages.
    const onVisible = () => { if (document.visibilityState === 'visible') void check(); };
    document.addEventListener('visibilitychange', onVisible);
    window.addEventListener('focus', onVisible);
    return () => {
      document.removeEventListener('visibilitychange', onVisible);
      window.removeEventListener('focus', onVisible);
    };
  }, [signedIn]);

  const login = useCallback(async (c: Credentials) => {
    await signedIn(await api.login(c));
  }, [signedIn]);

  const register = useCallback(async (c: Credentials) => {
    await signedIn(await api.register(c));
  }, [signedIn]);

  // Sign out, then land on the public home page. Asks first (our dialog) if the
  // page has unsaved work. User state is deliberately not cleared first: in the
  // staff area that would trigger the redirect to /signin.
  const logout = useCallback(async () => {
    if (!(await confirmLeave())) return;
    leavingNow();
    await api.logout();
    window.location.assign('/');
  }, []);

  return (
    <UserContext.Provider value={{ user, perms, loading, login, register, logout }}>
      {children}
    </UserContext.Provider>
  );
}

export function useUser(): UserContextValue {
  const ctx = useContext(UserContext);
  if (!ctx) throw new Error('useUser must be used within a UserProvider');
  return ctx;
}
