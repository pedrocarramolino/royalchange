import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { motion } from 'motion/react';
import { useWallet, useReadyWallet } from '@/data/wallet';
import {
  applyBlackjack,
  availableMoves,
  BLACKJACK_RULES,
  handValue,
  initialBlackjack,
  isBlackjack,
  isBust,
  stakeRequiredFor,
  totalPayout,
  totalStake,
  type BlackjackAction,
  type BlackjackEvent,
  type BlackjackState,
  type PlayerHand,
} from '@/engine/blackjack';
import type { Card } from '@/engine/cards';
import { chips, grouped } from '@/lib/format';
import { play, resultSound, BIG_WIN_MULTIPLIER } from '@/audio/sound';
import { Button } from '@/ui/Button';
import { ChipStack } from '@/ui/Chip';
import { PlayingCard } from '@/ui/PlayingCard';
import { Celebration, ResultBanner } from '../shared/Celebration';
import { ChipRack, TABLE_CHIPS } from '../shared/ChipRack';
import { GameShell, TableNotice, useGameViewport } from '../shared/GameShell';
import { economyNotice, loadSession, saveSession, usePlayerId, useHoldProgressEvents } from '../shared/session';

interface Session {
  state: BlackjackState;
  /** Ronda abierta en el monedero a la que pertenece la mano. */
  roundId: string | null;
  settled: boolean;
  lastBet: number;
}

const DEAL_GAP = 0.32;

