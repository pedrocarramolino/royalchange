import { useEffect, useLayoutEffect, useRef, useState, type PointerEvent, type ReactNode } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { useWallet, useReadyWallet } from '@/data/wallet';
import { useSettings } from '@/data/settings';
import { buyScratchTicket, SCRATCH_BETS, SCRATCH_CELLS, SCRATCH_PRIZES, type ScratchTicket } from '@/engine/scratch';
import { SYMBOL_NAME, type SlotSymbol } from '@/engine/slots';
import { chips, grouped } from '@/lib/format';
import { play, resultSound, BIG_WIN_MULTIPLIER } from '@/audio/sound';
import { Button } from '@/ui/Button';
import { EASE_IN_OUT, EASE_OUT, SPRING } from '@/ui/motion';
import { Celebration } from '../shared/Celebration';
import { GameShell, TableNotice, useTableSize } from '../shared/GameShell';
import { economyNotice, useHoldProgressEvents } from '../shared/session';
import { SlotSvgDefs, SlotSymbolArt } from '../slots/SlotSymbolArt';

const NONE = (): boolean[] => Array.from({ length: SCRATCH_CELLS }, () => false);
/** Nombre de cada símbolo (en el boleto la corona no es comodín: es el premio gordo). */
const symbolName = (symbol: SlotSymbol) => (symbol === 'Wild' ? 'Corona' : SYMBOL_NAME[symbol]);

