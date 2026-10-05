import { useLayoutEffect, useRef, useState } from 'react';
import { motion } from 'motion/react';
import { useWallet, useReadyWallet } from '@/data/wallet';
import { useSettings } from '@/data/settings';
import { dropPlinko, PLINKO_BETS, PLINKO_RISK_NAME, PLINKO_ROWS, PLINKO_TENTHS, plinkoMultiplierText, type PlinkoDrop, type PlinkoRisk } from '@/engine/plinko';
import { chips, grouped } from '@/lib/format';
import { play, resultSound, BIG_WIN_MULTIPLIER } from '@/audio/sound';
import { Button } from '@/ui/Button';
import { EASE_OUT } from '@/ui/motion';
import { Celebration } from '../shared/Celebration';
import { GameShell, TableNotice, useTableSize } from '../shared/GameShell';
import { economyNotice, useHoldProgressEvents } from '../shared/session';

/** Bolas cayendo a la vez, como mucho. */
const MAX_BALLS = 6;
const RISKS: PlinkoRisk[] = ['low', 'medium', 'high'];

/** Color de cada casilla según lo que paga: rojo los extremos, dorado el centro. */
function slotColor(tenths: number): { bg: string; text: string } {
  if (tenths >= 100) return { bg: '#b3263b', text: '#fff' };
  if (tenths >= 30) return { bg: '#d4602e', text: '#fff' };
  if (tenths >= 14) return { bg: '#d4af6a', text: '#231905' };
  if (tenths >= 10) return { bg: '#9c7a3c', text: '#fff7e0' };
  return { bg: '#3a3830', text: '#d8d0bd' };
}

interface Ball {
  id: number;
  drop: PlinkoDrop;
}

export default function PlinkoScreen() {
  const wallet = useReadyWallet();
  const playInstantRound = useWallet((s) => s.playInstantRound);
  const reducedMotion = useSettings((s) => s.reducedMotion);
  const table = useTableSize();
  const [betIndex, setBetIndex] = useState(3);
  const [risk, setRisk] = useState<PlinkoRisk>('medium');
  const [balls, setBalls] = useState<Ball[]>([]);
  const [recent, setRecent] = useState<PlinkoDrop[]>([]);
  const [hits, setHits] = useState<Record<number, number>>({});
  const [notice, setNotice] = useState<string | null>(null);
  const [celebrate, setCelebrate] = useState<number | null>(null);
  const pending = useRef(0);
  useHoldProgressEvents(balls.length > 0);

  const bet = PLINKO_BETS[betIndex]!;
  const balance = wallet?.balance ?? 0;
  const tenths = PLINKO_TENTHS[risk];

  const drop = async () => {
    if (balls.length + pending.current >= MAX_BALLS) return;
    setNotice(null);
    if (bet > balance) return setNotice('No tienes fichas suficientes para esa apuesta.');
    const result = dropPlinko(bet, risk);
    pending.current++;
    // El resultado se contabiliza antes de soltar la bola.
    const booked = await playInstantRound('Plinko', result.stake, result.payout);
    pending.current--;
    if (!booked.ok) return setNotice(economyNotice(booked.error));
    setBalls((b) => [...b, { id: Date.now() + Math.random(), drop: result }]);
    play('chip');
  };

  const landed = (ball: Ball) => {
    setBalls((b) => b.filter((x) => x.id !== ball.id));
    setRecent((r) => [ball.drop, ...r].slice(0, 12));
    setHits((h) => ({ ...h, [ball.drop.slot]: (h[ball.drop.slot] ?? 0) + 1 }));
    const net = ball.drop.payout - ball.drop.stake;
    const big = ball.drop.payout >= BIG_WIN_MULTIPLIER * ball.drop.stake;
    const sound = resultSound(net, big) ?? 'reelStop';
    play(sound);
    if (big) setCelebrate(Date.now());
  };

  // Medidas: el tablero manda; a la derecha, las últimas bolas.
  const sideWidth = table.width >= 560 ? 120 : 84;
  const gap = Math.max(12, Math.min((table.height - 12) / (PLINKO_ROWS + 2.3), (table.width - sideWidth - 48) / 14.2));
  const boardWidth = gap * 14.2;
  const boardHeight = gap * (PLINKO_ROWS + 2.3);
  const last = recent[0];

  return (
    <GameShell
      title="Plinko"
      surface="dark"
      notice={notice && <TableNotice onDismiss={() => setNotice(null)}>{notice}</TableNotice>}
      controls={
        <div className="flex h-[68px] items-center gap-2 px-3">
          <div className="flex rounded-xl bg-ink-3 p-1 ring-1 ring-gold/40" role="radiogroup" aria-label="Riesgo">
            {RISKS.map((r) => (
              <button
                key={r}
                type="button"
                role="radio"
                aria-checked={risk === r}
                disabled={balls.length > 0}
                onClick={() => setRisk(r)}
                className={`rounded-lg px-2.5 py-1.5 text-xs font-bold transition-[background-color,transform] duration-150 ease-out active:scale-[0.96] disabled:opacity-50 ${risk === r ? 'bg-gold text-on-gold' : 'text-gold-light'}`}
              >
                {PLINKO_RISK_NAME[r]}
              </button>
            ))}
          </div>
          <div className="flex items-center gap-1.5">
            <StepButton label="Bajar apuesta" disabled={betIndex === 0} onClick={() => setBetIndex((i) => i - 1)}>
              −
            </StepButton>
            <div className="w-[78px] text-center">
              <p className="tabular text-[14px] font-bold whitespace-nowrap text-gold-light">{chips(bet)}</p>
              <p className="text-[10px] whitespace-nowrap text-mute">por bola</p>
            </div>
            <StepButton label="Subir apuesta" disabled={betIndex === PLINKO_BETS.length - 1} onClick={() => setBetIndex((i) => i + 1)}>
              +
            </StepButton>
          </div>
          <div className="min-w-0 flex-1" />
          <Button className="min-w-24" disabled={bet > balance || balls.length >= MAX_BALLS} onClick={() => void drop()}>
            {bet > balance ? 'Sin saldo' : 'Soltar'}
          </Button>
        </div>
      }
    >
      <div className="flex h-full items-center justify-center gap-4 px-3 py-1.5">
        <div className="relative shrink-0" style={{ width: boardWidth, height: boardHeight }} role="img" aria-label={`Tablero de plinko, riesgo ${PLINKO_RISK_NAME[risk].toLowerCase()}`}>
          {/* Clavos: la fila r tiene r + 3. */}
          <svg className="absolute inset-0" width={boardWidth} height={boardHeight} aria-hidden>
            {Array.from({ length: PLINKO_ROWS }, (_, r) =>
              Array.from({ length: r + 3 }, (_, i) => (
                <circle key={`${r}-${i}`} cx={boardWidth / 2 + (i - (r + 2) / 2) * gap} cy={gap * (r + 0.9)} r={Math.max(1.6, gap * 0.12)} fill="#e9d29a" opacity={0.9} />
              )),
            )}
          </svg>
          {/* Casillas con su multiplicador. */}
          {tenths.map((t, k) => {
            const { bg, text } = slotColor(t);
            return (
              <motion.div
                key={`${risk}-${k}-${hits[k] ?? 0}`}
                className="absolute grid place-items-center rounded-md font-bold shadow-[0_3px_0_rgb(0_0_0/0.45)]"
                style={{
                  left: boardWidth / 2 + (k - PLINKO_ROWS / 2) * gap - gap * 0.45,
                  top: gap * (PLINKO_ROWS + 0.85),
                  width: gap * 0.9,
                  height: gap * 0.95,
                  background: bg,
                  color: text,
                  fontSize: Math.max(8, gap * 0.36),
                }}
                initial={hits[k] ? { transform: 'translateY(6px)' } : false}
                animate={{ transform: 'translateY(0px)' }}
                transition={{ duration: 0.25, ease: EASE_OUT }}
              >
                {plinkoMultiplierText(t).slice(1)}
              </motion.div>
            );
          })}
          {balls.map((ball) => (
            <BallView key={ball.id} ball={ball} gap={gap} boardWidth={boardWidth} reduced={reducedMotion} onLanded={landed} />
          ))}
        </div>

        <aside className="flex shrink-0 flex-col items-stretch gap-1.5 self-stretch py-2" style={{ width: sideWidth }} aria-label="Últimas bolas">
          <p className="felt-print text-center text-[10px] font-bold">Últimas</p>
          {last && (
            <p className={`text-center font-display text-[15px] font-bold ${last.payout > last.stake ? 'text-gold-gradient' : 'text-ivory-dim'}`} aria-live="polite">
              {last.payout > last.stake ? `+${grouped(last.payout - last.stake)}` : `${plinkoMultiplierText(last.tenths)}`}
            </p>
          )}
          <ol className="flex min-h-0 flex-col gap-1 overflow-hidden">
            {recent.slice(0, 8).map((d, i) => {
              const { bg, text } = slotColor(d.tenths);
              return (
                <li key={`${recent.length - i}`} className="tabular rounded-md px-2 py-0.5 text-center text-[11px] font-bold" style={{ background: bg, color: text, opacity: i === 0 ? 1 : 0.8 }}>
                  {plinkoMultiplierText(d.tenths)}
                </li>
              );
            })}
          </ol>
        </aside>
      </div>
      <Celebration trigger={celebrate} />
    </GameShell>
  );
}

