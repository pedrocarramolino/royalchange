import { useEffect } from 'react';
import { AnimatePresence, m as motion } from 'motion/react';
import { useWallet } from '@/data/wallet';
import { achievement } from '@/domain/progression';
import { chips } from '@/lib/format';
import { Chip } from '@/ui/Chip';
import { IconTrophy } from '@/ui/icons';

/**
 * Avisos de subida de nivel y de logros, de uno en uno arriba de la pantalla. Mientras una mesa
 * anima su resultado se retienen: no deben adelantarlo.
 */
export function ProgressToasts() {
  const event = useWallet((s) => (s.eventsHeld ? undefined : s.events[0]));
  const consume = useWallet((s) => s.consumeEvent);

  useEffect(() => {
    if (!event) return;
    const timer = setTimeout(consume, 3600);
    return () => clearTimeout(timer);
  }, [event, consume]);

  const key = event ? (event.type === 'levelUp' ? `level-${event.level}` : `ach-${event.id}`) : null;
  return (
    <div className="safe-pt-3 pointer-events-none fixed inset-x-0 top-0 z-40 flex justify-center px-4" aria-live="polite">
      <AnimatePresence>
        {event && key && (
          <motion.button
            key={key}
            type="button"
            onClick={consume}
            className="pointer-events-auto flex w-full max-w-sm items-center gap-3 rounded-2xl px-4 py-3 text-left panel ring-1 ring-gold/40"
            initial={{ y: -80, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            exit={{ y: -80, opacity: 0 }}
            transition={{ type: 'spring', damping: 22, stiffness: 300 }}
          >
            {event.type === 'levelUp' ? (
              <>
                <span className="grid size-11 shrink-0 place-items-center rounded-full metal-gold font-display text-lg font-bold">{event.level}</span>
                <span>
                  <span className="block font-display font-semibold text-gold-light">¡Subes al nivel {event.level}!</span>
                  <span className="block text-sm text-ivory-dim">Sigue jugando para desbloquear más logros.</span>
                </span>
              </>
            ) : (
              <>
                <span className="relative grid size-11 shrink-0 place-items-center">
                  <Chip value={1000} size={44} label="" />
                  <IconTrophy className="absolute size-5 text-on-gold" />
                </span>
                <span>
                  <span className="block font-display font-semibold text-gold-light">Logro: {achievement(event.id).name}</span>
                  <span className="block text-sm text-ivory-dim">Recoge {chips(achievement(event.id).reward)} en Progreso.</span>
                </span>
              </>
            )}
          </motion.button>
        )}
      </AnimatePresence>
    </div>
  );
}
