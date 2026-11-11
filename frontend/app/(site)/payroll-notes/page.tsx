'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  api, type PayPeriods, type PayrollNote, type PayrollNoteKind, type Person,
} from '@/src/lib/api';
import { formatDateTime } from '@/src/lib/format';
import { NOTE_KIND as KIND, noteStatus, signedAmount } from '@/src/lib/payroll';
import { useDialogs } from '@/src/components/Dialogs';
import { useUnsavedChangesGuard } from '@/src/lib/leaveGuard';
import PayPeriodPicker, { workRange } from '@/src/components/PayPeriodPicker';

type Draft = { userId: string; kind: PayrollNoteKind; amount: string; note: string };
const EMPTY: Draft = { userId: '', kind: 'DEDUCTION', amount: '', note: '' };

/**
 * Deductions, additions, and comments for an employee on a pay period. They all
 * show up on the Payroll tab for that period. Who can use this is set in Settings.
 */
export default function PayrollNotesPage() {
  const dialogs = useDialogs();
  const [periods, setPeriods] = useState<PayPeriods | null>(null);
  const [checkDate, setCheckDate] = useState('');
  const [people, setPeople] = useState<Person[]>([]);
  const [notes, setNotes] = useState<PayrollNote[] | null>(null);
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const [editing, setEditing] = useState<number | null>(null);
  const [original, setOriginal] = useState<Draft>(EMPTY);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const dirty = draft.userId !== original.userId || draft.kind !== original.kind || draft.amount !== original.amount
    || draft.note.trim() !== original.note.trim();
  useUnsavedChangesGuard(dirty, { message: 'This payroll note isn\'t saved yet.' });

  useEffect(() => {
    api.payPeriods().then((p) => { setPeriods(p); setCheckDate(p.defaultCheckDate); }).catch((e) => setError(e.message));
    api.payrollEmployees().then(setPeople).catch((e) => setError(e.message));
  }, []);

  const load = useCallback(() => {
    if (!checkDate) return;
    api.payrollNotes(checkDate).then(setNotes).catch((e) => setError(e.message));
  }, [checkDate]);
  useEffect(load, [load]);

  const period = periods?.periods.find((p) => p.checkDate === checkDate);

  async function pickPeriod(d: string) {
    if (dirty && !(await dialogs.confirm({
      title: 'Switch pay period?', message: 'The note you\'re writing isn\'t saved yet.',
      confirmLabel: 'Switch', cancelLabel: 'Stay', danger: true,
    }))) return;
    setDraft(EMPTY); setOriginal(EMPTY);
    setEditing(null);
    setNotes(null);
    setNotice(null);
    setCheckDate(d);
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setNotice(null);
    setBusy(true);
    const body = {
      userId: Number(draft.userId),
      checkDate,
      kind: draft.kind,
      amount: draft.kind === 'COMMENT' ? null : Number(draft.amount),
      note: draft.note.trim() || null,
    };
    try {
      if (editing) await api.updatePayrollNote(editing, body);
      else await api.addPayrollNote(body);
      const who = people.find((p) => String(p.userId) === draft.userId)?.name ?? 'them';
      setNotice(editing ? `Saved the change for ${who}.` : `Added a ${KIND[draft.kind][0].toLowerCase()} for ${who}.`);
      setDraft(EMPTY); setOriginal(EMPTY);
      setEditing(null);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong');
    } finally {
      setBusy(false);
    }
  }

  function edit(n: PayrollNote) {
    setNotice(null);
    setError(null);
    setEditing(n.id);
    const d: Draft = { userId: String(n.userId), kind: n.kind, amount: n.amount == null ? '' : String(n.amount), note: n.note ?? '' };
    setDraft(d);
    setOriginal(d);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async function remove(n: PayrollNote) {
    if (!(await dialogs.confirm({
      title: `Delete this ${KIND[n.kind][0].toLowerCase()}?`,
      message: `${n.name}${signedAmount(n) ? ` ${signedAmount(n)}` : ''}${n.note ? `: ${n.note}` : ''}`,
      confirmLabel: 'Delete', danger: true,
    }))) return;
    setError(null);
    try {
      await api.deletePayrollNote(n.id);
      if (editing === n.id) { setEditing(null); setDraft(EMPTY); setOriginal(EMPTY); }
      setNotice('Deleted.');
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong');
    }
  }

  // An employee who has left still shows on their old notes.
  const editingSomeoneGone = editing != null && draft.userId !== '' && !people.some((p) => String(p.userId) === draft.userId);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Payroll notes</h1>
        <p className="mt-1 text-sm opacity-70">
          Deductions, additions, and comments for a pay period. They all show up on the Payroll tab.
        </p>
      </div>

      {periods && (
        <div className="max-w-xl">
          <PayPeriodPicker periods={periods.periods} value={checkDate} defaultCheckDate={periods.defaultCheckDate} onChange={(d) => void pickPeriod(d)} />
        </div>
      )}

      {error && <p className="text-sm text-[var(--danger)]">{error}</p>}
      {notice && <p className="text-sm font-semibold text-[var(--success)]">{notice}</p>}

      <form onSubmit={save} className="card space-y-3 p-5">
        <h2 className="text-lg font-extrabold">{editing ? 'Change this note' : 'Add a note'}</h2>
        <div className="grid gap-3 sm:grid-cols-3">
          <label className="block text-sm">
            <span className="mb-1 block font-semibold opacity-80">Employee</span>
            <select className="input" required value={draft.userId} onChange={(e) => setDraft({ ...draft, userId: e.target.value })}>
              <option value="">Pick someone</option>
              {editingSomeoneGone && <option value={draft.userId}>{notes?.find((n) => n.id === editing)?.name}</option>}
              {people.map((p) => <option key={p.userId} value={p.userId}>{p.name}</option>)}
            </select>
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-semibold opacity-80">Type</span>
            <select className="input" value={draft.kind} onChange={(e) => setDraft({ ...draft, kind: e.target.value as PayrollNoteKind })}>
              <option value="DEDUCTION">Deduction</option>
              <option value="ADDITION">Addition</option>
              <option value="COMMENT">Comment only</option>
            </select>
          </label>
          {draft.kind !== 'COMMENT' && (
            <label className="block text-sm">
              <span className="mb-1 block font-semibold opacity-80">Amount ($)</span>
              <input className="input" type="number" inputMode="decimal" min="0.01" step="0.01" required
                value={draft.amount} onChange={(e) => setDraft({ ...draft, amount: e.target.value })} />
            </label>
          )}
        </div>
        <label className="block text-sm">
          <span className="mb-1 block font-semibold opacity-80">
            {draft.kind === 'COMMENT' ? 'Comment' : 'What it\'s for (optional)'}
          </span>
          <textarea className="input min-h-20" maxLength={500} required={draft.kind === 'COMMENT'}
            value={draft.note} onChange={(e) => setDraft({ ...draft, note: e.target.value })} />
        </label>
        <div className="flex flex-wrap items-center gap-3">
          <button type="submit" className="btn" disabled={busy || !checkDate}>
            {busy ? 'Saving…' : editing ? 'Save changes' : 'Add note'}
          </button>
          {(editing || dirty) && (
            <button type="button" className="btn-ghost" disabled={busy} onClick={() => { setEditing(null); setDraft(EMPTY); setOriginal(EMPTY); }}>
              {editing ? 'Stop editing' : 'Clear'}
            </button>
          )}
          {period && <span className="text-sm opacity-60">For the {workRange(period)} pay period</span>}
        </div>
      </form>

      <section className="card p-5">
        <h2 className="text-lg font-extrabold">Notes this pay period</h2>
        {!notes ? (
          <p className="mt-2 text-sm opacity-60">Loading…</p>
        ) : notes.length === 0 ? (
          <p className="mt-2 text-sm opacity-70">Nothing added for this pay period yet.</p>
        ) : (
          <ul className="mt-2 divide-y divide-[var(--border)]">
            {notes.map((n) => (
              <li key={n.id} className={`flex flex-wrap items-start justify-between gap-3 py-3 text-sm ${editing === n.id ? 'bg-[var(--accent-soft)] -mx-2 px-2 rounded-lg' : ''}`}>
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <b>{n.name}</b>
                    <span className={`rounded-full px-2.5 py-0.5 text-xs font-bold ${KIND[n.kind][1]}`}>{KIND[n.kind][0]}</span>
                    {signedAmount(n) && <b>{signedAmount(n)}</b>}
                  </div>
                  {n.note && <div className="mt-1 whitespace-pre-line">{n.note}</div>}
                  <div className="mt-1 text-xs opacity-60">
                    Added by {n.createdByName ?? 'someone'}{n.createdAt ? `, ${formatDateTime(n.createdAt)}` : ''}
                    {n.updatedAt && ` · Changed by ${n.updatedByName ?? 'someone'}, ${formatDateTime(n.updatedAt)}`}
                  </div>
                  <div className={`mt-1 text-xs font-semibold ${noteStatus(n)[1]}`}>{noteStatus(n)[0]}</div>
                </div>
                {n.status !== 'FINISHED' && (
                  <div className="flex shrink-0 gap-3">
                    <button className="text-sm font-semibold hover:underline" onClick={() => edit(n)}>Edit</button>
                    <button className="text-sm font-semibold text-[var(--danger)] hover:underline" onClick={() => void remove(n)}>Delete</button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
