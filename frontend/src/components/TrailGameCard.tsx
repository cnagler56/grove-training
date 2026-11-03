'use client';

import { useState } from 'react';
import { api, type TrailGame, type TrailItem, type TrailMe, type TrailPace, type TrailParty } from '@/src/lib/api';
import { formatDate, formatDateTime, ordinal } from '@/src/lib/format';
import { useDialogs } from '@/src/components/Dialogs';

const PACES: { value: TrailPace; label: string; help: string }[] = [
  { value: 'LEISURELY', label: 'Leisurely', help: 'Slower, and things go wrong less often. Self care.' },
  { value: 'STEADY', label: 'Steady', help: 'The usual.' },
  { value: 'GRUELING', label: 'Grueling', help: 'A quarter faster, but you eat double and luck gets worse.' },
];

const MOUNTS: Record<string, string> = { WALK: 'On foot', DONKEY: 'Donkey', HORSE: 'Horse' };

/** Party members' names: "Ava Stone", "Ava Stone and Mia Cruz", "Ava, Mia, and Lee". */
function names(p: TrailParty): string {
  const n = p.members.map((m) => m.name);
  if (n.length <= 2) return n.join(' and ');
  return `${n.slice(0, -1).join(', ')}, and ${n[n.length - 1]}`;
}

/** One game: status, the map, the viewer's own party and decisions, standings, and the log. */
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
        <p className="mt-1 text-sm opacity-80">
          {g.turns} turns from {formatDate(g.startDate, false)}. Coins: 1 per dollar of service sales, plus {g.bonusTreatment} per
          treatment, {g.bonusColor} per color, and {g.bonusWax} per wax
          {g.handicap !== 'NONE' && ', with a handicap'}.
        </p>
        {g.prize && <p className="mt-1 text-sm"><b>Prize:</b> {g.prize}</p>}
        <p className="mt-1 text-xs opacity-60">
          {g.lastCheckedAt ? `Coins last counted ${formatDateTime(g.lastCheckedAt)}. ` : ''}
          A turn runs at 8, 12, 4, and 8 every day.
        </p>
        {g.checkProblem && <p className="mt-2 text-sm text-[var(--danger)]">Coins couldn&apos;t be counted last turn: {g.checkProblem}</p>}
        {isManager && (
          <div className="mt-3 flex flex-wrap gap-2">
            {g.status !== 'FINISHED' && g.startDate <= new Date().toLocaleDateString('en-CA') && (
              <button className="btn-ghost" disabled={busy} onClick={() => void act(() => api.trailTurn(g.id))}>Play the next turn now</button>
            )}
            <button className="text-sm font-bold text-[var(--danger)] hover:underline" onClick={onDelete}>Delete</button>
          </div>
        )}
        {error && <p className="mt-2 text-sm text-[var(--danger)]">{error}</p>}
      </div>

      <TrailMap game={g} myPartyId={g.me?.party.id} />

      {g.me && myParty && (
        <MyTrail game={g} me={g.me} party={myParty} busy={busy} act={act} />
      )}

      <Standings game={g} viewerId={viewerId} />

      <div>
        <h3 className="mb-2 text-lg font-extrabold">What happened</h3>
        {g.log.length === 0 ? (
          <p className="text-sm opacity-70">Nothing yet. The trail awaits.</p>
        ) : (
          <ol className="max-h-96 space-y-2 overflow-y-auto pr-1 text-sm">
            {g.log.map((l, i) => (
              <li key={i} className="flex gap-3">
                <span className="w-14 shrink-0 text-xs font-bold opacity-60">Turn {l.turn}</span>
                <span className={l.kind === 'THEFT' ? 'text-[var(--danger)]' : l.kind === 'LANDMARK' ? 'font-semibold' : ''}>{l.body}</span>
              </li>
            ))}
          </ol>
        )}
      </div>
    </section>
  );
}

/**
 * The route from Maple Grove to the coast, top to bottom: every landmark with its miles,
 * and each party as a labeled marker where it is. A thin bar above shows the whole trail.
 */
