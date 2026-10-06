import { useEffect, useMemo, useState } from 'react';
import { m as motion } from 'motion/react';
import { useWallet, useReadyWallet } from '@/data/wallet';
import { useSettings } from '@/data/settings';
import { LINE_BETS, LINES, PAYTABLE, REELS, ROWS, slotWindow, spinSlots, STRIPS, SYMBOL_NAME, SYMBOLS, type SlotSpin, type SlotSymbol } from '@/engine/slots';
import { chips, grouped } from '@/lib/format';
import { play, resultSound, BIG_WIN_MULTIPLIER } from '@/audio/sound';
import { Button } from '@/ui/Button';
import { Dialog } from '@/ui/Dialog';
import { useCountUp } from '@/ui/ChipBalance';
import { EASE_OUT, SPRING } from '@/ui/motion';
import { Celebration } from '../shared/Celebration';
import { GameShell, TableNotice, useTableSize } from '../shared/GameShell';
import { economyNotice, useHoldProgressEvents } from '../shared/session';
import { SlotSvgDefs, SlotSymbolArt } from './SlotSymbolArt';

const LINE_COLORS = ['#f3dfa2', '#5cc79e', '#e0485e', '#7cc0ff', '#c79cff', '#ffb35c', '#ff7ab8', '#9be15d', '#5ce1e6', '#ffd84d'];

