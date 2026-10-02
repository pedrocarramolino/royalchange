import { memo, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { useSettings } from '@/data/settings';
import { useReadyWallet, useWallet } from '@/data/wallet';
import { DAILY_WHEEL, dailyBonusStatus } from '@/domain/progression';
import { secureRandom } from '@/engine/cards';
import { play } from '@/audio/sound';
import { chips, grouped, remaining } from '@/lib/format';
import { localEpochDay, msUntilLocalMidnight } from '@/lib/time';
import { Button } from '@/ui/Button';
import { EASE_OUT, EASE_SPIN_CSS } from '@/ui/motion';
import { Celebration } from '@/features/games/shared/Celebration';

const SEGMENT = 360 / DAILY_WHEEL.length;
const TOP_PRIZE = Math.max(...DAILY_WHEEL);

/** Punto a [r] del centro con ángulo [deg] en el sentido de las agujas del reloj desde arriba. */
function point(r: number, deg: number): string {
  const a = (deg * Math.PI) / 180;
  return `${(100 + r * Math.sin(a)).toFixed(2)} ${(100 - r * Math.cos(a)).toFixed(2)}`;
}

const label = (value: number) => (value >= 1_000 ? `${String(value / 1_000).replace('.', ',')}K` : String(value));

/** Color de cada casilla: oro el premio gordo, paño el segundo y el resto alterna obsidiana y rubí. */
function fill(value: number, index: number): string {
  if (value === TOP_PRIZE) return 'url(#dw-gold)';
  if (value === 5_000) return '#0f5b45';
  return index % 2 === 0 ? '#17181e' : '#8f1d30';
}

/** La rueda (sin la flecha): madera, aro dorado con bombillas, casillas y buje con el sello. */
const WheelFace = memo(function WheelFace({ spinning, winner }: { spinning: boolean; winner: number | null }) {
  return (
    <svg viewBox="0 0 200 200" className="block size-full" aria-hidden>
      <defs>
        <linearGradient id="dw-gold" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#fbefc8" />
          <stop offset="0.5" stopColor="#d4af6a" />
          <stop offset="1" stopColor="#9c7a3c" />
        </linearGradient>
        <radialGradient id="dw-wood" cx="50%" cy="45%" r="55%">
          <stop offset="0.8" stopColor="#3a2010" />
          <stop offset="1" stopColor="#1e1008" />
        </radialGradient>
      </defs>
      <circle cx="100" cy="100" r="99" fill="url(#dw-wood)" />
      <circle cx="100" cy="100" r="93.5" fill="none" stroke="url(#dw-gold)" strokeWidth="5" />
      {DAILY_WHEEL.map((value, i) => {
        const from = i * SEGMENT;
        const to = from + SEGMENT;
        return (
          <path
            key={i}
            d={`M100 100 L${point(88, from)} A88 88 0 0 1 ${point(88, to)} Z`}
            fill={fill(value, i)}
            stroke="rgb(212 175 106 / 0.55)"
            strokeWidth="0.8"
          />
        );
      })}
      {winner !== null && (
        <path
          d={`M100 100 L${point(88, winner * SEGMENT)} A88 88 0 0 1 ${point(88, (winner + 1) * SEGMENT)} Z`}
          fill="rgb(243 223 162 / 0.38)"
          stroke="#f3dfa2"
          strokeWidth="2"
          style={{ animation: 'bulb 0.9s steps(1) infinite' }}
        />
      )}
      {DAILY_WHEEL.map((value, i) => (
        <text
          key={i}
          x="100"
          y="36"
          transform={`rotate(${i * SEGMENT + SEGMENT / 2} 100 100)`}
          textAnchor="middle"
          dominantBaseline="central"
          fontFamily="Cinzel, Georgia, serif"
          fontWeight="700"
          fontSize={label(value).length > 3 ? 11.5 : 13}
          fill={value === TOP_PRIZE ? '#231905' : '#f7f1e3'}
        >
          {label(value)}
        </text>
      ))}
      {/* Bombillas: dos grupos que se encienden por turnos. */}
      {Array.from({ length: 24 }, (_, i) => (
        <circle
          key={i}
          cx={100 + 93.5 * Math.sin((i * 15 * Math.PI) / 180)}
          cy={100 - 93.5 * Math.cos((i * 15 * Math.PI) / 180)}
          r="2.4"
          fill="#fff3c4"
          style={{
            animation: `bulb ${spinning ? 0.26 : 1.2}s steps(1) infinite`,
            animationDelay: i % 2 ? `-${spinning ? 0.13 : 0.6}s` : '0s',
          }}
        />
      ))}
      <circle cx="100" cy="100" r="19" fill="url(#dw-gold)" />
      <circle cx="100" cy="100" r="14" fill="#15171d" stroke="rgb(243 223 162 / 0.6)" strokeWidth="1" />
      <text x="100" y="100" textAnchor="middle" dominantBaseline="central" fontFamily="Cinzel, Georgia, serif" fontWeight="700" fontSize="10" fill="#f3dfa2">
        RC
      </text>
    </svg>
  );
});

/** Flecha fija arriba: marca la casilla ganadora. */
function Pointer() {
  return (
    <svg viewBox="0 0 200 200" className="pointer-events-none absolute inset-0 size-full drop-shadow-[0_3px_3px_rgb(0_0_0/0.6)]" aria-hidden>
      <path d="M90 0 L110 0 L100 22 Z" fill="url(#dw-gold)" stroke="#231905" strokeWidth="1.2" strokeLinejoin="round" />
      <circle cx="100" cy="4" r="3" fill="#231905" />
    </svg>
  );
}

type Phase = { type: 'idle' } | { type: 'spinning'; index: number } | { type: 'won'; index: number };

/**
 * Ruleta diaria del lobby: un giro al día con 12 casillas iguales de 250 a 10.000 fichas. El premio
 * se cobra antes de girar (como las rondas de las mesas) y la rueda se frena en esa casilla; mientras
 * gira, [onHoldBalance] congela el saldo visible para no adelantar el resultado.
 */
export function DailyWheel({ now, onHoldBalance }: { now: number; onHoldBalance: (balance: number | null) => void }) {
  const wallet = useReadyWallet()!;
  const claimDailyBonus = useWallet((s) => s.claimDailyBonus);
  const reducedMotion = useSettings((s) => s.reducedMotion);
  const [phase, setPhase] = useState<Phase>({ type: 'idle' });
  const [failed, setFailed] = useState(false);
  const layer = useRef<HTMLDivElement>(null);
  // En reposo, el premio gordo (la última casilla) queda centrado bajo la flecha.
  const angle = useRef(SEGMENT / 2);
  const status = dailyBonusStatus(wallet, localEpochDay(new Date(now)), Date.now());

  const spin = async () => {
    if (phase.type !== 'idle' || status.type !== 'available') return;
    const index = secureRandom(DAILY_WHEEL.length);
    const prize = DAILY_WHEEL[index]!;
    setFailed(false);
    onHoldBalance(wallet.balance);
    // Se pasa a «girando» antes de cobrar: al cobrar, el monedero ya cuenta la tirada de hoy y, sin
    // esto, la tarjeta pasaría un instante a la fila compacta y la rueda se desmontaría.
    setPhase({ type: 'spinning', index });
    // El premio se cobra antes de girar: si se cierra la app a mitad de giro, ya está cobrado.
    const result = await claimDailyBonus(prize);
    if (!result.ok) {
      onHoldBalance(null);
      setPhase({ type: 'idle' });
      setFailed(result.error.type !== 'dailyBonusAlreadyClaimed');
      return;
    }
    play('spin');
    // La casilla ganadora acaba bajo la flecha, algo desplazada del centro para que no parezca medida.
    const offset = (Math.random() - 0.5) * SEGMENT * 0.6;
    let target = -(index * SEGMENT + SEGMENT / 2) + offset;
    while (target <= angle.current + (reducedMotion ? 360 : 360 * 5)) target += 360;
    const element = layer.current;
    const finish = () => {
      setPhase({ type: 'won', index });
      onHoldBalance(null);
      play(prize >= 5_000 ? 'bigWin' : 'win');
    };
    if (!element) return finish();
    const animation = element.animate([{ transform: `rotate(${angle.current}deg)` }, { transform: `rotate(${target}deg)` }], {
      duration: reducedMotion ? 600 : 4_600,
      easing: EASE_SPIN_CSS,
      fill: 'forwards',
    });
    angle.current = target;
    await animation.finished.catch(() => undefined);
    element.style.transform = `rotate(${target}deg)`;
    animation.cancel();
    finish();
  };

  const streak = wallet.dailyStreak > 0 && (
    <span className="shrink-0 text-xs font-semibold text-gold">{wallet.dailyStreak === 1 ? 'Racha de 1 día' : `Racha de ${wallet.dailyStreak} días`}</span>
  );

  // Ya girada hoy (y sin un giro en pantalla): una fila compacta con la cuenta atrás.
  if (phase.type === 'idle' && status.type !== 'available') {
    return (
      <section className="panel flex items-center gap-3 rounded-2xl px-4 py-3" aria-label="Ruleta diaria">
        <div className="size-11 shrink-0 opacity-60 saturate-50">
          <WheelFace spinning={false} winner={null} />
        </div>
        <div className="min-w-0 flex-1">
          <p className="font-semibold text-ivory">Ruleta diaria</p>
          <p className="text-[13px] leading-snug text-ivory-dim">
            {status.type === 'clockMovedBack'
              ? 'La fecha de tu dispositivo parece incorrecta. Corrígela para girar.'
              : `Vuelve mañana para girar otra vez (en ${remaining(msUntilLocalMidnight(new Date(now)))}).`}
          </p>
        </div>
        {streak}
      </section>
    );
  }

  const won = phase.type === 'won' ? DAILY_WHEEL[phase.index]! : null;
  return (
    <section className="panel relative overflow-hidden rounded-3xl px-4 pt-4 pb-4" aria-label="Ruleta diaria">
      <div className="flex items-center gap-2">
        <h3 className="flex-1 font-display text-lg font-semibold text-gold-gradient">Ruleta diaria</h3>
        {streak}
      </div>
      <p className="mt-0.5 text-sm text-ivory-dim">Un giro gratis cada día: de 250 a {grouped(TOP_PRIZE)} fichas.</p>

      <div className="relative mx-auto mt-4 aspect-square w-full max-w-[236px]">
        <div ref={layer} className="size-full" style={{ transform: `rotate(${angle.current}deg)` }}>
          <WheelFace spinning={phase.type === 'spinning'} winner={phase.type === 'won' ? phase.index : null} />
        </div>
        <Pointer />
      </div>

      <div className="mt-4 min-h-12" aria-live="polite">
        <AnimatePresence mode="wait" initial={false}>
          {won !== null ? (
            <motion.div
              key="won"
              className="flex items-center gap-3"
              initial={{ opacity: 0, transform: 'translateY(6px)' }}
              animate={{ opacity: 1, transform: 'translateY(0px)' }}
              transition={{ duration: 0.25, ease: EASE_OUT }}
            >
              <p className="min-w-0 flex-1 font-display text-xl font-bold text-gold-gradient">
                {won === TOP_PRIZE ? '¡Premio gordo! ' : '¡Has ganado '}
                {chips(won)}
                {won === TOP_PRIZE ? '' : '!'}
              </p>
              <Button variant="secondary" onClick={() => setPhase({ type: 'idle' })}>
                Genial
              </Button>
            </motion.div>
          ) : (
            <motion.div key="spin" exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
              <Button block size="lg" loading={phase.type === 'spinning'} onClick={() => void spin()}>
                Girar la ruleta
              </Button>
              {failed && <p className="mt-2 text-center text-sm text-[#ff9aa8]">No se pudo girar la ruleta. Vuelve a intentarlo.</p>}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
      <Celebration trigger={won !== null && won >= 5_000 ? `daily-${won}` : null} />
    </section>
  );
}
