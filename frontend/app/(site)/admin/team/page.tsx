'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { api, type AdminStatus, type TeamMember, type UserRole } from '@/src/lib/api';
import { canManage, roleOptions, roleBadgeClass, roleLabel } from '@/src/lib/roles';
import { employeeName, formatBirthday, formatDate, tenure } from '@/src/lib/format';
import { useUser } from '@/src/lib/UserContext';

export default function TeamPage() {
  const { user } = useUser();
  const [team, setTeam] = useState<TeamMember[] | null>(null);
  const [status, setStatus] = useState<AdminStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(() => {
    api.team().then(setTeam).catch((e) => setError(e.message));
  }, []);
  useEffect(() => {
    load();
    api.adminStatus().then(setStatus).catch(() => {});
  }, [load]);

  async function change(m: TeamMember, c: { active?: boolean; role?: UserRole }, done?: string) {
    setError(null);
    setNotice(null);
    try {
      await api.updateMember(m.userId, c);
      if (done) setNotice(done);
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong');
    }
  }

  if (!team) return <p className="opacity-60">{error ?? 'Loading team…'}</p>;
  const current = team.filter((m) => m.active);
  const former = team.filter((m) => !m.active);

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl font-extrabold tracking-tight">Team</h1>
        {user?.roles === 'OWNER' && <Link href="/admin/team/new" className="btn">+ Add employee</Link>}
      </div>
      <p className="mt-1 text-sm opacity-70">
        {current.length} current employee{current.length === 1 ? '' : 's'}.{' '}
        {status?.signupOpen
          ? 'New staff join with the team code (TEAM_SIGNUP_CODE). Change it when someone leaves.'
          : 'Sign-up is closed: set TEAM_SIGNUP_CODE on the server to let new staff join.'}
      </p>

      {error && <p className="mt-4 text-sm text-[var(--danger)]">{error}</p>}
      {notice && <p className="mt-4 text-sm font-semibold text-[var(--success)]">{notice}</p>}

      <div className="card mt-6 overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-[var(--border)] text-xs uppercase tracking-wide opacity-60">
            <tr>
              <th className="px-4 py-3 font-semibold">Name</th>
              <th className="px-4 py-3 font-semibold">Role</th>
              <th className="px-4 py-3 font-semibold">Phone</th>
              <th className="px-4 py-3 font-semibold">Hired</th>
              <th className="px-4 py-3 font-semibold">Birthday</th>
              <th className="px-4 py-3 font-semibold">Videos</th>
              <th className="px-4 py-3 font-semibold" />
            </tr>
          </thead>
          <tbody className="divide-y divide-[var(--border)]">
            {current.map((m) => (
              <tr key={m.userId} className="hover:bg-[var(--surface-2)]">
                <td className="px-4 py-3">
                  <div className="font-bold">
                    {employeeName(m)}
                    {m.userId === user?.userId && <span className="font-normal opacity-60"> (you)</span>}
                  </div>
                  <div className="text-xs opacity-60">{m.email}</div>
                </td>
                <td className="whitespace-nowrap px-4 py-3">
                  {user && m.userId !== user.userId && canManage(user.roles, m.role) ? (
                    <select
                      aria-label={`Role for ${employeeName(m)}`}
                      value={m.role}
                      onChange={(e) => {
                        const role = e.target.value as UserRole;
                        void change(m, { role }, `${employeeName(m)} is now ${article(roleLabel(role))} ${roleLabel(role).toLowerCase()}.`);
                      }}
                      className={`cursor-pointer rounded-full border-0 py-1 pl-3 pr-7 text-xs font-bold ${roleBadgeClass(m.role)}`}
                    >
                      {roleOptions(user.roles, m.role).map(({ value: r }) => (
                        <option key={r} value={r}>{roleLabel(r)}</option>
                      ))}
                    </select>
                  ) : (
                    <span className={`rounded-full px-3 py-1 text-xs font-bold ${roleBadgeClass(m.role)}`}>
                      {roleLabel(m.role)}
                    </span>
                  )}
                </td>
                <td className="whitespace-nowrap px-4 py-3">{m.phone || <Missing />}</td>
                <td className="whitespace-nowrap px-4 py-3">
                  {m.hireDate ? (
                    <>
                      {formatDate(m.hireDate)}
                      {tenure(m.hireDate) && <div className="text-xs opacity-60">{tenure(m.hireDate)}</div>}
                    </>
                  ) : <Missing />}
                </td>
                <td className="whitespace-nowrap px-4 py-3">{formatBirthday(m.birthday) || <Missing />}</td>
                <td className="px-4 py-3">{m.videosCompleted}</td>
                <td className="px-4 py-3 text-right">
                  {user && (m.userId === user.userId || canManage(user.roles, m.role)) && (
                    <Link href={`/admin/team/${m.userId}`} className="btn-ghost inline-block">Edit</Link>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {former.length > 0 && (
        <section className="mt-10">
          <h2 className="text-lg font-extrabold">Former employees</h2>
          <p className="text-sm opacity-70">Disabled accounts can&apos;t sign in. Their watch history is kept.</p>
          <ul className="card mt-3 divide-y divide-[var(--border)]">
            {former.map((m) => (
              <li key={m.userId} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3 text-sm">
                <div>
                  <Link href={`/admin/team/${m.userId}`} className="font-bold hover:underline">{employeeName(m)}</Link>
                  <div className="text-xs opacity-60">
                    {m.disabledAt ? `Disabled ${formatDate(m.disabledAt)}` : 'Disabled'}
                    {m.hireDate && ` · hired ${formatDate(m.hireDate)}`}
                  </div>
                </div>
                {user && canManage(user.roles, m.role) && (
                  <button className="btn-ghost" onClick={() => void change(m, { active: true }, `${employeeName(m)} can sign in again.`)}>Re-enable</button>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}

function Missing() {
  return <span className="opacity-40">—</span>;
}

function article(word: string) {
  return /^[aeiou]/i.test(word) ? 'an' : 'a';
}
