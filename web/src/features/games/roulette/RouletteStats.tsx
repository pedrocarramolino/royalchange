import { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { IconFlame } from '@/ui/icons';
import { usePlayerId } from '../shared/session';
import { COLOR_NAME, colorOf } from './wheel';

/** Tiradas que se guardan (y sobre las que se calculan los números calientes). */
const HISTORY_SIZE = 100;
/** Alto del marcador, con su separación de la mesa. */
export const STATS_HEIGHT = 42;

const BALL = { green: '#0f7a4f', red: '#b3263b', black: '#15171d' } as const;

function load(uid: string | null): number[] {
  if (!uid) return [];
  try {
    const raw = localStorage.getItem(`royal-chance-ruleta-tiradas-${uid}`);
    const parsed: unknown = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed.filter((n): n is number => Number.isInteger(n) && n >= 0 && n <= 36).slice(0, HISTORY_SIZE) : [];
  } catch {
    return [];
  }
}

/** Últimas tiradas del jugador en la ruleta (la más reciente primero), guardadas en el dispositivo. */
export function useRouletteHistory(): [number[], (n: number) => void] {
  const uid = usePlayerId();
  const [history, setHistory] = useState<number[]>(() => load(uid));
  useEffect(() => setHistory(load(uid)), [uid]);
  const add = (n: number) =>
    setHistory((previous) => {
      const next = [n, ...previous].slice(0, HISTORY_SIZE);
      try {
        if (uid) localStorage.setItem(`royal-chance-ruleta-tiradas-${uid}`, JSON.stringify(next));
      } catch {
        // Sin almacenamiento: el marcador dura lo que dure la mesa abierta.
      }
      return next;
    });
  return [history, add];
}

/** Los números que más han salido (al menos dos veces); a igualdad, el que salió más recientemente. */
export function hotNumbers(history: number[], count: number): { n: number; times: number }[] {
  const times = new Map<number, number>();
  history.forEach((n) => times.set(n, (times.get(n) ?? 0) + 1));
  return [...times.entries()]
    .filter(([, t]) => t >= 2)
    .sort((a, b) => b[1] - a[1] || history.indexOf(a[0]) - history.indexOf(b[0]))
    .slice(0, count)
    .map(([n, t]) => ({ n, times: t }));
}

function Ball({ n, latest }: { n: number; latest?: boolean }) {
  return (
    <span
      className={`grid size-6 shrink-0 place-items-center rounded-full text-[11px] font-bold text-white ${latest ? 'shadow-[0_0_10px_rgb(243_223_162/0.55)] ring-2 ring-gold-light' : 'ring-1 ring-gold/45'}`}
      style={{ background: BALL[colorOf(n)] }}
    >
      {n}
    </span>
  );
}

/**
 * Marcador sobre la rueda y el tapete, como el de las mesas de casino: los últimos números (el más
 * reciente primero y resaltado) y los números calientes con las veces que han salido.
 */
export function RouletteStats({ history, width }: { history: number[]; width: number }) {
  const hotCount = width >= 700 ? 5 : width >= 560 ? 4 : 3;
  // Lo que cabe a lo ancho: cada número ocupa 28 px; los calientes, 48 con su contador.
  const recentCount = Math.max(3, Math.min(12, Math.floor((width - 24 - 8 - (100 + hotCount * 48) - 16 - 62) / 28)));
  const hot = useMemo(() => hotNumbers(history, hotCount), [history, hotCount]);
  const recent = history.slice(0, recentCount);
  const panel = 'flex h-8 items-center gap-2 rounded-lg bg-black/45 px-2 ring-1 ring-gold/25';

  return (
    <div className="mx-auto flex w-full max-w-[900px] shrink-0 gap-2 px-3">
      <section
        className={`${panel} min-w-0 flex-1`}
        aria-label={recent.length ? `Últimos números: ${recent.map((n) => `${n} ${COLOR_NAME[colorOf(n)]}`).join(', ')}` : 'Últimos números: aún no hay tiradas'}
      >
        <span className="felt-print shrink-0 text-[10px] font-bold">Últimos</span>
        {recent.length ? (
          <div className="flex min-w-0 gap-1" aria-hidden>
            {recent.map((n, i) =>
              i === 0 ? (
                <motion.span key={`${history.length}-${n}`} initial={{ scale: 0.4, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} transition={{ type: 'spring', stiffness: 420, damping: 22 }}>
                  <Ball n={n} latest />
                </motion.span>
              ) : (
                <span key={`${history.length - i}-${n}`} className="opacity-85">
                  <Ball n={n} />
                </span>
              ),
            )}
          </div>
        ) : (
          <span className="truncate text-[11px] text-mute" aria-hidden>
            Aún no hay tiradas
          </span>
        )}
      </section>
      <section
        className={`${panel} shrink-0`}
        title={`En tus últimas ${history.length} tiradas`}
        aria-label={hot.length ? `Números calientes en tus últimas ${history.length} tiradas: ${hot.map((h) => `${h.n}, ${h.times} veces`).join('; ')}` : 'Números calientes: aún no hay suficientes tiradas'}
      >
        <IconFlame className="size-4 shrink-0 text-[#ff8a5b]" />
        <span className="felt-print shrink-0 text-[10px] font-bold">Calientes</span>
        {hot.length ? (
          <div className="flex gap-2" aria-hidden>
            {hot.map(({ n, times }) => (
              <span key={n} className="flex items-center gap-0.5">
                <Ball n={n} />
                <span className="tabular text-[10px] font-bold text-gold-light">×{times}</span>
              </span>
            ))}
          </div>
        ) : (
          <span className="text-[11px] text-mute" aria-hidden>
            Aún sin datos
          </span>
        )}
      </section>
    </div>
  );
}
