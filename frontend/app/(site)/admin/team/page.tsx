'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, type AdminStatus, type TeamMember } from '@/src/lib/api';
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

  async function run(action: () => Promise<unknown>, done?: string) {
    setError(null);
    setNotice(null);
    try {
      await action();
      if (done) setNotice(done);
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong');
    }
  }

  function resetPassword(m: TeamMember) {
    const pw = prompt(`New temporary password for ${name(m)} (8+ characters). Tell them in person, and have them use it to sign in.`);
    if (!pw) return;
    void run(() => api.resetPassword(m.userId, pw), `Password reset for ${name(m)}.`);
  }

  if (!team) return <p className="opacity-60">{error ?? 'Loading team…'}</p>;
  const active = team.filter((m) => m.active).length;

  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight">Team</h1>
      <p className="mt-1 text-sm opacity-70">
        {active} active account{active === 1 ? '' : 's'}.{' '}
        {status?.signupOpen
          ? 'New staff join with the team code (set as TEAM_SIGNUP_CODE on the server). Change it when someone leaves.'
          : 'Sign-up is closed: set TEAM_SIGNUP_CODE on the server to let new staff join.'}
      </p>

      {error && <p className="mt-4 text-sm text-red-300">{error}</p>}
      {notice && <p className="mt-4 text-sm text-[var(--accent)]">{notice}</p>}

      <div className="card mt-6 overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-white/10 text-xs uppercase tracking-wide opacity-60">
            <tr>
              <th className="px-4 py-3 font-medium">Name</th>
              <th className="px-4 py-3 font-medium">Videos watched</th>
              <th className="px-4 py-3 font-medium">Role</th>
              <th className="px-4 py-3 font-medium" />
            </tr>
          </thead>
          <tbody className="divide-y divide-white/10">
            {team.map((m) => {
              const self = m.userId === user?.userId;
              return (
                <tr key={m.userId} className={m.active ? '' : 'opacity-50'}>
                  <td className="px-4 py-3">
                    <div className="font-medium">{name(m)}{self && <span className="opacity-60"> (you)</span>}</div>
                    <div className="text-xs opacity-60">{m.email}</div>
                  </td>
                  <td className="px-4 py-3">{m.videosCompleted}</td>
                  <td className="px-4 py-3">{m.role === 'ADMIN' ? 'Manager' : 'Stylist'}</td>
                  <td className="px-4 py-3">
                    {!self && (
                      <div className="flex flex-wrap justify-end gap-2">
                        <button className="btn-ghost" onClick={() => resetPassword(m)}>Reset password</button>
                        <button
                          className="btn-ghost"
                          onClick={() => void run(() => api.updateMember(m.userId, { role: m.role === 'ADMIN' ? 'USER' : 'ADMIN' }))}
                        >
                          {m.role === 'ADMIN' ? 'Make stylist' : 'Make manager'}
                        </button>
                        <button
                          className={`btn-ghost ${m.active ? 'text-red-300' : ''}`}
                          onClick={() => void run(() => api.updateMember(m.userId, { active: !m.active }))}
                        >
                          {m.active ? 'Turn off' : 'Turn on'}
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function name(m: TeamMember) {
  return `${m.firstName ?? ''} ${m.lastName ?? ''}`.trim() || m.email;
}
