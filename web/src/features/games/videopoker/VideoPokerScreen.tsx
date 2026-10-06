import { useCallback, useEffect, useRef, useState } from 'react';
import { AnimatePresence, m as motion } from 'motion/react';
import { useWallet, useReadyWallet } from '@/data/wallet';
import type { Card } from '@/engine/cards';
import {
  classifyVideoPoker,
  dealVideoPoker,
  drawVideoPoker,
  PAYTABLE,
  VIDEO_POKER_BETS,
  videoPokerPayout,
  winningCards,
  type VideoPokerDeal,
  type VideoPokerHand,
} from '@/engine/videopoker';
import { chips, grouped } from '@/lib/format';
import { play, resultSound, BIG_WIN_MULTIPLIER } from '@/audio/sound';
import { Button } from '@/ui/Button';
import { cardName, PlayingCard } from '@/ui/PlayingCard';
import { EASE_OUT } from '@/ui/motion';

/** Carta que se va al cambiarla (o el dorso al repartir): cae un poco y se desvanece. */
const DISCARD = { opacity: 0, transform: 'translateY(14px) scale(0.96)', transition: { duration: 0.16, ease: EASE_OUT } };
import { Celebration } from '../shared/Celebration';
import { FlipCard } from '../shared/FlipCard';
import { GameShell, TableNotice, useTableSize } from '../shared/GameShell';
import { economyNotice, loadSession, saveSession, useHoldProgressEvents, usePlayerId } from '../shared/session';

/** Mano en curso: la apuesta ya está en el monedero (ronda abierta) y falta el cambio. */
interface OpenHand {
  roundId: string;
  bet: number;
  deal: VideoPokerDeal;
  held: boolean[];
}

interface Saved {
  open: OpenHand | null;
  lastBet: number;
}

const NAME = new Map(PAYTABLE.map((row) => [row.hand, row.name]));
/** Nombres cortos para la tabla de pagos estrecha. */
const SHORT: Partial<Record<VideoPokerHand, string>> = { royalFlush: 'Esc. real', straightFlush: 'Esc. de color', jacksOrBetter: 'Jotas o más' };
const NONE_HELD = [false, false, false, false, false];

