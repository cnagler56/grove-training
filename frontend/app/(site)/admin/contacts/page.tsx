'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { api, type BusinessContact, type ContactInput } from '@/src/lib/api';
import { formatPhone } from '@/src/lib/format';
import { useDialogs } from '@/src/components/Dialogs';

const EMPTY: ContactInput = { name: '', phone: '', type: null };
const COMMON_TYPES = ['Vendor', 'Landlord'];

/**
 * Numbers the business calls: vendors, the landlord, repair people.
 * Managers and up can see the list and add, change, and remove contacts.
 */
export default function ContactsPage() {
  const dialogs = useDialogs();
  const [contacts, setContacts] = useState<BusinessContact[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  // null = form closed; 'new' = adding; a number = editing that contact.
  const [editing, setEditing] = useState<number | 'new' | null>(null);
  const [form, setForm] = useState<ContactInput>(EMPTY);
  const [saving, setSaving] = useState(false);
  const [search, setSearch] = useState('');

  const load = useCallback(() => {
    api.contacts().then(setContacts).catch((e) => setError(e.message));
  }, []);
  useEffect(() => { load(); }, [load]);

  // Types already used, offered as suggestions while typing.
  const types = useMemo(
    () => [...new Set([...COMMON_TYPES, ...(contacts ?? []).map((c) => c.type).filter((t): t is string => !!t)])].sort(),
    [contacts],
  );

  const shown = useMemo(() => {
    const q = search.trim().toLowerCase();
    return (contacts ?? []).filter((c) => !q
      || [c.name, c.phone, c.type].some((v) => v?.toLowerCase().includes(q)));
  }, [contacts, search]);

  function startAdd() {
    setForm(EMPTY);
    setEditing('new');
    setError(null);
    setNotice(null);
  }

  function startEdit(c: BusinessContact) {
    setForm({ name: c.name, phone: c.phone, type: c.type });
    setEditing(c.id);
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
        await api.createContact(form);
        setNotice(`Added ${form.name.trim()}.`);
      } else {
        await api.updateContact(editing, form);
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

  async function remove(c: BusinessContact) {
    if (!(await dialogs.confirm({
      title: `Delete ${c.name}?`,
      message: 'They\'ll be taken off the contacts list. This can\'t be undone.',
      confirmLabel: 'Delete',
      danger: true,
    }))) return;
    setError(null);
    try {
      await api.deleteContact(c.id);
      if (editing === c.id) setEditing(null);
      setNotice(`Deleted ${c.name}.`);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not delete');
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight">Contacts</h1>
          <p className="mt-1 text-sm opacity-70">Phone numbers for vendors, the landlord, and anyone else the business calls. Managers and up can see this page.</p>
        </div>
        {editing === null && <button className="btn" onClick={startAdd}>Add contact</button>}
      </div>

      {error && <p className="text-sm text-[var(--danger)]">{error}</p>}
      {notice && <p className="text-sm font-semibold text-[var(--success)]">{notice}</p>}

      {editing !== null && (
        <form onSubmit={save} className="card space-y-4 p-5">
          <h2 className="text-lg font-extrabold">{editing === 'new' ? 'Add a contact' : `Edit ${form.name || 'contact'}`}</h2>
          <div className="grid gap-3 sm:grid-cols-3">
            <label className="block text-sm">
              <span className="mb-1 block font-semibold opacity-80">Name</span>
              <input className="input" value={form.name} required maxLength={120} autoFocus
                onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </label>
            <label className="block text-sm">
              <span className="mb-1 block font-semibold opacity-80">Phone</span>
              <input className="input" type="tel" value={form.phone} required placeholder="555-555-0100"
                onChange={(e) => setForm({ ...form, phone: formatPhone(e.target.value) })} />
            </label>
            <label className="block text-sm">
              <span className="mb-1 block font-semibold opacity-80">Type or note</span>
              <input className="input" value={form.type ?? ''} maxLength={120} list="contact-types"
                placeholder="Vendor, landlord, ext 12..."
                onChange={(e) => setForm({ ...form, type: e.target.value || null })} />
              <datalist id="contact-types">{types.map((t) => <option key={t} value={t} />)}</datalist>
            </label>
          </div>
          <div className="flex flex-wrap gap-2">
            <button type="submit" className="btn" disabled={saving}>
              {saving ? 'Saving…' : editing === 'new' ? 'Add contact' : 'Save changes'}
            </button>
            <button type="button" className="btn-ghost" onClick={() => setEditing(null)}>Cancel</button>
          </div>
        </form>
      )}

      {contacts === null ? (
        !error && <p className="opacity-60">Loading…</p>
      ) : contacts.length === 0 ? (
        <p className="card p-5 text-sm opacity-80">No contacts yet. Use Add contact to start the list.</p>
      ) : (
        <section className="card">
          <div className="flex flex-wrap items-center gap-3 px-5 pt-5">
            <input className="input max-w-xs" placeholder="Search names, numbers..." value={search}
              onChange={(e) => setSearch(e.target.value)} aria-label="Search contacts" />
            <span className="text-sm opacity-60">{shown.length} of {contacts.length}</span>
          </div>
          <ul className="mt-3 divide-y divide-[var(--border)] border-t border-[var(--border)] text-sm">
            {shown.map((c) => (
              <li key={c.id} className={`flex flex-wrap items-center gap-x-4 gap-y-1 px-5 py-3 ${editing === c.id ? 'bg-[var(--accent-soft)]' : ''}`}>
                <div className="min-w-0 flex-1 basis-48">
                  <p className="break-words font-bold">{c.name}</p>
                  {c.type && <p className="break-words opacity-70">{c.type}</p>}
                </div>
                <a href={`tel:${c.phone}`} className="whitespace-nowrap py-2 text-base font-semibold text-[var(--accent-strong)] hover:underline">
                  {c.phone}
                </a>
                <div className="flex items-center gap-2 whitespace-nowrap">
                  <button className="btn-ghost" onClick={() => startEdit(c)}>Edit</button>
                  <button className="text-sm font-bold text-[var(--danger)] hover:underline" onClick={() => void remove(c)}>
                    Delete
                  </button>
                </div>
              </li>
            ))}
            {shown.length === 0 && (
              <li className="px-5 py-4 text-sm opacity-60">No one matches that search.</li>
            )}
          </ul>
        </section>
      )}
    </div>
  );
}