export default function SlotsScreen() {
  const wallet = useReadyWallet();
  const playInstantRound = useWallet((s) => s.playInstantRound);
  const reducedMotion = useSettings((s) => s.reducedMotion);
  const table = useTableSize();
  const [betIndex, setBetIndex] = useState(3);
  const [stops, setStops] = useState<number[]>(() => STRIPS.map((s) => Math.floor(Math.random() * s.length)));
  const [spin, setSpin] = useState<{ id: number; previous: number[]; result: SlotSpin } | null>(null);
  const [spinning, setSpinning] = useState(false);
  const [showResult, setShowResult] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [paytable, setPaytable] = useState(false);
  const [celebrate, setCelebrate] = useState<number | null>(null);
  const [lineShown, setLineShown] = useState(0);
  useHoldProgressEvents(spinning);

  const lineBet = LINE_BETS[betIndex]!;
  const totalBet = lineBet * LINES.length;
  const balance = wallet?.balance ?? 0;

  const tableHeight = Math.max(180, table.height);
  // Rodillos en el centro; a los lados, las líneas (izquierda) y el indicador de premio (derecha).
  const cell = Math.floor(Math.min((tableHeight - 48) / ROWS, (table.width - 330) / REELS, 112));

  const go = async () => {
    if (busy || spinning) return;
    setNotice(null);
    if (totalBet > balance) {
      setNotice('No tienes fichas suficientes para esa apuesta.');
      return;
    }
    const result = spinSlots(lineBet);
    if (!result) return;
    setBusy(true);
    // El resultado se contabiliza antes de animarlo.
    const booked = await playInstantRound('Slots', result.totalBet, result.totalPayout);
    setBusy(false);
    if (!booked.ok) {
      setNotice(economyNotice(booked.error));
      return;
    }
    setShowResult(false);
    setCelebrate(null);
    setSpinning(true);
    setSpin({ id: Date.now(), previous: stops, result });
    setStops(result.stops);
    play('spin');
    const total = reducedMotion ? 400 : 1300 + (REELS - 1) * 260;
    for (let r = 0; r < REELS; r++) play('reelStop', reducedMotion ? 300 : 1300 + r * 260 - 40);
    setTimeout(() => {
      setSpinning(false);
      setShowResult(true);
      setLineShown(0);
      const net = result.totalPayout - result.totalBet;
      const big = result.totalPayout >= BIG_WIN_MULTIPLIER * result.totalBet;
      const sound = result.totalPayout > 0 ? resultSound(Math.max(1, net), big) : null;
      if (sound) play(sound);
      if (big) setCelebrate(Date.now());
    }, total + 150);
  };

  // Recorre las líneas premiadas una a una.
  const wins = showResult && spin ? spin.result.wins : [];
  useEffect(() => {
    if (wins.length < 2) return;
    const timer = setInterval(() => setLineShown((i) => (i + 1) % (wins.length + 1)), 1100);
    return () => clearInterval(timer);
  }, [wins.length]);

  const visibleWins = useMemo(() => (lineShown === 0 || wins.length < 2 ? wins : [wins[lineShown - 1]!]), [wins, lineShown]);
  const highlighted = useMemo(() => {
    const set = new Set<string>();
    visibleWins.forEach((w) => LINES[w.line]!.slice(0, w.count).forEach((row, reel) => set.add(`${reel}-${row}`)));
    return set;
  }, [visibleWins]);

  const result = spin?.result;

  return (
    <GameShell
      title="Slots"
      surface="dark"
      notice={notice && <TableNotice onDismiss={() => setNotice(null)}>{notice}</TableNotice>}
      actions={
        <Button variant="ghost" size="sm" onClick={() => setPaytable(true)}>
          Pagos
        </Button>
      }
      controls={
        <div className="flex h-[68px] items-center gap-3 px-3">
          <div className="flex items-center gap-2">
            <StepButton label="Bajar apuesta" disabled={busy || spinning || betIndex === 0} onClick={() => setBetIndex((i) => i - 1)}>
              −
            </StepButton>
            <div className="w-36 text-center">
              <p className="tabular text-[15px] font-bold whitespace-nowrap text-gold-light">{chips(totalBet)}</p>
              <p className="text-[11px] whitespace-nowrap text-mute">
                {grouped(lineBet)} por línea · {LINES.length} líneas
              </p>
            </div>
            <StepButton label="Subir apuesta" disabled={busy || spinning || betIndex === LINE_BETS.length - 1} onClick={() => setBetIndex((i) => i + 1)}>
              +
            </StepButton>
          </div>
          <div className="min-w-0 flex-1" />
          <Button size="lg" className="min-w-36" loading={busy || spinning} disabled={totalBet > balance} onClick={() => void go()}>
            {totalBet > balance ? 'Sin saldo' : 'Girar'}
          </Button>
        </div>
      }
    >
      <SlotSvgDefs />
      <div className="flex h-full items-center justify-center gap-3 px-2">
        {table.width >= 640 && <LineIndicators wins={showResult ? wins : []} shown={visibleWins} height={ROWS * (cell + 4) + 18} />}
        <div className="relative shrink-0 rounded-[28px] bg-[linear-gradient(180deg,#2a1810,#140b07)] p-3 shadow-[0_24px_50px_-16px_rgb(0_0_0/0.9),inset_0_0_0_2px_rgb(212_175_106/0.8),inset_0_0_0_7px_#1b100a,inset_0_0_0_8px_rgb(212_175_106/0.35)]">
          {/* Con premio, el marco de la máquina se enciende. */}
          {showResult && wins.length > 0 && (
            <motion.span
              key={spin?.id}
              className="pointer-events-none absolute inset-0 rounded-[28px] shadow-[0_0_0_2px_#f3dfa2,0_0_28px_rgb(243_223_162/0.6)]"
              initial={{ opacity: 0 }}
              animate={{ opacity: [0, 1, 0.55] }}
              transition={{ duration: 0.8, ease: EASE_OUT }}
            />
          )}
          <div className="relative overflow-hidden rounded-2xl bg-[#08090c] p-1.5 shadow-[inset_0_0_24px_rgb(0_0_0/1)]">
            <div className="flex gap-1.5">
              {Array.from({ length: REELS }, (_, reel) => (
                <Reel
                  key={reel}
                  reel={reel}
                  cell={cell}
                  stops={stops}
                  spin={spinning ? spin : null}
                  reducedMotion={reducedMotion}
                  highlighted={showResult && wins.length > 0 ? highlighted : null}
                />
              ))}
            </div>
            {showResult && wins.length > 0 && <PayLines wins={visibleWins} cell={cell} />}
            {/* Brillo del cristal. */}
            <div className="pointer-events-none absolute inset-0 rounded-2xl bg-[linear-gradient(180deg,rgb(255_255_255/0.08),transparent_35%,transparent_70%,rgb(0_0_0/0.4))]" />
          </div>
        </div>
        <PrizePanel
          result={showResult ? (result ?? null) : null}
          spinning={spinning}
          totalBet={totalBet}
          shown={visibleWins}
          height={ROWS * (cell + 4) + 18}
        />
      </div>
      <Celebration trigger={celebrate} />
      <PaytableDialog open={paytable} onClose={() => setPaytable(false)} lineBet={lineBet} />
    </GameShell>
  );
}

function StepButton({ children, label, disabled, onClick }: { children: string; label: string; disabled: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      aria-label={label}
      disabled={disabled}
      onClick={() => {
        play('chip');
        onClick();
      }}
      className="grid size-11 place-items-center rounded-full bg-ink-3 text-2xl font-bold text-gold-light ring-1 ring-gold/60 transition-transform duration-150 ease-out active:scale-[0.95] disabled:opacity-30 disabled:active:scale-100"
    >
      {children}
    </button>
  );
}