export default function BlackjackScreen() {
  const uid = usePlayerId();
  const wallet = useReadyWallet();
  const { placeBet, settleRound } = useWallet();
  const [session, setSession] = useState<Session | null>(null);
  const [bet, setBet] = useState(100);
  const [chip, setChip] = useState(100);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  /** Retardo de entrada de cada carta nueva (por clave) y de la carta oculta al darse la vuelta. */
  const [delays, setDelays] = useState<{ cards: Record<string, number>; reveal: number | null; until: number }>({ cards: {}, reveal: null, until: 0 });
  const [animating, setAnimating] = useState(false);
  const [celebrate, setCelebrate] = useState<number | null>(null);
  const restored = useRef(false);

  const state = session?.state ?? initialBlackjack();
  useHoldProgressEvents(animating);

  const persist = useCallback(
    (next: Session) => {
      setSession(next);
      if (uid) saveSession(uid, 'Blackjack', next);
    },
    [uid],
  );

  // Reanudar: la mano guardada corresponde a la ronda abierta en el monedero.
  useEffect(() => {
    if (restored.current || !uid || !wallet) return;
    restored.current = true;
    const saved = loadSession<Session>(uid, 'Blackjack');
    const open = wallet.openRound?.game === 'Blackjack' ? wallet.openRound : null;
    const shoeOnly = (s: Session | null): Session => ({ state: { ...initialBlackjack(), shoe: s?.state.shoe ?? null }, roundId: null, settled: true, lastBet: s?.lastBet ?? 100 });
    if (saved) setBet(saved.lastBet);
    if (saved && open && saved.roundId === open.id && saved.state.phase === 'playerTurn') {
      persist(saved);
    } else if (saved && open && saved.roundId === open.id && saved.state.phase === 'roundOver' && !saved.settled) {
      void settleRound(totalPayout(saved.state)).then(() => persist({ ...saved, settled: true, roundId: null }));
    } else if (open) {
      // Fichas en una mano de blackjack sin su estado (otro dispositivo, datos borrados): se pierde.
      void settleRound(0);
      setNotice('La mano anterior no se pudo reanudar y se ha cerrado.');
      persist(shoeOnly(saved));
    } else if (saved) {
      persist(saved.state.phase === 'playerTurn' ? shoeOnly(saved) : saved);
    } else {
      persist(shoeOnly(null));
    }
  }, [uid, wallet, persist, settleRound]);

  /** Calcula los retardos de animación de las cartas nuevas según el orden de los eventos. */
  const schedule = (before: BlackjackState, after: BlackjackState, events: BlackjackEvent[]) => {
    const cards: Record<string, number> = {};
    let index = 0;
    let reveal: number | null = null;
    const dealerStart = before.phase === 'playerTurn' ? before.dealer.length : 0;
    let dealerIndex = dealerStart;
    // Cartas que ya estaban: sin animación (tras separar se recolocan sin volver a salir del zapato).
    const existing = new Set<string>();
    if (before.phase === 'playerTurn') {
      after.hands.forEach((h, hi) => h.cards.forEach((c, ci) => before.hands.some((b) => b.cards.some((x) => x === c)) && existing.add(`p${hi}-${ci}`)));
    }
    for (const e of events) {
      if (e.type === 'cardToPlayer') {
        const hand = after.hands[e.hand]!;
        const ci = hand.cards.indexOf(e.card);
        const key = `p${e.hand}-${Math.max(0, ci)}`;
        if (!existing.has(key)) {
          cards[key] = index * DEAL_GAP;
          play('card', index * DEAL_GAP * 1000);
          index++;
        }
      } else if (e.type === 'cardToDealer') {
        cards[`d${dealerIndex}`] = index * DEAL_GAP;
        play('card', index * DEAL_GAP * 1000);
        dealerIndex++;
        index++;
      } else if (e.type === 'holeCardRevealed') {
        reveal = index * DEAL_GAP;
        play('card', index * DEAL_GAP * 1000);
        index++;
      }
    }
    const until = index * DEAL_GAP + 0.45;
    setDelays({ cards, reveal, until });
    return until;
  };

  const finishAnimation = (seconds: number, after: BlackjackState) => {
    setAnimating(true);
    setTimeout(() => {
      setAnimating(false);
      if (after.phase === 'roundOver') {
        const net = totalPayout(after) - totalStake(after);
        const big = totalPayout(after) >= BIG_WIN_MULTIPLIER * totalStake(after) || after.results.some((r) => r.outcome === 'blackjack');
        const sound = resultSound(net, big);
        if (sound) play(sound);
        if (net > 0 && big) setCelebrate(Date.now());
      }
    }, seconds * 1000);
  };

  const act = async (action: BlackjackAction) => {
    if (busy || animating || !session) return;
    setNotice(null);
    const before = session.state;
    // Comprobación previa sin cobrar: apuesta válida y acción permitida.
    const preview = applyBlackjack(before, action);
    if (!preview.ok) {
      setNotice(preview.error === 'invalidBet' ? `La apuesta va de ${grouped(BLACKJACK_RULES.minimumBet)} a ${grouped(BLACKJACK_RULES.maximumBet)} fichas.` : 'Ahora no se puede.');
      return;
    }
    setBusy(true);
    try {
      let roundId = session.roundId;
      const stake = stakeRequiredFor(before, action);
      if (stake > 0) {
        const placed = await placeBet('Blackjack', stake);
        if (!placed.ok) {
          setNotice(economyNotice(placed.error));
          return;
        }
        roundId = placed.wallet.openRound?.id ?? roundId;
        play('chip');
      }
      const { state: after, events } = preview;
      let settled = after.phase !== 'roundOver';
      // El resultado se contabiliza antes de animarlo.
      if (after.phase === 'roundOver') {
        const result = await settleRound(totalPayout(after));
        settled = result.ok || result.error.type === 'noOpenRound';
      }
      persist({ state: after, roundId: after.phase === 'roundOver' ? null : roundId, settled, lastBet: action.type === 'deal' ? action.bet : session.lastBet });
      if (action.type === 'deal') setCelebrate(null);
      finishAnimation(schedule(before, after, events), after);
    } finally {
      setBusy(false);
    }
  };

  const moves = availableMoves(state);
  const balance = wallet?.balance ?? 0;
  const betting = state.phase !== 'playerTurn';
  const showResults = state.phase === 'roundOver' && !animating;

  const addChip = (value: number) => {
    setChip(value);
    setBet((b) => Math.min(BLACKJACK_RULES.maximumBet, b + value));
  };

  const net = totalPayout(state) - totalStake(state);
  const resultLabel = useMemo(() => {
    if (state.results.length === 0) return '';
    if (state.results.length === 1) {
      const r = state.results[0]!;
      if (r.outcome === 'blackjack') return `¡Blackjack! Cobras ${chips(r.payout)}`;
      if (r.outcome === 'win') return `Ganas ${chips(r.payout - state.hands[0]!.stake)}`;
      if (r.outcome === 'push') return 'Empate: recuperas tu apuesta';
      return isBust(state.hands[0]!) ? 'Te pasas de 21' : 'Gana la banca';
    }
    if (net > 0) return `Ganas ${chips(net)}`;
    if (net < 0) return `Pierdes ${chips(-net)}`;
    return 'Recuperas lo apostado';
  }, [state, net]);

  return (
    <GameShell
      title="Blackjack"
      notice={notice && <TableNotice onDismiss={() => setNotice(null)}>{notice}</TableNotice>}
      controls={
        <div className="flex h-[68px] items-center gap-3 px-3">
          {betting ? (
            <>
              <ChipRack selected={chip} onSelect={addChip} size={38} values={TABLE_CHIPS} disabled={busy || animating} />
              <div className="min-w-0 flex-1 text-center">
                <p className="text-[11px] font-semibold tracking-wider text-mute uppercase">Apuesta</p>
                <p className="tabular font-bold text-gold-light">{chips(bet)}</p>
              </div>
              <Button variant="ghost" size="sm" onClick={() => setBet(0)} disabled={busy || animating || bet === 0}>
                Borrar
              </Button>
              <Button size="md" className="min-w-36" loading={busy} disabled={animating || bet > balance || bet < BLACKJACK_RULES.minimumBet} onClick={() => void act({ type: 'deal', bet })}>
                {bet > balance ? 'Sin saldo' : 'Repartir'}
              </Button>
            </>
          ) : (
            <div className="grid w-full grid-cols-4 gap-2">
              <Button variant="secondary" disabled={!moves.has('hit') || busy || animating} onClick={() => void act({ type: 'hit' })}>
                Pedir
              </Button>
              <Button disabled={!moves.has('stand') || busy || animating} onClick={() => void act({ type: 'stand' })}>
                Plantarse
              </Button>
              <Button variant="secondary" disabled={!moves.has('double') || busy || animating || (state.hands[state.activeHand]?.stake ?? 0) > balance} onClick={() => void act({ type: 'double' })}>
                Doblar
              </Button>
              <Button variant="secondary" disabled={!moves.has('split') || busy || animating || (state.hands[state.activeHand]?.stake ?? 0) > balance} onClick={() => void act({ type: 'split' })}>
                Separar
              </Button>
            </div>
          )}
        </div>
      }
    >
      <BlackjackTable state={state} delays={delays} animating={animating} showResults={showResults} pendingBet={betting && state.phase !== 'roundOver' ? bet : null} />
      {showResults && state.results.length > 0 && (
        <div className="pointer-events-none absolute inset-x-0 top-[46%] z-20 flex -translate-y-1/2 justify-center">
          <ResultBanner net={net} label={resultLabel} big={net > 0} />
        </div>
      )}
      <Celebration trigger={celebrate} />
    </GameShell>
  );
}