export default function ScratchScreen() {
  const wallet = useReadyWallet();
  const playInstantRound = useWallet((s) => s.playInstantRound);
  const reducedMotion = useSettings((s) => s.reducedMotion);
  const table = useTableSize();
  const [betIndex, setBetIndex] = useState(3);
  const [ticket, setTicket] = useState<{ id: number; data: ScratchTicket } | null>(null);
  const [revealed, setRevealed] = useState<boolean[]>(NONE);
  const [revealAll, setRevealAll] = useState(0);
  const [held, setHeld] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [celebrate, setCelebrate] = useState<number | null>(null);
  const finishedId = useRef<number | null>(null);

  const bet = SCRATCH_BETS[betIndex]!;
  const balance = wallet?.balance ?? 0;
  const finished = ticket !== null && revealed.every(Boolean);
  const active = ticket !== null && !finished;
  useHoldProgressEvents(active);

  const buy = async () => {
    if (busy || active) return;
    setNotice(null);
    if (bet > balance) return setNotice('No tienes fichas suficientes para este boleto.');
    const data = buyScratchTicket(bet);
    setBusy(true);
    // El resultado se contabiliza al comprar; el saldo visible no lo adelanta hasta rascarlo todo.
    setHeld(balance - data.stake);
    const booked = await playInstantRound('Scratch', data.stake, data.payout);
    setBusy(false);
    if (!booked.ok) {
      setHeld(null);
      return setNotice(economyNotice(booked.error));
    }
    setTicket({ id: Date.now(), data });
    setRevealed(NONE());
    setCelebrate(null);
    play('chip');
  };

  const reveal = (index: number) => {
    setRevealed((current) => {
      if (current[index]) return current;
      const next = [...current];
      next[index] = true;
      return next;
    });
  };

  // Boleto rascado del todo: se muestra el saldo real y suena el resultado.
  useEffect(() => {
    if (!finished || !ticket || finishedId.current === ticket.id) return;
    finishedId.current = ticket.id;
    setHeld(null);
    const { stake, payout } = ticket.data;
    const big = payout >= BIG_WIN_MULTIPLIER * stake;
    const sound = resultSound(payout - stake, big);
    if (sound) play(sound, 150);
    if (big) setCelebrate(Date.now());
  }, [finished, ticket]);

  // Medidas: el boleto manda; a su lado, la tabla de premios.
  const sideWidth = table.width >= 560 ? 156 : 124;
  const pad = 12;
  const header = Math.round(Math.max(30, Math.min(48, table.height * 0.15)));
  const grid = Math.max(120, Math.min(table.height - 20 - header - pad * 2, table.width - sideWidth - 48 - pad * 2));
  const gap = Math.round(grid * 0.035);
  const cell = (grid - gap * 2) / 3;
  const prize = finished && ticket ? ticket.data.prize : null;
  const net = ticket ? ticket.data.payout - ticket.data.stake : 0;

  return (
    <GameShell
      title="Rasca y gana"
      surface="dark"
      heldBalance={held}
      notice={notice && <TableNotice onDismiss={() => setNotice(null)}>{notice}</TableNotice>}
      controls={
        <div className="flex h-[68px] items-center gap-2 px-3">
          <div className="flex items-center gap-1.5">
            <StepButton label="Bajar precio" disabled={active || betIndex === 0} onClick={() => setBetIndex((i) => i - 1)}>
              −
            </StepButton>
            <div className="w-[84px] text-center">
              <p className="tabular text-[14px] font-bold whitespace-nowrap text-gold-light">{chips(bet)}</p>
              <p className="text-[10px] whitespace-nowrap text-mute">por boleto</p>
            </div>
            <StepButton label="Subir precio" disabled={active || betIndex === SCRATCH_BETS.length - 1} onClick={() => setBetIndex((i) => i + 1)}>
              +
            </StepButton>
          </div>
          <div className="min-w-0 flex-1" />
          {active ? (
            <Button className="min-w-32" variant="ghost" onClick={() => setRevealAll((n) => n + 1)}>
              Rascar todo
            </Button>
          ) : (
            <Button className="min-w-32" loading={busy} disabled={bet > balance} onClick={() => void buy()}>
              {bet > balance ? 'Sin saldo' : ticket ? 'Otro boleto' : 'Comprar boleto'}
            </Button>
          )}
        </div>
      }
    >
      <SlotSvgDefs />
      <div className="flex h-full items-center justify-center gap-5 px-3 py-2">
        <div className="relative shrink-0" style={{ width: grid + pad * 2, height: grid + header + pad * 2 }}>
          <AnimatePresence mode="popLayout" initial={false}>
            <motion.div
              key={ticket?.id ?? 0}
              className="absolute inset-0"
              // El boleto nuevo llega deslizándose y el rascado se aparta.
              initial={reducedMotion ? { opacity: 0 } : { opacity: 0, transform: 'translateX(48px) rotate(3deg)' }}
              animate={{ opacity: 1, transform: 'translateX(0px) rotate(0deg)' }}
              exit={reducedMotion ? { opacity: 0 } : { opacity: 0, transform: 'translateX(-56px) rotate(-4deg)', transition: { duration: 0.3, ease: EASE_IN_OUT } }}
              transition={{ duration: 0.4, ease: EASE_OUT }}
            >
              <Ticket
                ticket={ticket?.data ?? null}
                price={ticket?.data.stake ?? bet}
                revealed={ticket ? revealed : NONE()}
                finished={finished}
                header={header}
                pad={pad}
                cell={cell}
                gap={gap}
              >
                <ScratchFoil
                  key={`${ticket?.id ?? 0}-${Math.round(cell)}`}
                  cell={cell}
                  gap={gap}
                  enabled={active}
                  revealed={revealed}
                  revealAll={revealAll}
                  reduced={reducedMotion}
                  onReveal={reveal}
                />
              </Ticket>
            </motion.div>
          </AnimatePresence>
          {!ticket && (
            <p className="pointer-events-none absolute inset-x-0 bottom-[34%] z-10 mx-auto w-fit rounded-full bg-black/75 px-2.5 py-1 text-center text-[11px] font-semibold whitespace-nowrap text-ivory ring-1 ring-gold/40">
              Compra un boleto para rascar
            </p>
          )}
          {/* Resultado al terminar de rascar. */}
          <AnimatePresence>
            {finished && ticket && (
              <motion.p
                key={ticket.id}
                role="status"
                className={`tabular pointer-events-none absolute inset-x-0 -bottom-1 z-20 mx-auto w-fit rounded-full bg-black/80 px-4 py-1 font-display text-lg font-bold whitespace-nowrap ring-1 ring-gold/50 ${net > 0 ? 'text-gold-gradient' : 'text-ivory'}`}
                initial={{ opacity: 0, scale: 0.85 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, transition: { duration: 0.15 } }}
                transition={{ ...SPRING, delay: 0.35 }}
              >
                {net > 0 ? `+${grouped(net)}` : net === 0 ? 'Recuperas tu boleto' : 'Sin premio'}
              </motion.p>
            )}
          </AnimatePresence>
        </div>

        <aside className="flex shrink-0 flex-col gap-1 self-center" style={{ width: sideWidth }} aria-label="Premios por tres iguales">
          <p className="felt-print mb-0.5 text-center text-[10px] font-bold">Tres iguales</p>
          {SCRATCH_PRIZES.map((p) => {
            const hit = prize?.symbol === p.symbol;
            return (
              <motion.div
                key={p.symbol}
                className={`flex items-center gap-1.5 rounded-lg px-2 py-0.5 ring-1 transition-colors duration-200 ${hit ? 'bg-gold/25 ring-gold-light' : 'bg-white/[0.04] ring-white/10'}`}
                aria-label={`Tres ${symbolName(p.symbol).toLowerCase()}: ×${p.multiplier}`}
                initial={false}
                animate={{ scale: hit ? [1, 1.08, 1] : 1 }}
                transition={{ duration: 0.45, ease: EASE_OUT, delay: hit ? 0.3 : 0 }}
              >
                <span className="grid place-items-center rounded-md bg-[#fbf3df] p-0.5">
                  <SlotSymbolArt symbol={p.symbol} size={table.height < 260 ? 15 : 19} />
                </span>
                <span className="flex-1 text-[10px] font-semibold text-ivory-dim">× 3</span>
                <span className={`tabular text-[12px] font-bold ${hit ? 'text-gold-light' : 'text-ivory'}`}>×{p.multiplier}</span>
              </motion.div>
            );
          })}
        </aside>
      </div>
      <Celebration trigger={celebrate} />
    </GameShell>
  );
}

