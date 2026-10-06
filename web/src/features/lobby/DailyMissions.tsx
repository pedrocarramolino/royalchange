import { useState } from 'react';
import { AnimatePresence, m as motion } from 'motion/react';
import { useWallet, useReadyWallet } from '@/data/wallet';
import { missionsToday, missionText } from '@/domain/missions';
import { chips, grouped, remaining } from '@/lib/format';
import { localEpochDay, msUntilLocalMidnight } from '@/lib/time';
import { play } from '@/audio/sound';
import { Button } from '@/ui/Button';
import { Chip } from '@/ui/Chip';
import { EASE_OUT, SPRING } from '@/ui/motion';

/**
 * Misiones de hoy: tres retos con su progreso y su recompensa. Cuando una se completa, aparece el
 * botón para recogerla. A medianoche (hora del dispositivo) llegan tres nuevas.
 */
export function DailyMissions({ now }: { now: number }) {
  const wallet = useReadyWallet();
  const claimMission = useWallet((s) => s.claimMission);
  const [claiming, setClaiming] = useState<number | null>(null);
  const [failed, setFailed] = useState(false);
  if (!wallet) return null;

  const today = localEpochDay(new Date(now));
  const { missions, progress, claimed } = missionsToday(wallet, today);
  const done = claimed.filter(Boolean).length;

  const claim = async (index: number) => {
    setClaiming(index);
    setFailed(false);
    const result = await claimMission(index);
    setClaiming(null);
    if (result.ok) play('win');
    else if (result.error.type !== 'missionAlreadyClaimed') setFailed(true);
  };

  return (
    <motion.section
      className="panel rounded-3xl px-4 pt-4 pb-3"
      aria-label="Misiones de hoy"
      initial={{ opacity: 0, transform: 'translateY(-6px)' }}
      animate={{ opacity: 1, transform: 'translateY(0px)' }}
      transition={{ duration: 0.25, ease: EASE_OUT }}
    >
      <div className="flex items-baseline justify-between gap-2">
        <h2 className="font-display text-lg font-semibold text-gold-light">Misiones de hoy</h2>
        <p className="tabular shrink-0 text-xs text-mute">
          {done === missions.length ? '¡Todas hechas!' : `Nuevas en ${remaining(msUntilLocalMidnight(new Date(now)))}`}
        </p>
      </div>
      {failed && <p className="mt-2 rounded-xl bg-ruby/15 px-3 py-2 text-sm text-[#ffd9dd]">No se pudo recoger la misión. Vuelve a intentarlo.</p>}
      <ul className="mt-2 flex flex-col">
        {missions.map((m, i) => {
          const value = Math.min(progress[i] ?? 0, m.target);
          const complete = value >= m.target;
          const isClaimed = claimed[i] ?? false;
          return (
            <li key={i} className={`flex items-center gap-3 py-2.5 ${i > 0 ? 'border-t border-white/5' : ''}`}>
              <div className="min-w-0 flex-1">
                <p className={`text-[15px] leading-snug font-semibold ${isClaimed ? 'text-mute line-through decoration-gold/50' : 'text-ivory'}`}>{missionText(m)}</p>
                <div className="mt-1.5 flex items-center gap-2">
                  <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-ink-4" aria-hidden>
                    {/* La barra avanza con cada ronda (escala: la anima el navegador). */}
                    <motion.div
                      className={`h-full w-full origin-left rounded-full ${complete ? 'bg-emerald' : 'metal-gold'}`}
                      initial={{ transform: 'scaleX(0)' }}
                      animate={{ transform: `scaleX(${value / m.target})` }}
                      transition={{ duration: 0.6, ease: EASE_OUT, delay: 0.1 + i * 0.06 }}
                    />
                  </div>
                  <span className="tabular shrink-0 text-[11px] text-mute">
                    {grouped(value)} / {grouped(m.target)}
                  </span>
                </div>
              </div>
              <AnimatePresence mode="wait" initial={false}>
                {isClaimed ? (
                  <motion.span
                    key="recogida"
                    className="grid size-9 shrink-0 place-items-center rounded-full bg-emerald/20 text-lg font-bold text-emerald"
                    aria-label="Recogida"
                    initial={{ opacity: 0, transform: 'scale(0.5) rotate(-30deg)' }}
                    animate={{ opacity: 1, transform: 'scale(1) rotate(0deg)' }}
                    transition={SPRING}
                  >
                    ✓
                  </motion.span>
                ) : complete ? (
                  <motion.div
                    key="recoger"
                    className="shrink-0"
                    initial={{ opacity: 0, transform: 'scale(0.85)' }}
                    animate={{ opacity: 1, transform: 'scale(1)' }}
                    exit={{ opacity: 0, transform: 'scale(0.9)', transition: { duration: 0.12 } }}
                    transition={SPRING}
                  >
                    <Button size="sm" loading={claiming === i} onClick={() => void claim(i)}>
                      Recoger {grouped(m.reward)}
                    </Button>
                  </motion.div>
                ) : (
                  <motion.span key="premio" className="flex shrink-0 items-center gap-1.5" aria-label={`Recompensa: ${chips(m.reward)}`} exit={{ opacity: 0, transition: { duration: 0.12 } }}>
                    <Chip value={m.reward >= 1000 ? 1000 : 500} size={22} label="" />
                    <span className="tabular text-sm font-bold text-gold-light">{grouped(m.reward)}</span>
                  </motion.span>
                )}
              </AnimatePresence>
            </li>
          );
        })}
      </ul>
    </motion.section>
  );
}