export default function VideoPokerScreen() {
  const uid = usePlayerId();
  const wallet = useReadyWallet();
  const { placeBet, settleRound } = useWallet();
  const table = useTableSize();
  const [betIndex, setBetIndex] = useState(3);
  const [phase, setPhase] = useState<'betting' | 'dealt' | 'result'>('betting');
  const [open, setOpen] = useState<OpenHand | null>(null);
  const [final, setFinal] = useState<{ cards: Card[]; hand: VideoPokerHand | null; payout: number; bet: number; replaced: boolean[] } | null>(null);
  const [dealId, setDealId] = useState(0);
  const [busy, setBusy] = useState(false);
  const [revealing, setRevealing] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [celebrate, setCelebrate] = useState<number | null>(null);
  const restored = useRef(false);
  useHoldProgressEvents(revealing);

  const bet = VIDEO_POKER_BETS[betIndex]!;
  const balance = wallet?.balance ?? 0;

  const persist = useCallback(
    (saved: Saved) => {
      if (uid) saveSession(uid, 'VideoPoker', saved);
    },
    [uid],
  );

  // Reanudar: la mano guardada corresponde a la ronda abierta en el monedero.
  useEffect(() => {
    if (restored.current || !uid || !wallet) return;
    restored.current = true;
    const saved = loadSession<Saved>(uid, 'VideoPoker');
    const round = wallet.openRound?.game === 'VideoPoker' ? wallet.openRound : null;
    const savedIndex = saved ? VIDEO_POKER_BETS.indexOf(saved.lastBet) : -1;
    if (savedIndex >= 0) setBetIndex(savedIndex);
    if (saved?.open && round && saved.open.roundId === round.id) {
      setOpen(saved.open);
      setPhase('dealt');
    } else if (round) {
      // Fichas en una mano sin su estado (otro dispositivo, datos borrados): se cierra sin premio.
      void settleRound(0);
      setNotice('La mano anterior no se pudo reanudar y se ha cerrado.');
      persist({ open: null, lastBet: saved?.lastBet ?? bet });
    }
  }, [uid, wallet, settleRound, persist, bet]);

  const deal = async () => {
    if (busy || phase === 'dealt') return;
    setNotice(null);
    if (bet > balance) return setNotice('No tienes fichas suficientes para esa apuesta.');
    setBusy(true);
    const result = await placeBet('VideoPoker', bet);
    setBusy(false);
    if (!result.ok || !result.wallet.openRound) return setNotice(result.ok ? 'No se pudo abrir la mano.' : economyNotice(result.error));
    const hand: OpenHand = { roundId: result.wallet.openRound.id, bet, deal: dealVideoPoker(), held: NONE_HELD };
    setOpen(hand);
    setFinal(null);
    setCelebrate(null);
    setDealId(Date.now());
    setPhase('dealt');
    persist({ open: hand, lastBet: bet });
    for (let i = 0; i < 5; i++) play('card', i * 90);
  };

  const toggle = (index: number) => {
    if (!open || phase !== 'dealt' || busy) return;
    const next = { ...open, held: open.held.map((h, i) => (i === index ? !h : h)) };
    setOpen(next);
    persist({ open: next, lastBet: open.bet });
    play('chip');
  };

  const draw = async () => {
    if (!open || phase !== 'dealt' || busy) return;
    const cards = drawVideoPoker(open.deal, open.held);
    const hand = classifyVideoPoker(cards);
    const payout = videoPokerPayout(open.bet, hand);
    setBusy(true);
    // El resultado se contabiliza antes de mostrarlo.
    const result = await settleRound(payout);
    setBusy(false);
    if (!result.ok) return setNotice(economyNotice(result.error));
    persist({ open: null, lastBet: open.bet });
    const replaced = open.held.map((h) => !h);
    setFinal({ cards, hand, payout, bet: open.bet, replaced });
    setOpen(null);
    setPhase('result');
    setRevealing(true);
    const count = replaced.filter(Boolean).length;
    for (let i = 0; i < count; i++) play('card', i * 100);
    setTimeout(() => {
      setRevealing(false);
      const sound = resultSound(payout - open.bet, payout >= BIG_WIN_MULTIPLIER * open.bet);
      if (sound) play(sound);
      if (payout >= 25 * open.bet) setCelebrate(Date.now());
    }, 250 + count * 100 + 450);
  };

  // Medidas: la tabla de pagos a la izquierda y las cinco cartas a la derecha.
  const payWidth = table.width >= 640 ? 196 : 158;
  const cardWidth = Math.round(Math.max(40, Math.min(96, (table.width - payWidth - 24 - 16 - 40) / 5, ((table.height - 70) / 1.4) * 0.92)));

  const shown = phase === 'result' && final ? final.cards : open?.deal.hand ?? null;
  const currentHand = phase === 'dealt' && open ? classifyVideoPoker(open.deal.hand) : phase === 'result' && final ? final.hand : null;
  // Con premio se atenúan las cartas que no forman la jugada; sin premio, ninguna.
  const winners = phase === 'result' && final?.hand ? winningCards(final.cards, final.hand) : null;
  const status =
    phase === 'betting'
      ? 'Elige tu apuesta y pulsa Repartir.'
      : phase === 'dealt'
        ? currentHand
          ? `Ya tienes ${NAME.get(currentHand)!.toLowerCase()}. Guarda las cartas que quieras y cambia el resto.`
          : 'Toca las cartas que quieras guardar y pulsa Cambiar.'
        : final?.hand
          ? `${NAME.get(final.hand)} · ganas ${grouped(final.payout)}`
          : 'Sin premio esta vez.';
  const heldCount = open?.held.filter(Boolean).length ?? 0;

  return (
    <GameShell
      title="Video póker"
      surface="dark"
      notice={notice && <TableNotice onDismiss={() => setNotice(null)}>{notice}</TableNotice>}
      controls={
        <div className="flex h-[68px] items-center gap-3 px-3">
          <div className="flex items-center gap-2">
            <StepButton label="Bajar apuesta" disabled={busy || phase === 'dealt' || betIndex === 0} onClick={() => setBetIndex((i) => i - 1)}>
              −
            </StepButton>
            <div className="w-24 text-center">
              <p className="tabular text-[15px] font-bold whitespace-nowrap text-gold-light">{chips(phase === 'dealt' && open ? open.bet : bet)}</p>
              <p className="text-[11px] whitespace-nowrap text-mute">por mano</p>
            </div>
            <StepButton label="Subir apuesta" disabled={busy || phase === 'dealt' || betIndex === VIDEO_POKER_BETS.length - 1} onClick={() => setBetIndex((i) => i + 1)}>
              +
            </StepButton>
          </div>
          <div className="min-w-0 flex-1" />
          {phase === 'dealt' ? (
            <Button size="lg" className="min-w-36" loading={busy} onClick={() => void draw()}>
              {heldCount === 5 ? 'Plantarse' : 'Cambiar'}
            </Button>
          ) : (
            <Button size="lg" className="min-w-36" loading={busy} disabled={bet > balance} onClick={() => void deal()}>
              {bet > balance ? 'Sin saldo' : 'Repartir'}
            </Button>
          )}
        </div>
      }
    >
      <div className="flex h-full items-center justify-center gap-4 px-3 py-2">
        <Paytable width={payWidth} bet={phase === 'dealt' && open ? open.bet : (final?.bet ?? bet)} current={currentHand} final={phase === 'result'} short={payWidth < 190} dense={table.height < 230} />
        <div className="flex min-w-0 flex-col items-center gap-2">
          <motion.p
            key={status}
            className={`line-clamp-2 max-w-full text-center text-[13px] leading-snug font-semibold ${phase === 'result' && final?.hand ? 'font-display text-[17px] text-gold-gradient' : 'text-ivory-dim'}`}
            aria-live="polite"
            initial={{ opacity: 0, transform: 'translateY(4px)' }}
            animate={{ opacity: 1, transform: 'translateY(0px)' }}
            transition={{ duration: 0.2, ease: EASE_OUT, delay: phase === 'result' ? 0.5 : 0 }}
          >
            {status}
          </motion.p>
          <div className="flex gap-2.5 pt-2">
            {Array.from({ length: 5 }, (_, i) => {
              const card = shown?.[i];
              if (!card) {
                return (
                  <AnimatePresence key={`slot-${i}`} mode="wait" initial={false}>
                    <motion.div key="back" className="pb-5" initial={{ opacity: 0 }} animate={{ opacity: 0.6 }} exit={DISCARD}>
                      <PlayingCard rank="A" suit="spades" faceDown width={cardWidth} />
                    </motion.div>
                  </AnimatePresence>
                );
              }
              const held = phase === 'dealt' && !!open?.held[i];
              // Cartas nuevas (reparto y las cambiadas): llegan boca abajo y se destapan por turnos.
              const order = phase === 'result' && final ? final.replaced.slice(0, i).filter(Boolean).length : i;
              const fresh = phase === 'dealt' || (phase === 'result' && !!final?.replaced[i]);
              // Cada hueco cambia su carta en dos tiempos: la vieja se va y después llega la nueva.
              return (
                <AnimatePresence key={`slot-${i}`} mode="wait" initial={false}>
                <motion.button
                  key={`${dealId}-${i}-${card.rank}${card.suit}`}
                  exit={DISCARD}
                  type="button"
                  disabled={phase !== 'dealt' || busy}
                  onClick={() => toggle(i)}
                  aria-pressed={phase === 'dealt' ? held : undefined}
                  aria-label={phase === 'dealt' ? `${held ? 'Soltar' : 'Guardar'} ${cardName(card.rank, card.suit)}` : cardName(card.rank, card.suit)}
                  className="relative pb-5 transition-transform duration-150 ease-out enabled:active:scale-[0.97]"
                >
                  <motion.div
                    className="relative"
                    initial={fresh ? { opacity: 0, transform: 'translateY(-14px)' } : false}
                    animate={{ opacity: winners && !winners[i] ? 0.45 : 1, transform: held ? 'translateY(-8px)' : 'translateY(0px)' }}
                    // Retener o soltar es un interruptor: muelle (se puede pulsar otra vez a mitad).
                    transition={{
                      transform: { type: 'spring', duration: 0.35, bounce: 0.25, delay: fresh && !held ? order * 0.1 : 0 },
                      opacity: { duration: 0.25, ease: EASE_OUT, delay: fresh ? order * 0.1 : 0.45 },
                    }}
                  >
                    <FlipCard rank={card.rank} suit={card.suit} faceDown={false} dealtFaceDown={fresh} delay={0.15 + order * 0.1} width={cardWidth} />
                    <AnimatePresence>
                      {(held || (winners && winners[i])) && (
                        <motion.span
                          className="pointer-events-none absolute inset-0 rounded-[9%] shadow-[0_0_0_2px_#f3dfa2,0_0_16px_rgb(243_223_162/0.55)]"
                          initial={{ opacity: 0 }}
                          animate={{ opacity: 1 }}
                          exit={{ opacity: 0 }}
                          transition={{ duration: 0.2, ease: EASE_OUT, delay: winners ? 0.5 : 0 }}
                        />
                      )}
                    </AnimatePresence>
                  </motion.div>
                  {/* Bajo la carta, sin ensanchar la columna (con cartas pequeñas el texto es más ancho que ellas). */}
                  <motion.span
                    className="absolute bottom-0 left-1/2 -translate-x-1/2 text-[10px] font-black tracking-[0.14em] whitespace-nowrap text-gold-light uppercase"
                    aria-hidden
                    initial={false}
                    animate={{ opacity: held ? 1 : 0, transform: held ? 'translateY(0px)' : 'translateY(-4px)' }}
                    transition={{ duration: 0.15, ease: EASE_OUT }}
                  >
                    Retenida
                  </motion.span>
                </motion.button>
                </AnimatePresence>
              );
            })}
          </div>
        </div>
      </div>
      <Celebration trigger={celebrate} />
    </GameShell>
  );
}