/** El boleto impreso: cabecera, casillas con sus símbolos y, encima, la lámina que se rasca. */
function Ticket({
  ticket,
  price,
  revealed,
  finished,
  header,
  pad,
  cell,
  gap,
  children,
}: {
  ticket: ScratchTicket | null;
  price: number;
  revealed: boolean[];
  finished: boolean;
  header: number;
  pad: number;
  cell: number;
  gap: number;
  children: ReactNode;
}) {
  // Muescas a los lados, como un boleto troquelado.
  const notch = 'radial-gradient(circle at 0 50%, transparent 8px, #000 8.5px) left / 51% 100% no-repeat, radial-gradient(circle at 100% 50%, transparent 8px, #000 8.5px) right / 51% 100% no-repeat';
  return (
    <div
      className="relative size-full rounded-2xl shadow-[0_18px_40px_-14px_rgb(0_0_0/0.9)]"
      style={{
        padding: pad,
        background:
          'repeating-radial-gradient(circle at 50% 115%, rgb(243 223 162 / 0.07) 0 2px, transparent 2px 9px), linear-gradient(160deg, #7c1529, #4c0b1a 60%, #2b0610)',
        mask: notch,
        WebkitMask: notch,
      }}
    >
      <div className="pointer-events-none absolute inset-[5px] rounded-xl border border-dashed border-gold/45" aria-hidden />
      <div className="relative flex flex-col justify-center" style={{ height: header }}>
        <p className="font-display leading-none font-bold tracking-wide whitespace-nowrap text-gold-gradient" style={{ fontSize: Math.min(22, header * 0.48, (cell * 3) / 10.5) }}>
          Rasca y gana
        </p>
        <p className="tabular mt-0.5 truncate text-[10px] font-semibold text-ivory-dim">Boleto de {chips(price)}</p>
      </div>
      <div className="relative" style={{ width: cell * 3 + gap * 2, height: cell * 3 + gap * 2 }}>
        <div className="grid size-full" style={{ gridTemplateColumns: `repeat(3, ${cell}px)`, gap }} role="list" aria-label="Casillas del boleto">
          {Array.from({ length: SCRATCH_CELLS }, (_, i) => {
            const symbol = ticket?.cells[i];
            const winning = finished && ticket?.winning.includes(i);
            const dim = finished && ticket?.prize && !winning;
            return (
              <motion.div
                key={i}
                role="listitem"
                aria-label={symbol && revealed[i] ? symbolName(symbol) : 'Sin rascar'}
                className={`grid place-items-center rounded-[10px] bg-[radial-gradient(circle_at_50%_40%,#fffaf0,#efe3c4)] shadow-[inset_0_1px_3px_rgb(0_0_0/0.25)] ${winning ? 'ring-2 ring-gold-light' : ''}`}
                style={{ boxShadow: winning ? '0 0 16px rgb(243 223 162 / 0.75), inset 0 1px 3px rgb(0 0 0 / 0.25)' : undefined }}
                initial={false}
                animate={{ opacity: dim ? 0.4 : 1, scale: winning ? [1, 1.1, 1] : 1 }}
                transition={{ duration: 0.5, ease: EASE_OUT, delay: winning ? 0.1 + ticket!.winning.indexOf(i) * 0.1 : 0 }}
              >
                {symbol && <SlotSymbolArt symbol={symbol} size={cell * 0.72} />}
              </motion.div>
            );
          })}
        </div>
        {children}
      </div>
    </div>
  );
}