function BlackjackTable({
  state,
  delays,
  animating,
  showResults,
  pendingBet,
}: {
  state: BlackjackState;
  delays: { cards: Record<string, number>; reveal: number | null };
  animating: boolean;
  showResults: boolean;
  pendingBet: number | null;
}) {
  const viewport = useGameViewport();
  const tableHeight = Math.max(200, viewport.height - 48 - 68);
  const cardWidth = Math.round(Math.min(104, Math.max(46, tableHeight * 0.3)));
  const dealerValue = handValue(state.holeCardRevealed ? state.dealer : state.dealer.slice(0, 1));
  const empty = state.hands.length === 0;

  return (
    <div className="relative mx-auto h-full w-full max-w-5xl">
      <TablePrint />
      {/* Zapato: de aquí salen las cartas. */}
      <div className="absolute top-1 right-4 h-[22%] w-[9%] min-w-12 rounded-md bg-[#1b120c] shadow-[inset_0_0_0_1.5px_rgb(212_175_106/0.6),0_8px_18px_-6px_rgb(0_0_0/0.8)]" aria-hidden>
        <div className="absolute inset-x-1.5 top-1.5 bottom-3 rounded-sm bg-[repeating-linear-gradient(90deg,#5e1220_0_3px,#3b0b15_3px_5px)] opacity-90" />
      </div>

      {/* Crupier */}
      <div className="absolute top-1 left-1/2 flex -translate-x-1/2 items-center gap-3" aria-label="Cartas del crupier">
        <div className="flex" style={{ gap: 0 }}>
          {state.dealer.map((card, i) => (
            <DealtCard
              key={`d${i}-${card.rank}${card.suit}`}
              card={card}
              width={cardWidth}
              delay={delays.cards[`d${i}`]}
              faceDown={i === 1 && !state.holeCardRevealed}
              flipDelay={i === 1 && state.holeCardRevealed ? delays.reveal : null}
              overlap={i > 0 ? -cardWidth * 0.3 : 0}
            />
          ))}
        </div>
        {state.dealer.length > 0 && !animating && <ValueBadge label="Crupier" value={dealerValue.total} soft={dealerValue.soft && dealerValue.total < 21} bust={dealerValue.total > 21} />}
      </div>

      {/* Manos del jugador */}
      <div className="absolute inset-x-0 bottom-2 flex items-end justify-center gap-[3%]">
        {empty && (
          <BetCircle amount={pendingBet} width={cardWidth} />
        )}
        {state.hands.map((hand, hi) => (
          <Hand
            key={hi}
            hand={hand}
            index={hi}
            active={state.phase === 'playerTurn' && hi === state.activeHand && state.hands.length > 1}
            cardWidth={state.hands.length > 2 ? cardWidth * 0.82 : cardWidth}
            delays={delays.cards}
            result={showResults ? state.results[hi] : undefined}
            animating={animating}
          />
        ))}
      </div>
    </div>
  );
}

