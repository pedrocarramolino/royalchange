import { describe, expect, it } from 'vitest';
import { buyScratchTicket, SCRATCH_BETS, SCRATCH_CELLS, SCRATCH_PRIZES } from './scratch';

/** Generador reproducible para recorrer muchos boletos. */
function seeded(seed: number) {
  return (bound: number) => {
    seed = (seed * 1_103_515_245 + 12_345) % 2_147_483_648;
    return Math.floor((seed / 2_147_483_648) * bound);
  };
}

describe('rasca y gana', () => {
  it('devuelve alrededor de un 94 % y uno de cada tres boletos tiene premio', () => {
    const rtp = SCRATCH_PRIZES.reduce((sum, p) => sum + (p.weight / 1_000) * p.multiplier, 0);
    expect(rtp).toBeGreaterThan(0.93);
    expect(rtp).toBeLessThan(0.95);
    const hit = SCRATCH_PRIZES.reduce((sum, p) => sum + p.weight, 0) / 1_000;
    expect(hit).toBeGreaterThan(0.28);
    expect(hit).toBeLessThan(0.32);
  });

  it('cada boleto tiene 9 casillas y como mucho un trío, el de su premio', () => {
    const random = seeded(42);
    for (let n = 0; n < 5_000; n++) {
      const ticket = buyScratchTicket(100, random);
      expect(ticket.cells).toHaveLength(SCRATCH_CELLS);
      const counts = new Map<string, number>();
      for (const s of ticket.cells) counts.set(s, (counts.get(s) ?? 0) + 1);
      const triples = [...counts.entries()].filter(([, c]) => c >= 3).map(([s]) => s);
      if (ticket.prize) {
        expect(triples).toEqual([ticket.prize.symbol]);
        expect(counts.get(ticket.prize.symbol)).toBe(3);
        expect(ticket.winning.map((i) => ticket.cells[i])).toEqual([ticket.prize.symbol, ticket.prize.symbol, ticket.prize.symbol]);
        expect(ticket.payout).toBe(100 * ticket.prize.multiplier);
      } else {
        expect(triples).toEqual([]);
        expect(ticket.winning).toEqual([]);
        expect(ticket.payout).toBe(0);
      }
    }
  });

  it('el sorteo respeta las probabilidades de cada premio', () => {
    // Recorrer todas las tiradas posibles del premio da exactamente los pesos de la tabla.
    for (let roll = 0; roll < 1_000; roll++) {
      let first = true;
      const ticket = buyScratchTicket(10, (bound) => (first ? ((first = false), roll % bound) : 0));
      const expected = (() => {
        let r = roll;
        for (const p of SCRATCH_PRIZES) {
          if (r < p.weight) return p.symbol;
          r -= p.weight;
        }
        return null;
      })();
      expect(ticket.prize?.symbol ?? null).toBe(expected);
    }
  });

  it('los pagos son enteros y no superan el máximo de la economía', () => {
    for (const bet of SCRATCH_BETS) for (const p of SCRATCH_PRIZES) expect(Number.isInteger(bet * p.multiplier)).toBe(true);
    expect(Math.max(...SCRATCH_PRIZES.map((p) => p.multiplier))).toBeLessThanOrEqual(1_000);
  });
});
