'use client';

import { useEffect, useState, useSyncExternalStore } from 'react';

const DISMISSED_KEY = 'grove.installCard.dismissed';

/** Chrome on Android fires this when the site can be installed; calling prompt() shows its install sheet. */
interface InstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

type Device = 'iphone' | 'android' | 'computer';

/**
 * What the browser can tell us, read only in the browser (the server renders
 * nothing). "installed" means it's already running as the home-screen app.
 */
function readDevice(): string {
  const nav = navigator as Navigator & { standalone?: boolean };
  const installed = window.matchMedia('(display-mode: standalone)').matches || nav.standalone === true;
  const ua = navigator.userAgent;
  // iPads report themselves as Macs; a touch screen gives them away.
  const iphone = /iPhone|iPad|iPod/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1);
  const device: Device = iphone ? 'iphone' : /Android/.test(ua) ? 'android' : 'computer';
  let dismissed = false;
  try { dismissed = localStorage.getItem(DISMISSED_KEY) === '1'; } catch { /* private mode: just show it */ }
  return `${installed ? 'installed' : 'browser'}|${device}|${dismissed ? 'dismissed' : 'shown'}`;
}

const noSubscribe = () => () => {};

/**
 * "Get the app on your phone": how to put the site on the home screen. Hidden
 * once it's running as the installed app, or after "Got it".
 */
export default function InstallAppCard() {
  const info = useSyncExternalStore(noSubscribe, readDevice, () => 'server');
  const [closed, setClosed] = useState(false);
  const [installPrompt, setInstallPrompt] = useState<InstallPromptEvent | null>(null);

  useEffect(() => {
    const onPrompt = (e: Event) => {
      e.preventDefault(); // Show our button instead of Chrome's mini bar.
      setInstallPrompt(e as InstallPromptEvent);
    };
    const onInstalled = () => setClosed(true);
    window.addEventListener('beforeinstallprompt', onPrompt);
    window.addEventListener('appinstalled', onInstalled);
    return () => {
      window.removeEventListener('beforeinstallprompt', onPrompt);
      window.removeEventListener('appinstalled', onInstalled);
    };
  }, []);

  const [where, device, seen] = info.split('|') as ['installed' | 'browser' | 'server', Device, string];
  if (info === 'server' || where === 'installed' || seen === 'dismissed' || closed) return null;

  function dismiss() {
    try { localStorage.setItem(DISMISSED_KEY, '1'); } catch { /* fine: it just shows again next time */ }
    setClosed(true);
  }

  async function install() {
    if (!installPrompt) return;
    await installPrompt.prompt();
    const { outcome } = await installPrompt.userChoice;
    setInstallPrompt(null);
    if (outcome === 'accepted') setClosed(true);
  }

  return (
    <section className="card p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-extrabold">Get the app on your phone</h2>
          <p className="mt-1 text-sm opacity-70">
            Put Grove Salons on your home screen. It opens like an app, full screen, and stays signed in.
          </p>
        </div>
        <button className="text-sm font-semibold opacity-60 hover:opacity-100" onClick={dismiss}>Got it</button>
      </div>

      {installPrompt && (
        <button className="btn mt-4" onClick={() => void install()}>Install app</button>
      )}

      <div className={`mt-4 grid gap-4 text-sm ${device === 'computer' ? 'sm:grid-cols-2' : ''}`}>
        {(device === 'iphone' || device === 'computer') && (
          <div>
            <div className="font-bold">iPhone or iPad</div>
            <ol className="mt-1 list-decimal space-y-0.5 pl-5">
              <li>Open this site in <b>Safari</b>.</li>
              <li>Tap the <b>Share</b> button (the square with an arrow, at the bottom or top of the screen).</li>
              <li>Scroll down and tap <b>Add to Home Screen</b>, then <b>Add</b>.</li>
            </ol>
          </div>
        )}
        {(device === 'android' || device === 'computer') && (
          <div>
            <div className="font-bold">Android</div>
            <ol className="mt-1 list-decimal space-y-0.5 pl-5">
              <li>Open this site in <b>Chrome</b>.</li>
              <li>Tap the <b>menu</b> (three dots, top right).</li>
              <li>Tap <b>Install app</b> or <b>Add to Home screen</b>, then <b>Install</b>.</li>
            </ol>
          </div>
        )}
      </div>
      {device === 'computer' && (
        <p className="mt-3 text-xs opacity-60">Open this page on your phone to see just the steps for it.</p>
      )}
    </section>
  );
}
