/** Estadísticas del marcador de la ruleta, a partir de las tiradas (la más reciente primero). */

/** Con menos tiradas casi todos los números estarían «fríos»: no se muestran todavía. */
export const MIN_FOR_COLD = 20;

export interface NumberCount {
  n: number;
  times: number;
}

/** Los números que más han salido (al menos dos veces); a igualdad, el que salió más recientemente. */
export function hotNumbers(history: number[], count: number): NumberCount[] {
  const times = new Map<number, number>();
  history.forEach((n) => times.set(n, (times.get(n) ?? 0) + 1));
  return [...times.entries()]
    .filter(([, t]) => t >= 2)
    .sort((a, b) => b[1] - a[1] || history.indexOf(a[0]) - history.indexOf(b[0]))
    .slice(0, count)
    .map(([n, t]) => ({ n, times: t }));
}

/**
 * Los números que menos han salido; a igualdad, el que lleva más tiempo sin salir (primero los que
 * no han salido nunca). Sin repetir los calientes.
 */
export function coldNumbers(history: number[], count: number, exclude: number[] = []): NumberCount[] {
  if (history.length < MIN_FOR_COLD) return [];
  const numbers = Array.from({ length: 37 }, (_, n) => ({ n, times: 0, last: Infinity }));
  history.forEach((n, i) => {
    numbers[n]!.times++;
    if (numbers[n]!.last === Infinity) numbers[n]!.last = i;
  });
  return numbers
    .filter(({ n }) => !exclude.includes(n))
    .sort((a, b) => a.times - b.times || b.last - a.last || a.n - b.n)
    .slice(0, count)
    .map(({ n, times }) => ({ n, times }));
}