/** Las diez líneas, como en una máquina real: se encienden con su color cuando pagan. */
function LineIndicators({ wins, shown, height }: { wins: SlotSpin['wins']; shown: SlotSpin['wins']; height: number }) {
  const paying = new Set(wins.map((w) => w.line));
  const current = new Set(shown.map((w) => w.line));
  return (
    <ol className="flex shrink-0 flex-col justify-between py-1" style={{ height }} aria-label="Líneas de premio">
      {LINES.map((_, line) => {
        const on = paying.has(line);
        return (
          <motion.li
            key={line}
            className={`grid h-6 w-8 place-items-center rounded-md text-[11px] font-black transition-colors duration-200 ${on ? 'text-obsidian' : 'bg-white/5 text-mute'}`}
            style={on ? { background: LINE_COLORS[line], boxShadow: `0 0 12px ${LINE_COLORS[line]}` } : undefined}
            aria-label={`Línea ${line + 1}${on ? ', con premio' : ''}`}
            initial={false}
            animate={{ scale: current.has(line) ? 1.12 : 1 }}
            transition={SPRING}
          >
            {line + 1}
          </motion.li>
        );
      })}
    </ol>
  );
}

/** Indicador de premio: la cantidad cuenta hacia arriba y debajo se ve la línea que paga. */
function PrizePanel({ result, spinning, totalBet, shown, height }: { result: SlotSpin | null; spinning: boolean; totalBet: number; shown: SlotSpin['wins']; height: number }) {
  const payout = result?.totalPayout ?? 0;
  const counted = useCountUp(payout, 900);
  const big = result !== null && payout >= BIG_WIN_MULTIPLIER * totalBet;
  const win = result !== null && payout > 0;
  return (
    <section
      className={`flex w-36 shrink-0 flex-col items-center justify-center gap-2 rounded-2xl px-3 py-3 text-center ring-1 transition-colors ${win ? 'bg-[radial-gradient(circle_at_50%_30%,rgb(212_175_106/0.28),rgb(20_11_7/0.95))] ring-gold-light/70' : 'bg-black/45 ring-gold/25'}`}
      style={{ height }}
      aria-live="polite"
      aria-label="Premio"
    >
      <p className="felt-print text-[11px] font-bold">{big ? '¡Gran premio!' : 'Premio'}</p>
      <motion.p
        key={result ? 'r' : 'e'}
        className={`tabular font-display leading-none font-bold ${win ? 'text-gold-gradient' : 'text-mute'} ${counted >= 10_000 ? 'text-[26px]' : 'text-[32px]'}`}
        animate={win ? { scale: [0.8, 1.08, 1] } : { scale: 1 }}
        transition={{ duration: 0.5 }}
      >
        {spinning ? '···' : grouped(win ? counted : 0)}
      </motion.p>
      <p className="text-[11px] text-ivory-dim">{win ? 'fichas' : spinning ? 'Girando…' : result ? 'Sin premio esta vez' : 'Gira para jugar'}</p>
      {win && shown.length > 0 && (
        <div className="mt-1 flex w-full flex-col gap-1">
          {shown.slice(0, 2).map((w) => (
            <p key={w.line} className="flex items-center justify-center gap-1 text-[11px] leading-tight text-ivory">
              <span className="inline-block size-2.5 rounded-full" style={{ background: LINE_COLORS[w.line] }} aria-hidden />
              <span>
                Línea {w.line + 1} · {w.count} × {SYMBOL_NAME[w.symbol]}
              </span>
            </p>
          ))}
          {result && result.wins.length > 2 && shown.length !== 1 && <p className="text-[10px] text-mute">y {result.wins.length - 2} líneas más</p>}
        </div>
      )}
      {result && win && payout < totalBet && <p className="text-[10px] text-mute">Apostaste {grouped(totalBet)}</p>}
    </section>
  );
}

