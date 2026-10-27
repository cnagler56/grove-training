'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { api, type HaircutModel, type ModelInput } from '@/src/lib/api';
import { displayPhone, formatDate, formatPhone } from '@/src/lib/format';
import { useDialogs } from '@/src/components/Dialogs';
import DateField from '@/src/components/DateField';

const today = () => new Date().toLocaleDateString('en-CA');
const EMPTY: ModelInput = { name: '', source: null, phone: null, notes: null, lastVisit: null };

type Sort = 'name' | 'lastVisit';

/**
 * People who'll sit for a haircut so stylists can practice or train.
 * Managers and up can add, change, and remove them.
 */
export default function ModelsPage() {
  const dialogs = useDialogs();
  const [models, setModels] = useState<HaircutModel[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  // null = form closed; 'new' = adding; a number = editing that model.
  const [editing, setEditing] = useState<number | 'new' | null>(null);
  const [form, setForm] = useState<ModelInput>(EMPTY);
  const [saving, setSaving] = useState(false);
  const [search, setSearch] = useState('');
  const [sort, setSort] = useState<Sort>('name');

  const load = useCallback(() => {
    api.models().then(setModels).catch((e) => setError(e.message));
  }, []);
  useEffect(() => { load(); }, [load]);

  // Sources already used, offered as suggestions while typing.
  const sources = useMemo(
    () => [...new Set((models ?? []).map((m) => m.source).filter((s): s is string => !!s))].sort(),
    [models],
  );

  const shown = useMemo(() => {
    const q = search.trim().toLowerCase();
    const list = (models ?? []).filter((m) => !q
      || [m.name, m.source, m.phone, m.notes].some((v) => v?.toLowerCase().includes(q)));
    if (sort === 'lastVisit') {
      // Longest since their last visit first; never-visited at the very top.
      return [...list].sort((a, b) => (a.lastVisit ?? '').localeCompare(b.lastVisit ?? ''));
    }
    return list;
  }, [models, search, sort]);

  function startAdd() {
    setForm(EMPTY);
    setEditing('new');
    setError(null);
    setNotice(null);
  }

  function startEdit(m: HaircutModel) {
    setForm({ name: m.name, source: m.source, phone: m.phone && displayPhone(m.phone), notes: m.notes, lastVisit: m.lastVisit });
    setEditing(m.id);
    setError(null);
    setNotice(null);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (editing === null) return;
    setSaving(true);
    setError(null);
    try {
      if (editing === 'new') {
        await api.createModel(form);
        setNotice(`Added ${form.name.trim()}.`);
      } else {
        await api.updateModel(editing, form);
        setNotice(`Saved ${form.name.trim()}.`);
      }
      setEditing(null);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save');
    } finally {
      setSaving(false);
    }
  }

  async function remove(m: HaircutModel) {
    if (!(await dialogs.confirm({
      title: `Remove ${m.name}?`,
      message: 'They\'ll be taken off the models list. This can\'t be undone.',
      confirmLabel: 'Remove',
      danger: true,
    }))) return;
    setError(null);
    try {
      await api.deleteModel(m.id);
      if (editing === m.id) setEditing(null);
      setNotice(`Removed ${m.name}.`);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not remove');
    }
  }

  const text = (k: keyof ModelInput) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    setForm({ ...form, [k]: e.target.value === '' && k !== 'name' ? null : e.target.value });

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight">Models</h1>
          <p className="mt-1 text-sm opacity-70">People who&apos;ll sit for a haircut so stylists can practice and train.</p>
        </div>
        {editing === null && <button className="btn" onClick={startAdd}>Add model</button>}
      </div>

      {error && <p className="text-sm text-[var(--danger)]">{error}</p>}
      {notice && <p className="text-sm font-semibold text-[var(--success)]">{notice}</p>}

      {editing !== null && (
        <form onSubmit={save} className="card space-y-4 p-5">
          <h2 className="text-lg font-extrabold">{editing === 'new' ? 'Add a model' : `Edit ${form.name || 'model'}`}</h2>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <label className="block text-sm">
              <span className="mb-1 block font-semibold opacity-80">Name</span>
              <input className="input" value={form.name} onChange={text('name')} required maxLength={120} autoFocus />
            </label>
            <label className="block text-sm">
              <span className="mb-1 block font-semibold opacity-80">Source</span>
              <input className="input" value={form.source ?? ''} onChange={text('source')} maxLength={120}
                list="model-sources" placeholder="Instagram, walk-in, friend..." />
              <datalist id="model-sources">{sources.map((s) => <option key={s} value={s} />)}</datalist>
            </label>
            <label className="block text-sm">
              <span className="mb-1 block font-semibold opacity-80">Phone</span>
              <input className="input" type="tel" value={form.phone ?? ''} placeholder="555-555-0100"
                onChange={(e) => setForm({ ...form, phone: formatPhone(e.target.value) || null })} />
            </label>
            <DateField label="Last visit" value={form.lastVisit} onChange={(lastVisit) => setForm({ ...form, lastVisit })} max={today()} />
          </div>
          <label className="block text-sm">
            <span className="mb-1 block font-semibold opacity-80">Notes</span>
            <textarea className="input min-h-20" value={form.notes ?? ''} onChange={text('notes')} maxLength={2000}
              placeholder="Hair type, what cuts they're open to, availability..." />
          </label>
          <div className="flex flex-wrap gap-2">
            <button type="submit" className="btn" disabled={saving}>
              {saving ? 'Saving…' : editing === 'new' ? 'Add model' : 'Save changes'}
            </button>
            <button type="button" className="btn-ghost" onClick={() => setEditing(null)}>Cancel</button>
          </div>
        </form>
      )}

      {models === null ? (
        <p className="opacity-60">Loading…</p>
      ) : models.length === 0 ? (
        <p className="card p-5 text-sm opacity-80">No models yet. Use Add model to start the list.</p>
      ) : (
        <section className="card overflow-x-auto">
          <div className="flex flex-wrap items-center gap-3 px-5 pt-5">
            <input className="input max-w-xs" placeholder="Search names, notes..." value={search}
              onChange={(e) => setSearch(e.target.value)} aria-label="Search models" />
            <select className="input w-auto cursor-pointer" value={sort} onChange={(e) => setSort(e.target.value as Sort)}
              aria-label="Sort models">
              <option value="name">Sort by name</option>
              <option value="lastVisit">Longest since last visit</option>
            </select>
            <span className="text-sm opacity-60">{shown.length} of {models.length}</span>
          </div>
          <table className="mt-3 w-full text-left text-sm">
            <thead className="border-b border-[var(--border)] text-xs uppercase tracking-wide opacity-60">
              <tr>
                <th className="px-5 py-2 font-semibold">Name</th>
                <th className="px-3 py-2 font-semibold">Source</th>
                <th className="px-3 py-2 font-semibold">Phone</th>
                <th className="px-3 py-2 font-semibold">Last visit</th>
                <th className="px-3 py-2 font-semibold">Notes</th>
                <th className="px-5 py-2" />
              </tr>
            </thead>
            <tbody className="divide-y divide-[var(--border)]">
              {shown.map((m) => (
                <tr key={m.id} className={editing === m.id ? 'bg-[var(--accent-soft)]' : 'hover:bg-[var(--surface-2)]'}>
                  <td className="whitespace-nowrap px-5 py-2 font-bold">{m.name}</td>
                  <td className="px-3 py-2">{m.source}</td>
                  <td className="whitespace-nowrap px-3 py-2">
                    {m.phone && <a href={`tel:${m.phone}`} className="hover:underline">{displayPhone(m.phone)}</a>}
                  </td>
                  <td className="whitespace-nowrap px-3 py-2">{m.lastVisit ? formatDate(m.lastVisit) : <span className="opacity-50">Never</span>}</td>
                  <td className="max-w-md px-3 py-2"><span className="line-clamp-2 whitespace-pre-line">{m.notes}</span></td>
                  <td className="whitespace-nowrap px-5 py-2 text-right">
                    <button className="btn-ghost" onClick={() => startEdit(m)}>Edit</button>{' '}
                    <button className="text-sm font-bold text-[var(--danger)] hover:underline" onClick={() => void remove(m)}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
              {shown.length === 0 && (
                <tr><td colSpan={6} className="px-5 py-4 text-sm opacity-60">No one matches that search.</td></tr>
              )}
            </tbody>
          </table>
        </section>
      )}
    </div>
  );
}
