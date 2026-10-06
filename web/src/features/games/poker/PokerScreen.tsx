import { useEffect, useMemo, useRef, useState } from 'react';
import { AnimatePresence, m as motion } from 'motion/react';
import { useReadyWallet } from '@/data/wallet';
import { ACTION_LABEL, allBotsOut, CATEGORY_NAME, inHand, legalActions, POKER_TABLES, pot, type PokerState, type Seat } from '@/engine/poker';
import { chips, grouped } from '@/lib/format';
import { play, resultSound } from '@/audio/sound';
import { Button } from '@/ui/Button';
import { ChipStack } from '@/ui/Chip';
import { CHIP_DROP, EASE_IN_OUT, EASE_OUT } from '@/ui/motion';
import type { Card } from '@/engine/cards';
import { Celebration } from '../shared/Celebration';
import { useDealFrom } from '../shared/deal';
import { FlipCard } from '../shared/FlipCard';
import { GameShell, TableNotice, useTableSize } from '../shared/GameShell';
import { useHoldProgressEvents } from '../shared/session';
import { HERO, usePokerTable } from './usePokerTable';

export default function PokerScreen() {
  const poker = usePokerTable();
  const { table } = poker;
  const inPlay = table?.phase === 'betting';
  useHoldProgressEvents(Boolean(inPlay));

  return (
    <GameShell
      title="Póker · Texas Hold’em"
      surface="dark"
      notice={poker.notice && <TableNotice onDismiss={poker.dismissNotice}>{poker.notice}</TableNotice>}
      controls={table ? <Controls table={table} poker={poker} /> : undefined}
    >
      {poker.loading ? null : table ? <PokerTable table={table} /> : <SitDown onSit={poker.sitDown} busy={poker.busy} />}
    </GameShell>
  );
}

// ── Sentarse ─────────────────────────────────────────────────────────────────────────────────

function SitDown({ onSit, busy }: { onSit: (table: number, buyIn: number) => void; busy: boolean }) {
  const wallet = useReadyWallet();
  const balance = wallet?.balance ?? 0;
  const [selected, setSelected] = useState(0);
  const rules = POKER_TABLES[selected]!;
  const maxBuyIn = Math.min(rules.maxBuyIn, Math.floor(balance / rules.chipUnit) * rules.chipUnit);
  const [buyIn, setBuyIn] = useState(Math.round((rules.minBuyIn + rules.maxBuyIn) / 2 / 10) * 10);
  const clamped = Math.max(Math.min(buyIn, maxBuyIn), Math.min(rules.minBuyIn, maxBuyIn));
  const canSit = maxBuyIn >= rules.minBuyIn;
  const wide = useTableSize().width >= 640;

  return (
    <div className="flex h-full items-center justify-center overflow-y-auto px-4 py-2">
      <div className="flex w-full max-w-3xl items-center gap-6">
        <div className={wide ? 'flex-1' : 'hidden'}>
          <h2 className="font-display text-2xl font-semibold text-gold-gradient">Texas Hold’em sin límite</h2>
          <p className="mt-2 text-sm leading-relaxed text-ivory-dim">
            Seis asientos: tú y cinco bots con estilos distintos. Las fichas que llevas a la mesa son lo máximo que puedes arriesgar en una mano; cada ficha que pones en el bote se descuenta de tu saldo y lo que ganas vuelve a él al terminar la mano.
          </p>
        </div>
        <div className="panel w-full max-w-sm rounded-3xl p-4">
          <div className="grid grid-cols-2 gap-2" role="radiogroup" aria-label="Mesa">
            {POKER_TABLES.map((t, i) => (
              <button
                key={i}
                type="button"
                role="radio"
                aria-checked={selected === i}
                onClick={() => {
                  setSelected(i);
                  setBuyIn(Math.round((t.minBuyIn + t.maxBuyIn) / 2 / 10) * 10);
                }}
                className={`rounded-2xl px-3 py-2.5 text-left ring-1 ${selected === i ? 'bg-gold/15 ring-gold' : 'bg-ink-1 ring-white/10'}`}
              >
                <span className="block font-display text-[15px] font-semibold text-gold-light">
                  Ciegas {grouped(t.smallBlind)}/{grouped(t.bigBlind)}
                </span>
                <span className="block text-xs text-ivory-dim">
                  Entrada {grouped(t.minBuyIn)} – {grouped(t.maxBuyIn)}
                </span>
              </button>
            ))}
          </div>
          <label className="mt-4 block">
            <span className="flex items-baseline justify-between text-sm">
              <span className="font-semibold text-ivory">Fichas a la mesa</span>
              <span className="tabular font-bold text-gold-light">{chips(clamped)}</span>
            </span>
            <input
              type="range"
              min={Math.min(rules.minBuyIn, maxBuyIn)}
              max={Math.max(maxBuyIn, rules.minBuyIn)}
              step={rules.chipUnit}
              value={clamped}
              disabled={!canSit}
              onChange={(e) => setBuyIn(Number(e.target.value))}
              className="mt-2 w-full accent-[#d4af6a]"
            />
          </label>
          <Button block size="lg" className="mt-3" disabled={!canSit} loading={busy} onClick={() => onSit(selected, clamped)}>
            {canSit ? 'Sentarse' : 'No tienes fichas suficientes'}
          </Button>
        </div>
      </div>
    </div>
  );
}

