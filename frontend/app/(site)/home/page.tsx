'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { api, formatDuration, type Celebrations, type PendingWeek, type ShiftView, type VideoSummary } from '@/src/lib/api';
import { TodayCelebrations, UpcomingCelebrations } from '@/src/components/Celebrations';
import { isRequestedOff, longDayLabel, shiftLabel, todayIso, weekRangeLabel } from '@/src/lib/schedule';
import { canManageSite, roleLabel } from '@/src/lib/roles';
import { SALON } from '@/src/lib/salon';
import { useUser } from '@/src/lib/UserContext';
import VideoCard from '@/src/components/VideoCard';

const DAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

function greeting() {
  const h = new Date().getHours();
  return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
}

/** Where staff land after signing in. */
export default function StaffHomePage() {
  const { user, perms } = useUser();
  const isStaff = user?.roles !== 'CUSTOMER';
  const isManager = canManageSite(user?.roles);
  const isOwner = user?.roles === 'OWNER';
  const seesSchedule = perms.viewSchedule || perms.editSchedule || perms.approveSchedule;

  const [videos, setVideos] = useState<VideoSummary[] | null>(null);
  const [newApplicants, setNewApplicants] = useState<number | null>(null);
  const [myShifts, setMyShifts] = useState<ShiftView[] | null>(null);
  const [toApprove, setToApprove] = useState<PendingWeek[]>([]);
  const [celebrations, setCelebrations] = useState<Celebrations | null>(null);

  useEffect(() => {
    if (isStaff) api.listVideos().then(setVideos).catch(() => setVideos([]));
    if (isStaff) api.mySchedule().then(setMyShifts).catch(() => setMyShifts([]));
    if (isOwner) api.newApplicationCount().then(setNewApplicants).catch(() => {});
  }, [isStaff, isOwner]);

  useEffect(() => {
    if (perms.approveSchedule) api.waitingSchedules().then(setToApprove).catch(() => {});
  }, [perms.approveSchedule]);

  useEffect(() => {
    if (isStaff) api.celebrations().then(setCelebrations).catch(() => {});
  }, [isStaff]);

  if (!user) return null;
  const ready = (videos ?? []).filter((v) => v.status === 'READY');
  const watched = ready.filter((v) => v.completed).length;
  const pct = ready.length ? Math.round((watched / ready.length) * 100) : 0;
  // Oldest unwatched first: work through the library in the order it was built.
  const upNext = [...ready].reverse().find((v) => !v.completed);
  const newest = ready.slice(0, 3);
  const today = new Date().getDay();
  const todayHours = SALON.hoursByDay[today];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">
          {greeting()}{user.firstName ? `, ${user.firstName}` : ''}
        </h1>
        <p className="mt-1 text-sm opacity-70">
          {roleLabel(user.roles)} at {SALON.name} · {DAYS[today]}, {new Date().toLocaleDateString(undefined, { month: 'long', day: 'numeric' })}
        </p>
      </div>

      <TodayCelebrations items={celebrations?.todays ?? []} viewerId={user?.userId} />

      {toApprove.length > 0 && (
        <section className="card border-2 border-[var(--accent)] bg-[var(--accent-soft)] p-5">
          <h2 className="text-lg font-extrabold">Schedule waiting for your approval</h2>
          <ul className="mt-3 divide-y divide-[var(--border)] text-sm">
            {toApprove.map((w) => (
              <li key={w.weekStart} className="flex flex-wrap items-center justify-between gap-3 py-2">
                <span>
                  <b>Week of {weekRangeLabel(w.weekStart)}</b>
                  {w.submittedByName && <span className="opacity-70"> from {w.submittedByName}</span>}
                </span>
                <Link href={`/schedule/edit?week=${w.weekStart}`} className="btn">Review</Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      <div className="grid gap-4 md:grid-cols-3">
        {isStaff && (
          <section className="card p-5 md:col-span-2">
            <h2 className="text-lg font-extrabold">Your training</h2>
            {videos === null ? (
              <p className="mt-3 text-sm opacity-60">Loading…</p>
            ) : ready.length === 0 ? (
              <p className="mt-3 text-sm opacity-70">No training videos yet. Check back soon.</p>
            ) : (
              <>
                <div className="mt-3 flex items-baseline justify-between text-sm">
                  <span><b className="text-2xl">{watched}</b> of {ready.length} videos watched</span>
                  <span className="font-bold text-[var(--accent-strong)]">{pct}%</span>
                </div>
                <div className="mt-2 h-3 overflow-hidden rounded-full bg-[var(--accent-soft)]">
                  <div className="h-full rounded-full bg-[var(--accent)] transition-all" style={{ width: `${pct}%` }} />
                </div>
                {upNext ? (
                  <Link href={`/videos/${upNext.id}`}
                    className="mt-4 flex items-center justify-between gap-3 rounded-2xl bg-[var(--surface-2)] p-4 hover:bg-[var(--accent-soft)]">
                    <div className="min-w-0">
                      <div className="text-xs font-bold uppercase tracking-wide text-[var(--accent-strong)]">Up next</div>
                      <div className="truncate font-bold">{upNext.title}</div>
                      <div className="text-xs opacity-60">
                        {[upNext.category, formatDuration(upNext.durationSeconds)].filter(Boolean).join(' · ')}
                      </div>
                    </div>
                    <span className="shrink-0 rounded-full bg-[var(--accent)] px-4 py-1.5 text-sm font-bold text-white">Watch</span>
                  </Link>
                ) : (
                  <p className="mt-4 rounded-2xl bg-[var(--success-soft)] p-4 text-sm font-semibold text-[var(--success)]">
                    You&apos;re all caught up. Nice work!
                  </p>
                )}
              </>
            )}
          </section>
        )}

        {isStaff ? (
          <section className="card p-5">
            <div className="flex items-end justify-between gap-2">
              <h2 className="text-lg font-extrabold">Your schedule</h2>
              {seesSchedule && <Link href="/schedule" className="text-sm font-semibold text-[var(--accent-strong)] hover:underline">
                Full schedule
              </Link>}
            </div>
            {myShifts === null ? (
              <p className="mt-3 text-sm opacity-60">Loading…</p>
            ) : myShifts.length === 0 ? (
              <p className="mt-3 text-sm opacity-70">No upcoming shifts posted yet.</p>
            ) : (
              <ul className="mt-3 divide-y divide-[var(--border)] text-sm">
                {myShifts.map((s) => {
                  const isToday = s.date === todayIso();
                  return (
                    <li key={s.id} className={`flex flex-wrap justify-between gap-x-2 py-2 ${isToday ? 'font-bold text-[var(--accent-strong)]' : ''}`}>
                      <span>{isToday ? 'Today' : longDayLabel(s.date)}</span>
                      <span className="whitespace-nowrap">{isRequestedOff(s) ? 'Requested off' : shiftLabel(s.start!, s.end!)}</span>
                      {s.note && <span className="w-full text-xs opacity-60">{s.note}</span>}
                    </li>
                  );
                })}
              </ul>
            )}
          </section>
        ) : (
          // Customers don't have a schedule; they get the salon's details instead.
          <section className="card p-5 md:col-span-3">
            <h2 className="text-lg font-extrabold">The salon</h2>
            <div className="mt-3 text-sm">
              <div className="text-xs font-bold uppercase tracking-wide opacity-60">Open today</div>
              <div className="font-semibold">{todayHours || 'Closed'}</div>
            </div>
            {SALON.phone && (
              <div className="mt-3 text-sm">
                <div className="text-xs font-bold uppercase tracking-wide opacity-60">Phone</div>
                <a href={`tel:${SALON.phone}`} className="font-semibold hover:underline">{SALON.phone}</a>
              </div>
            )}
            {SALON.address.length > 0 && (
              <div className="mt-3 text-sm">
                <div className="text-xs font-bold uppercase tracking-wide opacity-60">Address</div>
                {SALON.address.map((l) => <div key={l}>{l}</div>)}
              </div>
            )}
            <Link href="/#visit" className="mt-3 inline-block text-sm font-semibold text-[var(--accent-strong)] underline underline-offset-4">
              All hours
            </Link>
          </section>
        )}
      </div>

      {isManager && celebrations && <UpcomingCelebrations items={celebrations.upcoming} />}

      {isManager && (
        <section className="card p-5">
          <h2 className="text-lg font-extrabold">Manager shortcuts</h2>
          <div className="mt-3 flex flex-wrap gap-2">
            <Link href="/admin" className="btn">Upload a video</Link>
            <Link href="/admin/team" className="btn-ghost">Team</Link>
            {isOwner && (
              <>
                <Link href="/admin/team/new" className="btn-ghost">Add employee</Link>
                <Link href="/admin/applicants" className="btn-ghost">
                  Applicants
                  {!!newApplicants && (
                    <span className="ml-1.5 rounded-full bg-[var(--accent)] px-1.5 py-0.5 text-xs font-bold text-white">
                      {newApplicants} new
                    </span>
                  )}
                </Link>
              </>
            )}
          </div>
        </section>
      )}

      {isStaff && newest.length > 0 && (
        <section>
          <div className="mb-3 flex items-end justify-between">
            <h2 className="text-lg font-extrabold">Newest videos</h2>
            <Link href="/videos" className="text-sm font-semibold text-[var(--accent-strong)] hover:underline">See all videos</Link>
          </div>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {newest.map((v) => <VideoCard key={v.id} video={v} />)}
          </div>
        </section>
      )}

      {!isStaff && (
        <p className="card p-5 text-sm opacity-80">
          Thanks for being a {SALON.name} customer! Need an appointment? Give us a call.
        </p>
      )}
    </div>
  );
}
