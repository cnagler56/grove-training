'use client';

import { useState } from 'react';
import { api, type Todo } from '@/src/lib/api';
import { formatDate, formatDateTime } from '@/src/lib/format';

/** "Due Nov 5", in red once an unfinished to-do is past it. Nothing without a due date. */
export function TodoDue({ todo }: { todo: Todo }) {
  if (!todo.dueDate) return null;
  const overdue = !todo.completedAt && todo.dueDate < new Date().toLocaleDateString('en-CA');
  return (
    <span className={`block text-xs font-semibold ${overdue ? 'text-[var(--danger)]' : 'opacity-70'}`}>
      {overdue ? 'Overdue, was due' : 'Due'} {formatDate(todo.dueDate, false)}
    </span>
  );
}

/** Someone's own to-dos with boxes to check off. Used on the To-do page and in the pop-up. */
export default function TodoChecklist({ todos, onChange }: { todos: Todo[]; onChange: (updated: Todo) => void }) {
  const [busy, setBusy] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function toggle(t: Todo) {
    setBusy(t.id);
    setError(null);
    try {
      onChange(await api.setTodoDone(t.id, !t.completedAt));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save that');
    } finally {
      setBusy(null);
    }
  }

  return (
    <div>
      {error && <p className="mb-2 text-sm text-[var(--danger)]">{error}</p>}
      <ul className="divide-y divide-[var(--border)]">
        {todos.map((t) => (
          <li key={t.id}>
            <label className="flex cursor-pointer items-start gap-3 py-3 text-sm">
              <input
                type="checkbox"
                className="mt-0.5 h-5 w-5 shrink-0 accent-[var(--accent)]"
                checked={!!t.completedAt}
                disabled={busy === t.id}
                onChange={() => void toggle(t)}
              />
              <span className="min-w-0">
                <span className={`block break-words font-semibold ${t.completedAt ? 'line-through opacity-60' : ''}`}>{t.text}</span>
                <TodoDue todo={t} />
                <span className="block text-xs opacity-60">
                  {t.completedAt
                    ? `Done ${formatDateTime(t.completedAt)}`
                    : `From ${t.assignedByName}, ${formatDateTime(t.assignedAt)}`}
                </span>
              </span>
            </label>
          </li>
        ))}
      </ul>
    </div>
  );
}
