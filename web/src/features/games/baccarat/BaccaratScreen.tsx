import { useEffect, useRef, useState } from 'react';
import { motion } from 'motion/react';
import { useWallet, useReadyWallet } from '@/data/wallet';
import { useSettings } from '@/data/settings';
import { BACCARAT_BET_NAME, BACCARAT_BETS, BACCARAT_RULES, dealBaccarat, handTotal, type BaccaratBet, type BaccaratRound, type Coup, type CoupWinner } from '@/engine/baccarat';
import type { Card, Shoe } from '@/engine/cards';
import { chips, grouped } from '@/lib/format';
import { play, resultSound, BIG_WIN_MULTIPLIER } from '@/audio/sound';
import { Button } from '@/ui/Button';
import { ChipStack } from '@/ui/Chip';
import { IconRepeat, IconTrash, IconUndo } from '@/ui/icons';
import { CHIP_DROP, SPRING } from '@/ui/motion';
import { Celebration } from '../shared/Celebration';
import { ChipRack } from '../shared/ChipRack';
import { useDealFrom } from '../shared/deal';
import { FlipCard } from '../shared/FlipCard';
import { GameShell, TableNotice, useTableSize } from '../shared/GameShell';
import { economyNotice, useHoldProgressEvents, usePlayerId } from '../shared/session';
import { TableAction } from '../shared/TableAction';

const CHIPS = [10, 50, 100, 500, 1000, 5000];
const SIDE = {
  player: { name: 'Jugador', color: '#5b8de6', tint: 'rgb(91 141 230 / 0.12)' },
  banker: { name: 'Banca', color: '#e0485e', tint: 'rgb(224 72 94 / 0.12)' },
  tie: { name: 'Empate', color: '#5cc79e', tint: 'rgb(92 199 158 / 0.12)' },
} as const;
const BOX: Record<BaccaratBet, { title: string; pays: string; side: keyof typeof SIDE }> = {
  playerPair: { title: 'Pareja J.', pays: '11:1', side: 'player' },
  player: { title: 'Jugador', pays: '1:1', side: 'player' },
  tie: { title: 'Empate', pays: '8:1', side: 'tie' },
  banker: { title: 'Banca', pays: '1:1 · con 6, 1:2', side: 'banker' },
  bankerPair: { title: 'Pareja B.', pays: '11:1', side: 'banker' },
};

/** Marcador de las últimas manos (la más reciente al final), guardado por jugador. */
function useBeadRoad(): [CoupWinner[], (winner: CoupWinner) => void] {
  const uid = usePlayerId();
  const key = `royal-chance-baccarat-marcador-${uid}`;
  const [road, setRoad] = useState<CoupWinner[]>(() => {
    try {
      const saved: unknown = JSON.parse(localStorage.getItem(key) ?? '[]');
      return Array.isArray(saved) ? saved.filter((w): w is CoupWinner => w === 'player' || w === 'banker' || w === 'tie').slice(-30) : [];
    } catch {
      return [];
    }
  });
  const add = (winner: CoupWinner) =>
    setRoad((previous) => {
      const next = [...previous, winner].slice(-30);
      try {
        localStorage.setItem(key, JSON.stringify(next));
      } catch {
        // Sin almacenamiento: el marcador dura lo que la mesa abierta.
      }
      return next;
    });
  return [road, add];
}

interface CardTiming {
  deal: number;
  flip: number;
}

/**
 * Cuándo sale cada carta del zapato y cuándo se destapa (en segundos), como en una mesa real:
 * jugador, banca, jugador, banca boca abajo; se destapan las del jugador, luego las de la banca, y
 * las terceras de una en una.
 */
function schedule(coup: Coup, reduced: boolean) {
  const gap = reduced ? 0.1 : 0.32;
  const settle = reduced ? 0.15 : 0.5;
  const player: CardTiming[] = [];
  const banker: CardTiming[] = [];
  const reveals: { ms: number; side: 'player' | 'banker'; count: number }[] = [];
  const flipPlayer = 4 * gap + (reduced ? 0.1 : 0.35);
  const flipBanker = flipPlayer + settle + 0.1;
  player.push({ deal: 0, flip: flipPlayer }, { deal: 2 * gap, flip: flipPlayer });
  banker.push({ deal: gap, flip: flipBanker }, { deal: 3 * gap, flip: flipBanker });
  reveals.push({ ms: (flipPlayer + 0.45) * 1000, side: 'player', count: 2 }, { ms: (flipBanker + 0.45) * 1000, side: 'banker', count: 2 });
  let time = flipBanker + settle + 0.25;
  if (coup.player.length === 3) {
    player.push({ deal: time, flip: time + settle });
    reveals.push({ ms: (time + settle + 0.45) * 1000, side: 'player', count: 3 });
    time += settle * 2 + 0.25;
  }
  if (coup.banker.length === 3) {
    banker.push({ deal: time, flip: time + settle });
    reveals.push({ ms: (time + settle + 0.45) * 1000, side: 'banker', count: 3 });
    time += settle * 2 + 0.25;
  }
  const deals = [...player, ...banker].map((c) => c.deal * 1000);
  return { player, banker, reveals, deals, endMs: (time + 0.2) * 1000 };
}

