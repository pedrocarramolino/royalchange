import { secureRandom, type RandomInt } from './cards';

/*
 * Plinko: la bola cae por 12 filas de clavos; en cada una rebota a la izquierda o a la derecha con
 * la misma probabilidad y acaba en una de 13 casillas (tantas como veces fue a la derecha). Cada
 * casilla multiplica la apuesta según el riesgo elegido; las tres tablas devuelven ~99 %.
 */

export const PLINKO_ROWS = 12;

export type PlinkoRisk = 'low' | 'medium' | 'high';

/** Multiplicadores en décimas (×1,6 = 16), de la casilla de la izquierda a la de la derecha. */
export const PLINKO_TENTHS: Record<PlinkoRisk, number[]> = {
  low: [100, 30, 16, 14, 11, 10, 5, 10, 11, 14, 16, 30, 100],
  medium: [330, 110, 40, 20, 11, 6, 3, 6, 11, 20, 40, 110, 330],
  high: [1700, 240, 81, 20, 7, 2, 2, 2, 7, 20, 81, 240, 1700],
};

export const PLINKO_RISK_NAME: Record<PlinkoRisk, string> = { low: 'Bajo', medium: 'Medio', high: 'Alto' };

/** Apuestas posibles por bola. */
export const PLINKO_BETS = [10, 20, 50, 100, 200, 500, 1_000, 2_000, 5_000];

export interface PlinkoDrop {
  /** Rebote en cada fila: `true` a la derecha. */
  path: boolean[];
  /** Casilla final (0 a 12). */
  slot: number;
  stake: number;
  payout: number;
  tenths: number;
}

/** 16 → "×1,6"; 1700 → "×170". */
export function plinkoMultiplierText(tenths: number): string {
  return tenths % 10 === 0 ? `×${tenths / 10}` : `×${Math.floor(tenths / 10)},${tenths % 10}`;
}

/** Suelta una bola: el pago es entero porque las apuestas son múltiplos de 10. */
export function dropPlinko(stake: number, risk: PlinkoRisk, random: RandomInt = secureRandom): PlinkoDrop {
  const path = Array.from({ length: PLINKO_ROWS }, () => random(2) === 1);
  const slot = path.filter(Boolean).length;
  const tenths = PLINKO_TENTHS[risk][slot]!;
  return { path, slot, stake, payout: (stake * tenths) / 10, tenths };
}