/** Un rodillo: al girar cae una tira de símbolos y se frena en su posición final. */
function Reel({
  reel,
  cell,
  stops,
  spin,
  reducedMotion,
  highlighted,
}: {
  reel: number;
  cell: number;
  stops: number[];
  spin: { id: number; previous: number[]; result: SlotSpin } | null;
  reducedMotion: boolean;
  highlighted: Set<string> | null;
}) {
  const finalWindow = slotWindow(stops)[reel]!;
  const filler = useMemo(() => {
    if (!spin) return [] as SlotSymbol[];
    const strip = STRIPS[reel]!;
    const count = reducedMotion ? 3 : 14 + reel * 4;
    return Array.from({ length: count }, (_, i) => strip[(spin.previous[reel]! + 37 + i * 7) % strip.length]!);
  }, [spin, reel, reducedMotion]);
  const previousWindow = spin ? slotWindow(spin.previous)[reel]! : finalWindow;
  // De arriba abajo: final, relleno, anterior. Se parte mostrando la anterior y se baja hasta la final.
  const column = spin ? [...finalWindow, ...filler, ...previousWindow] : finalWindow;
  const travel = spin ? (filler.length + ROWS) * (cell + 4) : 0;
  const duration = reducedMotion ? 0.3 : 1.3 + reel * 0.26;
  return (
    <div className="relative overflow-hidden rounded-lg bg-[linear-gradient(180deg,#d9d0bd,#fffaf0_18%,#fffaf0_82%,#d9d0bd)]" style={{ width: cell, height: ROWS * (cell + 4) - 4 }}>
      <motion.div
        key={spin?.id ?? 'idle'}
        className="flex flex-col gap-1"
        // transform completo (va por la GPU); la curva acaba con un pequeño rebote, como un rodillo que se clava.
        initial={{ transform: `translateY(${-travel}px)` }}
        animate={{ transform: 'translateY(0px)' }}
        transition={{ duration, ease: [0.25, 0.1, 0.25, 1.08] }}
      >
        {column.map((symbol, i) => {
          const row = i;
          const lit = highlighted?.has(`${reel}-${row}`) && i < ROWS;
          const dim = highlighted !== null && !lit && i < ROWS;
          return (
            <div
              key={i}
              className={`relative grid shrink-0 place-items-center transition-opacity duration-300 ${lit ? 'rounded-md bg-gold/30 shadow-[inset_0_0_0_3px_#e2c27f,0_0_18px_rgb(243_223_162/0.6)]' : ''}`}
              style={{ width: cell, height: cell, opacity: dim ? 0.3 : 1 }}
            >
              <motion.div animate={{ transform: lit ? ['scale(1)', 'scale(1.12)', 'scale(1)'] : 'scale(1)' }} transition={lit ? { duration: 0.9, repeat: Infinity, ease: 'easeInOut' } : { duration: 0.2 }}>
                <SlotSymbolArt symbol={symbol} size={cell * 0.78} />
              </motion.div>
            </div>
          );
        })}
      </motion.div>
      <div className="pointer-events-none absolute inset-0 shadow-[inset_0_10px_14px_-8px_rgb(0_0_0/0.55),inset_0_-10px_14px_-8px_rgb(0_0_0/0.55)]" />
    </div>
  );
}

/** Líneas premiadas dibujadas sobre los rodillos. */
function PayLines({ wins, cell }: { wins: SlotSpin['wins']; cell: number }) {
  const step = cell + 6;
  const visible = wins;
  return (
    <svg className="pointer-events-none absolute inset-0 h-full w-full" aria-hidden>
      {visible.map((w) => {
        const points = LINES[w.line]!.map((row, reel) => `${6 + reel * step + cell / 2},${6 + row * (cell + 4) + cell / 2}`).join(' ');
        // La línea se dibuja de izquierda a derecha, como recorriendo los símbolos que paga.
        return (
          <motion.polyline
            key={w.line}
            points={points}
            fill="none"
            stroke={LINE_COLORS[w.line]}
            strokeWidth="4"
            strokeLinejoin="round"
            strokeLinecap="round"
            opacity="0.9"
            initial={{ pathLength: 0 }}
            animate={{ pathLength: 1 }}
            transition={{ duration: 0.4, ease: EASE_OUT }}
          />
        );
      })}
    </svg>
  );
}

function PaytableDialog({ open, onClose, lineBet }: { open: boolean; onClose: () => void; lineBet: number }) {
  return (
    <Dialog open={open} onClose={onClose} title="Tabla de pagos" actions={<Button onClick={onClose}>Cerrar</Button>}>
      <p className="mb-3 text-sm">Premios por línea con tu apuesta actual ({chips(lineBet)} por línea). Cuentan los símbolos seguidos desde el primer rodillo. La corona es comodín: sustituye a cualquier símbolo.</p>
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-xs text-mute">
            <th className="pb-2 font-semibold">Símbolo</th>
            <th className="pb-2 text-right font-semibold">×2</th>
            <th className="pb-2 text-right font-semibold">×3</th>
            <th className="pb-2 text-right font-semibold">×4</th>
            <th className="pb-2 text-right font-semibold">×5</th>
          </tr>
        </thead>
        <tbody>
          {[...SYMBOLS].reverse().map((symbol) => (
            <tr key={symbol} className="border-t border-white/5">
              <td className="flex items-center gap-2 py-1.5">
                <SlotSymbolArt symbol={symbol} size={28} />
                <span className="text-ivory">{SYMBOL_NAME[symbol]}</span>
              </td>
              {PAYTABLE[symbol].map((multiplier, i) => (
                <td key={i} className="tabular py-1.5 text-right text-gold-light">
                  {multiplier ? grouped(multiplier * lineBet) : '—'}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </Dialog>
  );
}
