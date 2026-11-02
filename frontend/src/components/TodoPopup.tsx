'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { api, type Todo } from '@/src/lib/api';
import { hasTodos } from '@/src/lib/roles';
import { useUser } from '@/src/lib/UserContext';
import TodoChecklist from './TodoChecklist';

const SHOWN_KEY = 'grove_todo_popup_shown_for';
/** A phone keeps the app in memory, so coming back after this long counts as opening it again. */
const AWAY_MS = 30 * 60 * 1000;

const shownFor = () => {
  try { return sessionStorage.getItem(SHOWN_KEY); } catch { return null; }
};
const rememberShown = (userId: number | null) => {
  try {
    if (userId === null) sessionStorage.removeItem(SHOWN_KEY);
    else sessionStorage.setItem(SHOWN_KEY, String(userId));
  } catch { /* Private browsing: it may just show again on reload. */ }
};

/** Opens on top of whatever page loads when someone opens the app with to-dos still undone. */
export default function TodoPopup() {
  const { user, loading } = useUser();
  const [todos, setTodos] = useState<Todo[] | null>(null);
  const userId = hasTodos(user?.roles) ? user!.userId : null;

  useEffect(() => {
    if (loading) return;
    if (!user) { rememberShown(null); return; }
    if (userId === null) return;

    const show = () => {
      if (window.location.pathname.startsWith('/todos')) return; // Already looking at it.
      api.myTodos().then((list) => {
        const open = list.filter((t) => !t.completedAt);
        if (open.length > 0) setTodos(open);
      }).catch(() => {});
    };

    if (shownFor() !== String(userId)) {
      rememberShown(userId);
      show();
    }

    let awaySince: number | null = null;
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') {
        awaySince = Date.now();
      } else {
        if (awaySince !== null && Date.now() - awaySince >= AWAY_MS) show();
        awaySince = null;
      }
    };
    document.addEventListener('visibilitychange', onVisibility);
    return () => document.removeEventListener('visibilitychange', onVisibility);
  }, [user, userId, loading]);

  const isOpen = todos !== null;
  useEffect(() => {
    if (!isOpen) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setTodos(null); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [isOpen]);

  if (!todos) return null;
  const left = todos.filter((t) => !t.completedAt).length;

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-4 print:hidden">
      <div role="dialog" aria-modal="true" aria-labelledby="todo-popup-title" className="card flex max-h-full w-full max-w-md flex-col p-6">
        <h2 id="todo-popup-title" className="text-lg font-extrabold">Your to-do list</h2>
        <p className="mt-1 text-sm font-semibold opacity-80">
          {left === 0 ? 'All done. Thank you!' : `${left} left to do`}
        </p>
        <div className="mt-2 min-h-0 overflow-y-auto">
          <TodoChecklist todos={todos} onChange={(u) => setTodos(todos.map((t) => (t.id === u.id ? u : t)))} />
        </div>
        <div className="mt-4 flex items-center justify-between gap-2">
          <Link href="/todos" className="text-sm font-semibold hover:underline" onClick={() => setTodos(null)}>Open To-do page</Link>
          <button className="btn" onClick={() => setTodos(null)}>Close</button>
        </div>
      </div>
    </div>
  );
}
