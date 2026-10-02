import { memo, useEffect, useRef } from 'react';
import { animate, motion, useMotionValue, useTransform } from 'motion/react';
import { colorOf, WHEEL_ORDER } from './wheel';

const POCKET_ANGLE = 360 / 37;
const FILL = { green: '#0f7a4f', red: '#b3263b', black: '#15171d' } as const;

/** Rueda en SVG (dibujada una vez): madera, aro dorado, casillas numeradas y torreta. */
const WheelFace = memo(function WheelFace() {
  const outer = 46;
  const inner = 33;
  const point = (r: number, deg: number) => {
    const a = ((deg - 90) * Math.PI) / 180;
    return `${50 + r * Math.cos(a)} ${50 + r * Math.sin(a)}`;
  };
  return (
    <svg viewBox="0 0 100 100" className="absolute inset-0 h-full w-full" aria-hidden>
      <defs>
        <radialGradient id="rc-wood" cx="50%" cy="45%" r="55%">
          <stop offset="0.75" stopColor="#5a3418" />
          <stop offset="0.92" stopColor="#3a2010" />
          <stop offset="1" stopColor="#1e1008" />
        </radialGradient>
        <radialGradient id="rc-cone" cx="45%" cy="40%" r="60%">
          <stop offset="0" stopColor="#7a4a22" />
          <stop offset="1" stopColor="#3b220f" />
        </radialGradient>
        <linearGradient id="rc-turret" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#fbefc8" />
          <stop offset="0.5" stopColor="#d4af6a" />
          <stop offset="1" stopColor="#8a6a30" />
        </linearGradient>
      </defs>
      <circle cx="50" cy="50" r="49.5" fill="url(#rc-wood)" />
      <circle cx="50" cy="50" r={outer + 1.2} fill="none" stroke="#d4af6a" strokeWidth="0.9" />
      {WHEEL_ORDER.map((n, i) => {
        const a0 = i * POCKET_ANGLE - POCKET_ANGLE / 2;
        const a1 = a0 + POCKET_ANGLE;
        return (
          <g key={n}>
            <path d={`M${point(outer, a0)} A${outer} ${outer} 0 0 1 ${point(outer, a1)} L${point(inner, a1)} A${inner} ${inner} 0 0 0 ${point(inner, a0)} Z`} fill={FILL[colorOf(n)]} stroke="#d4af6a" strokeWidth="0.25" />
            <text
              x="50"
              y={50 - outer + 4.6}
              transform={`rotate(${i * POCKET_ANGLE} 50 50)`}
              textAnchor="middle"
              fontFamily="Manrope, sans-serif"
              fontWeight="800"
              fontSize="3.6"
              fill="#f7f1e3"
            >
              {n}
            </text>
          </g>
        );
      })}
      <circle cx="50" cy="50" r={inner} fill="url(#rc-cone)" stroke="#d4af6a" strokeWidth="0.6" />
      {Array.from({ length: 8 }, (_, i) => (
        <path key={i} d={`M50 50 L${point(inner - 2, i * 45 - 4)} A${inner - 2} ${inner - 2} 0 0 1 ${point(inner - 2, i * 45 + 4)} Z`} fill="rgb(0 0 0 / 0.12)" />
      ))}
      <g fill="url(#rc-turret)" stroke="#6b5020" strokeWidth="0.3">
        <rect x="48.6" y="27" width="2.8" height="46" rx="1.4" />
        <rect x="27" y="48.6" width="46" height="2.8" rx="1.4" />
        <circle cx="50" cy="50" r="6" />
        {[27, 73].map((c) => (
          <circle key={`h${c}`} cx={c} cy="50" r="2.2" />
        ))}
        {[27, 73].map((c) => (
          <circle key={`v${c}`} cx="50" cy={c} r="2.2" />
        ))}
      </g>
      <circle cx="50" cy="50" r="2.4" fill="#fbefc8" />
    </svg>
  );
});

export interface WheelSpin {
  id: number;
  number: number;
}

/**
 * Rueda que gira y bola que corre en sentido contrario, se frena y cae en la casilla de [spin].
 * Llama a [onSettled] cuando la bola se para.
 */
export function RouletteWheel({ size, spin, reducedMotion, onSettled }: { size: number; spin: WheelSpin | null; reducedMotion: boolean; onSettled: (id: number) => void }) {
  const wheel = useMotionValue(0);
  const ballAngle = useMotionValue(0);
  const ballRadius = useMotionValue(0.4);
  const lastSpin = useRef<number | null>(null);
  const settled = useRef(onSettled);
  settled.current = onSettled;

  useEffect(() => {
    if (!spin || lastSpin.current === spin.id) return;
    lastSpin.current = spin.id;
    const index = WHEEL_ORDER.indexOf(spin.number);
    const duration = reducedMotion ? 0.9 : 5.2;
    const wheelTarget = wheel.get() + (reducedMotion ? 120 : 360 * 2.5) + Math.random() * 90;
    // La bola acaba sobre su casilla: ángulo de la casilla en la rueda ya girada.
    const pocketScreen = wheelTarget + index * POCKET_ANGLE;
    const start = ballAngle.get();
    let ballTarget = pocketScreen;
    while (ballTarget > start - (reducedMotion ? 200 : 360 * 5)) ballTarget -= 360;
    const easing: [number, number, number, number] = [0.12, 0.6, 0.25, 1];
    const controls = [
      animate(wheel, wheelTarget, { duration, ease: easing }),
      animate(ballAngle, ballTarget, { duration, ease: easing }),
      animate(ballRadius, [0.47, 0.47, 0.44, 0.36], { duration, times: [0, 0.55, 0.8, 1], ease: 'easeIn' }),
    ];
    const timer = setTimeout(() => settled.current(spin.id), duration * 1000 + 120);
    return () => {
      clearTimeout(timer);
      controls.forEach((c) => c.stop());
    };
  }, [spin, reducedMotion, wheel, ballAngle, ballRadius]);

  const ballX = useTransform(() => 50 + ballRadius.get() * 100 * Math.sin((ballAngle.get() * Math.PI) / 180));
  const ballY = useTransform(() => 50 - ballRadius.get() * 100 * Math.cos((ballAngle.get() * Math.PI) / 180));
  const half = size * 0.0225;
  const ballLeft = useTransform(ballX, (v) => `calc(${v}% - ${half}px)`);
  const ballTop = useTransform(ballY, (v) => `calc(${v}% - ${half}px)`);

  return (
    <div className="relative" style={{ width: size, height: size }} role="img" aria-label="Rueda de la ruleta">
      <div className="absolute inset-0 rounded-full shadow-[0_18px_40px_-10px_rgb(0_0_0/0.85)]" />
      <motion.div className="absolute inset-0" style={{ rotate: wheel }}>
        <WheelFace />
      </motion.div>
      {/* Bola de marfil. */}
      <motion.div
        className="absolute rounded-full bg-[radial-gradient(circle_at_35%_30%,#ffffff,#e8e1d1_55%,#a9a08c)] shadow-[0_1px_3px_rgb(0_0_0/0.7)]"
        style={{
          width: size * 0.045,
          height: size * 0.045,
          left: ballLeft,
          top: ballTop,
        }}
      />
    </div>
  );
}
