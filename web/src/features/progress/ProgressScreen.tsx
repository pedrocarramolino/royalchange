import { useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { useWallet, useReadyWallet } from '@/data/wallet';
import { ACHIEVEMENTS, levelProgress, TITLE_NAMES } from '@/domain/progression';
import type { AchievementId } from '@/domain/economy';
import { chips, grouped } from '@/lib/format';
import { Button } from '@/ui/Button';
import { Chip } from '@/ui/Chip';
import { useCountUp } from '@/ui/ChipBalance';
import { EASE_OUT, SPRING } from '@/ui/motion';
import { IconTrophy } from '@/ui/icons';

export function ProgressScreen() {
  const wallet = useReadyWallet();
  const status = useWallet((s) => s.state.status);
  const claimAchievement = useWallet((s) => s.claimAchievement);
  const [claiming, setClaiming] = useState<AchievementId | null>(null);
  const [failed, setFailed] = useState(false);
  // Logros ya recogidos al abrir: su trofeo no salta (solo los que se recogen ahora).
  const claimedAtOpen = useRef<Set<AchievementId> | null>(null);
  if (wallet && claimedAtOpen.current === null) claimedAtOpen.current = new Set(wallet.claimed);

  if (!wallet) {
    return (
      <div className="safe-pt-6 mx-auto max-w-lg px-5">
        <h1 className="font-display text-2xl font-semibold text-gold-gradient">Progreso</h1>
        {status === 'loading' ? (
          <div className="mt-6 h-40 animate-pulse rounded-3xl bg-ink-2" />
        ) : (
          <p className="mt-4 text-ivory-dim">No hemos podido cargar tu progreso. Vuelve a intentarlo en unos minutos.</p>
        )}
      </div>
    );
  }

  const level = levelProgress(wallet.xp);
  const unlockedCount = wallet.unlocked.length;
  const stats: [string, number][] = [
    ['Rondas', wallet.rounds],
    ['Victorias', wallet.wins],
    ['Derrotas', wallet.losses],
    ['Empates', wallet.pushes],
    ['Mejor racha', wallet.bestWinStreak],
    ['Días seguidos', wallet.dailyStreak],
  ];

  const claim = async (id: AchievementId) => {
    setClaiming(id);
    setFailed(false);
    const result = await claimAchievement(id);
    setClaiming(null);
    if (!result.ok && result.error.type !== 'achievementAlreadyClaimed') setFailed(true);
  };

  return (
    <div className="safe-top safe-px-4 mx-auto max-w-lg pb-8">
      <h1 className="px-1 pt-6 font-display text-2xl font-semibold text-gold-gradient">Progreso</h1>

      <motion.section
        className="mt-5 flex items-center gap-4 rounded-3xl px-5 py-5 felt ring-1 ring-gold/30"
        aria-label="Nivel"
        initial={{ opacity: 0, transform: 'translateY(8px)' }}
        animate={{ opacity: 1, transform: 'translateY(0px)' }}
        transition={{ duration: 0.3, ease: EASE_OUT }}
      >
        <span className="relative grid size-20 shrink-0 place-items-center">
          <svg viewBox="0 0 100 100" className="absolute inset-0 -rotate-90" aria-hidden>
            <circle cx="50" cy="50" r="44" fill="none" stroke="rgb(0 0 0 / 0.35)" strokeWidth="8" />
            <motion.circle
              cx="50"
              cy="50"
              r="44"
              fill="none"
              stroke="#e2c27f"
              strokeWidth="8"
              strokeLinecap="round"
              strokeDasharray={2 * Math.PI * 44}
              initial={{ strokeDashoffset: 2 * Math.PI * 44 }}
              animate={{ strokeDashoffset: 2 * Math.PI * 44 * (1 - level.fraction) }}
              transition={{ duration: 1, ease: EASE_OUT, delay: 0.1 }}
            />
          </svg>
          <motion.span
            key={level.level}
            className="font-display text-3xl font-bold text-gold-light"
            initial={{ opacity: 0, transform: 'scale(0.6)' }}
            animate={{ opacity: 1, transform: 'scale(1)' }}
            transition={{ ...SPRING, delay: 0.15 }}
          >
            {level.level}
          </motion.span>
        </span>
        <div className="min-w-0">
          <p className="font-display text-xl font-semibold text-ivory">{TITLE_NAMES[level.title]}</p>
          <p className="tabular mt-0.5 text-sm text-gold-light/90">
            {level.xpForNextLevel === null ? 'Has alcanzado el nivel máximo.' : `${grouped(level.xpIntoLevel)} / ${grouped(level.xpForNextLevel)} XP`}
          </p>
          {level.xpForNextLevel !== null && (
            <p className="mt-1 text-[13px] leading-snug text-ivory-dim">
              Te faltan {grouped(level.xpForNextLevel - level.xpIntoLevel)} XP para el nivel {level.level + 1}. Cada ronda suma experiencia.
            </p>
          )}
        </div>
      </motion.section>

      <h2 className="mt-8 mb-3 px-1 text-xs font-bold tracking-[0.2em] text-gold uppercase">Estadísticas</h2>
      <dl className="grid grid-cols-3 gap-2">
        {stats.map(([label, value], i) => (
          <motion.div
            key={label}
            className="panel rounded-2xl px-3 py-3"
            initial={{ opacity: 0, transform: 'translateY(8px)' }}
            animate={{ opacity: 1, transform: 'translateY(0px)' }}
            transition={{ duration: 0.3, ease: EASE_OUT, delay: 0.08 + i * 0.04 }}
          >
            <dt className="text-[11px] font-semibold tracking-wide text-mute uppercase">{label}</dt>
            <dd className="tabular mt-1 text-lg font-bold text-ivory" aria-label={grouped(value)}>
              <CountUp value={value} />
            </dd>
          </motion.div>
        ))}
        <motion.div
          className="panel col-span-3 flex items-center justify-between rounded-2xl px-4 py-3"
          initial={{ opacity: 0, transform: 'translateY(8px)' }}
          animate={{ opacity: 1, transform: 'translateY(0px)' }}
          transition={{ duration: 0.3, ease: EASE_OUT, delay: 0.32 }}
        >
          <dt className="text-[11px] font-semibold tracking-wide text-mute uppercase">Saldo máximo</dt>
          <dd className="tabular font-bold text-gold-light" aria-label={chips(wallet.highestBalance)}>
            <CountUp value={wallet.highestBalance} /> fichas
          </dd>
        </motion.div>
      </dl>

      <h2 className="mt-8 mb-3 px-1 text-xs font-bold tracking-[0.2em] text-gold uppercase">
        Logros · {unlockedCount} de {ACHIEVEMENTS.length}
      </h2>
      {failed && <p className="mb-3 rounded-2xl bg-ruby/15 px-4 py-3 text-sm text-[#ffd9dd]">No se pudo recoger la recompensa. Vuelve a intentarlo.</p>}
      <ul className="flex flex-col gap-2">
        {ACHIEVEMENTS.map((a, i) => {
          const unlocked = wallet.unlocked.includes(a.id);
          const claimed = wallet.claimed.includes(a.id);
          const [current, target] = a.progress(wallet);
          const fraction = Math.min(1, current / target);
          return (
            <motion.li
              key={a.id}
              className={`panel flex items-center gap-3 rounded-2xl px-4 py-3 ${unlocked ? '' : 'opacity-80'}`}
              initial={{ opacity: 0, transform: 'translateY(8px)' }}
              animate={{ opacity: unlocked ? 1 : 0.8, transform: 'translateY(0px)' }}
              transition={{ duration: 0.3, ease: EASE_OUT, delay: 0.2 + Math.min(i, 8) * 0.04 }}
            >
              <span className="relative shrink-0">
                {/* Pendiente de recoger: un aro dorado late alrededor del trofeo. */}
                {unlocked && !claimed && <span className="pointer-events-none absolute -inset-1 animate-pulse rounded-full ring-2 ring-gold-light/70" aria-hidden />}
                <motion.span
                  // Al recogerlo, el trofeo da un salto con giro.
                  key={claimed ? 'recogido' : unlocked ? 'desbloqueado' : 'bloqueado'}
                  className={`grid size-11 place-items-center rounded-full ${unlocked ? 'metal-gold' : 'bg-ink-4 text-mute'}`}
                  initial={claimed && !claimedAtOpen.current?.has(a.id) ? { transform: 'scale(0.7) rotate(-25deg)' } : false}
                  animate={{ transform: 'scale(1) rotate(0deg)' }}
                  transition={SPRING}
                >
                  <IconTrophy className="size-5" />
                </motion.span>
              </span>
              <div className="min-w-0 flex-1">
                <p className={`font-semibold ${unlocked ? 'text-ivory' : 'text-ivory-dim'}`}>{a.name}</p>
                <p className="text-[13px] leading-snug text-mute">{a.description}</p>
                {!unlocked && (
                  <div className="mt-2 flex items-center gap-2">
                    <div className="h-1 flex-1 overflow-hidden rounded-full bg-ink-4">
                      <motion.div
                        className="h-full w-full origin-left rounded-full bg-gold/70"
                        initial={{ transform: 'scaleX(0)' }}
                        animate={{ transform: `scaleX(${fraction})` }}
                        transition={{ duration: 0.7, ease: EASE_OUT, delay: 0.3 + Math.min(i, 8) * 0.04 }}
                      />
                    </div>
                    <span className="tabular text-[11px] text-mute">
                      {grouped(Math.min(current, target))} de {grouped(target)}
                    </span>
                  </div>
                )}
                <AnimatePresence initial={false}>
                  {claimed && (
                    <motion.p
                      className="mt-1 text-xs font-semibold text-emerald"
                      initial={{ opacity: 0, transform: 'translateY(4px)' }}
                      animate={{ opacity: 1, transform: 'translateY(0px)' }}
                      transition={{ duration: 0.25, ease: EASE_OUT }}
                    >
                      Recogido: {chips(a.reward)}
                    </motion.p>
                  )}
                </AnimatePresence>
                {!unlocked && <p className="mt-1 text-xs text-mute">Recompensa: {chips(a.reward)}</p>}
              </div>
              {unlocked && !claimed && (
                <Button size="sm" onClick={() => void claim(a.id)} loading={claiming === a.id} icon={<Chip value={1000} size={18} label="" />}>
                  Recoger {chips(a.reward)}
                </Button>
              )}
            </motion.li>
          );
        })}
      </ul>
    </div>
  );
}

/** Cifra que cuenta desde cero al abrir la pantalla (y sigue el valor si cambia). */
function CountUp({ value }: { value: number }) {
  return <>{grouped(useCountUp(value, 900, 0))}</>;
}
