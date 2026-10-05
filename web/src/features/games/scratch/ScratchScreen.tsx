import { useEffect, useLayoutEffect, useRef, useState, type PointerEvent, type ReactNode } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { useWallet, useReadyWallet } from '@/data/wallet';
import { useSettings } from '@/data/settings';
import { buyScratchTicket, SCRATCH_BETS, SCRATCH_CELLS, SCRATCH_PRIZES, type ScratchTicket } from '@/engine/scratch';
import { SYMBOL_NAME, type SlotSymbol } from '@/engine/slots';
import { chips, grouped } from '@/lib/format';
import { play, resultSound, BIG_WIN_MULTIPLIER } from '@/audio/sound';
import { Button } from '@/ui/Button';
import { useCountUp } from '@/ui/ChipBalance';
import { EASE_IN_OUT, EASE_OUT, SPRING } from '@/ui/motion';
import { Celebration } from '../shared/Celebration';
import { GameShell, TableNotice, useTableSize } from '../shared/GameShell';
import { economyNotice, useHoldProgressEvents } from '../shared/session';
import { SlotSvgDefs, SlotSymbolArt } from '../slots/SlotSymbolArt';

/** Margen del lienzo de virutas alrededor de las casillas (el relleno del boleto). */
const DUST_MARGIN = 12;

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

  // Medidas: el boleto manda. En el móvil (vertical) la tabla de premios va debajo, en dos filas;
  // en pantallas anchas, al lado.
  const stacked = table.width < table.height * 1.15;
  const sideWidth = table.width >= 560 ? 156 : 124;
  const pad = 12;
  const header = stacked ? 46 : Math.round(Math.max(30, Math.min(48, table.height * 0.15)));
  const prizesHeight = 84;
  const grid = stacked
    ? Math.max(120, Math.min(420, table.width - 24 - pad * 2, table.height - prizesHeight - 36 - header - pad * 2))
    : Math.max(120, Math.min(table.height - 20 - header - pad * 2, table.width - sideWidth - 48 - pad * 2));
  const gap = Math.round(grid * 0.035);
  const cell = (grid - gap * 2) / 3;
  const prize = finished && ticket ? ticket.data.prize : null;
  // Símbolos con dos casillas ya destapadas: falta uno para el trío (suspense).
  const teasing = new Set<SlotSymbol>();
  if (ticket && !finished) {
    const counts = new Map<SlotSymbol, number>();
    ticket.data.cells.forEach((s, i) => revealed[i] && counts.set(s, (counts.get(s) ?? 0) + 1));
    counts.forEach((n, s) => n >= 2 && teasing.add(s));
  }
  const net = ticket ? ticket.data.payout - ticket.data.stake : 0;

  return (
    <GameShell
      title="Rasca y gana"
      surface="dark"
      portrait
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
      <div className={`flex h-full items-center justify-center px-3 py-2 ${stacked ? 'flex-col gap-4' : 'gap-5'}`}>
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
                teasing={teasing}
                reduced={reducedMotion}
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
                aria-label={net > 0 ? `Ganas ${chips(net)}` : undefined}
                initial={{ opacity: 0, scale: 0.85 }}
                animate={
                  net > 0
                    ? { opacity: 1, scale: 1, boxShadow: ['0 0 0px rgb(243 223 162 / 0)', '0 0 26px rgb(243 223 162 / 0.65)', '0 0 12px rgb(243 223 162 / 0.3)'] }
                    : { opacity: 1, scale: 1 }
                }
                exit={{ opacity: 0, transition: { duration: 0.15 } }}
                transition={{ ...SPRING, delay: 0.35 }}
              >
                {net > 0 ? <CountUpWin amount={net} /> : net === 0 ? 'Recuperas tu boleto' : 'Sin premio'}
              </motion.p>
            )}
          </AnimatePresence>
        </div>

        <aside
          className={stacked ? 'grid shrink-0 grid-cols-4 gap-1.5' : 'flex shrink-0 flex-col gap-1 self-center'}
          style={{ width: stacked ? grid + pad * 2 : sideWidth }}
          aria-label="Premios por tres iguales"
        >
          <p className={`felt-print mb-0.5 text-center text-[10px] font-bold ${stacked ? 'col-span-4' : ''}`}>Tres iguales</p>
          {SCRATCH_PRIZES.map((p) => {
            const hit = prize?.symbol === p.symbol;
            const close = teasing.has(p.symbol);
            return (
              <motion.div
                key={p.symbol}
                className={`relative flex items-center gap-1.5 rounded-lg px-2 py-0.5 ring-1 transition-colors duration-200 ${hit ? 'bg-gold/25 ring-gold-light' : close ? 'bg-gold/15 ring-gold/60' : 'bg-white/[0.04] ring-white/10'}`}
                aria-label={`Tres ${symbolName(p.symbol).toLowerCase()}: ×${p.multiplier}`}
                initial={false}
                animate={{ scale: hit ? [1, 1.08, 1] : 1 }}
                transition={{ duration: 0.45, ease: EASE_OUT, delay: hit ? 0.3 : 0 }}
              >
                <span className="grid place-items-center rounded-md bg-[#fbf3df] p-0.5">
                  <SlotSymbolArt symbol={p.symbol} size={table.height < 260 ? 15 : 19} />
                </span>
                <span className="flex-1 text-[10px] font-semibold text-ivory-dim">{stacked ? '' : '× 3'}</span>
                <span className={`tabular text-[12px] font-bold ${hit ? 'text-gold-light' : 'text-ivory'}`}>×{p.multiplier}</span>
                {/* A uno del trío: late el aro de su premio. */}
                {close && <span className="pointer-events-none absolute inset-0 animate-pulse rounded-lg ring-2 ring-gold-light" aria-hidden />}
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
  teasing,
  reduced,
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
  teasing: Set<SlotSymbol>;
  reduced: boolean;
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
            const close = symbol !== undefined && revealed[i] && teasing.has(symbol);
            return (
              <motion.div
                key={i}
                role="listitem"
                aria-label={symbol && revealed[i] ? symbolName(symbol) : 'Sin rascar'}
                className={`relative grid place-items-center overflow-hidden rounded-[10px] bg-[radial-gradient(circle_at_50%_40%,#fffaf0,#efe3c4)] shadow-[inset_0_1px_3px_rgb(0_0_0/0.25)] ${winning ? 'ring-2 ring-gold-light' : ''}`}
                style={{ boxShadow: winning ? '0 0 16px rgb(243 223 162 / 0.75), inset 0 1px 3px rgb(0 0 0 / 0.25)' : undefined }}
                initial={false}
                animate={{ opacity: dim ? 0.4 : 1, scale: winning ? [1, 1.1, 1] : 1 }}
                transition={{ duration: 0.5, ease: EASE_OUT, delay: winning ? 0.1 + ticket!.winning.indexOf(i) * 0.1 : 0 }}
              >
                {symbol && (
                  // Al destaparse del todo, el símbolo da un pequeño salto.
                  <motion.div
                    key={revealed[i] ? 'visto' : 'tapado'}
                    initial={revealed[i] && !reduced ? { transform: 'scale(0.75) rotate(-8deg)' } : false}
                    animate={{ transform: 'scale(1) rotate(0deg)' }}
                    transition={SPRING}
                  >
                    <SlotSymbolArt symbol={symbol} size={cell * 0.72} />
                  </motion.div>
                )}
                {/* A uno del trío: las dos casillas iguales laten. */}
                {close && <span className="pointer-events-none absolute inset-0 animate-pulse rounded-[10px] ring-2 ring-gold ring-inset" aria-hidden />}
                {/* Trío ganador: un destello recorre cada casilla. */}
                {winning && !reduced && (
                  <motion.span
                    className="pointer-events-none absolute inset-y-0 w-1/2 bg-[linear-gradient(100deg,transparent,rgb(255_255_255/0.75),transparent)]"
                    initial={{ transform: 'translateX(-150%)' }}
                    animate={{ transform: 'translateX(250%)' }}
                    transition={{ duration: 0.7, ease: EASE_IN_OUT, delay: 0.25 + ticket!.winning.indexOf(i) * 0.12 }}
                    aria-hidden
                  />
                )}
              </motion.div>
            );
          })}
        </div>
        {children}
        {/* Trío ganador: una línea dorada se dibuja uniendo sus tres casillas. */}
        {finished && ticket && ticket.winning.length === 3 && (
          <svg className="pointer-events-none absolute inset-0 overflow-visible" width={cell * 3 + gap * 2} height={cell * 3 + gap * 2} aria-hidden>
            <motion.polyline
              points={shortestPath(ticket.winning)
                .map((i) => `${(i % 3) * (cell + gap) + cell / 2},${Math.floor(i / 3) * (cell + gap) + cell / 2}`)
                .join(' ')}
              fill="none"
              stroke="#f3dfa2"
              strokeWidth={Math.max(3, cell * 0.04)}
              strokeLinecap="round"
              strokeLinejoin="round"
              style={{ filter: 'drop-shadow(0 0 6px rgb(243 223 162 / 0.85))' }}
              initial={{ pathLength: reduced ? 1 : 0, opacity: 0.9 }}
              animate={{ pathLength: 1, opacity: 0.9 }}
              transition={{ duration: 0.6, ease: EASE_IN_OUT, delay: 0.2 }}
            />
          </svg>
        )}
        {/* Boleto recién comprado: un brillo recorre la lámina dorada. */}
        {ticket && !reduced && (
          <div className="pointer-events-none absolute inset-0 overflow-hidden rounded-[10px]" aria-hidden>
            <motion.div
              className="absolute inset-y-0 w-2/5 bg-[linear-gradient(105deg,transparent,rgb(255_255_255/0.55),transparent)]"
              initial={{ transform: 'translateX(-120%) skewX(-12deg)' }}
              animate={{ transform: 'translateX(320%) skewX(-12deg)' }}
              transition={{ duration: 0.9, ease: EASE_IN_OUT, delay: 0.35 }}
            />
          </div>
        )}
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
  const dust = useRef<HTMLCanvasElement>(null);
  const flakes = useRef<{ x: number; y: number; vx: number; vy: number; age: number; life: number; size: number; color: string }[]>([]);
  const frame = useRef<number | null>(null);
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
    const dustElement = dust.current;
    if (dustElement) {
      dustElement.width = Math.round((size + DUST_MARGIN * 2) * dpr);
      dustElement.height = Math.round((size + DUST_MARGIN * 2) * dpr);
      dustElement.getContext('2d')?.setTransform(dpr, 0, 0, dpr, DUST_MARGIN * dpr, DUST_MARGIN * dpr);
    }
    // Solo al montar o cambiar de tamaño: lo rascado vive en el propio canvas.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cell, gap]);

  useEffect(() => () => {
    if (frame.current !== null) cancelAnimationFrame(frame.current);
  }, []);

  const FLAKE_COLORS = ['#f3dfa2', '#d4af6a', '#fbefc8', '#a8813f'];

  /** Virutas doradas que saltan del trazo y caen con gravedad. */
  const spawnDust = (x: number, y: number) => {
    if (reduced) return;
    for (let k = 0; k < 3; k++) {
      flakes.current.push({
        x,
        y,
        vx: (Math.random() - 0.5) * 140,
        vy: -40 - Math.random() * 90,
        age: 0,
        life: 0.45 + Math.random() * 0.35,
        size: 1.2 + Math.random() * 2,
        color: FLAKE_COLORS[k % FLAKE_COLORS.length]!,
      });
    }
    runDust();
  };

  /** Al destaparse una casilla, lo que queda de lámina salta en pedazos desde su centro. */
  const burstCell = (i: number) => {
    if (reduced) return;
    const { x, y } = origin(i);
    const cx = x + cell / 2;
    const cy = y + cell / 2;
    for (let k = 0; k < 18; k++) {
      const px = x + Math.random() * cell;
      const py = y + Math.random() * cell;
      flakes.current.push({
        x: px,
        y: py,
        vx: (px - cx) * 3 + (Math.random() - 0.5) * 60,
        vy: (py - cy) * 2 - 70 - Math.random() * 80,
        age: 0,
        life: 0.5 + Math.random() * 0.4,
        size: 2 + Math.random() * 3.5,
        color: FLAKE_COLORS[k % FLAKE_COLORS.length]!,
      });
    }
    runDust();
  };

  const runDust = () => {
    if (flakes.current.length > 260) flakes.current.splice(0, flakes.current.length - 260);
    if (frame.current !== null) return;
    let last = performance.now();
    const tick = (now: number) => {
      const dt = Math.min(0.05, (now - last) / 1000);
      last = now;
      const ctx = dust.current?.getContext('2d');
      if (!ctx) {
        frame.current = null;
        return;
      }
      ctx.clearRect(-DUST_MARGIN, -DUST_MARGIN, size + DUST_MARGIN * 2, size + DUST_MARGIN * 2);
      flakes.current = flakes.current.filter((f) => (f.age += dt) < f.life);
      for (const f of flakes.current) {
        f.vy += 520 * dt;
        f.x += f.vx * dt;
        f.y += f.vy * dt;
        ctx.globalAlpha = 1 - f.age / f.life;
        ctx.fillStyle = f.color;
        ctx.fillRect(f.x, f.y, f.size, f.size);
      }
      ctx.globalAlpha = 1;
      frame.current = flakes.current.length ? requestAnimationFrame(tick) : null;
    };
    frame.current = requestAnimationFrame(tick);
  };

  /** Casilla bajo un punto de la lámina (o `null` si cae entre casillas). */
  const cellAt = (x: number, y: number) => {
    const col = Math.floor(x / (cell + gap));
    const row = Math.floor(y / (cell + gap));
    if (col < 0 || col > 2 || row < 0 || row > 2 || x - col * (cell + gap) > cell || y - row * (cell + gap) > cell) return null;
    return row * 3 + col;
  };

  /** Borra lo que queda de una casilla (con un fundido corto) y la da por desvelada. */
  const finishCell = (i: number) => {
    if (state.current.done[i]) return;
    state.current.done[i] = true;
    latestReveal.current(i);
    play('card');
    // Un toque corto en los móviles que vibran (Android).
    try {
      navigator.vibrate?.(8);
    } catch {
      // Sin vibración: no pasa nada.
    }
    const ctx = context();
    if (!ctx) return;
    const { x, y } = origin(i);
    if (reduced) {
      ctx.clearRect(x - 1, y - 1, cell + 2, cell + 2);
      return;
    }
    burstCell(i);
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

  // «Rascar todo»: se desvelan las que faltan en una ola diagonal, desde arriba a la izquierda.
  useEffect(() => {
    if (!revealAll || !enabled) return;
    const pending = state.current.done.flatMap((d, i) => (d ? [] : [i]));
    const wave = (i: number) => (i % 3) + Math.floor(i / 3);
    const timers = pending.map((i) => setTimeout(() => finishCell(i), reduced ? 0 : wave(i) * 110));
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
    const under = cellAt(to.x, to.y);
    if (under !== null && !state.current.done[under]) spawnDust(to.x, to.y);
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
    <>
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
      {/* Las virutas pueden salirse un poco de las casillas (hasta el borde del boleto). */}
      <canvas
        ref={dust}
        className="pointer-events-none absolute"
        style={{ left: -DUST_MARGIN, top: -DUST_MARGIN, width: size + DUST_MARGIN * 2, height: size + DUST_MARGIN * 2 }}
        aria-hidden
      />
    </>
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

/** Premio del boleto: cuenta desde cero al aparecer. */
function CountUpWin({ amount }: { amount: number }) {
  return <>+{grouped(useCountUp(amount, 700, 0))}</>;
}

/**
 * Orden de las tres casillas para unirlas con el trazo más corto: se deja fuera el lado más largo
 * del triángulo (la casilla del medio es la opuesta a ese lado).
 */
function shortestPath(cells: number[]): number[] {
  const [a, b, c] = cells as [number, number, number];
  const d = (p: number, q: number) => Math.hypot((p % 3) - (q % 3), Math.floor(p / 3) - Math.floor(q / 3));
  const ab = d(a, b);
  const bc = d(b, c);
  const ac = d(a, c);
  if (ab >= bc && ab >= ac) return [a, c, b];
  if (bc >= ab && bc >= ac) return [b, a, c];
  return [a, b, c];
}
