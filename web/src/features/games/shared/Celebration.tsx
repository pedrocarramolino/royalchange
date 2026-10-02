import { useMemo } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { useSettings } from '@/data/settings';
import { Chip } from '@/ui/Chip';

/** Lluvia de fichas para los premios grandes (se omite con animaciones reducidas). */
export function Celebration({ trigger }: { trigger: string | number | null }) {
  const reduced = useSettings((s) => s.reducedMotion);
  const pieces = useMemo(
    () =>
      Array.from({ length: 26 }, (_, i) => ({
        x: (i * 37) % 100,
        delay: (i % 9) * 0.06,
        drift: ((i * 53) % 40) - 20,
        spin: ((i * 71) % 720) - 360,
        value: [10, 50, 100, 500, 1000, 5000][i % 6]!,
        size: 22 + ((i * 13) % 18),
      })),
    [],
  );
  if (reduced) return null;
  return (
    <AnimatePresence>
      {trigger !== null && (
        <motion.div key={trigger} className="pointer-events-none absolute inset-0 z-40 overflow-hidden" initial={{ opacity: 1 }} exit={{ opacity: 0 }} aria-hidden>
          {pieces.map((p, i) => (
            <motion.div
              key={i}
              className="absolute top-0"
              style={{ left: `${p.x}%` }}
              initial={{ y: -60, x: 0, rotate: 0 }}
              animate={{ y: '110vh', x: p.drift, rotate: p.spin }}
              transition={{ duration: 1.8 + (i % 5) * 0.15, delay: p.delay, ease: 'easeIn' }}
            >
              <Chip value={p.value} size={p.size} />
            </motion.div>
          ))}
        </motion.div>
      )}
    </AnimatePresence>
  );
}

/** Rótulo del resultado de la ronda sobre la mesa. */
export function ResultBanner({ net, label, big }: { net: number | null; label: string; big?: boolean }) {
  const tone = net === null || net === 0 ? 'text-ivory' : net > 0 ? 'text-gold-gradient' : 'text-[#ff9aa8]';
  return (
    <motion.div
      initial={{ scale: 0.7, opacity: 0 }}
      animate={{ scale: 1, opacity: 1 }}
      transition={{ type: 'spring', damping: 14, stiffness: 260 }}
      className="rounded-2xl bg-black/55 px-5 py-2 text-center ring-1 ring-gold/40 backdrop-blur-sm"
      role="status"
    >
      <p className={`font-display font-bold ${big ? 'text-2xl' : 'text-lg'} ${tone}`}>{label}</p>
    </motion.div>
  );
}
