import { secureRandom, type RandomInt } from './cards';
import type { SlotSymbol } from './slots';

/*
 * Rasca y gana: un boleto de 9 casillas (3 × 3) con símbolos. Si salen tres iguales, el boleto paga
 * su premio; nunca hay dos tríos. El premio se sortea primero con estas probabilidades (sobre 1.000)
 * y después se reparte el boleto a juego: devuelve un 94 % y uno de cada tres boletos tiene premio.
 */

export interface ScratchPrize {
  symbol: SlotSymbol;
  multiplier: number;
  /** Boletos de cada 1.000 con este premio. */
  weight: number;
}

export const SCRATCH_PRIZES: ScratchPrize[] = [
  { symbol: 'Wild', multiplier: 100, weight: 1 },
  { symbol: 'Seven', multiplier: 50, weight: 2 },
  { symbol: 'Bar', multiplier: 20, weight: 5 },
  { symbol: 'Diamond', multiplier: 10, weight: 15 },
  { symbol: 'Spade', multiplier: 5, weight: 30 },
  { symbol: 'Heart', multiplier: 2, weight: 95 },
  { symbol: 'Cherry', multiplier: 1, weight: 150 },
];

export const SCRATCH_SYMBOLS: SlotSymbol[] = SCRATCH_PRIZES.map((p) => p.symbol);

/** Precios del boleto. */
export const SCRATCH_BETS = [10, 20, 50, 100, 200, 500, 1_000, 2_000, 5_000];

export const SCRATCH_CELLS = 9;

export interface ScratchTicket {
  cells: SlotSymbol[];
  /** Premio del boleto, o `null` si no tiene. */
  prize: ScratchPrize | null;
  /** Casillas del trío ganador (vacío si no hay premio). */
  winning: number[];
  stake: number;
  payout: number;
}

function shuffle<T>(items: T[], random: RandomInt): T[] {
  const copy = [...items];
  for (let i = copy.length - 1; i > 0; i--) {
    const j = random(i + 1);
    [copy[i], copy[j]] = [copy[j]!, copy[i]!];
  }
  return copy;
}

/** Compra un boleto: sortea el premio y reparte los símbolos sin formar ningún otro trío. */
export function buyScratchTicket(stake: number, random: RandomInt = secureRandom): ScratchTicket {
  let roll = random(1_000);
  let prize: ScratchPrize | null = null;
  for (const candidate of SCRATCH_PRIZES) {
    if (roll < candidate.weight) {
      prize = candidate;
      break;
    }
    roll -= candidate.weight;
  }
  // Cada símbolo sin premio aparece como mucho dos veces: así solo puede haber un trío.
  const others = SCRATCH_SYMBOLS.filter((s) => s !== prize?.symbol).flatMap((s) => [s, s]);
  const filler = shuffle(others, random).slice(0, prize ? SCRATCH_CELLS - 3 : SCRATCH_CELLS);
  const cells = shuffle(prize ? [prize.symbol, prize.symbol, prize.symbol, ...filler] : filler, random);
  const winning = prize ? cells.flatMap((s, i) => (s === prize.symbol ? [i] : [])) : [];
  return { cells, prize, winning, stake, payout: prize ? stake * prize.multiplier : 0 };
}
