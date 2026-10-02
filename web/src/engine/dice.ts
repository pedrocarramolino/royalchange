import { secureRandom, type RandomInt } from './cards';

export interface DiceRoll {
  first: number;
  second: number;
}

export const rollSum = (r: DiceRoll) => r.first + r.second;
export const isDouble = (r: DiceRoll) => r.first === r.second;

/** Apuestas: "low" (2–6), "high" (8–12), "seven", "doubles" y "sum:N" (suma exacta, sin el 7). */
export type DiceBet = 'low' | 'high' | 'seven' | 'doubles' | `sum:${number}`;

const SUM_TENTHS: Record<number, number> = { 2: 350, 12: 350, 3: 175, 11: 175, 4: 115, 10: 115, 5: 87, 9: 87, 6: 69, 8: 69 };

/** Pago en décimas (apuesta incluida): 23 = ×2,3. Con fichas de 10 el pago siempre es entero. */
export function multiplierTenths(bet: DiceBet): number {
  if (bet === 'low' || bet === 'high') return 23;
  if (bet === 'seven' || bet === 'doubles') return 58;
  return SUM_TENTHS[Number(bet.slice(4))] ?? 0;
}

export function diceWins(bet: DiceBet, roll: DiceRoll): boolean {
  const sum = rollSum(roll);
  if (bet === 'low') return sum <= 6;
  if (bet === 'high') return sum >= 8;
  if (bet === 'seven') return sum === 7;
  if (bet === 'doubles') return isDouble(roll);
  return sum === Number(bet.slice(4));
}

export const dicePayout = (bet: DiceBet, stake: number, roll: DiceRoll) => (diceWins(bet, roll) ? (stake * multiplierTenths(bet)) / 10 : 0);

export const ALL_DICE_BETS: DiceBet[] = ['low', 'seven', 'high', 'doubles', ...[2, 3, 4, 5, 6, 8, 9, 10, 11, 12].map((n) => `sum:${n}` as DiceBet)];

export const DICE_RULES = { chipUnit: 10, maximumTotalBet: 25_000 } as const;

export interface PlacedDiceBet {
  bet: DiceBet;
  stake: number;
}

export interface DiceThrow {
  roll: DiceRoll;
  results: (PlacedDiceBet & { payout: number })[];
  totalStake: number;
  totalPayout: number;
}

export type DiceError = 'noBets' | 'invalidBet' | 'aboveTableMaximum';

export function validateDice(bets: PlacedDiceBet[]): DiceError | null {
  if (bets.length === 0) return 'noBets';
  if (bets.some((b) => multiplierTenths(b.bet) === 0 || b.stake < DICE_RULES.chipUnit || b.stake % DICE_RULES.chipUnit !== 0)) return 'invalidBet';
  if (bets.reduce((s, b) => s + b.stake, 0) > DICE_RULES.maximumTotalBet) return 'aboveTableMaximum';
  return null;
}

export function settleDice(bets: PlacedDiceBet[], roll: DiceRoll): DiceThrow {
  const results = bets.map((b) => ({ ...b, payout: dicePayout(b.bet, b.stake, roll) }));
  return { roll, results, totalStake: results.reduce((s, r) => s + r.stake, 0), totalPayout: results.reduce((s, r) => s + r.payout, 0) };
}

export function throwDice(bets: PlacedDiceBet[], random: RandomInt = secureRandom): { ok: true; result: DiceThrow } | { ok: false; error: DiceError } {
  const error = validateDice(bets);
  if (error) return { ok: false, error };
  return { ok: true, result: settleDice(bets, { first: random(6) + 1, second: random(6) + 1 }) };
}

export function diceBetName(bet: DiceBet): string {
  if (bet === 'low') return 'menor, suma de 2 a 6';
  if (bet === 'high') return 'mayor, suma de 8 a 12';
  if (bet === 'seven') return 'siete exacto';
  if (bet === 'doubles') return 'dobles, los dos dados iguales';
  return `suma exacta ${bet.slice(4)}`;
}

/** 23 → "×2,3"; 350 → "×35". */
export function multiplierText(tenths: number): string {
  return tenths % 10 === 0 ? `×${tenths / 10}` : `×${Math.floor(tenths / 10)},${tenths % 10}`;
}
