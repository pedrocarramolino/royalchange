import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { m as motion } from 'motion/react';
import { useAuth } from '@/data/auth';
import { useReadyWallet } from '@/data/wallet';
import { useSettings } from '@/data/settings';
import { currentWeek, LEADERBOARD_SIZE, rankFor, syncLeaderboard, topPlayers, weekEndsAt, type LeaderboardRow } from '@/data/leaderboard';
import { grouped, remaining, signed } from '@/lib/format';
import { Avatar } from '@/ui/Avatar';
import { Button } from '@/ui/Button';
import { EASE_OUT, SPRING } from '@/ui/motion';

/** Medallas de los tres primeros: oro, plata y bronce. */
const MEDAL = ['bg-[linear-gradient(135deg,#fbefc8,#d4af6a_55%,#a8813f)] text-on-gold', 'bg-[linear-gradient(135deg,#f4f4f2,#b9bcc2_55%,#80838a)] text-obsidian', 'bg-[linear-gradient(135deg,#f3c9a0,#c27a43_55%,#8a4f25)] text-white'];

/** Tiempo hasta el final de la semana: «5 d 9 h» (o el formato corto si queda menos de un día). */
function untilWeekEnd(ms: number): string {
  const hours = Math.floor(ms / 3_600_000);
  return hours >= 24 ? `${Math.floor(hours / 24)} d ${hours % 24} h` : remaining(ms);
}

/** Puesto con su forma ordinal corta: 1.º, 2.º… */
const ordinal = (n: number) => `${grouped(n)}.º`;

