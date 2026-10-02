import { secureRandom, type RandomInt } from './cards';

/** Símbolos de menor a mayor premio. El comodín sustituye a cualquiera y tiene su propio premio. */
export const SYMBOLS = ['Cherry', 'Club', 'Heart', 'Spade', 'Diamond', 'Bar', 'Seven', 'Wild'] as const;
export type SlotSymbol = (typeof SYMBOLS)[number];

export const REELS = 5;
export const ROWS = 3;

const strip = (...indices: number[]): SlotSymbol[] => indices.map((i) => SYMBOLS[i]!);

/** Tiras de los rodillos (32 posiciones). RTP calculado exactamente: 97,27 %. */
export const STRIPS: SlotSymbol[][] = [
  strip(0, 1, 2, 7, 0, 3, 1, 4, 0, 2, 5, 0, 1, 6, 3, 0, 2, 7, 4, 1, 0, 3, 2, 5, 0, 1, 4, 6, 0, 2, 7, 3),
  strip(1, 0, 3, 2, 7, 0, 4, 1, 0, 5, 2, 0, 3, 6, 1, 0, 7, 2, 4, 0, 1, 3, 5, 0, 2, 6, 1, 0, 4, 7, 3, 2),
  strip(2, 0, 1, 4, 0, 7, 3, 2, 0, 6, 1, 0, 5, 3, 2, 0, 4, 7, 1, 0, 2, 3, 6, 0, 1, 5, 0, 2, 4, 3, 1, 7),
  strip(0, 3, 1, 2, 6, 0, 7, 4, 1, 0, 2, 5, 3, 0, 1, 7, 2, 0, 4, 6, 1, 3, 0, 2, 5, 0, 4, 1, 7, 0, 3, 2),
  strip(3, 0, 2, 1, 5, 0, 4, 7, 2, 0, 1, 6, 3, 0, 2, 4, 1, 0, 7, 3, 2, 0, 5, 1, 6, 0, 4, 2, 1, 0, 7, 3),
];

/** Diez líneas de premio: fila de cada rodillo. */
export const LINES: number[][] = [
  [1, 1, 1, 1, 1],
  [0, 0, 0, 0, 0],
  [2, 2, 2, 2, 2],
  [0, 1, 2, 1, 0],
  [2, 1, 0, 1, 2],
  [0, 0, 1, 2, 2],
  [2, 2, 1, 0, 0],
  [1, 0, 0, 0, 1],
  [1, 2, 2, 2, 1],
  [0, 1, 1, 1, 0],
];

/** Premio por apuesta de línea para 2, 3, 4 y 5 símbolos seguidos desde el primer rodillo. */
export const PAYTABLE: Record<SlotSymbol, [number, number, number, number]> = {
  Cherry: [1, 3, 8, 25],
  Club: [0, 4, 12, 40],
  Heart: [0, 4, 12, 40],
  Spade: [0, 6, 20, 60],
  Diamond: [0, 10, 30, 100],
  Bar: [0, 12, 40, 150],
  Seven: [0, 20, 80, 300],
  Wild: [0, 30, 120, 500],
};

export const SYMBOL_NAME: Record<SlotSymbol, string> = {
  Cherry: 'Cereza',
  Club: 'Trébol',
  Heart: 'Corazón',
  Spade: 'Pica',
  Diamond: 'Diamante',
  Bar: 'BAR',
  Seven: 'Siete',
  Wild: 'Corona (comodín)',
};

export const LINE_BETS = [1, 2, 5, 10, 20, 50, 100, 250, 500];

export function pay(symbol: SlotSymbol, run: number): number {
  return run < 2 ? 0 : PAYTABLE[symbol][run - 2]!;
}

/** Ventana visible: rodillo × fila. */
export function slotWindow(stops: number[]): SlotSymbol[][] {
  return stops.map((stop, reel) => {
    const s = STRIPS[reel]!;
    return Array.from({ length: ROWS }, (_, row) => s[(stop + row) % s.length]!);
  });
}

/** Mejor premio de una línea: el del comodín solo o el del primer símbolo con comodines. */
export function evaluateLine(symbols: SlotSymbol[]): [SlotSymbol, number] | null {
  let wildRun = 0;
  while (wildRun < symbols.length && symbols[wildRun] === 'Wild') wildRun++;
  const first = symbols.find((s) => s !== 'Wild');
  let run = 0;
  if (first) while (run < symbols.length && (symbols[run] === first || symbols[run] === 'Wild')) run++;
  const wildPay = pay('Wild', wildRun);
  const symbolPay = first ? pay(first, run) : 0;
  if (wildPay === 0 && symbolPay === 0) return null;
  return wildPay >= symbolPay ? ['Wild', wildRun] : [first!, run];
}

export interface LineWin {
  line: number;
  symbol: SlotSymbol;
  count: number;
  payout: number;
}

export interface SlotSpin {
  stops: number[];
  lineBet: number;
  wins: LineWin[];
  totalBet: number;
  totalPayout: number;
}

export function settleSlots(stops: number[], lineBet: number): SlotSpin {
  const window = slotWindow(stops);
  const wins: LineWin[] = [];
  LINES.forEach((rows, line) => {
    const symbols = rows.map((row, reel) => window[reel]![row]!);
    const win = evaluateLine(symbols);
    if (win) wins.push({ line, symbol: win[0], count: win[1], payout: pay(win[0], win[1]) * lineBet });
  });
  return { stops, lineBet, wins, totalBet: lineBet * LINES.length, totalPayout: wins.reduce((s, w) => s + w.payout, 0) };
}

export function spinSlots(lineBet: number, random: RandomInt = secureRandom): SlotSpin | null {
  if (!LINE_BETS.includes(lineBet)) return null;
  return settleSlots(STRIPS.map((s) => random(s.length)), lineBet);
}