/**
 * Lámina dorada sobre las casillas (canvas). Al pasar el dedo se borra un trazo; cuando una casilla
 * está rascada en más de la mitad, se desvela entera. Los puntos de muestreo de cada casilla dicen
 * cuánto se ha rascado sin tener que leer los píxeles.
 */
function ScratchFoil({
  cell,
  gap,
  enabled,
  revealed,
  revealAll,
  reduced,
  onReveal,
}: {
  cell: number;
  gap: number;
  enabled: boolean;
  revealed: boolean[];
  revealAll: number;
  reduced: boolean;
  onReveal: (index: number) => void;
}) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const size = cell * 3 + gap * 2;
  const brush = Math.max(16, cell * 0.3);
  const SAMPLES = 7;
  const state = useRef({
    last: null as { x: number; y: number } | null,
    pointer: null as number | null,
    cleared: Array.from({ length: SCRATCH_CELLS }, () => new Set<number>()),
    done: [...revealed],
  });
  const latestReveal = useRef(onReveal);
  latestReveal.current = onReveal;

  const origin = (i: number) => ({ x: (i % 3) * (cell + gap), y: Math.floor(i / 3) * (cell + gap) });
  const context = () => canvas.current?.getContext('2d') ?? null;

  // Pinta la lámina de las casillas que faltan por desvelar.
  useLayoutEffect(() => {
    const element = canvas.current;
    const ctx = context();
    if (!element || !ctx) return;
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    element.width = Math.round(size * dpr);
    element.height = Math.round(size * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, size, size);
    for (let i = 0; i < SCRATCH_CELLS; i++) {
      if (state.current.done[i]) continue;
      const { x, y } = origin(i);
      ctx.save();
      ctx.beginPath();
      if (ctx.roundRect) ctx.roundRect(x, y, cell, cell, 10);
      else ctx.rect(x, y, cell, cell);
      ctx.clip();
      const metal = ctx.createLinearGradient(x, y, x + cell, y + cell);
      metal.addColorStop(0, '#f6e7b8');
      metal.addColorStop(0.35, '#c9a35a');
      metal.addColorStop(0.55, '#f3dfa2');
      metal.addColorStop(1, '#9c7a3c');
      ctx.fillStyle = metal;
      ctx.fillRect(x, y, cell, cell);
      // Picas en relieve y un brillo en diagonal.
      ctx.fillStyle = 'rgb(110 80 28 / 0.22)';
      ctx.font = `${Math.round(cell * 0.16)}px serif`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      for (let r = 0; r < 4; r++) for (let c = 0; c < 4; c++) ctx.fillText('♠', x + (c + (r % 2 ? 0.75 : 0.25)) * (cell / 4), y + (r + 0.5) * (cell / 4));
      const sheen = ctx.createLinearGradient(x, y + cell, x + cell, y);
      sheen.addColorStop(0.35, 'rgb(255 255 255 / 0)');
      sheen.addColorStop(0.5, 'rgb(255 255 255 / 0.45)');
      sheen.addColorStop(0.65, 'rgb(255 255 255 / 0)');
      ctx.fillStyle = sheen;
      ctx.fillRect(x, y, cell, cell);
      ctx.fillStyle = 'rgb(84 58 16 / 0.7)';
      ctx.font = `700 ${Math.round(cell * 0.14)}px Cinzel, serif`;
      ctx.fillText('RASCA', x + cell / 2, y + cell / 2);
      ctx.restore();
    }
    // Solo al montar o cambiar de tamaño: lo rascado vive en el propio canvas.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cell, gap]);

  /** Borra lo que queda de una casilla (con un fundido corto) y la da por desvelada. */
  const finishCell = (i: number) => {
    if (state.current.done[i]) return;
    state.current.done[i] = true;
    latestReveal.current(i);
    play('card');
    const ctx = context();
    if (!ctx) return;
    const { x, y } = origin(i);
    if (reduced) {
      ctx.clearRect(x - 1, y - 1, cell + 2, cell + 2);
      return;
    }
    let frame = 0;
    const step = () => {
      ctx.save();
      ctx.globalCompositeOperation = 'destination-out';
      ctx.fillStyle = 'rgb(0 0 0 / 0.3)';
      ctx.fillRect(x - 1, y - 1, cell + 2, cell + 2);
      ctx.restore();
      if (++frame < 8) requestAnimationFrame(step);
      else ctx.clearRect(x - 1, y - 1, cell + 2, cell + 2);
    };
    requestAnimationFrame(step);
  };

  // «Rascar todo»: se desvelan las que faltan, una tras otra.
  useEffect(() => {
    if (!revealAll || !enabled) return;
    const pending = state.current.done.flatMap((d, i) => (d ? [] : [i]));
    const timers = pending.map((i, k) => setTimeout(() => finishCell(i), reduced ? 0 : k * 70));
    return () => timers.forEach(clearTimeout);
    // Solo cuando se pulsa el botón.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [revealAll]);

  const scratch = (from: { x: number; y: number }, to: { x: number; y: number }) => {
    const ctx = context();
    if (!ctx) return;
    ctx.save();
    ctx.globalCompositeOperation = 'destination-out';
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.lineWidth = brush;
    ctx.beginPath();
    ctx.moveTo(from.x, from.y);
    ctx.lineTo(to.x, to.y);
    ctx.stroke();
    ctx.beginPath();
    ctx.arc(to.x, to.y, brush / 2, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
    // Puntos de muestreo tocados por el trazo (distancia al segmento).
    const dx = to.x - from.x;
    const dy = to.y - from.y;
    const length = dx * dx + dy * dy;
    const radius = brush / 2;
    for (let i = 0; i < SCRATCH_CELLS; i++) {
      if (state.current.done[i]) continue;
      const o = origin(i);
      const cleared = state.current.cleared[i]!;
      for (let s = 0; s < SAMPLES * SAMPLES; s++) {
        if (cleared.has(s)) continue;
        const px = o.x + ((s % SAMPLES) + 0.5) * (cell / SAMPLES);
        const py = o.y + (Math.floor(s / SAMPLES) + 0.5) * (cell / SAMPLES);
        const t = length ? Math.max(0, Math.min(1, ((px - from.x) * dx + (py - from.y) * dy) / length)) : 0;
        const qx = from.x + t * dx - px;
        const qy = from.y + t * dy - py;
        if (qx * qx + qy * qy <= radius * radius) cleared.add(s);
      }
      if (cleared.size >= SAMPLES * SAMPLES * 0.55) finishCell(i);
    }
  };

  // Coordenadas locales del canvas: ya tienen en cuenta el giro de la mesa (móvil en vertical).
  const point = (event: PointerEvent<HTMLCanvasElement>) => ({ x: event.nativeEvent.offsetX, y: event.nativeEvent.offsetY });
  const onPointerDown = (event: PointerEvent<HTMLCanvasElement>) => {
    if (!enabled || (event.pointerType === 'mouse' && event.button !== 0)) return;
    event.currentTarget.setPointerCapture(event.pointerId);
    const p = point(event);
    state.current.pointer = event.pointerId;
    state.current.last = p;
    scratch(p, p);
  };
  const onPointerMove = (event: PointerEvent<HTMLCanvasElement>) => {
    if (!enabled || state.current.pointer !== event.pointerId || !state.current.last) return;
    const p = point(event);
    scratch(state.current.last, p);
    state.current.last = p;
  };
  const end = (event: PointerEvent<HTMLCanvasElement>) => {
    if (state.current.pointer !== event.pointerId) return;
    state.current.pointer = null;
    state.current.last = null;
  };

  return (
    <canvas
      ref={canvas}
      className={`absolute inset-0 touch-none ${enabled ? 'cursor-pointer' : 'pointer-events-none'}`}
      style={{ width: size, height: size }}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={end}
      onPointerCancel={end}
      aria-hidden
    />
  );
}

function StepButton({ children, label, disabled, onClick }: { children: string; label: string; disabled: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      className="grid size-10 place-items-center rounded-full bg-ink-3 text-xl font-bold text-gold-light ring-1 ring-gold/60 transition-transform duration-150 ease-out active:scale-[0.95] disabled:opacity-30 disabled:active:scale-100"
    >
      {children}
    </button>
  );
}
