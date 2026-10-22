'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

export type ConfirmOptions = {
  title: string;
  message?: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  /** Red confirm button, for deletes and other things that can't be undone. */
  danger?: boolean;
};

export type PromptOptions = ConfirmOptions & {
  placeholder?: string;
  initial?: string;
  /** Show a larger text box. */
  multiline?: boolean;
  /** The confirm button stays disabled until this many characters are typed (default 1). */
  minLength?: number;
  hint?: string;
};

type Dialogs = {
  /** Our own yes/no box (instead of the browser's confirm()). Resolves true on confirm. */
  confirm: (o: ConfirmOptions) => Promise<boolean>;
  /** Our own text box (instead of the browser's prompt()). Resolves the text, or null if cancelled. */
  prompt: (o: PromptOptions) => Promise<string | null>;
};

type Open =
  | { kind: 'confirm'; opts: ConfirmOptions; resolve: (v: boolean) => void }
  | { kind: 'prompt'; opts: PromptOptions; resolve: (v: string | null) => void };

const DialogContext = createContext<Dialogs | null>(null);

/** Hosts the site's dialog boxes. Wrap the app in this once (root layout). */
export function DialogProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState<Open | null>(null);

  const confirm = useCallback((opts: ConfirmOptions) =>
    new Promise<boolean>((resolve) => setOpen({ kind: 'confirm', opts, resolve })), []);
  const prompt = useCallback((opts: PromptOptions) =>
    new Promise<string | null>((resolve) => setOpen({ kind: 'prompt', opts, resolve })), []);

  // Stable, so hooks that depend on it don't re-run each time a dialog opens.
  const value = useMemo(() => ({ confirm, prompt }), [confirm, prompt]);

  return (
    <DialogContext.Provider value={value}>
      {children}
      {open && <DialogBox open={open} close={() => setOpen(null)} />}
    </DialogContext.Provider>
  );
}

export function useDialogs(): Dialogs {
  const ctx = useContext(DialogContext);
  if (!ctx) throw new Error('useDialogs must be used within a DialogProvider');
  return ctx;
}

function DialogBox({ open, close }: { open: Open; close: () => void }) {
  const { opts } = open;
  const isPrompt = open.kind === 'prompt';
  const promptOpts = isPrompt ? (open.opts as PromptOptions) : null;
  const [text, setText] = useState(promptOpts?.initial ?? '');
  const inputRef = useRef<HTMLInputElement & HTMLTextAreaElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);
  const minLength = promptOpts?.minLength ?? 1;
  const canConfirm = !isPrompt || text.trim().length >= minLength;

  const cancel = useCallback(() => {
    if (open.kind === 'confirm') open.resolve(false); else open.resolve(null);
    close();
  }, [open, close]);

  const ok = useCallback(() => {
    if (!canConfirm) return;
    if (open.kind === 'confirm') open.resolve(true); else open.resolve(text.trim());
    close();
  }, [open, close, canConfirm, text]);

  useEffect(() => {
    (isPrompt ? inputRef.current : confirmRef.current)?.focus();
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') cancel(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [isPrompt, cancel]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4 print:hidden"
      onMouseDown={(e) => { if (e.target === e.currentTarget) cancel(); }}
    >
      <div role="dialog" aria-modal="true" aria-labelledby="dialog-title" className="card w-full max-w-md p-6">
        <h2 id="dialog-title" className="text-lg font-extrabold">{opts.title}</h2>
        {opts.message && <div className="mt-2 whitespace-pre-line text-sm opacity-80">{opts.message}</div>}

        {promptOpts && (
          <form className="mt-4" onSubmit={(e) => { e.preventDefault(); ok(); }}>
            {promptOpts.multiline ? (
              <textarea
                ref={inputRef}
                className="input min-h-24"
                value={text}
                placeholder={promptOpts.placeholder}
                onChange={(e) => setText(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) ok(); }}
              />
            ) : (
              <input
                ref={inputRef}
                className="input"
                value={text}
                placeholder={promptOpts.placeholder}
                onChange={(e) => setText(e.target.value)}
              />
            )}
            {promptOpts.hint && <p className="mt-1 text-xs opacity-60">{promptOpts.hint}</p>}
          </form>
        )}

        <div className="mt-6 flex justify-end gap-2">
          <button className="btn-ghost" onClick={cancel}>{opts.cancelLabel ?? 'Cancel'}</button>
          <button
            ref={confirmRef}
            className={opts.danger
              ? 'rounded-full bg-[var(--danger)] px-5 py-2 text-sm font-bold text-white hover:opacity-90 disabled:opacity-50'
              : 'btn'}
            onClick={ok}
            disabled={!canConfirm}
          >
            {opts.confirmLabel ?? 'OK'}
          </button>
        </div>
      </div>
    </div>
  );
}
