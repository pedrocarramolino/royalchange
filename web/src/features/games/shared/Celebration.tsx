import { useLayoutEffect, useMemo, useRef } from 'react';
import { AnimatePresence, m as motion } from 'motion/react';
import { useSettings } from '@/data/settings';
import { Chip } from '@/ui/Chip';
import { SPRING } from '@/ui/motion';

/** Lluvia de fichas para los premios grandes (se omite con animaciones reducidas). */
export function Celebration({ trigger }: { trigger: string | number | null }) {
  const reduced = useSettings((s) => s.reducedMotion);
  if (reduced) return null;
  return <AnimatePresence>{trigger !== null && <ChipRain key={trigger} />}</AnimatePresence>;
}

function ChipRain() {
  const container = useRef<HTMLDivElement>(null);
  const pieces = useMemo(
    () =>
      Array.from({ length: 26 }, (_, i) => ({
        x: (i * 37) % 100,
        delay: (i % 9) * 60,
        drift: ((i * 53) % 40) - 20,
        spin: ((i * 71) % 720) - 360,
        duration: 1800 + (i % 5) * 150,
        value: [10, 50, 100, 500, 1000, 5000][i % 6]!,
        size: 22 + ((i * 13) % 18),
      })),
    [],
  );

  // Movimiento ya decidido de antemano: con WAAPI va fuera del hilo principal y no da tirones aunque
  // la mesa esté ocupada mostrando el premio. Caen con aceleración (ease-in): es la gravedad, no una
  // pieza de interfaz que entra. La distancia es el alto de la mesa, que puede ir girada.
  useLayoutEffect(() => {
    const element = container.current;
    if (!element) return;
    const fall = element.offsetHeight + 80;
    const animations = [...element.children].map((child, i) => {
      const p = pieces[i]!;
      return (child as HTMLElement).animate(
        [{ transform: 'translate(0px, -60px) rotate(0deg)' }, { transform: `translate(${p.drift}px, ${fall}px) rotate(${p.spin}deg)` }],
        { duration: p.duration, delay: p.delay, easing: 'ease-in', fill: 'both' },
      );
    });
    return () => animations.forEach((a) => a.cancel());
  }, [pieces]);

  return (
    <motion.div ref={container} className="pointer-events-none absolute inset-0 z-40 overflow-hidden" initial={{ opacity: 1 }} exit={{ opacity: 0 }} aria-hidden>
      {pieces.map((p, i) => (
        <div key={i} className="absolute top-0" style={{ left: `${p.x}%` }}>
          <Chip value={p.value} size={p.size} />
        </div>
      ))}
    </motion.div>
  );
}

/** Rótulo del resultado de la ronda sobre la mesa. */
export function ResultBanner({ net, label, big }: { net: number | null; label: string; big?: boolean }) {
  const tone = net === null || net === 0 ? 'text-ivory' : net > 0 ? 'text-gold-gradient' : 'text-[#ff9aa8]';
  return (
    <motion.div
      // Aparece desde casi su tamaño (nunca de la nada); con más rebote solo en los premios grandes.
      initial={{ scale: 0.9, opacity: 0 }}
      animate={{ scale: 1, opacity: 1 }}
      transition={big ? { type: 'spring', duration: 0.5, bounce: 0.3 } : SPRING}
      className="rounded-2xl bg-black/75 px-5 py-2 text-center ring-1 ring-gold/40"
      role="status"
    >
      <p className={`font-display font-bold ${big ? 'text-2xl' : 'text-lg'} ${tone}`}>{label}</p>
    </motion.div>
  );
}
