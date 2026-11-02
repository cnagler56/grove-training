'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, type TimeOffQueue, type TimeOffRequest } from '@/src/lib/api';
import { onSchedule } from '@/src/lib/roles';
import { longDayLabel, timeOffRange } from '@/src/lib/schedule';
import { useUser } from '@/src/lib/UserContext';
import { useDialogs } from '@/src/components/Dialogs';
import DateField from '@/src/components/DateField';

const STATUS: Record<TimeOffRequest['status'], [string, string]> = {
  PENDING: ['Waiting for an answer', 'bg-amber-100 text-[var(--warn)]'],
  APPROVED: ['Approved', 'bg-[var(--success-soft)] text-[var(--success)]'],
  SENT_BACK: ['Sent back', 'bg-red-50 text-[var(--danger)]'],
};

const today = () => new Date().toLocaleDateString('en-CA');

/**
 * Employees ask for days off and see what happened to their requests. People
 * whose role can approve time off (owner's Settings) answer requests here too.
 */
export default function TimeOffPage() {
  const { user, perms } = useUser();
  const dialogs = useDialogs();
  const asks = onSchedule(user?.roles);
  const approves = perms.approveTimeOff;

  const [mine, setMine] = useState<TimeOffRequest[] | null>(null);
  const [queue, setQueue] = useState<TimeOffQueue | null>(null);
  const [start, setStart] = useState<string | null>(null);
  const [end, setEnd] = useState<string | null>(null);
  const [note, setNote] = useState('');
  // Bumped after sending, to clear the typed dates.
  const [formKey, setFormKey] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(() => {
    if (asks) api.myTimeOff().then(setMine).catch((e) => setError(e.message));
    if (approves) api.timeOffQueue().then(setQueue).catch((e) => setError(e.message));
  }, [asks, approves]);
  useEffect(load, [load]);

  async function run(action: () => Promise<unknown>, done: string) {
    setError(null);
    setNotice(null);
    setBusy(true);
    try {
      await action();
      setNotice(done);
      load();
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong');
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!start) return;
    const last = end ?? start;
    const sent = await run(() => api.requestTimeOff({ startDate: start, endDate: last, note: note.trim() || null }),
      'Sent. You\'ll see the answer here.');
    if (sent) {
      setStart(null);
      setEnd(null);
      setNote('');
      setFormKey(formKey + 1);
    }
  }

  async function sendBack(r: TimeOffRequest, undoing: boolean) {
    const message = await dialogs.prompt({
      title: undoing ? `Undo ${r.name}'s approved time off?` : `Send back ${r.name}'s request?`,
      message: `${timeOffRange(r.startDate, r.endDate)}. They'll see your message${undoing ? ', and these days come off the schedule as RO' : ''}.`,
      placeholder: 'Your message to them',
      multiline: true,
      confirmLabel: undoing ? 'Undo approval' : 'Send back',
    });
    if (message) void run(() => api.sendBackTimeOff(r.id, message), `Sent back to ${r.name}.`);
  }

  async function cancelApproved(r: TimeOffRequest) {
    if (!(await dialogs.confirm({
      title: 'Cancel your approved time off?',
      message: `${timeOffRange(r.startDate, r.endDate)}. These days come off the schedule as RO, and whoever approves time off will be told.`,
      confirmLabel: 'Cancel it',
      cancelLabel: 'Keep it',
      danger: true,
    }))) return;
    void run(() => api.cancelTimeOff(r.id), 'Approved time off cancelled. Those days came off the schedule.');
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Time off</h1>
        <p className="mt-1 text-sm opacity-70">
          {asks ? 'Ask for days off. Once approved, they show as RO on the schedule automatically.' : 'Answer your team\'s requests. Approved days show as RO on the schedule automatically.'}
        </p>
      </div>

      {error && <p className="text-sm text-[var(--danger)]">{error}</p>}
      {notice && <p className="text-sm font-semibold text-[var(--success)]">{notice}</p>}

      {approves && (
        <section className="card p-5">
          <h2 className="text-lg font-extrabold">Waiting for your answer</h2>
          {!queue ? (
            <p className="mt-2 text-sm opacity-60">Loading…</p>
          ) : queue.waiting.length === 0 ? (
            <p className="mt-2 text-sm opacity-70">Nothing waiting right now.</p>
          ) : (
            <ul className="mt-2 divide-y divide-[var(--border)]">
              {queue.waiting.map((r) => (
                <li key={r.id} className="flex flex-wrap items-center justify-between gap-3 py-3 text-sm">
                  <div className="min-w-0">
                    <div><b>{r.name}</b> · {timeOffRange(r.startDate, r.endDate)}</div>
                    {r.note && <div className="mt-0.5 whitespace-pre-line opacity-80">{r.note}</div>}
                    {r.createdAt && <div className="mt-0.5 text-xs opacity-60">Asked {longDayLabel(r.createdAt.slice(0, 10))}</div>}
                  </div>
                  <div className="flex shrink-0 gap-2">
                    <button className="btn" disabled={busy}
                      onClick={() => void run(() => api.approveTimeOff(r.id), `Approved ${r.name}'s time off.`)}>Approve</button>
                    <button className="btn-ghost" disabled={busy} onClick={() => void sendBack(r, false)}>Send back</button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      {asks && (
        <form onSubmit={submit} className="card space-y-3 p-5">
          <h2 className="text-lg font-extrabold">Ask for time off</h2>
          <div key={formKey} className="grid gap-3 sm:grid-cols-2">
            <DateField label="First day off" value={start} onChange={setStart} />
            <DateField label="Last day off" value={end} onChange={setEnd} hint="Leave blank for just one day" />
          </div>
          <label className="block text-sm">
            <span className="mb-1 block font-semibold opacity-80">Note (optional)</span>
            <textarea className="input min-h-16" value={note} maxLength={500} onChange={(e) => setNote(e.target.value)}
              placeholder="Anything they should know" />
          </label>
          <button type="submit" className="btn" disabled={busy || !start}>{busy ? 'Sending…' : 'Send request'}</button>
        </form>
      )}

      {asks && (
        <section className="card p-5">
          <h2 className="text-lg font-extrabold">Your requests</h2>
          {!mine ? (
            <p className="mt-2 text-sm opacity-60">Loading…</p>
          ) : mine.length === 0 ? (
            <p className="mt-2 text-sm opacity-70">You haven&apos;t asked for any time off yet.</p>
          ) : (
            <ul className="mt-2 divide-y divide-[var(--border)]">
              {mine.map((r) => (
                <li key={r.id} className="flex flex-wrap items-start justify-between gap-3 py-3 text-sm">
                  <div className="min-w-0">
                    <div className="font-bold">{timeOffRange(r.startDate, r.endDate)}</div>
                    {r.note && <div className="mt-0.5 whitespace-pre-line opacity-70">{r.note}</div>}
                    {r.responseNote && (
                      <div className="mt-1 whitespace-pre-line rounded-lg bg-[var(--surface-2)] px-3 py-2">
                        <b>{r.decidedByName ?? 'Your manager'}:</b> {r.responseNote}
                      </div>
                    )}
                  </div>
                  <div className="flex shrink-0 items-center gap-3">
                    <span className={`rounded-full px-3 py-1 text-xs font-bold ${STATUS[r.status][1]}`}>{STATUS[r.status][0]}</span>
                    {r.status === 'PENDING' && (
                      <button className="text-sm font-semibold text-[var(--danger)] hover:underline" disabled={busy}
                        onClick={() => void run(() => api.cancelTimeOff(r.id), 'Request cancelled.')}>Cancel</button>
                    )}
                    {r.status === 'APPROVED' && r.startDate >= today() && (
                      <button className="text-sm font-semibold text-[var(--danger)] hover:underline" disabled={busy}
                        onClick={() => void cancelApproved(r)}>Cancel</button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      {approves && queue && queue.approved.length > 0 && (
        <section className="card p-5">
          <h2 className="text-lg font-extrabold">Approved, coming up</h2>
          <ul className="mt-2 divide-y divide-[var(--border)]">
            {queue.approved.map((r) => (
              <li key={r.id} className="flex flex-wrap items-center justify-between gap-3 py-2 text-sm">
                <span><b>{r.name}</b> · {timeOffRange(r.startDate, r.endDate)}</span>
                <button className="btn-ghost" disabled={busy} onClick={() => void sendBack(r, true)}>Undo</button>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