/** Leyendas serigrafiadas en arco sobre el paño. */
function TablePrint() {
  return (
    <svg className="pointer-events-none absolute inset-0 h-full w-full" viewBox="0 0 1000 400" preserveAspectRatio="xMidYMid meet" aria-hidden>
      <defs>
        <path id="bj-arc-1" d="M140 150 Q500 330 860 150" />
        <path id="bj-arc-2" d="M220 196 Q500 340 780 196" />
      </defs>
      <path d="M120 140 Q500 340 880 140" fill="none" stroke="rgb(243 223 162 / 0.28)" strokeWidth="1.5" />
      <path d="M200 186 Q500 352 800 186" fill="none" stroke="rgb(243 223 162 / 0.18)" strokeWidth="1" />
      <text fontFamily="Cinzel, serif" fontSize="30" fontWeight="700" letterSpacing="7" fill="rgb(243 223 162 / 0.6)">
        <textPath href="#bj-arc-1" startOffset="50%" textAnchor="middle">
          BLACKJACK PAGA 3 A 2
        </textPath>
      </text>
      <text fontFamily="Cinzel, serif" fontSize="17" fontWeight="600" letterSpacing="5" fill="rgb(243 223 162 / 0.45)">
        <textPath href="#bj-arc-2" startOffset="50%" textAnchor="middle">
          EL CRUPIER SE PLANTA EN 17
        </textPath>
      </text>
    </svg>
  );
}

function DealtCard({ card, width, delay, faceDown, flipDelay, overlap }: { card: Card; width: number; delay: number | undefined; faceDown: boolean; flipDelay: number | null; overlap: number }) {
  return (
    <motion.div
      style={{ marginLeft: overlap }}
      initial={delay !== undefined ? { x: '60vw', y: -140, rotate: -25, opacity: 0 } : false}
      animate={{ x: 0, y: 0, rotate: 0, opacity: 1 }}
      transition={{ delay: delay ?? 0, type: 'spring', damping: 24, stiffness: 210 }}
    >
      <motion.div
        initial={flipDelay !== null ? { rotateY: 180 } : false}
        animate={{ rotateY: 0 }}
        transition={{ delay: flipDelay ?? 0, duration: 0.35 }}
        style={{ transformStyle: 'preserve-3d' }}
      >
        <PlayingCard rank={card.rank} suit={card.suit} faceDown={faceDown} width={width} />
      </motion.div>
    </motion.div>
  );
}

