import { describe, expect, it } from 'vitest';
import { dropPlinko, PLINKO_BETS, PLINKO_ROWS, PLINKO_TENTHS, plinkoMultiplierText, type PlinkoRisk } from './plinko';

/** Probabilidad de cada casilla: binomial con 12 filas y rebotes al 50 %. */
function slotProbabilities(): number[] {
  const choose = (n: number, k: number): number => (k === 0 || k === n ? 1 : choose(n - 1, k - 1) + choose(n - 1, k));
  return Array.from({ length: PLINKO_ROWS + 1 }, (_, k) => choose(PLINKO_ROWS, k) / 2 ** PLINKO_ROWS);
}

describe('plinko', () => {
  it('las tres tablas son simétricas, tienen 13 casillas y devuelven alrededor de un 99 %', () => {
    const probabilities = slotProbabilities();
    for (const risk of ['low', 'medium', 'high'] as PlinkoRisk[]) {
      const table = PLINKO_TENTHS[risk];
      expect(table).toHaveLength(13);
      expect([...table].reverse()).toEqual(table);
      const rtp = table.reduce((sum, tenths, k) => sum + (tenths / 10) * probabilities[k]!, 0);
      expect(rtp).toBeGreaterThan(0.985);
      expect(rtp).toBeLessThan(0.995);
    }
  });

  it('la casilla es el número de rebotes a la derecha y el pago es entero', () => {
    let calls = 0;
    const drop = dropPlinko(50, 'high', () => (calls++ % 3 === 0 ? 1 : 0));
    expect(drop.path).toHaveLength(12);
    expect(drop.slot).toBe(drop.path.filter(Boolean).length);
    expect(drop.payout).toBe((50 * PLINKO_TENTHS.high[drop.slot]!) / 10);
    for (const bet of PLINKO_BETS) for (const tenths of PLINKO_TENTHS.high) expect(Number.isInteger((bet * tenths) / 10)).toBe(true);
  });

  it('nunca supera el techo de 1.000 veces la apuesta', () => {
    expect(Math.max(...PLINKO_TENTHS.high) / 10).toBeLessThanOrEqual(1_000);
    expect(plinkoMultiplierText(1700)).toBe('×170');
    expect(plinkoMultiplierText(16)).toBe('×1,6');
  });
});
