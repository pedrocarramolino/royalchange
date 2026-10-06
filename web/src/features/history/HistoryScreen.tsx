import { useCallback, useEffect, useState } from 'react';
import { m as motion } from 'motion/react';
import { useAuth } from '@/data/auth';
import { useReadyWallet } from '@/data/wallet';
import { HISTORY_PAGE_SIZE, ledgerBefore, loadStatistics } from '@/data/history';
import type { GameType } from '@/domain/economy';
import { GAMES } from '@/domain/economy';
import { historyItems, type GameStats, type HistoryItem } from '@/domain/history';
import { achievement } from '@/domain/progression';
import { chips, formatDateTime, grouped, signed } from '@/lib/format';
import { Button } from '@/ui/Button';
import { EASE_OUT } from '@/ui/motion';

const GAME_NAME: Record<GameType, string> = { Blackjack: 'Blackjack', Roulette: 'Ruleta', Slots: 'Slots', Poker: 'Póker', Dice: 'Dados', Baccarat: 'Baccarat', VideoPoker: 'Video póker', Plinko: 'Plinko', Scratch: 'Rasca y gana' };

export function HistoryScreen() {
  const auth = useAuth((s) => s.state);
  const uid = auth.status === 'signedIn' ? auth.user.uid : null;
  const wallet = useReadyWallet();
  const [items, setItems] = useState<HistoryItem[]>([]);
  const [nextBefore, setNextBefore] = useState<number | null>(null);
  const [stats, setStats] = useState<Partial<Record<GameType, GameStats>> | null>(null);
  const [state, setState] = useState<'loading' | 'ready' | 'failed'>('loading');
  const [loadingMore, setLoadingMore] = useState(false);

  const load = useCallback(async () => {
    if (!uid) return;
    setState('loading');
    try {
      const [entries, snapshot] = await Promise.all([ledgerBefore(uid, null), loadStatistics(uid)]);
      setItems(historyItems(entries));
      setNextBefore(entries.length === HISTORY_PAGE_SIZE ? entries[entries.length - 1]!.seq : null);
      setStats(snapshot.games);
      setState('ready');
    } catch {
      setState('failed');
    }
  }, [uid]);

  // Se recarga cuando cambia el monedero (una ronda nueva) para que el historial esté al día.
  const seq = wallet?.seq;
  useEffect(() => {
    void load();
  }, [load, seq]);

  const loadMore = async () => {
    if (!uid || nextBefore === null) return;
    setLoadingMore(true);
    try {
      const entries = await ledgerBefore(uid, nextBefore);
      setItems((current) => [...current, ...historyItems(entries)]);
      setNextBefore(entries.length === HISTORY_PAGE_SIZE ? entries[entries.length - 1]!.seq : null);
    } catch {
      // Se puede volver a intentar con el mismo botón.
    }
    setLoadingMore(false);
  };

  const rounds = items.filter((i) => i.type === 'round').length;
  const anyRounds = stats && Object.values(stats).some((s) => s && s.rounds > 0);

  return (
    <div className="safe-top safe-px-4 mx-auto max-w-lg pb-8">
      <h1 className="px-1 pt-6 font-display text-2xl font-semibold text-gold-gradient">Historial</h1>

      {state === 'loading' && items.length === 0 && (
        <div className="mt-6 flex flex-col gap-2" aria-label="Cargando historial">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="h-16 animate-pulse rounded-2xl bg-ink-2" />
          ))}
        </div>
      )}

      {state === 'failed' && (
        <div className="mt-6 rounded-3xl bg-ink-2 px-5 py-6 text-center">
          <p className="text-ivory-dim">No se pudo cargar el historial. Comprueba tu conexión.</p>
          <Button variant="secondary" className="mt-4" onClick={() => void load()}>
            Reintentar
          </Button>
        </div>
      )}

      {state !== 'failed' && stats && !anyRounds && rounds === 0 && state === 'ready' && (
        <motion.div className="mt-6 rounded-3xl bg-ink-2 px-5 py-8 text-center" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.3, ease: EASE_OUT }}>
          <p className="font-display text-lg font-semibold text-ivory">Aún no hay partidas</p>
          <p className="mt-2 text-sm leading-relaxed text-ivory-dim">
            Cuando juegues, aquí aparecerá cada partida con su apuesta y su resultado, además de tus estadísticas por juego.
          </p>
        </motion.div>
      )}

      {stats && anyRounds && (
        <>
          <h2 className="mt-6 mb-3 px-1 text-xs font-bold tracking-[0.2em] text-gold uppercase">Por juego</h2>
          <ul className="grid grid-cols-1 gap-2">
            {GAMES.map((game, i) => {
              const s = stats[game];
              const net = s ? s.returned - s.staked : 0;
              const winRate = s && s.rounds > 0 ? s.wins / s.rounds : 0;
              return (
                <motion.li
                  key={game}
                  className="panel flex items-center gap-4 rounded-2xl px-4 py-3"
                  initial={{ opacity: 0, transform: 'translateY(8px)' }}
                  animate={{ opacity: 1, transform: 'translateY(0px)' }}
                  transition={{ duration: 0.3, ease: EASE_OUT, delay: i * 0.035 }}
                >
                  <span className="w-24 shrink-0 font-display font-semibold text-gold-light">{GAME_NAME[game]}</span>
                  {s && s.rounds > 0 ? (
                    <div className="min-w-0 flex-1 text-sm">
                      <p className="text-ivory">
                        {grouped(s.rounds)} {s.rounds === 1 ? 'ronda' : 'rondas'} · {Math.round(winRate * 100)} % ganadas
                      </p>
                      {/* % de rondas ganadas: la barra se llena al aparecer. */}
                      <div className="my-1 h-1 overflow-hidden rounded-full bg-ink-4" aria-hidden>
                        <motion.div
                          className="h-full w-full origin-left rounded-full metal-gold"
                          initial={{ transform: 'scaleX(0)' }}
                          animate={{ transform: `scaleX(${winRate})` }}
                          transition={{ duration: 0.7, ease: EASE_OUT, delay: 0.15 + i * 0.035 }}
                        />
                      </div>
                      <p className="text-xs text-ivory-dim">
                        Balance: <span className={net > 0 ? 'text-emerald' : net < 0 ? 'text-ruby-bright' : ''}>{signed(net)}</span>
                        {s.biggestWin > 0 && <> · Mejor ronda: +{grouped(s.biggestWin)}</>}
                      </p>
                    </div>
                  ) : (
                    <p className="flex-1 text-sm text-mute">Sin partidas todavía</p>
                  )}
                </motion.li>
              );
            })}
          </ul>
        </>
      )}

      {items.length > 0 && (
        <>
          <h2 className="mt-8 mb-3 px-1 text-xs font-bold tracking-[0.2em] text-gold uppercase">Últimos movimientos</h2>
          <ul className="flex flex-col gap-1.5">
            {items.map((item, i) => (
              <HistoryRow key={item.seq} item={item} order={i % HISTORY_PAGE_SIZE} />
            ))}
          </ul>
          {nextBefore !== null && (
            <Button variant="secondary" block className="mt-4" loading={loadingMore} onClick={() => void loadMore()}>
              Cargar más
            </Button>
          )}
        </>
      )}
    </div>
  );
}

