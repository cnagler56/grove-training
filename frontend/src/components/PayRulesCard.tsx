'use client';

import { useEffect, useState } from 'react';
import { api, type PayRules } from '@/src/lib/api';
import { moneyCents } from '@/src/lib/format';
import PayPeopleCard from './PayPeopleCard';

/** Where the payroll skill keeps its matrix on the owner's computer. */
const SKILL_DATA_FOLDER = String.raw`C:\Users\chris\.claude\skills\payroll\data`;

/**
 * Owner Settings: load the payroll skill's matrix.json (PPH steps and rates), then
 * set the minimum, the lead and manager bumps, and flat rates on the card below.
 */
export default function PayRulesCard() {
  const [rules, setRules] = useState<PayRules | null>(null);
  const [matrixFile, setMatrixFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  async function copyFolder() {
    try {
      await navigator.clipboard.writeText(SKILL_DATA_FOLDER);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  }

  useEffect(() => {
    api.payRules().then(setRules).catch((e) => setError(e.message));
  }, []);

  async function load() {
    if (!matrixFile) return;
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const matrix = JSON.parse(await matrixFile.text());
      if (!Array.isArray(matrix)) throw new Error('That file doesn\'t look like matrix.json.');
      const loaded = await api.loadPayRules({ matrix });
      setRules(loaded);
      setNotice(`Loaded ${loaded.matrix.length} matrix steps.`);
      setMatrixFile(null);
    } catch (e) {
      setError(e instanceof SyntaxError ? 'That file isn\'t valid JSON.' : e instanceof Error ? e.message : 'Could not load the file');
    } finally {
      setBusy(false);
    }
  }

  const loaded = rules && rules.matrix.length > 0;
  const top = loaded ? rules.matrix[rules.matrix.length - 1] : null;

  return (
    <>
      <section className="card mt-8 p-5">
        <h2 className="text-lg font-extrabold">Pay matrix</h2>
        <p className="mt-1 text-sm opacity-70">
          The PPH steps and rates stylists are paid on. Load the payroll skill&apos;s <code>matrix.json</code> whenever
          it changes. Stylists see their own rate on My week; managers never see anyone&apos;s rate.
        </p>

        <div className="mt-3 rounded-xl bg-[var(--surface-2)] p-3 text-sm">
          <div className="font-semibold">Where the file is</div>
          <div className="mt-1 flex flex-wrap items-center gap-2">
            <code className="break-all">{SKILL_DATA_FOLDER}</code>
            <button type="button" className="btn-ghost py-1" onClick={() => void copyFolder()}>
              {copied ? 'Copied' : 'Copy'}
            </button>
          </div>
          <div className="mt-1 text-xs opacity-70">
            Click Choose File, paste this into the File name box, and press Enter to jump to the folder.
          </div>
        </div>

        <p className="mt-3 text-sm">
          {!rules ? 'Loading…' : loaded
            ? <>Loaded: {rules.matrix.length} steps, up to {moneyCents(top!.rate)} at {moneyCents(top!.pph)} PPH.</>
            : <b>Nothing loaded yet, so stylists have no rate.</b>}
        </p>

        <div className="mt-4 flex flex-wrap items-end gap-3">
          <label className="block text-sm">
            <span className="mb-1 block font-semibold opacity-80">matrix.json</span>
            <input type="file" accept=".json,application/json" className="input" onChange={(e) => setMatrixFile(e.target.files?.[0] ?? null)} />
          </label>
          <button className="btn" onClick={() => void load()} disabled={busy || !matrixFile}>
            {busy ? 'Loading…' : loaded ? 'Replace matrix' : 'Load matrix'}
          </button>
        </div>
        {error && <p className="mt-3 text-sm text-[var(--danger)]">{error}</p>}
        {notice && <p className="mt-3 text-sm font-semibold text-[var(--success)]">{notice}</p>}
      </section>
      {rules && <PayPeopleCard rules={rules} onChange={setRules} />}
    </>
  );
}
