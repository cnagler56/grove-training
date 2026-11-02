'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, type Todo, type TodoAssignOptions } from '@/src/lib/api';
import { formatDateTime } from '@/src/lib/format';
import { canManageSite, type UserRole } from '@/src/lib/roles';
import { useUser } from '@/src/lib/UserContext';
import DateField from '@/src/components/DateField';
import { useDialogs } from '@/src/components/Dialogs';
import TodoChecklist, { TodoDue } from '@/src/components/TodoChecklist';

const NEW_ITEM = 'new';
const GROUP_ROLES: UserRole[] = ['GENERAL_MANAGER', 'MANAGER', 'RECEPTIONIST'];
const GROUP_LABELS: Partial<Record<UserRole, string>> = {
  GENERAL_MANAGER: 'General managers',
  MANAGER: 'Managers',
  RECEPTIONIST: 'Receptionists',
};

/**
 * Your own checklist, plus, for managers and up, assigning to-dos and seeing
 * when they were done. Owners assign to general managers, managers, and
 * receptionists; general managers to managers and receptionists; managers to
 * receptionists. The server enforces this. Each group has its own saved to-dos.
 */
export default function TodosPage() {
  const { user } = useUser();
  const dialogs = useDialogs();
  const canAssign = canManageSite(user?.roles);
  const [mine, setMine] = useState<Todo[] | null>(null);
  const [assigned, setAssigned] = useState<Todo[] | null>(null);
  const [options, setOptions] = useState<TodoAssignOptions | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [groupRole, setGroupRole] = useState('');
  // '' = nothing picked yet; 'new' = typing one; otherwise a saved item's id.
  const [choice, setChoice] = useState('');
  const [newText, setNewText] = useState('');
  const [dueDate, setDueDate] = useState<string | null>(null);
  const [people, setPeople] = useState<number[]>([]);
  const [saving, setSaving] = useState(false);

  const loadAssigning = useCallback(() => {
    api.assignedTodos().then(setAssigned).catch((e) => setError(e.message));
    api.todoAssignOptions().then(setOptions).catch((e) => setError(e.message));
  }, []);

  useEffect(() => {
    api.myTodos().then(setMine).catch((e) => setError(e.message));
    if (canAssign) loadAssigning();
  }, [canAssign, loadAssigning]);

  // Someone who can only assign to one group doesn't need to choose it.
  const onlyGroup = options?.groups.length === 1;
  const group = onlyGroup ? options?.groups[0] : options?.groups.find((g) => g.role === groupRole);
  const savedItem = group?.items.find((i) => String(i.id) === choice);
  // With nothing saved yet, the only way to pick is to type.
  const typing = choice === NEW_ITEM || group?.items.length === 0;
  const showMine = (!!user && GROUP_ROLES.includes(user.roles)) || (mine?.length ?? 0) > 0;
  const assignedElsewhere = (assigned ?? []).filter((t) => !t.assigneeRole || !GROUP_ROLES.includes(t.assigneeRole));
  const assignedSections = [
    ...GROUP_ROLES.map((role) => ({
      label: GROUP_LABELS[role] ?? role,
      todos: (assigned ?? []).filter((t) => t.assigneeRole === role),
    })),
    { label: 'Others', todos: assignedElsewhere },
  ].filter((s) => s.todos.length > 0);

  function togglePerson(id: number) {
    setPeople(people.includes(id) ? people.filter((p) => p !== id) : [...people, id]);
  }

  // People and saved to-dos belong to a group, so a new group starts over.
  function chooseGroup(role: string) {
    setGroupRole(role);
    setChoice('');
    setNewText('');
    setPeople([]);
  }

  async function assign(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setNotice(null);
    const text = typing ? newText.trim() : savedItem?.text ?? '';
    if (!text) { setError('Pick or type a to-do'); return; }
    if (people.length === 0) { setError('Pick at least one person'); return; }
    setSaving(true);
    try {
      const created = await api.assignTodo(text, people, dueDate);
      setNotice(`Assigned "${text}" to ${created.map((t) => t.assigneeName).join(', ')}.`);
      setChoice('');
      setNewText('');
      setDueDate(null);
      loadAssigning();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not assign that');
    } finally {
      setSaving(false);
    }
  }

  async function removeSaved() {
    if (!savedItem) return;
    if (!(await dialogs.confirm({
      title: 'Remove from saved items?',
      message: `"${savedItem.text}" won't be in the dropdown anymore. Lists it's already on keep it.`,
      confirmLabel: 'Remove',
      danger: true,
    }))) return;
    setError(null);
    setNotice(null);
    try {
      await api.removeSavedTodoItem(savedItem.id);
      setChoice('');
      loadAssigning();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not remove that');
    }
  }

  async function takeBack(t: Todo) {
    if (!(await dialogs.confirm({
      title: 'Remove this to-do?',
      message: `"${t.text}" will come off ${t.assigneeName}'s list and off this one.`,
      confirmLabel: 'Remove',
      danger: true,
    }))) return;
    setError(null);
    setNotice(null);
    try {
      await api.removeTodo(t.id);
      loadAssigning();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not remove that');
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">To-do</h1>
        {showMine && <p className="mt-1 text-sm opacity-70">Check things off as you finish them. Whoever assigned it sees when it was done.</p>}
      </div>

      {error && <p className="text-sm text-[var(--danger)]">{error}</p>}
      {notice && <p className="text-sm font-semibold text-[var(--success)]">{notice}</p>}

      {showMine && (
        <section className="card p-5">
          <h2 className="text-lg font-extrabold">My list</h2>
          {mine === null ? (
            <p className="mt-2 text-sm opacity-60">Loading…</p>
          ) : mine.length === 0 ? (
            <p className="mt-2 text-sm opacity-80">Nothing on your list right now.</p>
          ) : (
            <div className="mt-1">
              <TodoChecklist todos={mine} onChange={(u) => setMine(mine.map((t) => (t.id === u.id ? u : t)))} />
            </div>
          )}
        </section>
      )}

      {canAssign && (
        <form onSubmit={assign} className="card space-y-4 p-5">
          <h2 className="text-lg font-extrabold">Assign a to-do</h2>
          {options === null ? (
            <p className="text-sm opacity-60">Loading…</p>
          ) : (
            <>
              {!onlyGroup && (
                <label className="block text-sm">
                  <span className="mb-1 block font-semibold opacity-80">Assign to</span>
                  <select className="input cursor-pointer" value={groupRole} onChange={(e) => chooseGroup(e.target.value)}>
                    <option value="">Choose a group</option>
                    {options.groups.map((g) => <option key={g.role} value={g.role}>{GROUP_LABELS[g.role] ?? g.role}</option>)}
                  </select>
                </label>
              )}

              {group && (
                <>
                  <div className="space-y-2">
                    {group.items.length > 0 && (
                      <label className="block text-sm">
                        <span className="mb-1 block font-semibold opacity-80">To-do</span>
                        <select className="input cursor-pointer" value={choice} onChange={(e) => setChoice(e.target.value)}>
                          <option value="">Choose a saved to-do</option>
                          {group.items.map((i) => <option key={i.id} value={i.id}>{i.text}</option>)}
                          <option value={NEW_ITEM}>Type a new one</option>
                        </select>
                      </label>
                    )}
                    {typing && (
                      <label className="block text-sm">
                        <span className="mb-1 block font-semibold opacity-80">New to-do</span>
                        <input className="input" value={newText} onChange={(e) => setNewText(e.target.value)} maxLength={200}
                          placeholder="Restock the retail shelves" />
                        <span className="mt-1 block text-xs opacity-60">It&apos;s saved so you can pick it from this group&apos;s list next time.</span>
                      </label>
                    )}
                    {savedItem && (
                      <button type="button" className="text-sm font-bold text-[var(--danger)] hover:underline" onClick={() => void removeSaved()}>
                        Remove from saved to-dos
                      </button>
                    )}
                  </div>

                  <div className="sm:max-w-xs">
                    <DateField label="Due date (optional)" value={dueDate} onChange={setDueDate} />
                  </div>

                  <fieldset className="text-sm">
                    <legend className="mb-1 font-semibold opacity-80">{GROUP_LABELS[group.role] ?? group.role}</legend>
                    {group.people.length === 0 ? (
                      <p className="opacity-60">There&apos;s no one in this group yet.</p>
                    ) : (
                      <div className="grid gap-x-4 sm:grid-cols-2">
                        {group.people.map((p) => (
                          <label key={p.userId} className="flex cursor-pointer items-center gap-2 py-1.5">
                            <input type="checkbox" className="h-5 w-5 shrink-0 accent-[var(--accent)]"
                              checked={people.includes(p.userId)} onChange={() => togglePerson(p.userId)} />
                            <span className="min-w-0 break-words font-semibold">{p.name}</span>
                          </label>
                        ))}
                      </div>
                    )}
                  </fieldset>

                  <button type="submit" className="btn" disabled={saving}>{saving ? 'Assigning…' : 'Assign'}</button>
                </>
              )}
            </>
          )}
        </form>
      )}

      {canAssign && (
        <section className="card p-5">
          <h2 className="text-lg font-extrabold">Assigned by me</h2>
          {assigned === null ? (
            <p className="mt-2 text-sm opacity-60">Loading…</p>
          ) : assigned.length === 0 ? (
            <p className="mt-2 text-sm opacity-80">You haven&apos;t assigned anything yet.</p>
          ) : (
            <div className="mt-3 space-y-5">
              {assignedSections.map((section) => (
                <div key={section.label}>
                  <h3 className="text-sm font-extrabold opacity-80">{section.label}</h3>
                  <ul className="divide-y divide-[var(--border)] text-sm">
                    {section.todos.map((t) => (
                      <li key={t.id} className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 py-3">
                        <div className="min-w-0 flex-1 basis-56">
                          <p className="break-words font-semibold">{t.text}</p>
                          <TodoDue todo={t} />
                          <p className="text-xs opacity-60">For {t.assigneeName}, assigned {formatDateTime(t.assignedAt)}</p>
                          {t.completedAt ? (
                            <p className="mt-0.5 text-xs font-semibold text-[var(--success)]">Done {formatDateTime(t.completedAt)} by {t.assigneeName}</p>
                          ) : (
                            <p className="mt-0.5 text-xs font-semibold opacity-70">Not done yet</p>
                          )}
                        </div>
                        <button className="text-sm font-bold text-[var(--danger)] hover:underline" onClick={() => void takeBack(t)}>
                          Remove
                        </button>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          )}
        </section>
      )}
    </div>
  );
}