type Timeline = ReturnType<typeof schedule>;

export default function BaccaratScreen() {
  const wallet = useReadyWallet();
  const playInstantRound = useWallet((s) => s.playInstantRound);
  const reducedMotion = useSettings((s) => s.reducedMotion);
  const table = useTableSize();
  const [chip, setChip] = useState(100);
  const [bets, setBets] = useState<Map<BaccaratBet, number>>(new Map());
  const [history, setHistory] = useState<Map<BaccaratBet, number>[]>([]);
  const [lastBets, setLastBets] = useState<Map<BaccaratBet, number> | null>(null);
  const [round, setRound] = useState<{ id: number; data: BaccaratRound; timeline: Timeline } | null>(null);
  const [phase, setPhase] = useState<'betting' | 'dealing' | 'result'>('betting');
  const [revealed, setRevealed] = useState({ player: 0, banker: 0 });
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [celebrate, setCelebrate] = useState<number | null>(null);
  const [road, addToRoad] = useBeadRoad();
  const shoe = useRef<Shoe | null>(null);
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);
  useHoldProgressEvents(phase === 'dealing');
  useEffect(() => () => timers.current.forEach(clearTimeout), []);

  const showResult = phase === 'result';
  const total = [...bets.values()].reduce((s, v) => s + v, 0);
  const balance = wallet?.balance ?? 0;
  const canBet = phase !== 'dealing' && !busy;

  const place = (bet: BaccaratBet) => {
    if (!canBet) return;
    // Tras una mano, la primera ficha empieza una mano nueva: se recogen cartas y apuestas.
    const base = showResult ? new Map<BaccaratBet, number>() : bets;
    const nextTotal = (showResult ? 0 : total) + chip;
    if (nextTotal > BACCARAT_RULES.maximumTotalBet) return setNotice(`El máximo por mano es de ${chips(BACCARAT_RULES.maximumTotalBet)}.`);
    if (nextTotal > balance) return setNotice('No tienes fichas suficientes para esa apuesta.');
    setHistory((h) => [...h, base]);
    setBets(new Map(base).set(bet, (base.get(bet) ?? 0) + chip));
    if (showResult) {
      setPhase('betting');
      setRound(null);
    }
    play('chip');
  };

  const undo = () => {
    const previous = history[history.length - 1];
    if (!previous) return;
    setHistory((h) => h.slice(0, -1));
    setBets(previous);
  };
  const clear = () => {
    setHistory((h) => [...h, bets]);
    setBets(new Map());
  };
  const repeat = () => {
    if (!lastBets) return;
    const amount = [...lastBets.values()].reduce((s, v) => s + v, 0);
    if (amount > balance) return setNotice('No tienes fichas suficientes para repetir la apuesta.');
    setHistory((h) => [...h, showResult ? new Map() : bets]);
    setBets(new Map(lastBets));
    setPhase('betting');
    setRound(null);
    play('chip');
  };

  const deal = async () => {
    if (!canBet || bets.size === 0 || showResult) return;
    setNotice(null);
    const placed = [...bets.entries()].map(([bet, stake]) => ({ bet, stake }));
    const outcome = dealBaccarat(placed, shoe.current);
    if (!outcome.ok) return setNotice(outcome.error === 'aboveTableMaximum' ? `El máximo por mano es de ${chips(BACCARAT_RULES.maximumTotalBet)}.` : 'Revisa tus apuestas.');
    setBusy(true);
    // El resultado se contabiliza antes de animarlo.
    const booked = await playInstantRound('Baccarat', outcome.round.totalStake, outcome.round.totalPayout);
    setBusy(false);
    if (!booked.ok) return setNotice(economyNotice(booked.error));
    shoe.current = outcome.shoe;
    setLastBets(new Map(bets));
    const timeline = schedule(outcome.round.coup, reducedMotion);
    timers.current.forEach(clearTimeout);
    timers.current = [];
    setRound({ id: Date.now(), data: outcome.round, timeline });
    setRevealed({ player: 0, banker: 0 });
    setCelebrate(null);
    setPhase('dealing');
    timeline.deals.forEach((ms) => play('card', ms));
    for (const { ms, side, count } of timeline.reveals) timers.current.push(setTimeout(() => setRevealed((r) => ({ ...r, [side]: count })), ms));
    timers.current.push(
      setTimeout(() => {
        setPhase('result');
        setHistory([]);
        addToRoad(outcome.round.coup.winner);
        const net = outcome.round.totalPayout - outcome.round.totalStake;
        const big = outcome.round.totalPayout >= BIG_WIN_MULTIPLIER * outcome.round.totalStake;
        const sound = resultSound(net, big);
        if (sound) play(sound);
        if (big && net > 0) setCelebrate(Date.now());
      }, timeline.endMs),
    );
  };

  // Medidas: arriba las dos manos (con la tercera carta de lado), abajo las casillas de apuesta.
  const boxHeight = Math.round(Math.max(54, Math.min(84, table.height * 0.26)));
  // En el centro, el zapato del crupier, el marcador y el resultado; a los lados, las dos manos.
  const centerWidth = table.width >= 560 ? 144 : 112;
  const cardWidth = Math.round(Math.max(34, Math.min(70, ((table.height - boxHeight - 64) / 1.4) * 0.92, (table.width * 0.92 - centerWidth - 72) / 6.8)));
  const compact = table.width < 720;
  const showTotal = table.width >= (compact ? 600 : 800);
  const coup = round?.data.coup ?? null;
  const payouts = showResult && round ? new Map(round.data.results.map((r) => [r.bet, r.payout])) : null;
  const shownBets = round && phase !== 'betting' ? new Map(round.data.results.map((r) => [r.bet, r.stake])) : bets;
  const net = round ? round.data.totalPayout - round.data.totalStake : 0;

  return (
    <GameShell
      title="Baccarat"
      notice={notice && <TableNotice onDismiss={() => setNotice(null)}>{notice}</TableNotice>}
      controls={
        <div className="flex h-[68px] items-center gap-2 px-3">
          <ChipRack selected={chip} onSelect={setChip} size={compact ? 32 : 38} values={CHIPS} disabled={!canBet} />
          {showTotal ? (
            <div className="mx-1 min-w-0 flex-1 text-center">
              <p className="text-[11px] font-semibold tracking-wider text-mute uppercase">Apuesta total</p>
              <p className="tabular font-bold text-gold-light">{chips(showResult ? 0 : total)}</p>
            </div>
          ) : (
            <div className="flex-1" />
          )}
          <TableAction label="Deshacer" icon={<IconUndo className="size-5" />} compact={compact} disabled={!canBet || history.length === 0 || showResult} onClick={undo} />
          <TableAction label="Borrar" icon={<IconTrash className="size-5" />} compact={compact} disabled={!canBet || bets.size === 0 || showResult} onClick={clear} />
          <TableAction label="Repetir" icon={<IconRepeat className="size-5" />} compact={compact} disabled={!canBet || !lastBets || (bets.size > 0 && !showResult)} onClick={repeat} />
          <Button className={compact ? 'min-w-24' : 'min-w-28'} loading={busy || phase === 'dealing'} disabled={bets.size === 0 || showResult} onClick={() => void deal()}>
            Repartir
          </Button>
        </div>
      }
    >
      <div className="relative mx-auto flex h-full w-full max-w-4xl flex-col gap-2 px-3 pt-2 pb-2" data-table>
        <div className="flex min-h-0 flex-1 items-center justify-center gap-[4%]">
          <HandArea side="player" cards={coup?.player ?? []} timing={round?.timeline.player ?? []} roundId={round?.id ?? 0} shown={revealed.player} cardWidth={cardWidth} result={showResult && coup ? coup.winner : null} />
          <div className="flex shrink-0 flex-col items-center gap-1.5" style={{ width: centerWidth }}>
            {/* Zapato del crupier: de aquí salen las cartas hacia cada lado. */}
            <div data-shoe className="relative h-7 w-14 shrink-0 rounded-md bg-[#1b120c] shadow-[inset_0_0_0_1.5px_rgb(212_175_106/0.6),0_8px_18px_-6px_rgb(0_0_0/0.8)]" aria-hidden>
              <div className="absolute inset-x-1.5 top-1.5 bottom-1.5 rounded-sm bg-[repeating-linear-gradient(90deg,#5e1220_0_3px,#3b0b15_3px_5px)] opacity-90" />
            </div>
            {table.width >= 560 && <BeadRoad road={road} detailed={table.height >= 240} />}
            {/* Resultado de la mano: el balance en una línea (quién gana ya lo marca su mano). */}
            {showResult && round && (
              <motion.p
                role="status"
                aria-label={net > 0 ? `Ganas ${chips(net)}` : net < 0 ? `Pierdes ${chips(-net)}` : 'Recuperas lo apostado'}
                className={`tabular rounded-full bg-black/60 px-3 py-1 font-display text-base font-bold whitespace-nowrap ring-1 ring-gold/40 ${net > 0 ? 'text-gold-gradient' : net < 0 ? 'text-[#ff9aa8]' : 'text-ivory'}`}
                initial={{ opacity: 0, scale: 0.9 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={SPRING}
              >
                {net > 0 ? `+${grouped(net)}` : net < 0 ? `−${grouped(-net)}` : 'Sin cambios'}
              </motion.p>
            )}
          </div>
          <HandArea side="banker" cards={coup?.banker ?? []} timing={round?.timeline.banker ?? []} roundId={round?.id ?? 0} shown={revealed.banker} cardWidth={cardWidth} result={showResult && coup ? coup.winner : null} />
        </div>

        <div className="grid shrink-0 gap-2" style={{ height: boxHeight, gridTemplateColumns: '1fr 1.5fr 1.15fr 1.5fr 1fr' }}>
          {BACCARAT_BETS.map((bet) => (
            <BetBox key={bet} bet={bet} stake={shownBets.get(bet)} payout={payouts?.get(bet)} coup={showResult ? coup : null} disabled={!canBet} onPlace={place} />
          ))}
        </div>
      </div>
      <Celebration trigger={celebrate} />
    </GameShell>
  );
}

/** Una mano (jugador o banca): nombre, puntuación según se destapan las cartas y las cartas. */
function HandArea({
  side,
  cards,
  timing,
  roundId,
  shown,
  cardWidth,
  result,
}: {
  side: 'player' | 'banker';
  cards: Card[];
  timing: CardTiming[];
  roundId: number;
  shown: number;
  cardWidth: number;
  result: CoupWinner | null;
}) {
  const { name, color, tint } = SIDE[side];
  const total = shown > 0 ? handTotal(cards.slice(0, shown)) : null;
  const cardHeight = (cardWidth * 350) / 250;
  const won = result === side;
  const tie = result === 'tie';
  return (
    <section className="flex flex-col items-center gap-1.5" aria-label={`${name}${total !== null ? `: ${total}` : ''}${won ? ', gana' : tie ? ', empate' : ''}`}>
      <div className="flex items-center gap-2">
        <span className="font-display text-[13px] font-bold tracking-[0.16em] uppercase" style={{ color }}>
          {name}
        </span>
        <span
          className="tabular grid h-6 min-w-6 place-items-center rounded-full px-1.5 text-xs font-bold text-white ring-1 ring-white/20 transition-colors"
          style={{ background: total === null ? 'rgb(0 0 0 / 0.4)' : color }}
        >
          {total ?? '–'}
        </span>
        {(won || tie) && <span className="rounded-full bg-gold-light px-2 py-0.5 text-[10px] font-black tracking-wider text-on-gold uppercase">{won ? 'Gana' : 'Empate'}</span>}
      </div>
      <div
        className={`flex items-center rounded-xl border-2 border-dashed p-1.5 transition-shadow duration-300 ${won ? 'shadow-[0_0_22px_rgb(243_223_162/0.45)]' : ''}`}
        style={{ minWidth: cardWidth * 2 + cardHeight + 18, minHeight: cardHeight + 16, borderColor: won ? '#f3dfa2' : `${color}55`, background: tint }}
      >
        {cards.map((card, i) => (
          <BaccaratCard key={`${roundId}-${side}-${i}`} card={card} width={cardWidth} timing={timing[i]} sideways={i === 2} overlap={i === 1 ? -cardWidth * 0.1 : i === 2 ? 4 : 0} />
        ))}
      </div>
    </section>
  );
}

/** Carta repartida desde el zapato, boca abajo, que se destapa a su tiempo. La tercera va de lado. */
function BaccaratCard({ card, width, timing, sideways, overlap }: { card: Card; width: number; timing: CardTiming | undefined; sideways: boolean; overlap: number }) {
  const ref = useRef<HTMLDivElement>(null);
  useDealFrom(ref, '[data-shoe]', timing?.deal, sideways ? 90 : 0);
  const face = <FlipCard rank={card.rank} suit={card.suit} faceDown={false} dealtFaceDown={timing !== undefined} delay={timing?.flip ?? 0} width={width} />;
  if (!sideways) {
    return (
      <div ref={ref} style={{ marginLeft: overlap }}>
        {face}
      </div>
    );
  }
  const height = (width * 350) / 250;
  return (
    <div className="grid place-items-center" style={{ width: height, height: width, marginLeft: overlap }}>
      <div ref={ref} style={{ transform: 'rotate(90deg)' }}>
        {face}
      </div>
    </div>
  );
}

/** Marcador: últimas manos como en las mesas (azul el jugador, rojo la banca, verde el empate). */
function BeadRoad({ road, detailed }: { road: CoupWinner[]; detailed: boolean }) {
  const recent = road.slice(-12);
  const count = (w: CoupWinner) => road.filter((r) => r === w).length;
  return (
    <div className="flex flex-col items-center gap-1" aria-label={`Últimas manos: ${recent.map((w) => SIDE[w].name).join(', ') || 'ninguna todavía'}`}>
      {detailed && <p className="felt-print text-[9px] font-bold">Marcador</p>}
      <div className="grid grid-cols-6 gap-1 rounded-lg bg-black/35 p-1.5 ring-1 ring-gold/20" aria-hidden>
        {Array.from({ length: 12 }, (_, i) => {
          const w = recent[i];
          return (
            <span
              key={i}
              className="grid size-4 place-items-center rounded-full text-[8px] font-black text-white"
              style={{ background: w ? SIDE[w].color : 'rgb(255 255 255 / 0.06)' }}
            >
              {w === 'player' ? 'J' : w === 'banker' ? 'B' : w === 'tie' ? 'E' : ''}
            </span>
          );
        })}
      </div>
      {detailed && road.length > 0 && (
        <p className="tabular text-[9px] font-semibold text-ivory-dim" aria-hidden>
          J {count('player')} · B {count('banker')} · E {count('tie')}
        </p>
      )}
    </div>
  );
}

function BetBox({
  bet,
  stake,
  payout,
  coup,
  disabled,
  onPlace,
}: {
  bet: BaccaratBet;
  stake: number | undefined;
  payout: number | undefined;
  coup: Coup | null;
  disabled: boolean;
  onPlace: (bet: BaccaratBet) => void;
}) {
  const { title, pays, side } = BOX[bet];
  const { color, tint } = SIDE[side];
  const won = stake !== undefined && payout !== undefined && payout > stake;
  const lost = coup !== null && stake !== undefined && !payout;
  const winningBox = coup !== null && (bet === coup.winner || (bet === 'playerPair' && coup.playerPair) || (bet === 'bankerPair' && coup.bankerPair));
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => onPlace(bet)}
      aria-label={`Apostar a ${BACCARAT_BET_NAME[bet]}, paga ${pays}${stake ? `. Tienes ${chips(stake)}` : ''}`}
      className={`relative flex min-w-0 flex-col items-center justify-center rounded-xl border-2 px-1 text-center transition-[transform,background-color,border-color,box-shadow] duration-150 ease-out ${disabled ? '' : 'active:scale-[0.97]'} ${winningBox ? 'shadow-[0_0_18px_rgb(243_223_162/0.45)]' : ''}`}
      style={{ borderColor: winningBox ? '#f3dfa2' : `${color}88`, background: winningBox ? 'rgb(212 175 106 / 0.2)' : tint }}
    >
      <span className="truncate font-display text-[13px] leading-tight font-bold tracking-wide uppercase" style={{ color }}>
        {title}
      </span>
      <span className="tabular mt-0.5 truncate text-[10px] font-semibold text-gold-light/70">{pays}</span>
      {stake ? (
        <motion.span className="absolute -top-2 -right-2 flex items-center gap-0.5" initial={CHIP_DROP.initial} animate={{ ...CHIP_DROP.animate, opacity: lost ? 0.3 : 1 }} transition={CHIP_DROP.transition}>
          <ChipStack amount={won ? payout! : stake} size={24} max={3} />
          <span className="tabular rounded bg-black/60 px-1 text-[10px] font-bold text-gold-light">{grouped(won ? payout! : stake)}</span>
        </motion.span>
      ) : null}
    </button>
  );
}