function HistoryRow({ item, order }: { item: HistoryItem; order: number }) {
  let title: string;
  let detail: string | null = null;
  let net: number | null;
  if (item.type === 'round') {
    title = GAME_NAME[item.game];
    net = item.stake === null ? null : item.payout - item.stake;
    detail = item.stake === null ? `cobra ${chips(item.payout)}` : `apuesta ${grouped(item.stake)} · cobra ${grouped(item.payout)}`;
  } else {
    net = item.amount;
    title =
      item.kind === 'Welcome'
        ? 'Fichas de bienvenida'
        : item.kind === 'Rescue'
          ? 'Recarga gratuita'
          : item.kind === 'DailyBonus'
            ? 'Bono diario'
            : item.kind === 'DailySpin'
              ? 'Ruleta diaria'
            : `Logro: ${item.achievementId ? achievement(item.achievementId).name : ''}`;
  }
  return (
    <motion.li
      layout="position"
      className="flex items-center gap-3 rounded-2xl bg-ink-1 px-4 py-3 ring-1 ring-white/5"
      initial={{ opacity: 0, transform: 'translateY(6px)' }}
      animate={{ opacity: 1, transform: 'translateY(0px)' }}
      // Escalonado corto: como mucho las diez primeras de cada página.
      transition={{ duration: 0.25, ease: EASE_OUT, delay: Math.min(order, 10) * 0.03 }}
    >
      <div className="min-w-0 flex-1">
        <p className="truncate font-semibold text-ivory">{title}</p>
        <p className="truncate text-xs text-mute">
          {formatDateTime(item.at)}
          {detail && ` · ${detail}`}
        </p>
      </div>
      <div className="text-right">
        {net !== null && <p className={`tabular font-bold ${net > 0 ? 'text-emerald' : net < 0 ? 'text-ruby-bright' : 'text-ivory-dim'}`}>{signed(net)}</p>}
        <p className="tabular text-[11px] text-mute">Saldo {grouped(item.balanceAfter)}</p>
      </div>
    </motion.li>
  );
}