/** Tabla de pagos: lo que paga cada jugada con la apuesta actual; la jugada que se tiene, resaltada. */
function Paytable({ width, bet, current, final, short, dense }: { width: number; bet: number; current: VideoPokerHand | null; final: boolean; short: boolean; dense: boolean }) {
  return (
    <section className="shrink-0 rounded-2xl bg-black/45 p-2 ring-1 ring-gold/30" style={{ width }} aria-label="Tabla de pagos">
      <ol className="flex flex-col">
        {PAYTABLE.map((row) => {
          const active = row.hand === current;
          return (
            <motion.li
              key={row.hand}
              // La jugada final late una vez al aparecer el premio.
              animate={active && final ? { scale: [1, 1.05, 1] } : { scale: 1 }}
              transition={{ duration: 0.45, ease: EASE_OUT, delay: 0.55 }}
              className={`flex items-center justify-between gap-2 rounded-md px-2 leading-tight transition-colors duration-200 ${dense ? 'py-0 text-[10.5px]' : 'py-[3px] text-[11px]'} ${
                active ? (final ? 'bg-gold text-on-gold' : 'bg-gold/20 text-gold-light') : 'text-ivory-dim'
              }`}
            >
              <span className={`truncate ${row.hand === 'royalFlush' ? 'font-display font-bold' : 'font-semibold'}`}>{(short && SHORT[row.hand]) || row.name}</span>
              <span className="tabular font-bold">{grouped(row.multiplier * bet)}</span>
            </motion.li>
          );
        })}
      </ol>
    </section>
  );
}

function StepButton({ children, label, disabled, onClick }: { children: string; label: string; disabled: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      className="grid size-11 place-items-center rounded-full bg-ink-3 text-2xl font-bold text-gold-light ring-1 ring-gold/60 transition-transform duration-150 ease-out active:scale-[0.95] disabled:opacity-30 disabled:active:scale-100"
    >
      {children}
    </button>
  );
}