function Hand({
  hand,
  index,
  active,
  cardWidth,
  delays,
  result,
  animating,
}: {
  hand: PlayerHand;
  index: number;
  active: boolean;
  cardWidth: number;
  delays: Record<string, number>;
  result?: { outcome: string; payout: number };
  animating: boolean;
}) {
  const value = handValue(hand.cards);
  const outcomeLabel: Record<string, string> = { blackjack: 'Blackjack', win: 'Gana', push: 'Empate', loss: 'Pierde' };
  return (
    <div className={`relative flex items-end gap-2 rounded-2xl p-1 transition-shadow ${active ? 'shadow-[0_0_0_2px_#f3dfa2,0_0_24px_rgb(243_223_162/0.35)]' : ''}`}>
      <div className="flex items-end" style={{ paddingTop: (hand.cards.length - 1) * cardWidth * 0.12 }}>
        {hand.cards.map((card, ci) => (
          <div key={`${ci}-${card.rank}${card.suit}`} style={{ marginBottom: ci * cardWidth * 0.12 }}>
            <DealtCard card={card} width={cardWidth} delay={delays[`p${index}-${ci}`]} faceDown={false} flipDelay={null} overlap={ci > 0 ? -cardWidth * 0.5 : 0} />
          </div>
        ))}
      </div>
      <div className="mb-1 flex flex-col items-start gap-1.5">
        <span className="flex items-center gap-1" aria-label={`Apuesta ${chips(hand.stake)}`}>
          <ChipStack amount={hand.stake} size={22} max={4} />
          <span className="tabular text-xs font-bold text-gold-light">{grouped(hand.stake)}</span>
        </span>
        {!animating && (
          <ValueBadge
            label="Tu mano"
            value={value.total}
            soft={value.soft && value.total < 21}
            bust={isBust(hand)}
            extra={result ? outcomeLabel[result.outcome] : isBlackjack(hand) ? 'Blackjack' : undefined}
            tone={result ? (result.outcome === 'loss' ? 'lose' : result.outcome === 'push' ? 'neutral' : 'win') : 'neutral'}
          />
        )}
      </div>
    </div>
  );
}

function ValueBadge({ label, value, soft, bust, extra, tone = 'neutral' }: { label: string; value: number; soft: boolean; bust: boolean; extra?: string | undefined; tone?: 'win' | 'lose' | 'neutral' }) {
  const color = tone === 'win' ? 'bg-emerald/90 text-[#00281d]' : tone === 'lose' || bust ? 'bg-ruby text-ivory' : 'bg-black/60 text-ivory';
  const text = bust ? `${value} · Se pasa` : soft ? `${value - 10}/${value}` : String(value);
  return (
    <span className={`tabular mt-1 rounded-full px-2.5 py-0.5 text-xs font-bold ring-1 ring-white/15 ${color}`} aria-label={`${label}: ${value}${bust ? ', se pasa' : ''}${extra ? `, ${extra}` : ''}`}>
      {text}
      {extra && <span className="ml-1.5 font-display">{extra}</span>}
    </span>
  );
}

/** Círculo de apuesta serigrafiado, con las fichas apostadas. */
function BetCircle({ amount, width }: { amount: number | null; width: number }) {
  const size = width * 1.25;
  return (
    <div className="mb-3 flex flex-col items-center gap-1">
      <div className="grid place-items-center rounded-full border-2 border-dashed border-gold-light/50" style={{ width: size, height: size }}>
        {amount ? <ChipStack amount={amount} size={size * 0.5} max={5} /> : <span className="felt-print text-[10px]">Apuesta</span>}
      </div>
      {amount ? <span className="tabular text-xs font-bold text-gold-light">{grouped(amount)}</span> : null}
    </div>
  );
}