/** Una bola: baja de clavo en clavo hasta su casilla (WAAPI, con caída acelerada en cada tramo). */
function BallView({ ball, gap, boardWidth, reduced, onLanded }: { ball: Ball; gap: number; boardWidth: number; reduced: boolean; onLanded: (ball: Ball) => void }) {
  const ref = useRef<HTMLDivElement>(null);
  const done = useRef(onLanded);
  done.current = onLanded;
  const size = gap * 0.55;

  useLayoutEffect(() => {
    const element = ref.current;
    if (!element) return;
    const cx = boardWidth / 2;
    const points: [number, number][] = [[cx, size / 2]];
    let rights = 0;
    ball.drop.path.forEach((right, r) => {
      points.push([cx + (rights - r / 2) * gap, gap * (r + 0.9) - gap * 0.42]);
      if (right) rights++;
    });
    points.push([cx + (rights - PLINKO_ROWS / 2) * gap, gap * (PLINKO_ROWS + 1.1)]);
    const keyframes = points.map(([x, y]) => ({ transform: `translate(${x - size / 2}px, ${y - size / 2}px)`, easing: 'cubic-bezier(0.5, 0, 0.75, 1)' }));
    const animation = element.animate(keyframes, { duration: reduced ? 500 : 130 * points.length, fill: 'forwards' });
    animation.finished.then(() => done.current(ball), () => undefined);
    return () => animation.cancel();
    // Solo al soltar la bola.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div
      ref={ref}
      className="pointer-events-none absolute top-0 left-0 rounded-full bg-[radial-gradient(circle_at_35%_30%,#ffffff,#f3dfa2_45%,#b8862f)] shadow-[0_2px_4px_rgb(0_0_0/0.6)]"
      style={{ width: size, height: size }}
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
