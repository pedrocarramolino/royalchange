import { colorOf } from '@/features/games/roulette/wheel';
import { secureRandom, type RandomInt } from './cards';

/** Casillas de la rueda europea (0–36). */
export const POCKETS = 37;

/**
 * Apuesta de ruleta como clave estable (se guarda y sirve de identificador en el tapete):
 * "n:17" pleno, "split:17-20" caballo, "street:6" transversal (fila 6), "corner:17" cuadro (esquina
 * superior izquierda), "line:3" seisena (filas 3 y 4), "column:1", "dozen:2", "red", "black",
 * "even", "odd", "low", "high".
 */
export type RouletteBet = string;

const range = (from: number, to: number, step = 1) => Array.from({ length: Math.floor((to - from) / step) + 1 }, (_, i) => from + i * step);
const rowNumbers = (row: number) => [3 * row - 2, 3 * row - 1, 3 * row];

/** Números que cubre una apuesta; vacío si no es válida. */
export function betNumbers(bet: RouletteBet): number[] {
  const [kind, arg] = bet.split(':') as [string, string | undefined];
  const n = Number(arg);
  switch (kind) {
    case 'n':
      return Number.isInteger(n) && n >= 0 && n <= 36 ? [n] : [];
    case 'split': {
      const [a, b] = (arg ?? '').split('-').map(Number) as [number, number];
      const low = Math.min(a, b);
      const high = Math.max(a, b);
      const valid = low === 0 ? high >= 1 && high <= 3 : low >= 1 && high <= 36 && (high - low === 3 || (high - low === 1 && low % 3 !== 0));
      return valid ? [low, high] : [];
    }
    case 'street':
      return n >= 1 && n <= 12 ? rowNumbers(n) : [];
    case 'corner':
      return n >= 1 && n <= 32 && n % 3 !== 0 ? [n, n + 1, n + 3, n + 4] : [];
    case 'line':
      return n >= 1 && n <= 11 ? [...rowNumbers(n), ...rowNumbers(n + 1)] : [];
    case 'column':
      return n >= 1 && n <= 3 ? range(n, 36, 3) : [];
    case 'dozen':
      return n >= 1 && n <= 3 ? range(n * 12 - 11, n * 12) : [];
    case 'red':
      return range(1, 36).filter((x) => colorOf(x) === 'red');
    case 'black':
      return range(1, 36).filter((x) => colorOf(x) === 'black');
    case 'even':
      return range(2, 36, 2);
    case 'odd':
      return range(1, 35, 2);
    case 'low':
      return range(1, 18);
    case 'high':
      return range(19, 36);
    default:
      return [];
  }
}

/** Pago (apuesta incluida): 36 / números cubiertos. Pleno 36×, caballo 18×, sencillas 2×. */
export function rouletteMultiplier(bet: RouletteBet): number {
  const count = betNumbers(bet).length;
  return count ? (POCKETS - 1) / count : 0;
}

export function roulettePayout(bet: RouletteBet, stake: number, number: number): number {
  const numbers = betNumbers(bet);
  return numbers.includes(number) ? (stake * (POCKETS - 1)) / numbers.length : 0;
}

export const ROULETTE_RULES = { chipUnit: 10, maximumTotalBet: 25_000 } as const;

export interface PlacedBet {
  bet: RouletteBet;
  stake: number;
}

export interface BetResult extends PlacedBet {
  payout: number;
}

export interface RouletteSpin {
  number: number;
  results: BetResult[];
  totalStake: number;
  totalPayout: number;
}

export type RouletteError = 'noBets' | 'invalidBet' | 'aboveTableMaximum';

export function validateRoulette(bets: PlacedBet[]): RouletteError | null {
  if (bets.length === 0) return 'noBets';
  if (bets.some((b) => betNumbers(b.bet).length === 0 || b.stake < ROULETTE_RULES.chipUnit || b.stake % ROULETTE_RULES.chipUnit !== 0)) return 'invalidBet';
  if (bets.reduce((s, b) => s + b.stake, 0) > ROULETTE_RULES.maximumTotalBet) return 'aboveTableMaximum';
  return null;
}

export function settleRoulette(bets: PlacedBet[], number: number): RouletteSpin {
  const results = bets.map((b) => ({ ...b, payout: roulettePayout(b.bet, b.stake, number) }));
  return {
    number,
    results,
    totalStake: results.reduce((s, r) => s + r.stake, 0),
    totalPayout: results.reduce((s, r) => s + r.payout, 0),
  };
}

export function spinRoulette(bets: PlacedBet[], random: RandomInt = secureRandom): { ok: true; spin: RouletteSpin } | { ok: false; error: RouletteError } {
  const error = validateRoulette(bets);
  if (error) return { ok: false, error };
  return { ok: true, spin: settleRoulette(bets, random(POCKETS)) };
}

/** Nombre de la apuesta para lectores de pantalla y el historial de la mesa. */
export function betName(bet: RouletteBet): string {
  const [kind, arg] = bet.split(':') as [string, string | undefined];
  switch (kind) {
    case 'n':
      return `pleno al ${arg}`;
    case 'split':
      return `caballo ${arg?.replace('-', ' y ')}`;
    case 'street':
      return `transversal ${rowNumbers(Number(arg)).join('-')}`;
    case 'corner':
      return `cuadro ${betNumbers(bet).join('-')}`;
    case 'line':
      return `seisena ${betNumbers(bet)[0]}-${betNumbers(bet)[5]}`;
    case 'column':
      return `columna ${arg}`;
    case 'dozen':
      return `docena ${arg}`;
    case 'red':
      return 'rojo';
    case 'black':
      return 'negro';
    case 'even':
      return 'par';
    case 'odd':
      return 'impar';
    case 'low':
      return 'falta (1 a 18)';
    case 'high':
      return 'pasa (19 a 36)';
    default:
      return bet;
  }
}