// ── Mesa ─────────────────────────────────────────────────────────────────────────────────────

/** Posición de cada asiento alrededor del óvalo (fracciones del área). El jugador, abajo en el centro. */
const SEAT_POS: [number, number][] = [
  [0.5, 0.9],
  [0.13, 0.74],
  [0.13, 0.24],
  [0.5, 0.08],
  [0.87, 0.24],
  [0.87, 0.74],
];
/** Donde se dejan las apuestas de cada asiento (hacia el centro). */
const BET_POS: [number, number][] = [
  [0.5, 0.68],
  [0.27, 0.66],
  [0.27, 0.34],
  [0.5, 0.27],
  [0.73, 0.34],
  [0.73, 0.66],
];

function PokerTable({ table }: { table: PokerState }) {
  const size = useTableSize();
  const height = Math.max(200, size.height);
  // Tamaño en píxeles de la mesa (máximo max-w-5xl), para mover fichas y cartas hacia el centro.
  const W = Math.min(size.width, 1024);
  const toCenter = (x: number, y: number) => ({ dx: (0.5 - x) * W, dy: (0.5 - y) * height });
  const boardCard = Math.round(Math.min(64, Math.max(34, height * 0.17)));
  const heroCard = Math.round(Math.min(72, Math.max(40, height * 0.2)));
  const botCard = Math.round(Math.min(40, Math.max(24, height * 0.11)));
  const handOver = table.phase === 'handOver';
  const heroWon = handOver && table.awards.some((a) => a.winners.includes(HERO));
  const [celebrate, setCelebrate] = useState<number | null>(null);
  const lastHand = useRef(0);

  // Sonido y celebración del resultado de la mano del jugador.
  useEffect(() => {
    if (!handOver || lastHand.current === table.handNumber) return;
    lastHand.current = table.handNumber;
    const hero = table.seats[HERO]!;
    if (hero.committed === 0 && !heroWon) return;
    const won = table.awards.filter((a) => a.winners.includes(HERO)).reduce((s, a) => s + a.amount / a.winners.length, 0);
    const net = won - hero.committed;
    const big = won >= 25 * table.rules.bigBlind;
    const sound = resultSound(net, big);
    if (sound) play(sound, 300);
    if (big && net > 0) setCelebrate(Date.now());
  }, [handOver, table, heroWon]);

  return (
    <div className="relative mx-auto h-full w-full max-w-5xl" data-table>
      {/* Óvalo: paño con borde acolchado y filete dorado. */}
      <div className="rail felt absolute inset-x-[9%] inset-y-[10%]" />
      <div className="pointer-events-none absolute inset-x-[16%] inset-y-[20%] rounded-[50%] border border-gold-light/20" />

      {/* Centro: cartas comunitarias y bote. */}
      <div className="absolute top-1/2 left-1/2 flex -translate-x-1/2 -translate-y-1/2 flex-col items-center gap-1.5">
        {/* El crupier reparte desde aquí. */}
        <div className="flex gap-1" data-deck>
          {Array.from({ length: 5 }, (_, i) => {
            const card = table.board[i];
            return card ? (
              <motion.div
                key={`${table.handNumber}-${i}`}
                initial={{ opacity: 0, transform: 'translateY(-18px) scale(0.92)' }}
                animate={{ opacity: 1, transform: 'translateY(0px) scale(1)' }}
                transition={{ duration: 0.3, ease: EASE_OUT, delay: (i < 3 ? i : 0) * 0.12 }}
              >
                {/* Llegan boca abajo y el crupier las destapa una a una. */}
                <FlipCard rank={card.rank} suit={card.suit} faceDown={false} dealtFaceDown delay={0.3 + (i < 3 ? i : 0) * 0.15} width={boardCard} />
              </motion.div>
            ) : (
              <div key={i} className="rounded-md border border-dashed border-gold-light/25" style={{ width: boardCard, height: boardCard * 1.4 }} />
            );
          })}
        </div>
        {pot(table) > 0 && !handOver && (
          <div className="flex items-center gap-1.5 rounded-full bg-black/40 px-2.5 py-0.5" aria-label={`Bote: ${chips(pot(table))}`}>
            <ChipStack amount={pot(table)} size={18} max={4} />
            <span className="tabular text-sm font-bold text-gold-light">Bote {grouped(pot(table))}</span>
          </div>
        )}
      </div>

      {/* Apuestas de la calle actual: al cerrarse la calle se deslizan al bote. */}
      <AnimatePresence>
      {table.seats.map((seat, i) =>
        seat.bet > 0 ? (
          <motion.div
            key={`bet-${i}`}
            className="absolute flex -translate-x-1/2 -translate-y-1/2 items-center gap-1"
            style={{ left: `${BET_POS[i]![0] * 100}%`, top: `${BET_POS[i]![1] * 100}%` }}
            initial={CHIP_DROP.initial}
            animate={CHIP_DROP.animate}
            exit={{
              opacity: 0,
              transform: `translate(${toCenter(BET_POS[i]![0], BET_POS[i]![1]).dx}px, ${toCenter(BET_POS[i]![0], BET_POS[i]![1]).dy}px) scale(0.6)`,
              transition: { duration: 0.35, ease: EASE_IN_OUT },
            }}
            transition={CHIP_DROP.transition}
          >
            <ChipStack amount={seat.bet} size={18} max={3} />
            <span className="tabular rounded bg-black/50 px-1 text-[11px] font-bold text-gold-light">{grouped(seat.bet)}</span>
          </motion.div>
        ) : null,
      )}
      </AnimatePresence>

      {table.seats.map((seat, i) => (
        <SeatView key={i} table={table} index={i} seat={seat} cardWidth={i === HERO ? heroCard : botCard} toCenter={toCenter(SEAT_POS[i]![0], SEAT_POS[i]![1])} />
      ))}

      {/* Al acabar la mano, el bote viaja hasta quien lo gana. */}
      {handOver &&
        table.awards.flatMap((award, a) =>
          award.winners.map((w) => {
            const { dx, dy } = toCenter(SEAT_POS[w]![0], w === HERO ? 0.86 : SEAT_POS[w]![1]);
            return (
              <motion.div
                key={`bote-${table.handNumber}-${a}-${w}`}
                className="pointer-events-none absolute top-1/2 left-1/2 z-20 -translate-x-1/2 -translate-y-1/2"
                initial={{ opacity: 0, transform: 'translate(0px, 0px) scale(1)' }}
                animate={{ opacity: [0, 1, 1, 0], transform: ['translate(0px, 0px) scale(1)', 'translate(0px, 0px) scale(1.1)', `translate(${-dx}px, ${-dy}px) scale(0.8)`, `translate(${-dx}px, ${-dy}px) scale(0.8)`] }}
                transition={{ duration: 1.1, times: [0, 0.15, 0.75, 1], ease: EASE_IN_OUT, delay: 0.15 }}
              >
                <ChipStack amount={award.amount / award.winners.length} size={20} max={4} />
              </motion.div>
            );
          }),
        )}

      <AnimatePresence>
        {handOver && table.awards.length > 0 && (
          <motion.div className="pointer-events-none absolute inset-x-0 top-[63%] z-30 flex justify-center" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}>
            <div className="rounded-2xl bg-black/85 px-4 py-1.5 text-center ring-1 ring-gold/50" role="status">
              {table.awards.map((award, i) => (
                <p key={i} className={`font-display text-[15px] font-semibold ${award.winners.includes(HERO) ? 'text-gold-gradient' : 'text-ivory'}`}>
                  {award.winners.map((w) => (w === HERO ? 'Tú' : table.seats[w]!.name)).join(' y ')} {award.winners.length > 1 ? 'se reparten' : award.winners.includes(HERO) ? 'ganas' : 'gana'} {chips(award.amount)}
                  {award.category ? ` con ${CATEGORY_NAME[award.category]}` : ''}
                </p>
              ))}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
      <Celebration trigger={celebrate} />
    </div>
  );
}

/** Carta propia de un asiento: sale del centro de la mesa y, en la confrontación, se da la vuelta. */
function HoleCard({ card, width, faceDown, overlap, tilt, delay }: { card: Card; width: number; faceDown: boolean; overlap: number; tilt: number; delay: number }) {
  const ref = useRef<HTMLDivElement>(null);
  useDealFrom(ref, '[data-deck]', delay, tilt);
  return (
    <div ref={ref} style={{ marginLeft: overlap, transform: tilt ? `rotate(${tilt}deg)` : undefined }}>
      <FlipCard rank={card.rank} suit={card.suit} faceDown={faceDown} width={width} />
    </div>
  );
}

function SeatView({ table, index, seat, cardWidth, toCenter }: { table: PokerState; index: number; seat: Seat; cardWidth: number; toCenter: { dx: number; dy: number } }) {
  const [x, y] = SEAT_POS[index]!;
  const active = table.phase === 'betting' && table.toAct === index;
  const won = table.phase === 'handOver' && table.awards.some((a) => a.winners.includes(index));
  const out = seat.hole.length === 0 && seat.stack === 0;
  const showCards = seat.isHuman || (table.showdown && inHand(seat));
  const status = out && !seat.isHuman ? 'Eliminado' : active && !seat.isHuman ? 'Pensando…' : seat.lastAction ? ACTION_LABEL[seat.lastAction] : null;
  const hero = index === HERO;
  const description = `${seat.isHuman ? 'Tú' : seat.name}: ${chips(seat.stack)}${status ? `, ${status}` : ''}${seat.folded ? ', retirado' : ''}`;
  // Los asientos de abajo (el jugador) y de arriba van pegados al borde: en una mesa baja (móvil) no
  // se meten bajo los controles ni la cabecera.
  const edge = hero ? { bottom: 4 } : index === 3 ? { top: 10 } : null;
  return (
    <div
      className={`absolute flex -translate-x-1/2 ${edge ? '' : '-translate-y-1/2'} items-center gap-1.5 transition-opacity ${seat.folded || out ? 'opacity-45' : ''} ${hero ? 'flex-row' : index < 3 ? 'flex-row' : 'flex-row-reverse'}`}
      style={{ left: `${x * 100}%`, ...(edge ?? { top: `${y * 100}%` }) }}
      aria-label={description}
    >
      {seat.hole.length > 0 && (
        <motion.div
          className="flex"
          initial={false}
          // Al retirarse, sus cartas se deslizan un poco hacia el centro y se apagan.
          animate={seat.folded ? { opacity: 0.3, transform: `translate(${toCenter.dx * 0.18}px, ${toCenter.dy * 0.18}px) scale(0.85)` } : { opacity: 1, transform: 'translate(0px, 0px) scale(1)' }}
          transition={{ duration: 0.35, ease: EASE_OUT }}
        >
          {seat.hole.map((card, ci) => (
            <HoleCard
              key={`${table.handNumber}-${ci}`}
              card={card}
              width={cardWidth}
              faceDown={!showCards}
              overlap={ci > 0 ? -cardWidth * (hero ? 0.25 : 0.45) : 0}
              tilt={hero ? (ci === 0 ? -5 : 5) : 0}
              // Una carta a cada asiento por turno y luego la segunda, como reparte el crupier.
              delay={(ci * 6 + index) * 0.08}
            />
          ))}
        </motion.div>
      )}
      <div
        className={`relative min-w-[78px] rounded-2xl px-2.5 py-1 text-center ring-1 transition-shadow ${
          won ? 'bg-gold/25 ring-gold-light shadow-[0_0_18px_rgb(243_223_162/0.5)]' : active ? 'bg-black/70 ring-gold-light shadow-[0_0_14px_rgb(243_223_162/0.4)]' : 'bg-black/60 ring-white/15'
        }`}
      >
        {table.button === index && (
          <span className="absolute -top-2 -right-2 grid size-5 place-items-center rounded-full bg-ivory text-[10px] font-black text-obsidian shadow" aria-label="Repartidor">
            D
          </span>
        )}
        <p className={`truncate text-[12px] font-bold ${hero ? 'text-gold-light' : 'text-ivory'}`}>{hero ? 'Tú' : seat.name}</p>
        <p className="tabular text-[11px] text-ivory-dim">{grouped(seat.stack)}</p>
        {status && <p className={`text-[10px] font-semibold ${active ? 'animate-pulse text-gold-light' : 'text-gold/80'}`}>{status}</p>}
      </div>
    </div>
  );
}

// ── Controles ────────────────────────────────────────────────────────────────────────────────

function Controls({ table, poker }: { table: PokerState; poker: ReturnType<typeof usePokerTable> }) {
  const wallet = useReadyWallet();
  const legal = legalActions(table, HERO);
  const hero = table.seats[HERO]!;
  const heroTurn = legal !== null && !poker.busy;
  const [raiseTo, setRaiseTo] = useState(0);
  const unit = table.rules.chipUnit;
  const currentPot = pot(table);

  useEffect(() => {
    if (legal) setRaiseTo(legal.minRaiseTo);
  }, [table.handNumber, table.street, legal?.minRaiseTo]);

  const presets = useMemo(() => {
    if (!legal?.canRaise) return [];
    const clamp = (v: number) => Math.min(Math.max(Math.floor(v / unit) * unit, legal.minRaiseTo), legal.maxRaiseTo);
    return [
      { label: 'Mín.', value: legal.minRaiseTo },
      { label: '½ bote', value: clamp(table.currentBet + currentPot / 2) },
      { label: 'Bote', value: clamp(table.currentBet + currentPot) },
      { label: 'All-in', value: legal.maxRaiseTo },
    ];
  }, [legal, table.currentBet, currentPot, unit]);

  if (table.phase === 'betting' && legal) {
    return (
      <div className="flex h-[68px] items-center gap-2 px-3">
        <Button variant="secondary" className="w-28" disabled={!heroTurn} onClick={() => poker.heroAction({ type: 'fold' })}>
          Retirarse
        </Button>
        <Button variant="secondary" className="w-32" disabled={!heroTurn} onClick={() => poker.heroAction({ type: 'call' })}>
          {legal.canCheck ? 'Pasar' : `Igualar ${grouped(legal.callAmount)}`}
        </Button>
        {legal.canRaise && (
          <>
            <div className="flex min-w-0 flex-1 flex-col gap-1">
              <div className="flex gap-1">
                {presets.map((p) => (
                  <button key={p.label} type="button" disabled={!heroTurn} onClick={() => setRaiseTo(p.value)} className={`h-6 flex-1 rounded-md text-[11px] font-bold ring-1 ${raiseTo === p.value ? 'bg-gold/25 text-gold-light ring-gold' : 'text-ivory-dim ring-white/15'}`}>
                    {p.label}
                  </button>
                ))}
              </div>
              {legal.maxRaiseTo > legal.minRaiseTo && (
                <input
                  type="range"
                  aria-label="Cantidad de la subida"
                  min={legal.minRaiseTo}
                  max={legal.maxRaiseTo}
                  step={unit}
                  value={raiseTo}
                  disabled={!heroTurn}
                  onChange={(e) => setRaiseTo(Math.min(Math.max(Number(e.target.value), legal.minRaiseTo), legal.maxRaiseTo))}
                  className="w-full accent-[#d4af6a]"
                />
              )}
            </div>
            <Button className="w-40" disabled={!heroTurn} onClick={() => poker.heroAction({ type: 'raiseTo', amount: raiseTo === legal.maxRaiseTo ? legal.maxRaiseTo : raiseTo })}>
              {raiseTo >= legal.maxRaiseTo ? 'All-in' : table.currentBet === 0 ? `Apostar ${grouped(raiseTo)}` : `Subir a ${grouped(raiseTo)}`}
            </Button>
          </>
        )}
      </div>
    );
  }

  const needsRebuy = hero.stack < table.rules.bigBlind && table.phase !== 'betting';
  const waitingOthers = table.phase === 'betting';
  const tableWon = !waitingOthers && !needsRebuy && allBotsOut(table);
  return (
    <div className="flex h-[68px] items-center gap-3 px-3">
      <p className="line-clamp-2 min-w-0 flex-1 text-sm leading-snug text-ivory-dim" aria-live="polite">
        {waitingOthers
          ? hero.folded
            ? 'Te has retirado: espera a la siguiente mano.'
            : `Turno de ${table.toAct !== null ? table.seats[table.toAct]!.name : '…'}`
          : needsRebuy
            ? 'Te has quedado sin fichas en la mesa. Recompra para seguir.'
            : tableWon
              ? (
                <>
                  <span className="font-semibold text-gold-light">¡Has eliminado a todos!</span> Tienes {chips(hero.stack)} en la mesa.
                </>
              )
              : (
              <>
                En la mesa: <span className="font-semibold text-gold-light">{chips(hero.stack)}</span>
                <br />
                Saldo: {chips(wallet?.balance ?? 0)}
              </>
            )}
      </p>
      <Button variant="ghost" disabled={poker.busy || (waitingOthers && !hero.folded)} onClick={poker.standUp}>
        Levantarse
      </Button>
      {!waitingOthers &&
        (needsRebuy ? (
          <Button className="min-w-36" loading={poker.busy} onClick={poker.rebuy}>
            Recomprar
          </Button>
        ) : (
          <Button className="min-w-36" loading={poker.busy} onClick={poker.dealNextHand}>
            {tableWon ? 'Nuevos rivales' : 'Repartir'}
          </Button>
        ))}
    </div>
  );
}
