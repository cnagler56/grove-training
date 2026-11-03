'use client';

import { useState } from 'react';
import { api, type TrailGame, type TrailItem, type TrailMe, type TrailParty } from '@/src/lib/api';
import { formatDate, ordinal } from '@/src/lib/format';
import { useDialogs } from '@/src/components/Dialogs';

/** Party members' names: "Ava Stone", "Ava Stone and Mia Cruz", "Ava, Mia, and Lee". */
function names(p: TrailParty): string {
  const n = p.members.map((m) => m.name);
  if (n.length <= 2) return n.join(' and ');
  return `${n.slice(0, -1).join(', ')}, and ${n[n.length - 1]}`;
}

/** "The Crow River" reads as "the Crow River" mid-sentence. */
const inSentence = (place: string) => place.replace(/^The /, 'the ');

const firstNames = (p: TrailParty) => p.members.map((m) => m.name.split(' ')[0]).join(' and ');

/** One game: the map, then the viewer's turn. Standings and the log are tucked away below. */
export default function TrailGameCard({ game: g, viewerId, isManager, onChange, onDelete }: {
  game: TrailGame;
  viewerId: number | undefined;
  isManager: boolean;
  onChange: (g: TrailGame) => void;
  onDelete: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const myParty = g.me ? g.parties.find((p) => p.id === g.me!.party.id) : undefined;

  async function act(action: () => Promise<TrailGame>) {
    setBusy(true);
    setError(null);
    try {
      onChange(await action());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong');
    } finally {
      setBusy(false);
    }
  }

  const status = g.status === 'FINISHED' ? 'Finished'
    : g.status === 'UPCOMING' ? `Starts ${formatDate(g.startDate, false)}`
    : `Turn ${g.turn} of ${g.turns}`;

  return (
    <section className="card space-y-5 p-5">
      <div>
        <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
          <h2 className="text-xl font-extrabold">{g.name}</h2>
          <span className={`text-sm font-bold ${g.status === 'LIVE' ? 'text-[var(--success)]' : 'opacity-70'}`}>{status}</span>
        </div>
        {g.prize && <p className="mt-1 text-sm"><b>Prize:</b> {g.prize}</p>}
        {g.checkProblem && <p className="mt-2 text-sm text-[var(--danger)]">Coins couldn&apos;t be counted: {g.checkProblem}</p>}
        {isManager && (
          <div className="mt-3 flex flex-wrap gap-2">
            {g.status !== 'FINISHED' && g.startDate <= new Date().toLocaleDateString('en-CA') && (
              <button className="btn-ghost" disabled={busy} onClick={() => void act(() => api.trailUnlock(g.id))}>
                {g.turn >= g.turns ? 'End the game now' : 'Unlock the next turn now'}
              </button>
            )}
            <button className="text-sm font-bold text-[var(--danger)] hover:underline" onClick={onDelete}>Delete</button>
          </div>
        )}
      </div>

      {error && <p className="text-sm text-[var(--danger)]">{error}</p>}

      <TrailMap game={g} myPartyId={g.me?.party.id} />

      {g.me && myParty && <MyTurn game={g} me={g.me} party={myParty} busy={busy} act={act} />}

      <details className="text-sm">
        <summary className="cursor-pointer font-extrabold">Standings</summary>
        <Standings game={g} viewerId={viewerId} />
      </details>

      <details className="text-sm">
        <summary className="cursor-pointer font-extrabold">Everything that&apos;s happened</summary>
        {g.log.length === 0 ? (
          <p className="mt-2 opacity-70">Nothing yet. The trail awaits.</p>
        ) : (
          <ol className="mt-2 max-h-96 space-y-2 overflow-y-auto pr-1">
            {g.log.map((l, i) => (
              <li key={i} className="flex gap-3">
                <span className="w-14 shrink-0 text-xs font-bold opacity-60">Turn {l.turn}</span>
                <span className={l.kind === 'THEFT' ? 'text-[var(--danger)]' : l.kind === 'LANDMARK' ? 'font-semibold' : ''}>{l.body}</span>
              </li>
            ))}
          </ol>
        )}
      </details>

      {g.me && g.me.purchases.length > 0 && (
        <details className="text-sm">
          <summary className="cursor-pointer font-extrabold">Who paid for what</summary>
          <ul className="mt-2 space-y-1">
            {g.me.purchases.map((l, i) => (
              <li key={i}><span className="mr-2 text-xs font-bold opacity-60">Turn {l.turn}</span>{l.body}</li>
            ))}
          </ul>
        </details>
      )}
    </section>
  );
}

// The map: the overland route plotted from real coordinates, Maple Grove on the right, the Pacific on the left.

const WEST = -125.2, EAST = -90.6, NORTH = 48.3, SOUTH = 44.9;
const x = (lon: number) => 3 + ((lon - WEST) / (EAST - WEST)) * 94;
const y = (lat: number) => 12 + ((NORTH - lat) / (NORTH - SOUTH)) * 76;

const TERRITORIES = [
  { name: 'Oregon', lon: -121.6, lat: 45.1 },
  { name: 'Washington Territory', lon: -119.6, lat: 47.9 },
  { name: 'Idaho Territory', lon: -115.2, lat: 45.4 },
  { name: 'Montana Territory', lon: -109.0, lat: 47.6 },
  { name: 'Dakota Territory', lon: -100.0, lat: 46.2 },
  { name: 'Minnesota', lon: -94.5, lat: 47.6 },
];

/** Where on the map someone this many miles along is: between the two landmarks around them. */
function spot(g: TrailGame, miles: number): { x: number; y: number } {
  const ls = g.landmarks;
  for (let i = 0; i < ls.length - 1; i++) {
    const a = ls[i], b = ls[i + 1];
    if (miles <= b.miles) {
      const t = (miles - a.miles) / (b.miles - a.miles || 1);
      return { x: x(a.lon + (b.lon - a.lon) * t), y: y(a.lat + (b.lat - a.lat) * t) };
    }
  }
  const last = ls[ls.length - 1];
  return { x: x(last.lon), y: y(last.lat) };
}

function TrailMap({ game: g, myPartyId }: { game: TrailGame; myPartyId: number | undefined }) {
  const me = g.parties.find((p) => p.id === myPartyId);
  const points = g.landmarks.map((l) => `${x(l.lon)},${y(l.lat)}`).join(' ');
  // The route walked so far, up to where the viewer is.
  const walked = me ? [...g.landmarks.filter((l) => l.miles <= me.miles).map((l) => ({ x: x(l.lon), y: y(l.lat) })), spot(g, me.miles)] : [];
  const nextStop = me ? g.landmarks.find((l) => l.miles > me.miles) : undefined;
  const start = g.landmarks[0], end = g.landmarks[g.landmarks.length - 1];

  // Everyone else, with names stacked so close neighbors don't overlap.
  const others = g.parties.filter((p) => p.id !== myPartyId)
    .map((p) => ({ p, at: spot(g, p.miles) }))
    .sort((a, b) => a.at.x - b.at.x);
  const rows: number[] = [];
  const placed = others.map((o) => {
    let row = 0;
    while (rows[row] !== undefined && o.at.x - rows[row] < 9) row++;
    rows[row] = o.at.x;
    return { ...o, row };
  });

  return (
    <div>
      <div className="relative h-56 w-full overflow-hidden rounded-xl bg-[var(--surface-2)] sm:h-72">
        <svg className="absolute inset-0 h-full w-full" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden>
          <polyline points={points} fill="none" stroke="var(--foreground)" strokeOpacity="0.3" strokeWidth="2" strokeDasharray="6 6"
            vectorEffect="non-scaling-stroke" strokeLinejoin="round" strokeLinecap="round" />
          {walked.length > 1 && (
            <polyline points={walked.map((p) => `${p.x},${p.y}`).join(' ')} fill="none" stroke="var(--accent)" strokeWidth="4"
              vectorEffect="non-scaling-stroke" strokeLinejoin="round" strokeLinecap="round" />
          )}
        </svg>
        {TERRITORIES.map((s) => (
          <span key={s.name} className="pointer-events-none absolute -translate-x-1/2 text-[10px] font-bold uppercase tracking-widest opacity-25 sm:text-xs"
            style={{ left: `${x(s.lon)}%`, top: `${y(s.lat)}%` }}>{s.name}</span>
        ))}
        {g.landmarks.map((l) => (
          <span key={l.name} title={`${l.name}, mile ${l.miles}`}
            className={`absolute h-2 w-2 -translate-x-1/2 -translate-y-1/2 rounded-full ${l.miles === g.trailMiles ? 'bg-[var(--success)]' : 'bg-[var(--foreground)] opacity-40'}`}
            style={{ left: `${x(l.lon)}%`, top: `${y(l.lat)}%` }} />
        ))}
        <span className="pointer-events-none absolute -translate-x-1/2 whitespace-nowrap text-[10px] font-bold opacity-70 sm:text-xs"
          style={{ left: `${x(start.lon)}%`, top: `calc(${y(start.lat)}% + 24px)` }}>Maple Grove</span>
        <span className="pointer-events-none absolute -translate-x-1/2 whitespace-nowrap text-[10px] font-bold text-[var(--success)] sm:text-xs"
          style={{ left: `${x(end.lon)}%`, top: `calc(${y(end.lat)}% - 26px)` }}>The Pacific</span>
        {placed.map(({ p, at, row }) => (
          <div key={p.id} className="absolute" style={{ left: `${at.x}%`, top: `${at.y}%` }}>
            <span className="absolute h-3 w-3 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-[var(--surface)] bg-[var(--foreground)] opacity-60" />
            <span className="absolute z-20 -translate-x-1/2 whitespace-nowrap rounded bg-[var(--surface)] px-1 text-[10px] font-semibold opacity-80 sm:text-xs"
              style={{ top: `${-26 - row * 16}px` }}>{firstNames(p)}</span>
          </div>
        ))}
        {me && (
          <div className="absolute z-10" style={{ left: `${spot(g, me.miles).x}%`, top: `${spot(g, me.miles).y}%` }}>
            <span className="absolute h-4 w-4 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-[var(--surface)] bg-[var(--accent)]" />
            <span className="absolute -translate-x-1/2 whitespace-nowrap rounded bg-[var(--accent)] px-1.5 text-xs font-extrabold text-[var(--accent-ink)]"
              style={{ top: '12px' }}>You</span>
          </div>
        )}
      </div>
      {me && (
        <p className="mt-2 text-sm">
          {me.arrivedTurn !== null
            ? <>You reached the Pacific in {me.arrivedTurn} turns.</>
            : <><b>Mile {me.miles}</b> of {g.trailMiles.toLocaleString('en-US')}, past {inSentence(me.at)}{nextStop && `. ${nextStop.miles - me.miles} miles to ${inSentence(nextStop.name)}`}.</>}
        </p>
      )}
    </div>
  );
}

function Standings({ game: g, viewerId }: { game: TrailGame; viewerId: number | undefined }) {
  return (
    <ol className="mt-2 divide-y divide-[var(--border)]">
      {g.parties.map((p) => {
        const mine = p.members.some((m) => m.userId === viewerId);
        const tied = p.place !== null && g.parties.filter((o) => o.place === p.place).length > 1;
        return (
          <li key={p.id} className={`flex items-center gap-3 px-2 py-2 ${mine ? 'rounded-lg bg-[var(--accent-soft)]' : ''}`}>
            <span className="w-14 shrink-0 font-extrabold">{p.place === null ? '' : `${tied ? 'Tied ' : ''}${ordinal(p.place)}`}</span>
            <div className="min-w-0 flex-1">
              <div className="break-words font-bold">{names(p)}{mine && ' (you)'}</div>
              <div className="text-xs opacity-70">{p.travel}</div>
              {p.members.some((m) => m.labels.length > 0) && (
                <div className="mt-1 flex flex-wrap gap-1">
                  {p.members.flatMap((m) => m.labels.map((label) => (
                    <span key={`${m.userId}-${label}`}
                      className={`rounded-full px-2 py-0.5 text-xs font-bold ${label === 'Sticky Fingers' ? 'bg-[var(--danger)] text-white' : 'bg-[var(--success-soft)] text-[var(--success)]'}`}>
                      {p.members.length > 1 ? `${m.name.split(' ')[0]}: ` : ''}{label}
                    </span>
                  )))}
                </div>
              )}
            </div>
            <span className="shrink-0 text-right font-extrabold">{p.arrivedTurn !== null ? 'At the Pacific' : `${p.miles} mi`}</span>
          </li>
        );
      })}
    </ol>
  );
}

/** The viewer's turn: what just happened, anything to answer, four choices and Go, and a trading post when there is one. */
function MyTurn({ game: g, me, party, busy, act }: {
  game: TrailGame;
  me: TrailMe;
  party: TrailParty;
  busy: boolean;
  act: (a: () => Promise<TrailGame>) => Promise<void>;
}) {
  const dialogs = useDialogs();
  const p = me.party;
  const together = p.members.length > 1;
  const turn = me.turn;
  const [picked, setPicked] = useState<string | null>(null);
  // Nothing is picked until you pick it: no hints about which choice is safe.
  const choice = picked !== null && turn?.options.some((o) => o.value === picked && o.available) ? picked : null;

  async function go() {
    if (!turn || choice === null) return;
    await act(() => api.trailPlay(g.id, turn.kind === 'MEETING' ? { answer: choice } : { option: Number(choice) }));
    setPicked(null);
  }

  async function leave() {
    if (!(await dialogs.confirm({
      title: 'Leave your party?',
      message: 'You keep your coins and your animal and take your share of the provisions. The wagon stays with them.',
      confirmLabel: 'Leave',
      danger: true,
    }))) return;
    await act(() => api.trailLeave(g.id));
  }

  async function abandon() {
    if (!(await dialogs.confirm({
      title: 'Abandon the wagon?',
      message: 'No refund. Everyone goes back to their own animal, or their own feet.',
      confirmLabel: 'Abandon it',
      danger: true,
    }))) return;
    await act(() => api.trailAbandonWagon(g.id));
  }

  return (
    <div className="space-y-4">
      <p className="flex flex-wrap gap-x-4 gap-y-1 text-sm">
        <span><b>{me.coins.toLocaleString('en-US')}</b> coins</span>
        <span><b>{p.food}</b> provisions{p.hangry && <b className="text-[var(--danger)]"> (hungry: half speed)</b>}</span>
        <span>{party.travel}</span>
        {together && <span>With {p.members.filter((m) => m.userId !== me.userId).map((m) => m.name).join(', ')}</span>}
      </p>

      {me.lastResult.length > 0 && (
        <div className="rounded-xl bg-[var(--surface-2)] p-4">
          <p className="text-xs font-bold uppercase tracking-wide opacity-60">Turn {me.turnsPlayed}</p>
          {me.lastResult.map((line, i) => <p key={i} className="mt-1">{line}</p>)}
        </div>
      )}

      {me.choices.map((c) => (
        <div key={c.id} className="rounded-xl border-2 border-[var(--accent)] p-4">
          <p className="font-bold">{c.title}</p>
          <p className="mt-1 text-sm opacity-80">{c.text}</p>
          <div className="mt-3 flex flex-wrap gap-2">
            {c.options.map((o) => (
              <button key={o.value} className="btn-ghost" disabled={busy}
                onClick={() => void act(() => api.trailAnswer(g.id, c.id, o.value))}>{o.label}</button>
            ))}
          </div>
        </div>
      ))}

      {party.arrivedTurn !== null ? (
        <p className="rounded-xl bg-[var(--success-soft)] p-4 font-semibold text-[var(--success)]">
          You reached the Pacific. Enjoy Haystack Rock while everyone else catches up.
        </p>
      ) : !turn ? (
        <div className="rounded-xl border-2 border-[var(--accent)] p-4">
          <p className="text-xs font-bold uppercase tracking-wide opacity-60">Turn 1</p>
          <p className="mt-1 text-lg font-bold">The frontier awaits.</p>
          <p className="mt-1 text-sm opacity-80">
            {g.status === 'UPCOMING' && g.startDate > new Date().toLocaleDateString('en-CA')
              ? `The game starts ${formatDate(g.startDate, false)}. `
              : 'Your first turn unlocks at the next Salon Ultimate check: 8, 12, 4, or 8. '}
            Stock up at the trading post before you leave.
          </p>
        </div>
      ) : (
        <div className="rounded-xl border-2 border-[var(--accent)] p-4">
          <p className="text-xs font-bold uppercase tracking-wide opacity-60">Turn {me.turnsPlayed + 1}</p>
          <p className="mt-1 text-lg font-bold">{turn.text}</p>
          <div className="mt-3 grid gap-2 sm:grid-cols-2" role="radiogroup">
            {turn.options.map((o) => {
              const selected = choice === o.value;
              return (
                <button key={o.value} role="radio" aria-checked={selected} disabled={!o.available || !me.ready || busy}
                  onClick={() => setPicked(o.value)}
                  className={`w-full rounded-lg border-2 p-3 text-left text-sm font-bold transition ${selected
                    ? 'border-[var(--accent)] bg-[var(--accent-soft)]'
                    : 'border-[var(--border)] hover:border-[var(--accent)]'} ${o.available ? '' : 'opacity-50'}`}>
                  {o.label}
                </button>
              );
            })}
          </div>
          <div className="mt-4 flex flex-wrap items-center gap-3">
            <button className="btn" disabled={!me.ready || busy || choice === null} onClick={() => void go()}>{busy ? 'Going…' : 'Go'}</button>
            {me.ready && choice === null && <span className="text-sm opacity-70">Pick one, then press Go.</span>}
            {!me.ready && (
              <span className="text-sm opacity-70">
                {g.status === 'UPCOMING' ? 'The first turn unlocks when the game starts.' : 'Your next turn unlocks at the next check: 8, 12, 4, or 8.'}
              </span>
            )}
          </div>
        </div>
      )}

      {p.wagonBroken && party.arrivedTurn === null && (
        <div className="rounded-xl bg-[var(--surface-2)] p-4 text-sm">
          <p className="font-bold">The wagon is broken down. It won&apos;t move until it&apos;s fixed.</p>
          <div className="mt-2 flex flex-wrap gap-2">
            <button className="btn-ghost" disabled={busy || me.coins < me.repairPrice}
              onClick={() => void act(() => api.trailBuy(g.id, 'REPAIR', 1, false))}>Send for a wheelwright, {me.repairPrice.toLocaleString('en-US')}</button>
            {together && (
              <button className="btn-ghost" disabled={busy} onClick={() => void act(() => api.trailBuy(g.id, 'REPAIR', 1, true))}>Split the wheelwright</button>
            )}
            <button className="btn-ghost" disabled={busy} onClick={() => void abandon()}>Abandon the wagon</button>
          </div>
        </div>
      )}

      {me.store && <Store game={g} me={me} busy={busy} act={act} />}

      {together && party.arrivedTurn === null && (
        <button className="text-sm font-semibold text-[var(--accent-strong)] hover:underline" disabled={busy} onClick={() => void leave()}>
          Leave the party
        </button>
      )}
    </div>
  );
}

/** A trading post, open until you move on. Shows only what makes sense for you right now. */
function Store({ game: g, me, busy, act }: {
  game: TrailGame;
  me: TrailMe;
  busy: boolean;
  act: (a: () => Promise<TrailGame>) => Promise<void>;
}) {
  const p = me.party;
  const together = p.members.length > 1;
  const pooled = p.members.reduce((sum, m) => sum + m.coins, 0);
  const [quantity, setQuantity] = useState<Record<string, number>>({ FOOD: Math.max(1, p.members.length * 4) });
  const items = (me.store?.items ?? []).filter((s) => {
    switch (s.item) {
      case 'DONKEY': return me.mount === 'WALK';
      case 'HORSE': return me.mount !== 'HORSE';
      case 'WAGON': return !p.wagon;
      case 'SPARE_WHEEL': return p.wagon;
      case 'HORSESHOES': return p.members.some((m) => m.mount === 'HORSE');
      case 'CARROTS': return p.members.some((m) => m.mount === 'DONKEY');
      case 'PLAYLIST': return together;
      default: return true;
    }
  });
  const many = (item: TrailItem) => ['FOOD', 'SPARE_WHEEL', 'HORSESHOES', 'CARROTS', 'PLAYLIST'].includes(item);
  const isTrader = me.store?.name.startsWith('A ');

  return (
    <div className="rounded-xl bg-[var(--surface-2)] p-4">
      <h4 className="font-extrabold">{isTrader ? me.store?.name : `Trading post at ${me.store?.name.replace(/, Minnesota Territory$/, '')}`}</h4>
      <p className="text-sm opacity-70">
        Open until you move on.{together ? ` Your party has ${pooled.toLocaleString('en-US')} coins between you.` : ''}
      </p>
      <ul className="mt-2 divide-y divide-[var(--border)]">
        {items.map((s) => {
          const q = many(s.item) ? quantity[s.item] ?? 1 : 1;
          const total = s.price * q;
          return (
            <li key={s.item} className="flex flex-wrap items-center gap-x-3 gap-y-2 py-2.5 text-sm">
              <div className="min-w-0 flex-1 basis-48">
                <span className="font-bold">{s.label}</span>
                <span className="ml-2 opacity-70">{s.price.toLocaleString('en-US')} each</span>
                <span className="block text-xs opacity-70">{s.help}</span>
              </div>
              {many(s.item) && (
                <input className="input w-20 py-1" type="number" min={1} max={s.item === 'FOOD' ? 60 : 5} value={q}
                  aria-label={`How many ${s.label}`}
                  onChange={(e) => setQuantity({ ...quantity, [s.item]: Math.max(1, Number(e.target.value) || 1) })} />
              )}
              <div className="flex flex-wrap gap-2">
                <button className="btn-ghost py-1" disabled={busy || me.coins < total}
                  onClick={() => void act(() => api.trailBuy(g.id, s.item, q, false))}>
                  Buy, {total.toLocaleString('en-US')}
                </button>
                {together && s.item !== 'DONKEY' && s.item !== 'HORSE' && (
                  <button className="btn-ghost py-1" disabled={busy || pooled < total}
                    onClick={() => void act(() => api.trailBuy(g.id, s.item, q, true))}>
                    Split it
                  </button>
                )}
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
