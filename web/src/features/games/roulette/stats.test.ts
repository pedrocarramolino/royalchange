import { describe, expect, it } from 'vitest';
import { coldNumbers, hotNumbers, MIN_FOR_COLD } from './stats';

// Tiradas de ejemplo, la más reciente primero: 17 sale 3 veces, 9 y 32 dos (32 más recientemente).
const HISTORY = [32, 17, 5, 9, 17, 32, 0, 9, 17, 14, 26, 31, 3, 20, 1, 36, 11, 7, 28, 12];

describe('marcador de la ruleta', () => {
  it('calientes: los que más salen, al menos dos veces; a igualdad, el más reciente', () => {
    expect(hotNumbers(HISTORY, 5)).toEqual([
      { n: 17, times: 3 },
      { n: 32, times: 2 },
      { n: 9, times: 2 },
    ]);
    expect(hotNumbers(HISTORY, 1)).toEqual([{ n: 17, times: 3 }]);
    expect(hotNumbers([4, 8, 15], 3)).toEqual([]);
  });

  it('fríos: primero los que no han salido nunca, luego los que llevan más sin salir', () => {
    expect(HISTORY.length).toBe(MIN_FOR_COLD);
    const cold = coldNumbers(HISTORY, 3);
    // Ninguno de los tres ha salido; a igualdad, por número.
    expect(cold).toEqual([
      { n: 2, times: 0 },
      { n: 4, times: 0 },
      { n: 6, times: 0 },
    ]);
    // Si todos han salido alguna vez, el que menos; a igualdad, el que lleva más sin salir.
    const all = [...Array.from({ length: 37 }, (_, n) => n), 5, 6];
    expect(coldNumbers(all, 2)).toEqual([
      { n: 36, times: 1 },
      { n: 35, times: 1 },
    ]);
  });

  it('fríos: no repiten los calientes y esperan a tener tiradas suficientes', () => {
    const many = [...Array.from({ length: 37 }, (_, n) => n), ...Array.from({ length: 37 }, (_, n) => n)];
    expect(coldNumbers(many, 2, [36]).map((c) => c.n)).toEqual([35, 34]);
    expect(coldNumbers(HISTORY.slice(0, MIN_FOR_COLD - 1), 3)).toEqual([]);
  });
});
