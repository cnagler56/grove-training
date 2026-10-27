'use client';

import { useEffect, useState } from 'react';
import { api, type PayRules, type PayRulesUpload } from '@/src/lib/api';
import { moneyCents } from '@/src/lib/format';

/**
 * Owner Settings: load the payroll skill's matrix.json and employee_codes.json
 * so each stylist's My week can show that week's rate, figured the same way as
 * payroll. Re-load them whenever the skill's files change.
 */
export default function PayRulesCard() {
  const [rules, setRules] = useState<PayRules | null>(null);
  const [matrixFile, setMatrixFile] = useState<File | null>(null);
  const [codesFile, setCodesFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [showCodes, setShowCodes] = useState(false);

  useEffect(() => {
    api.payRules().then(setRules).catch((e) => setError(e.message));
  }, []);

  async function load() {
    if (!matrixFile || !codesFile) return;
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const upload: PayRulesUpload = {
        matrix: JSON.parse(await matrixFile.text()),
        codes: JSON.parse(await codesFile.text()),
      };
      if (!Array.isArray(upload.matrix)) throw new Error('That matrix file doesn\'t look like matrix.json.');
      if (typeof upload.codes !== 'object' || Array.isArray(upload.codes)) {
        throw new Error('That codes file doesn\'t look like employee_codes.json.');
      }
      const loaded = await api.loadPayRules(upload);
      setRules(loaded);
      setNotice(`Loaded ${loaded.matrix.length} matrix steps and ${loaded.codes.length} people.`);
      setMatrixFile(null);
      setCodesFile(null);
    } catch (e) {
      setError(e instanceof SyntaxError ? 'One of those files isn\'t valid JSON.' : e instanceof Error ? e.message : 'Could not load the files');
    } finally {
      setBusy(false);
    }
  }

  const loaded = rules && rules.matrix.length > 0;
  const top = loaded ? rules.matrix[rules.matrix.length - 1] : null;

  return (
    <section className="card mt-8 p-5">
      <h2 className="text-lg font-extrabold">Pay rates</h2>
      <p className="mt-1 text-sm opacity-70">
        Each stylist sees their own weekly rate on My week, figured the same way as payroll. Load the payroll
        skill&apos;s <code>matrix.json</code> and <code>employee_codes.json</code> (in
        <code> .claude\skills\payroll\data</code>) whenever they change. Managers never see anyone&apos;s rate.
      </p>

      <p className="mt-3 text-sm">
        {!rules ? 'Loading…' : loaded
          ? <>Loaded: {rules.matrix.length} matrix steps, up to {moneyCents(top!.rate)} at {moneyCents(top!.pph)} PPH, and {rules.codes.length} people&apos;s codes.</>
          : <b>Nothing loaded yet, so no one sees a rate.</b>}
      </p>

      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        <label className="block text-sm">
          <span className="mb-1 block font-semibold opacity-80">matrix.json</span>
          <input type="file" accept=".json,application/json" className="input" onChange={(e) => setMatrixFile(e.target.files?.[0] ?? null)} />
        </label>
        <label className="block text-sm">
          <span className="mb-1 block font-semibold opacity-80">employee_codes.json</span>
          <input type="file" accept=".json,application/json" className="input" onChange={(e) => setCodesFile(e.target.files?.[0] ?? null)} />
        </label>
      </div>
      {error && <p className="mt-3 text-sm text-[var(--danger)]">{error}</p>}
      {notice && <p className="mt-3 text-sm font-semibold text-[var(--success)]">{notice}</p>}
      <div className="mt-4 flex flex-wrap items-center gap-3">
        <button className="btn" onClick={() => void load()} disabled={busy || !matrixFile || !codesFile}>
          {busy ? 'Loading…' : loaded ? 'Replace pay rates' : 'Load pay rates'}
        </button>
        {loaded && (
          <button className="btn-ghost" onClick={() => setShowCodes(!showCodes)}>
            {showCodes ? 'Hide codes' : 'Show codes'}
          </button>
        )}
      </div>

      {showCodes && rules && (
        <table className="mt-4 w-full text-left text-sm">
          <thead className="border-b border-[var(--border)] text-xs uppercase tracking-wide opacity-60">
            <tr>
              <th className="py-2 font-semibold">Name</th>
              <th className="py-2 font-semibold">Code</th>
              <th className="py-2 text-right font-semibold">Pay</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[var(--border)]">
            {rules.codes.map((c) => (
              <tr key={c.name}>
                <td className="py-1.5">{c.name}</td>
                <td className="py-1.5">{c.code ?? ''}</td>
                <td className="py-1.5 text-right">
                  {c.flatRate !== null
                    ? `${moneyCents(c.flatRate)} flat`
                    : c.premium
                      ? `Matrix ${c.premium > 0 ? '+' : '-'} ${moneyCents(Math.abs(c.premium))}`
                      : 'Matrix'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