/** Clasificación semanal: los mejores de la semana por ganancias y el puesto del jugador. */
export function LeaderboardScreen() {
  const auth = useAuth((s) => s.state);
  const wallet = useReadyWallet();
  const enabled = useSettings((s) => s.leaderboardEnabled);
  const navigate = useNavigate();
  const [rows, setRows] = useState<LeaderboardRow[] | null>(null);
  const [me, setMe] = useState<{ score: number; rank: number } | null>(null);
  const [failed, setFailed] = useState(false);
  const week = currentWeek();
  const uid = auth.status === 'signedIn' ? auth.user.uid : null;
  const profile = auth.status === 'signedIn' ? auth.user.profile : null;

  const load = useCallback(async () => {
    if (!uid || !profile) return;
    setFailed(false);
    try {
      const score = enabled ? await syncLeaderboard(uid, profile, { force: true }) : null;
      const top = await topPlayers(week);
      setRows(top);
      const mine = top.find((r) => r.uid === uid);
      const myScore = score ?? mine?.score ?? null;
      setMe(enabled && myScore !== null ? { score: myScore, rank: mine ? top.indexOf(mine) + 1 : await rankFor(myScore, week) } : null);
    } catch {
      setFailed(true);
    }
  }, [uid, profile, enabled, week]);

  // Al abrir y cada vez que cambia el saldo (una jugada en otro dispositivo, un premio…).
  const balance = wallet?.balance;
  useEffect(() => {
    void load();
  }, [load, balance]);

  const now = Date.now();
  return (
    <div className="safe-top safe-px-4 mx-auto max-w-lg pb-8">
      <h1 className="px-1 pt-6 font-display text-2xl font-semibold text-gold-gradient">Clasificación semanal</h1>
      <p className="mt-1 px-1 text-sm text-ivory-dim">
        Ganancias de esta semana · termina en {untilWeekEnd(Math.max(0, weekEndsAt(week) - now))}
      </p>

      {/* Tu puesto, siempre a la vista. */}
      <motion.section
        className="mt-5 flex items-center gap-4 rounded-3xl px-5 py-4 felt ring-1 ring-gold/30"
        aria-label="Tu puesto"
        initial={{ opacity: 0, transform: 'translateY(8px)' }}
        animate={{ opacity: 1, transform: 'translateY(0px)' }}
        transition={{ duration: 0.3, ease: EASE_OUT }}
      >
        {profile && <Avatar id={profile.avatar} size={52} />}
        <div className="min-w-0 flex-1">
          <p className="truncate font-display text-lg font-semibold text-ivory">{profile?.alias}</p>
          {!enabled ? (
            <p className="text-sm text-ivory-dim">No apareces en la clasificación. Puedes activarlo en Ajustes.</p>
          ) : me ? (
            <p className="text-sm text-ivory-dim">
              Vas el <span className="font-bold text-gold-light">{ordinal(me.rank)}</span> esta semana
            </p>
          ) : (
            <p className="text-sm text-ivory-dim">Calculando tu puesto…</p>
          )}
        </div>
        {enabled && me && (
          <motion.p
            key={me.score}
            className={`tabular font-display text-xl font-bold ${me.score > 0 ? 'text-gold-gradient' : me.score < 0 ? 'text-ruby-bright' : 'text-ivory'}`}
            initial={{ opacity: 0, transform: 'scale(0.85)' }}
            animate={{ opacity: 1, transform: 'scale(1)' }}
            transition={SPRING}
          >
            {signed(me.score)}
          </motion.p>
        )}
      </motion.section>
      {!enabled && (
        <Button variant="secondary" size="sm" className="mt-3" onClick={() => navigate('/ajustes')}>
          Ir a Ajustes
        </Button>
      )}

      <h2 className="mt-8 mb-3 px-1 text-xs font-bold tracking-[0.2em] text-gold uppercase">Los {LEADERBOARD_SIZE} mejores</h2>
      {failed && (
        <div className="rounded-3xl bg-ink-2 px-5 py-6 text-center">
          <p className="text-ivory-dim">No se pudo cargar la clasificación. Comprueba tu conexión.</p>
          <Button variant="secondary" className="mt-4" onClick={() => void load()}>
            Reintentar
          </Button>
        </div>
      )}
      {!failed && rows === null && (
        <div className="flex flex-col gap-1.5" aria-label="Cargando la clasificación">
          {[0, 1, 2, 3, 4].map((i) => (
            <div key={i} className="h-14 animate-pulse rounded-2xl bg-ink-2" />
          ))}
        </div>
      )}
      {!failed && rows?.length === 0 && (
        <div className="rounded-3xl bg-ink-2 px-5 py-8 text-center">
          <p className="font-display text-lg font-semibold text-ivory">Semana recién empezada</p>
          <p className="mt-2 text-sm text-ivory-dim">Juega una partida y serás el primero de la lista.</p>
        </div>
      )}
      {rows && rows.length > 0 && (
        <ol className="flex flex-col gap-1.5">
          {rows.map((row, i) => {
            const mine = row.uid === uid;
            return (
              <motion.li
                key={row.uid}
                layout="position"
                className={`flex items-center gap-3 rounded-2xl px-3 py-2.5 ring-1 ${mine ? 'bg-gold/10 ring-gold/60' : 'bg-ink-1 ring-white/5'}`}
                initial={{ opacity: 0, transform: 'translateY(6px)' }}
                animate={{ opacity: 1, transform: 'translateY(0px)' }}
                transition={{ duration: 0.25, ease: EASE_OUT, delay: Math.min(i, 12) * 0.03 }}
                aria-label={`${ordinal(i + 1)}: ${row.alias}, ${signed(row.score)} fichas${mine ? ' (tú)' : ''}`}
              >
                <span
                  className={`tabular grid size-8 shrink-0 place-items-center rounded-full text-sm font-black ${i < 3 ? `${MEDAL[i]} shadow-[0_2px_6px_rgb(0_0_0/0.5)]` : 'text-mute'}`}
                  aria-hidden
                >
                  {i + 1}
                </span>
                <Avatar id={row.avatar} size={36} />
                <span className={`min-w-0 flex-1 truncate font-semibold ${mine ? 'text-gold-light' : 'text-ivory'}`}>
                  {row.alias}
                  {mine && <span className="ml-1.5 text-xs font-bold text-gold">(tú)</span>}
                </span>
                <span className={`tabular shrink-0 font-bold ${row.score > 0 ? 'text-emerald' : row.score < 0 ? 'text-ruby-bright' : 'text-ivory-dim'}`}>{signed(row.score)}</span>
              </motion.li>
            );
          })}
        </ol>
      )}
      <p className="mt-6 px-1 text-xs leading-relaxed text-mute">
        Cuentan las fichas ganadas desde que entras en la clasificación esa semana (premios y bonos incluidos). Cada lunes empieza una semana nueva.
      </p>
    </div>
  );
}
