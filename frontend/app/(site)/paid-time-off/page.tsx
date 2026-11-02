'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, type VacationPayQueue, type VacationPayRequest } from '@/src/lib/api';
import { onSchedule } from '@/src/lib/roles';
import { longDayLabel } from '@/src/lib/schedule';
import { useUser } from '@/src/lib/UserContext';
import { useDialogs } from '@/src/components/Dialogs';

const STATUS: Record<VacationPayRequest['status'], [string, string]> = {
  PENDING: ['Waiting for an answer', 'bg-amber-100 text-[var(--warn)]'],
  APPROVED: ['Approved', 'bg-[var(--success-soft)] text-[var(--success)]'],
  SENT_BACK: ['Sent back', 'bg-red-50 text-[var(--danger)]'],
};

const hoursLabel = (h: number) => `${h} ${h === 1 ? 'hour' : 'hours'}`;
const today = () => new Date().toLocaleDateString('en-CA');

/**
 * Employees ask for paid time off hours on a check date and see what happened
 * to their requests. Owners answer them here.
 */
export default function PaidTimeOffPage() {
  const { user } = useUser();
  const dialogs = useDialogs();
  const asks = onSchedule(user?.roles);
  const answers = user?.roles === 'OWNER';

  const [mine, setMine] = useState<VacationPayRequest[] | null>(null);
  const [queue, setQueue] = useState<VacationPayQueue | null>(null);
  const [checkDates, setCheckDates] = useState<string[]>([]);
  const [hours, setHours] = useState('');
  const [checkDate, setCheckDate] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(() => {
    if (asks) api.myVacationPay().then(setMine).catch((e) => setError(e.message));
    if (answers) api.vacationPayQueue().then(setQueue).catch((e) => setError(e.message));
  }, [asks, answers]);
  useEffect(load, [load]);

  useEffect(() => {
    if (asks) api.vacationPayCheckDates().then(setCheckDates).catch((e) => setError(e.message));
  }, [asks]);

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
    const sent = await run(() => api.requestVacationPay({ hours: Number(hours), checkDate }), 'Sent. You\'ll see the answer here.');
    if (sent) {
      setHours('');
      setCheckDate('');
    }
  }

  async function sendBack(r: VacationPayRequest, undoing: boolean) {
    const message = await dialogs.prompt({
      title: undoing ? `Undo ${r.name}'s approved paid time off?` : `Send back ${r.name}'s request?`,
      message: `${hoursLabel(r.hours)} on the ${longDayLabel(r.checkDate)} check. They'll see your message.`,
      placeholder: 'Your message to them',
      multiline: true,
      confirmLabel: undoing ? 'Undo approval' : 'Send back',
    });
    if (message) void run(() => api.sendBackVacationPay(r.id, message), `Sent back to ${r.name}.`);
  }

  async function cancelApproved(r: VacationPayRequest) {
    if (!(await dialogs.confirm({
      title: 'Cancel your approved paid time off?',
      message: `${hoursLabel(r.hours)} on the ${longDayLabel(r.checkDate)} check. The owners will be told.`,
      confirmLabel: 'Cancel it',
      cancelLabel: 'Keep it',
      danger: true,
    }))) return;
    void run(() => api.cancelVacationPay(r.id), 'Approved paid time off cancelled. The owners have been told.');
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Paid Time Off</h1>
        <p className="mt-1 text-sm opacity-70">
          {asks ? 'Ask for paid time off hours on an upcoming check.' : 'Answer your team\'s paid time off requests.'}
        </p>
      </div>

      {error && <p className="text-sm text-[var(--danger)]">{error}</p>}
      {notice && <p className="text-sm font-semibold text-[var(--success)]">{notice}</p>}

      {answers && (
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
                    <div><b>{r.name}</b> · {hoursLabel(r.hours)}</div>
                    <div className="mt-0.5 opacity-80">Check date {longDayLabel(r.checkDate)}</div>
                    {r.createdAt && <div className="mt-0.5 text-xs opacity-60">Asked {longDayLabel(r.createdAt.slice(0, 10))}</div>}
                  </div>
                  <div className="flex shrink-0 gap-2">
                    <button className="btn" disabled={busy}
                      onClick={() => void run(() => api.approveVacationPay(r.id), `Approved ${r.name}'s paid time off.`)}>Approve</button>
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
          <h2 className="text-lg font-extrabold">Ask for paid time off</h2>
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block text-sm">
              <span className="mb-1 block font-semibold opacity-80">Number of hours</span>
              <input className="input" type="number" inputMode="decimal" min="0.01" max="400" step="any" required
                value={hours} onChange={(e) => setHours(e.target.value)} />
            </label>
            <label className="block text-sm">
              <span className="mb-1 block font-semibold opacity-80">Check date</span>
              <select className="input" required value={checkDate} onChange={(e) => setCheckDate(e.target.value)}>
                <option value="">Pick a check date</option>
                {checkDates.map((d) => <option key={d} value={d}>{longDayLabel(d)}, {d.slice(0, 4)}</option>)}
              </select>
            </label>
          </div>
          <button type="submit" className="btn" disabled={busy || !hours || !checkDate}>{busy ? 'Sending…' : 'Send request'}</button>
        </form>
      )}

      {asks && (
        <section className="card p-5">
          <h2 className="text-lg font-extrabold">Your requests</h2>
          {!mine ? (
            <p className="mt-2 text-sm opacity-60">Loading…</p>
          ) : mine.length === 0 ? (
            <p className="mt-2 text-sm opacity-70">You haven&apos;t asked for any paid time off yet.</p>
          ) : (
            <ul className="mt-2 divide-y divide-[var(--border)]">
              {mine.map((r) => (
                <li key={r.id} className="flex flex-wrap items-start justify-between gap-3 py-3 text-sm">
                  <div className="min-w-0">
                    <div className="font-bold">{hoursLabel(r.hours)}</div>
                    <div className="mt-0.5 opacity-70">Check date {longDayLabel(r.checkDate)}</div>
                    {r.responseNote && (
                      <div className="mt-1 whitespace-pre-line rounded-lg bg-[var(--surface-2)] px-3 py-2">
                        <b>{r.decidedByName ?? 'The owner'}:</b> {r.responseNote}
                      </div>
                    )}
                  </div>
                  <div className="flex shrink-0 items-center gap-3">
                    <span className={`rounded-full px-3 py-1 text-xs font-bold ${STATUS[r.status][1]}`}>{STATUS[r.status][0]}</span>
                    {r.status === 'PENDING' && (
                      <button className="text-sm font-semibold text-[var(--danger)] hover:underline" disabled={busy}
                        onClick={() => void run(() => api.cancelVacationPay(r.id), 'Request cancelled.')}>Cancel</button>
                    )}
                    {r.status === 'APPROVED' && r.checkDate > today() && (
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

      {answers && queue && queue.approved.length > 0 && (
        <section className="card p-5">
          <h2 className="text-lg font-extrabold">Approved, coming up</h2>
          <ul className="mt-2 divide-y divide-[var(--border)]">
            {queue.approved.map((r) => (
              <li key={r.id} className="flex flex-wrap items-center justify-between gap-3 py-2 text-sm">
                <span><b>{r.name}</b> · {hoursLabel(r.hours)} · {longDayLabel(r.checkDate)}</span>
                <button className="btn-ghost" disabled={busy} onClick={() => void sendBack(r, true)}>Undo</button>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
