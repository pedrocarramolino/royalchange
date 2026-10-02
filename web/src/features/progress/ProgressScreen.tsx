import { useState } from 'react';
import { motion } from 'motion/react';
import { useWallet, useReadyWallet } from '@/data/wallet';
import { ACHIEVEMENTS, levelProgress, TITLE_NAMES } from '@/domain/progression';
import type { AchievementId } from '@/domain/economy';
import { chips, grouped } from '@/lib/format';
import { Button } from '@/ui/Button';
import { Chip } from '@/ui/Chip';
import { IconTrophy } from '@/ui/icons';

export function ProgressScreen() {
  const wallet = useReadyWallet();
  const status = useWallet((s) => s.state.status);
  const claimAchievement = useWallet((s) => s.claimAchievement);
  const [claiming, setClaiming] = useState<AchievementId | null>(null);
  const [failed, setFailed] = useState(false);

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
  const stats: [string, string][] = [
    ['Rondas', grouped(wallet.rounds)],
    ['Victorias', grouped(wallet.wins)],
    ['Derrotas', grouped(wallet.losses)],
    ['Empates', grouped(wallet.pushes)],
    ['Mejor racha', grouped(wallet.bestWinStreak)],
    ['Días seguidos', grouped(wallet.dailyStreak)],
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

      <section className="mt-5 flex items-center gap-4 rounded-3xl px-5 py-5 felt ring-1 ring-gold/30" aria-label="Nivel">
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
              transition={{ duration: 0.9, ease: 'easeOut' }}
            />
          </svg>
          <span className="font-display text-3xl font-bold text-gold-light">{level.level}</span>
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
      </section>

      <h2 className="mt-8 mb-3 px-1 text-xs font-bold tracking-[0.2em] text-gold uppercase">Estadísticas</h2>
      <dl className="grid grid-cols-3 gap-2">
        {stats.map(([label, value]) => (
          <div key={label} className="panel rounded-2xl px-3 py-3">
            <dt className="text-[11px] font-semibold tracking-wide text-mute uppercase">{label}</dt>
            <dd className="tabular mt-1 text-lg font-bold text-ivory">{value}</dd>
          </div>
        ))}
        <div className="panel col-span-3 flex items-center justify-between rounded-2xl px-4 py-3">
          <dt className="text-[11px] font-semibold tracking-wide text-mute uppercase">Saldo máximo</dt>
          <dd className="tabular font-bold text-gold-light">{chips(wallet.highestBalance)}</dd>
        </div>
      </dl>

      <h2 className="mt-8 mb-3 px-1 text-xs font-bold tracking-[0.2em] text-gold uppercase">
        Logros · {unlockedCount} de {ACHIEVEMENTS.length}
      </h2>
      {failed && <p className="mb-3 rounded-2xl bg-ruby/15 px-4 py-3 text-sm text-[#ffd9dd]">No se pudo recoger la recompensa. Vuelve a intentarlo.</p>}
      <ul className="flex flex-col gap-2">
        {ACHIEVEMENTS.map((a) => {
          const unlocked = wallet.unlocked.includes(a.id);
          const claimed = wallet.claimed.includes(a.id);
          const [current, target] = a.progress(wallet);
          const fraction = Math.min(1, current / target);
          return (
            <li key={a.id} className={`panel flex items-center gap-3 rounded-2xl px-4 py-3 ${unlocked ? '' : 'opacity-80'}`}>
              <span className={`grid size-11 shrink-0 place-items-center rounded-full ${unlocked ? 'metal-gold' : 'bg-ink-4 text-mute'}`}>
                <IconTrophy className="size-5" />
              </span>
              <div className="min-w-0 flex-1">
                <p className={`font-semibold ${unlocked ? 'text-ivory' : 'text-ivory-dim'}`}>{a.name}</p>
                <p className="text-[13px] leading-snug text-mute">{a.description}</p>
                {!unlocked && (
                  <div className="mt-2 flex items-center gap-2">
                    <div className="h-1 flex-1 overflow-hidden rounded-full bg-ink-4">
                      <div className="h-full rounded-full bg-gold/70" style={{ width: `${fraction * 100}%` }} />
                    </div>
                    <span className="tabular text-[11px] text-mute">
                      {grouped(Math.min(current, target))} de {grouped(target)}
                    </span>
                  </div>
                )}
                {claimed && <p className="mt-1 text-xs font-semibold text-emerald">Recogido: {chips(a.reward)}</p>}
                {!unlocked && <p className="mt-1 text-xs text-mute">Recompensa: {chips(a.reward)}</p>}
              </div>
              {unlocked && !claimed && (
                <Button size="sm" onClick={() => void claim(a.id)} loading={claiming === a.id} icon={<Chip value={1000} size={18} label="" />}>
                  Recoger {chips(a.reward)}
                </Button>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