function TrailMap({ game: g, myPartyId }: { game: TrailGame; myPartyId: number | undefined }) {
  const at = (miles: number) => {
    let i = 0;
    g.landmarks.forEach((l, j) => { if (l.miles <= miles) i = j; });
    return i;
  };
  const pct = (miles: number) => `${(Math.min(miles, g.trailMiles) / g.trailMiles) * 100}%`;
  const mine = g.parties.find((p) => p.id === myPartyId);

  return (
    <div>
      <h3 className="mb-2 text-lg font-extrabold">The map</h3>
      <div className="relative mb-6 mt-1 h-2 rounded-full bg-[var(--surface-2)]" aria-hidden>
        {g.landmarks.map((l) => (
          <span key={l.name} className="absolute top-0 h-2 w-px bg-[var(--border)]" style={{ left: pct(l.miles) }} />
        ))}
        {g.parties.map((p) => (
          <span key={p.id}
            className={`absolute -top-1 h-4 w-4 -translate-x-1/2 rounded-full border-2 border-[var(--surface)] ${p.id === myPartyId ? 'bg-[var(--accent)]' : 'bg-[var(--foreground)] opacity-50'}`}
            style={{ left: pct(p.miles) }} />
        ))}
        {mine && (
          <span className="absolute top-4 -translate-x-1/2 whitespace-nowrap text-xs font-bold text-[var(--accent-strong)]"
            style={{ left: `clamp(1.5rem, ${pct(mine.miles)}, calc(100% - 1.5rem))` }}>You</span>
        )}
      </div>
      <ol className="relative ml-2 border-l-2 border-[var(--border)]">
        {g.landmarks.map((l, i) => {
          const here = g.parties.filter((p) => at(p.miles) === i);
          const last = i === g.landmarks.length - 1;
          return (
            <li key={l.name} className="relative pb-3 pl-5 last:pb-0">
              <span className={`absolute -left-[7px] top-1.5 h-3 w-3 rounded-full ${last ? 'bg-[var(--success)]' : 'bg-[var(--border)]'}`} />
              <div className="flex flex-wrap items-baseline gap-x-2 text-sm">
                <span className="font-bold">{l.name}</span>
                <span className="text-xs opacity-60">mile {l.miles}</span>
              </div>
              {here.length > 0 && (
                <ul className="mt-1 space-y-1">
                  {here.map((p) => (
                    <li key={p.id}
                      className={`rounded-lg px-2.5 py-1 text-sm ${p.id === myPartyId ? 'bg-[var(--accent)] text-[var(--accent-ink)]' : 'bg-[var(--surface-2)]'}`}>
                      <b>{names(p)}</b>
                      {p.id === myPartyId && ' (you)'}
                      <span className="opacity-80">
                        {p.arrivedTurn !== null ? `, at the coast since turn ${p.arrivedTurn}` : `, mile ${p.miles}, ${p.travel}`}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </li>
          );
        })}
      </ol>
    </div>
  );
}

function Standings({ game: g, viewerId }: { game: TrailGame; viewerId: number | undefined }) {
  return (
    <div>
      <h3 className="mb-2 text-lg font-extrabold">Standings</h3>
      <ol className="divide-y divide-[var(--border)]">
        {g.parties.map((p) => {
          const mine = p.members.some((m) => m.userId === viewerId);
          const tied = p.place !== null && g.parties.filter((o) => o.place === p.place).length > 1;
          return (
            <li key={p.id} className={`flex items-center gap-3 px-2 py-2.5 ${mine ? 'rounded-lg bg-[var(--accent-soft)]' : ''}`}>
              <span className="w-14 shrink-0 text-sm font-extrabold">{p.place === null ? '' : `${tied ? 'Tied ' : ''}${ordinal(p.place)}`}</span>
              <div className="min-w-0 flex-1">
                <div className="break-words font-bold">{names(p)}{mine && ' (you)'}</div>
                <div className="text-xs opacity-70">
                  {p.travel}{p.members.length > 1 && !p.travel.startsWith('Wagon') && `: ${p.members.map((m) => `${m.name.split(' ')[0]} ${m.mount.toLowerCase()}`).join(', ')}`}
                </div>
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
              <span className="shrink-0 text-right text-sm font-extrabold">
                {p.arrivedTurn !== null ? 'At the coast' : `${p.miles} mi`}
              </span>
            </li>
          );
        })}
      </ol>
    </div>
  );
}

/** The viewer's own party: this turn's scenario, open questions, supplies, the shop, and who to travel with. */
function MyTrail({ game: g, me, party, busy, act }: {
  game: TrailGame;
  me: TrailMe;
  party: TrailParty;
  busy: boolean;
  act: (a: () => Promise<TrailGame>) => Promise<void>;
}) {
  const dialogs = useDialogs();
  const p = me.party;
  const together = p.members.length > 1;
  const open = g.status !== 'FINISHED' && party.arrivedTurn === null;

  async function leave() {
    if (!(await dialogs.confirm({
      title: 'Leave your party?',
      message: 'You keep your coins and your animal and take your share of the snack packs. The wagon stays with them. You won\'t be able to join anyone for 4 turns.',
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
    <div className="space-y-5 rounded-xl border-2 border-[var(--accent)] p-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="text-lg font-extrabold">{together ? 'Your party' : 'You, going it alone'}</h3>
        <span className="text-sm font-bold">{me.coins.toLocaleString('en-US')} coins</span>
      </div>

      {party.arrivedTurn !== null && (
        <p className="rounded-lg bg-[var(--success-soft)] p-3 text-sm font-semibold text-[var(--success)]">
          You made it to the Oregon Coast on turn {party.arrivedTurn}. Enjoy Haystack Rock while everyone else catches up.
        </p>
      )}

      {me.choices.map((c) => (
        <div key={c.id} className={`rounded-lg p-3 ${c.kind === 'SKIM' ? 'bg-[var(--surface-2)]' : 'bg-[var(--accent-soft)]'}`}>
          <p className="font-bold">{c.title}</p>
          <p className="mt-1 text-sm opacity-80">{c.text}</p>
          {c.answer && c.answeredByName && (
            <p className="mt-1 text-sm font-semibold">
              {c.answeredByName} picked: {c.options.find((o) => o.value === c.answer)?.label}. You can still change it until the next turn.
            </p>
          )}
          <div className="mt-2 flex flex-wrap gap-2">
            {c.options.map((o) => (
              <button key={o.value} disabled={busy}
                className={c.answer === o.value ? 'btn' : 'btn-ghost'}
                onClick={() => void act(() => api.trailAnswer(g.id, c.id, o.value))}>
                {o.label}
              </button>
            ))}
          </div>
        </div>
      ))}

      {me.scenario && (
        <div>
          <p className="text-xs font-bold uppercase tracking-wide opacity-60">This turn</p>
          <p className="mt-1 text-lg font-bold">{me.scenario.text}</p>
          <p className="text-xs opacity-70">
            {me.scenario.chosen !== null
              ? `${me.scenario.chosenByName ?? 'Someone'} picked. It plays out at the next turn; anyone in the party can still change it.`
              : 'It plays out at the next turn. If nobody picks, the party goes with the safe choice.'}
          </p>
          <ul className="mt-2 space-y-2">
            {me.scenario.options.map((o) => {
              const picked = me.scenario!.chosen === o.index;
              return (
                <li key={o.index}>
                  <button disabled={busy || !o.available}
                    onClick={() => void act(() => api.trailChoose(g.id, o.index))}
                    className={`w-full rounded-lg border-2 p-3 text-left text-sm transition ${picked
                      ? 'border-[var(--accent)] bg-[var(--accent-soft)]'
                      : 'border-[var(--border)] hover:border-[var(--accent)]'} ${o.available ? '' : 'opacity-50'}`}>
                    <span className="font-bold">{o.label}</span>
                    {picked && <span className="ml-2 text-xs font-bold text-[var(--accent-strong)]">Picked</span>}
                    {o.isDefault && !picked && <span className="ml-2 text-xs opacity-60">The safe choice</span>}
                    <span className="mt-1 block opacity-80">
                      {o.bad === null ? o.good : <>Could go well: {o.good} Could go badly: {o.bad}</>}
                    </span>
                    {!o.available && <span className="mt-1 block text-xs font-semibold">You don&apos;t have what this needs.</span>}
                  </button>
                </li>
              );
            })}
          </ul>
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-1 text-sm">
          <p><b>Snack packs:</b> {p.food}{p.hangry && <span className="font-bold text-[var(--danger)]"> (Hangry: half speed)</span>}</p>
          <p><b>Getting around:</b> {party.travel}</p>
          {p.wagon && <p><b>Wagon:</b> {p.wagonBroken ? 'broken down. It won\'t move until it\'s repaired.' : 'rolling'}</p>}
          {(p.spareWheels > 0 || p.horseshoes > 0 || p.carrots > 0 || p.playlists > 0) && (
            <p><b>Packed:</b> {[
              p.spareWheels > 0 && `${p.spareWheels} spare ${p.spareWheels === 1 ? 'wheel' : 'wheels'}`,
              p.horseshoes > 0 && `${p.horseshoes} horseshoes`,
              p.carrots > 0 && `${p.carrots} carrots`,
              p.playlists > 0 && 'A Really Good Playlist',
            ].filter(Boolean).join(', ')}</p>
          )}
          {p.puppy && <p><b>Also:</b> a puppy. Morale is high.</p>}
          {together && (
            <ul className="pt-1">
              {p.members.map((m) => (
                <li key={m.userId}>
                  {m.name}: {m.coins.toLocaleString('en-US')} coins, {MOUNTS[m.mount]}{m.shoeThrown && ' (thrown shoe)'}
                </li>
              ))}
            </ul>
          )}
        </div>
        {open && (
          <div className="space-y-2 text-sm">
            <label className="block">
              <span className="mb-1 block font-semibold opacity-80">Pace</span>
              <select className="input cursor-pointer" value={p.pace} disabled={busy}
                onChange={(e) => void act(() => api.trailPace(g.id, e.target.value as TrailPace))}>
                {PACES.map((x) => <option key={x.value} value={x.value}>{x.label}</option>)}
              </select>
              <span className="mt-1 block text-xs opacity-70">{PACES.find((x) => x.value === p.pace)?.help}</span>
            </label>
            <div className="flex flex-wrap gap-2 pt-1">
              {together && <button className="btn-ghost" disabled={busy} onClick={() => void leave()}>Leave the party</button>}
              {p.wagon && <button className="btn-ghost" disabled={busy} onClick={() => void abandon()}>Abandon the wagon</button>}
            </div>
          </div>
        )}
      </div>

      {open && <Shop game={g} me={me} busy={busy} act={act} />}

      {open && (
        <div>
          <h4 className="font-extrabold">Travel with someone</h4>
          {me.noJoinUntil > g.turn ? (
            <p className="text-sm opacity-80">You need some space until turn {me.noJoinUntil}.</p>
          ) : me.canInvite.length === 0 ? (
            <p className="text-sm opacity-70">
              Nobody traveling alone is within 60 miles of you right now{p.members.length >= 4 ? ', and your party is full anyway' : ''}.
            </p>
          ) : (
            <>
              <p className="text-sm opacity-70">
                A party shares food, a wagon, and buying power, but eats more, takes bad luck together, moves at its slowest member&apos;s pace without a wagon, and splits the prize.
              </p>
              <ul className="mt-2 flex flex-wrap gap-2">
                {me.canInvite.map((t) => (
                  <li key={t.userId}>
                    <button className="btn-ghost" disabled={busy} onClick={() => void act(() => api.trailInvite(g.id, t.userId))}>
                      Invite {t.name} (mile {t.miles})
                    </button>
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      )}

      {me.purchases.length > 0 && (
        <details className="text-sm">
          <summary className="cursor-pointer font-extrabold">Who paid for what</summary>
          <ul className="mt-2 space-y-1">
            {me.purchases.map((l, i) => (
              <li key={i}><span className="mr-2 text-xs font-bold opacity-60">Turn {l.turn}</span>{l.body}</li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}

/** Items that make sense for this party right now. Several of a thing are bought with a quantity. */
function Shop({ game: g, me, busy, act }: {
  game: TrailGame;
  me: TrailMe;
  busy: boolean;
  act: (a: () => Promise<TrailGame>) => Promise<void>;
}) {
  const p = me.party;
  const together = p.members.length > 1;
  const pooled = p.members.reduce((sum, m) => sum + m.coins, 0);
  const [quantity, setQuantity] = useState<Record<string, number>>({ FOOD: Math.max(1, p.members.length * 4) });
  const shown = g.shop.filter((s) => {
    switch (s.item) {
      case 'DONKEY': return me.mount === 'WALK';
      case 'HORSE': return me.mount !== 'HORSE';
      case 'WAGON': return !p.wagon;
      case 'REPAIR': return p.wagonBroken;
      case 'HORSESHOES': return p.members.some((m) => m.mount === 'HORSE');
      case 'CARROTS': return p.members.some((m) => m.mount === 'DONKEY');
      case 'PLAYLIST': return together;
      default: return true;
    }
  });
  const many = (item: TrailItem) => ['FOOD', 'SPARE_WHEEL', 'HORSESHOES', 'CARROTS', 'PLAYLIST'].includes(item);

  return (
    <div>
      <h4 className="font-extrabold">The general store</h4>
      <p className="text-sm opacity-70">
        {together ? `Your party has ${pooled.toLocaleString('en-US')} coins between you. Split buys share the cost evenly among whoever has coins.` : 'Prices follow the team\'s usual sales.'}
      </p>
      <ul className="mt-2 divide-y divide-[var(--border)]">
        {shown.map((s) => {
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
