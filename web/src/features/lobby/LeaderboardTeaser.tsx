import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { m as motion } from 'motion/react';
import { useAuth } from '@/data/auth';
import { useReadyWallet } from '@/data/wallet';
import { useSettings } from '@/data/settings';
import { rankFor, syncLeaderboard } from '@/data/leaderboard';
import { grouped, signed } from '@/lib/format';
import { IconTrophy } from '@/ui/icons';
import { EASE_OUT } from '@/ui/motion';

/**
 * Acceso a la clasificación desde el casino, con el puesto del jugador. Al pasar por aquí se pone
 * al día su fila (como mucho cada 30 segundos).
 */
export function LeaderboardTeaser() {
  const auth = useAuth((s) => s.state);
  const wallet = useReadyWallet();
  const enabled = useSettings((s) => s.leaderboardEnabled);
  const navigate = useNavigate();
  const [me, setMe] = useState<{ score: number; rank: number } | null>(null);
  const uid = auth.status === 'signedIn' ? auth.user.uid : null;
  const profile = auth.status === 'signedIn' ? auth.user.profile : null;
  const balance = wallet?.balance;

  useEffect(() => {
    if (!uid || !profile || !enabled) return;
    let alive = true;
    void (async () => {
      const score = await syncLeaderboard(uid, profile);
      if (score === null || !alive) return;
      const rank = await rankFor(score).catch(() => null);
      if (alive && rank !== null) setMe({ score, rank });
    })();
    return () => {
      alive = false;
    };
  }, [uid, profile, enabled, balance]);

  return (
    <motion.button
      type="button"
      onClick={() => navigate('/clasificacion')}
      className="panel flex w-full items-center gap-3 rounded-2xl px-4 py-3 text-left transition-colors duration-150 ease-out active:bg-white/[0.04]"
      initial={{ opacity: 0, transform: 'translateY(-6px)' }}
      animate={{ opacity: 1, transform: 'translateY(0px)' }}
      transition={{ duration: 0.25, ease: EASE_OUT }}
      whileTap={{ scale: 0.98 }}
    >
      <span className="grid size-11 shrink-0 place-items-center rounded-full metal-gold text-on-gold">
        <IconTrophy className="size-5" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block font-semibold text-ivory">Clasificación semanal</span>
        <span className="block text-[13px] text-ivory-dim">
          {!enabled
            ? 'Mira quién va ganando esta semana.'
            : me
              ? (
                <>
                  Vas el <span className="font-bold text-gold-light">{grouped(me.rank)}.º</span> · {signed(me.score)} fichas
                </>
              )
              : '¿Quién gana más fichas esta semana?'}
        </span>
      </span>
      <svg viewBox="0 0 24 24" className="size-4 shrink-0 text-mute" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
        <path d="M9 6l6 6-6 6" />
      </svg>
    </motion.button>
  );
}
