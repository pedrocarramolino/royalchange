import { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { IconFlame, IconSnowflake } from '@/ui/icons';
import { EASE_OUT } from '@/ui/motion';
import { usePlayerId } from '../shared/session';
import { coldNumbers, hotNumbers, MIN_FOR_COLD, type NumberCount } from './stats';
import { COLOR_NAME, colorOf } from './wheel';

/** Tiradas que se guardan (y sobre las que se calculan los números calientes y fríos). */
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

/**
 * Cuántos números caben a lo ancho. Cada último número ocupa 28 px; cada caliente o frío, 46 con su
 * contador, y el panel de calientes y fríos, 73 más (icono, separador y márgenes). Se reparte para
 * que se vean al menos cinco de los últimos; los nombres «Calientes» y «Fríos», solo si sobra sitio.
 */
function layout(width: number): { group: number; recent: number; labels: boolean } {
  const usable = Math.min(width, 900) - 32;
  const recentFits = (group: number, labels: boolean) => Math.floor((usable - (73 + 92 * group + (labels ? 112 : 0)) - 82) / 28);
  for (const group of [4, 3, 2]) {
    if (recentFits(group, true) >= 8) return { group, recent: Math.min(12, recentFits(group, true)), labels: true };
    if (recentFits(group, false) >= (group > 2 ? 5 : 0)) return { group, recent: Math.max(3, Math.min(12, recentFits(group, false))), labels: false };
  }
  return { group: 2, recent: 3, labels: false };
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

function CountedBalls({ items, tone }: { items: NumberCount[]; tone: string }) {
  return (
    <div className="flex gap-1.5" aria-hidden>
      {items.map(({ n, times }) => (
        <span key={n} className="flex items-center gap-0.5">
          <Ball n={n} />
          <span className={`tabular text-[10px] font-bold ${tone}`}>×{times}</span>
        </span>
      ))}
    </div>
  );
}

/**
 * Marcador sobre la rueda y el tapete, como el de las mesas de casino: los últimos números (el más
 * reciente primero y resaltado) y, juntos, los calientes y los fríos con las veces que han salido.
 */
export function RouletteStats({ history, width }: { history: number[]; width: number }) {
  const { group, recent: recentCount, labels } = layout(width);
  const hot = useMemo(() => hotNumbers(history, group), [history, group]);
  const cold = useMemo(() => coldNumbers(history, group, hot.map((h) => h.n)), [history, group, hot]);
  const recent = history.slice(0, recentCount);
  const panel = 'flex h-8 items-center gap-2 rounded-lg bg-black/45 px-2 ring-1 ring-gold/25';
  const spins = history.length === 1 ? 'tu última tirada' : `tus últimas ${history.length} tiradas`;

  return (
    <div className="mx-auto flex w-full max-w-[900px] shrink-0 gap-2 px-3">
      <section
        className={`${panel} min-w-0 flex-1 overflow-hidden`}
        aria-label={recent.length ? `Últimos números: ${recent.map((n) => `${n} ${COLOR_NAME[colorOf(n)]}`).join(', ')}` : 'Últimos números: aún no hay tiradas'}
      >
        <span className="felt-print shrink-0 text-[10px] font-bold">Últimos</span>
        {recent.length ? (
          <div className="flex min-w-0 gap-1" aria-hidden>
            {recent.map((n, i) =>
              i === 0 ? (
                <motion.span key={`${history.length}-${n}`} initial={{ opacity: 0, transform: 'scale(0.9)' }} animate={{ opacity: 1, transform: 'scale(1)' }} transition={{ duration: 0.25, ease: EASE_OUT }}>
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
      <section className={`${panel} shrink-0`} title={`Calientes y fríos en ${spins}`}>
        <div
          className="flex items-center gap-1.5"
          aria-label={hot.length ? `Números calientes en ${spins}: ${hot.map((h) => `${h.n}, ${h.times} veces`).join('; ')}` : 'Números calientes: aún no hay suficientes tiradas'}
        >
          <IconFlame className="size-4 shrink-0 text-[#ff8a5b]" />
          {labels && <span className="felt-print text-[10px] font-bold">Calientes</span>}
          {hot.length ? <CountedBalls items={hot} tone="text-[#ffb08f]" /> : <span className="text-[11px] text-mute">—</span>}
        </div>
        <span className="mx-1 h-5 w-px bg-gold/25" aria-hidden />
        <div
          className="flex items-center gap-1.5"
          aria-label={cold.length ? `Números fríos en ${spins}: ${cold.map((c) => `${c.n}, ${c.times === 0 ? 'ninguna vez' : c.times === 1 ? 'una vez' : `${c.times} veces`}`).join('; ')}` : `Números fríos: se muestran a partir de ${MIN_FOR_COLD} tiradas`}
        >
          <IconSnowflake className="size-4 shrink-0 text-[#8fd3ff]" />
          {labels && <span className="felt-print text-[10px] font-bold">Fríos</span>}
          {cold.length ? <CountedBalls items={cold} tone="text-[#b5e3ff]" /> : <span className="text-[11px] text-mute">—</span>}
        </div>
      </section>
    </div>
  );
}
