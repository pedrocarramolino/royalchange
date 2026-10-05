import { rankIndex, secureRandom, shuffle, standardDeck, SUITS, type Card, type RandomInt } from './cards';

// ── Evaluador de manos (5 a 7 cartas) ──────────────────────────────────────────────────────────

export const HAND_CATEGORIES = ['HighCard', 'OnePair', 'TwoPair', 'ThreeOfAKind', 'Straight', 'Flush', 'FullHouse', 'FourOfAKind', 'StraightFlush'] as const;
export type HandCategory = (typeof HAND_CATEGORIES)[number];

export const CATEGORY_NAME: Record<HandCategory, string> = {
  HighCard: 'carta alta',
  OnePair: 'pareja',
  TwoPair: 'doble pareja',
  ThreeOfAKind: 'trío',
  Straight: 'escalera',
  Flush: 'color',
  FullHouse: 'full',
  FourOfAKind: 'póker',
  StraightFlush: 'escalera de color',
};

/** Valor de 2 a 14 (as). */
export const cardValue = (card: Card) => rankIndex(card.rank) + 2;

/** Puntuación comparable: categoría en los bits altos y hasta 5 desempates de 4 bits. */
export function evaluateHand(cards: Card[]): number {
  const counts = new Array<number>(15).fill(0);
  const suitCounts = [0, 0, 0, 0];
  const suitMasks = [0, 0, 0, 0];
  let mask = 0;
  for (const card of cards) {
    const v = cardValue(card);
    const s = SUITS.indexOf(card.suit);
    counts[v]!++;
    suitCounts[s]!++;
    suitMasks[s]! |= 1 << v;
    mask |= 1 << v;
  }
  const flushSuit = suitCounts.findIndex((c) => c >= 5);
  if (flushSuit >= 0) {
    const high = straightHigh(suitMasks[flushSuit]!);
    if (high) return score('StraightFlush', [high]);
  }
  let quads = 0;
  const trips: number[] = [];
  const pairs: number[] = [];
  const singles: number[] = [];
  for (let rank = 14; rank >= 2; rank--) {
    const c = counts[rank]!;
    if (c === 4) quads = rank;
    else if (c === 3) trips.push(rank);
    else if (c === 2) pairs.push(rank);
    else if (c === 1) singles.push(rank);
  }
  if (quads) {
    let kicker = 0;
    for (let r = 14; r >= 2; r--) if (r !== quads && counts[r]! > 0) {
      kicker = r;
      break;
    }
    return score('FourOfAKind', [quads, kicker]);
  }
  if (trips.length && (trips.length >= 2 || pairs.length)) {
    // Con dos tríos, el segundo hace de pareja.
    return score('FullHouse', [trips[0]!, Math.max(trips[1] ?? 0, pairs[0] ?? 0)]);
  }
  if (flushSuit >= 0) {
    const ranks: number[] = [];
    for (let r = 14; r >= 2 && ranks.length < 5; r--) if (suitMasks[flushSuit]! & (1 << r)) ranks.push(r);
    return score('Flush', ranks);
  }
  const straight = straightHigh(mask);
  if (straight) return score('Straight', [straight]);
  if (trips.length) return score('ThreeOfAKind', [trips[0]!, ...singles.slice(0, 2)]);
  if (pairs.length >= 2) {
    // La tercera pareja, si la hay, puede ser el mejor desempate.
    const kicker = Math.max(...pairs.slice(2), ...singles, 0);
    return score('TwoPair', [pairs[0]!, pairs[1]!, kicker]);
  }
  if (pairs.length === 1) return score('OnePair', [pairs[0]!, ...singles.slice(0, 3)]);
  return score('HighCard', singles.slice(0, 5));
}

function straightHigh(mask: number): number {
  const withLowAce = mask & (1 << 14) ? mask | (1 << 1) : mask;
  for (let high = 14; high >= 5; high--) {
    const run = 0b11111 << (high - 4);
    if ((withLowAce & run) === run) return high;
  }
  return 0;
}

function score(category: HandCategory, ranks: number[]): number {
  let value = HAND_CATEGORIES.indexOf(category);
  for (let i = 0; i < 5; i++) value = value * 16 + (ranks[i] ?? 0);
  return value;
}

export const categoryOf = (value: number): HandCategory => HAND_CATEGORIES[Math.floor(value / 16 ** 5)]!;

// ── Mesa ───────────────────────────────────────────────────────────────────────────────────────

export interface PokerRules {
  smallBlind: number;
  bigBlind: number;
  minBuyIn: number;
  maxBuyIn: number;
  chipUnit: number;
}

export const POKER_TABLES: PokerRules[] = [
  { smallBlind: 10, bigBlind: 20, minBuyIn: 400, maxBuyIn: 2_000, chipUnit: 10 },
  { smallBlind: 50, bigBlind: 100, minBuyIn: 2_000, maxBuyIn: 10_000, chipUnit: 10 },
];

export type Street = 'preflop' | 'flop' | 'turn' | 'river';
export type PokerPhase = 'waiting' | 'betting' | 'handOver';
export type SeatAction = 'smallBlind' | 'bigBlind' | 'fold' | 'check' | 'call' | 'bet' | 'raise' | 'allIn';

export const ACTION_LABEL: Record<SeatAction, string> = {
  smallBlind: 'Ciega pequeña',
  bigBlind: 'Ciega grande',
  fold: 'Se retira',
  check: 'Pasa',
  call: 'Iguala',
  bet: 'Apuesta',
  raise: 'Sube',
  allIn: 'All-in',
};

export type BotStyle = 'tight' | 'balanced' | 'loose' | 'aggressive';
const STYLE: Record<BotStyle, { looseness: number; aggression: number }> = {
  tight: { looseness: 0, aggression: 0.35 },
  balanced: { looseness: 0.06, aggression: 0.5 },
  loose: { looseness: 0.12, aggression: 0.45 },
  aggressive: { looseness: 0.08, aggression: 0.8 },
};

export interface Seat {
  name: string;
  isHuman: boolean;
  stack: number;
  style: BotStyle;
  hole: Card[];
  /** Apostado en la calle actual. */
  bet: number;
  /** Aportado al bote en toda la mano. */
  committed: number;
  folded: boolean;
  acted: boolean;
  lastAction: SeatAction | null;
}

export const inHand = (s: Seat) => s.hole.length > 0 && !s.folded;
export const isAllIn = (s: Seat) => inHand(s) && s.stack === 0;
const canAct = (s: Seat) => inHand(s) && s.stack > 0;

export interface PotAward {
  amount: number;
  winners: number[];
  category: HandCategory | null;
}

export interface PokerState {
  rules: PokerRules;
  seats: Seat[];
  button: number;
  deck: Card[];
  board: Card[];
  street: Street;
  phase: PokerPhase;
  toAct: number | null;
  currentBet: number;
  minRaise: number;
  handNumber: number;
  awards: PotAward[];
  showdown: boolean;
}

export const pot = (s: PokerState) => s.seats.reduce((sum, seat) => sum + seat.committed, 0);

export interface LegalActions {
  canCheck: boolean;
  callAmount: number;
  canRaise: boolean;
  minRaiseTo: number;
  maxRaiseTo: number;
}

export function legalActions(state: PokerState, seatIndex: number): LegalActions | null {
  if (state.phase !== 'betting' || state.toAct !== seatIndex) return null;
  const seat = state.seats[seatIndex]!;
  const toCall = Math.min(state.currentBet - seat.bet, seat.stack);
  const maxTo = seat.bet + seat.stack;
  const minTo = Math.min(state.currentBet + Math.max(state.minRaise, state.rules.bigBlind), maxTo);
  return { canCheck: toCall === 0, callAmount: toCall, canRaise: maxTo > state.currentBet, minRaiseTo: minTo, maxRaiseTo: maxTo };
}

export type PokerAction = { type: 'fold' } | { type: 'check' } | { type: 'call' } | { type: 'raiseTo'; amount: number };

const nextIndex = (size: number, after: number, predicate: (i: number) => boolean): number | null => {
  for (let k = 1; k <= size; k++) {
    const i = (after + k) % size;
    if (predicate(i)) return i;
  }
  return null;
};

const update = (state: PokerState, index: number, change: (s: Seat) => Seat): PokerState => ({
  ...state,
  seats: state.seats.map((s, i) => (i === index ? change(s) : s)),
});

function payChips(state: PokerState, index: number, amount: number, kind: SeatAction): PokerState {
  return update(state, index, (seat) => {
    const paid = Math.min(amount, seat.stack);
    const stack = seat.stack - paid;
    return { ...seat, stack, bet: seat.bet + paid, committed: seat.committed + paid, lastAction: stack === 0 ? 'allIn' : kind };
  });
}

export function startHand(state: PokerState, random: RandomInt = secureRandom): PokerState | null {
  const active = state.seats.map((s, i) => (s.stack > 0 ? i : -1)).filter((i) => i >= 0);
  if (active.length < 2) return null;
  const n = state.seats.length;
  const button = state.handNumber === 0 && state.seats[state.button]!.stack > 0 ? state.button : nextIndex(n, state.button, (i) => state.seats[i]!.stack > 0)!;
  const order = Array.from({ length: n }, (_, k) => (button + k + 1) % n).filter((i) => state.seats[i]!.stack > 0);
  const deck = shuffle(standardDeck(), random);
  let cursor = 0;
  // Una carta a cada uno, empezando a la izquierda del botón, y luego otra.
  const holes = new Map<number, Card[]>();
  for (let round = 0; round < 2; round++) for (const seat of order) holes.set(seat, [...(holes.get(seat) ?? []), deck[cursor++]!]);
  const seats = state.seats.map((seat, i) => ({ ...seat, hole: holes.get(i) ?? [], bet: 0, committed: 0, folded: false, acted: false, lastAction: null }));
  const headsUp = active.length === 2;
  const smallBlindSeat = headsUp ? button : order[0]!;
  const bigBlindSeat = headsUp ? order.find((i) => i !== button)! : order[1]!;
  let next: PokerState = {
    ...state,
    seats,
    button,
    deck: deck.slice(cursor),
    board: [],
    street: 'preflop',
    phase: 'betting',
    toAct: null,
    currentBet: state.rules.bigBlind,
    minRaise: state.rules.bigBlind,
    handNumber: state.handNumber + 1,
    awards: [],
    showdown: false,
  };
  next = payChips(next, smallBlindSeat, state.rules.smallBlind, 'smallBlind');
  next = payChips(next, bigBlindSeat, state.rules.bigBlind, 'bigBlind');
  return continueFrom(next, bigBlindSeat);
}

export function applyPoker(state: PokerState, seatIndex: number, action: PokerAction): PokerState | null {
  const legal = legalActions(state, seatIndex);
  if (!legal) return null;
  const seat = state.seats[seatIndex]!;
  let next: PokerState;
  switch (action.type) {
    case 'fold':
      next = update(state, seatIndex, (s) => ({ ...s, folded: true, acted: true, lastAction: 'fold' }));
      break;
    case 'check':
      if (!legal.canCheck) return null;
      next = update(state, seatIndex, (s) => ({ ...s, acted: true, lastAction: 'check' }));
      break;
    case 'call':
      next =
        legal.callAmount === 0
          ? update(state, seatIndex, (s) => ({ ...s, acted: true, lastAction: 'check' }))
          : update(payChips(state, seatIndex, legal.callAmount, 'call'), seatIndex, (s) => ({ ...s, acted: true }));
      break;
    case 'raiseTo': {
      const to = action.amount;
      const allIn = to === legal.maxRaiseTo;
      const valid = legal.canRaise && to > state.currentBet && to <= legal.maxRaiseTo && (allIn || (to >= legal.minRaiseTo && to % state.rules.chipUnit === 0));
      if (!valid) return null;
      const increase = to - state.currentBet;
      const raised = payChips(state, seatIndex, to - seat.bet, state.currentBet === 0 ? 'bet' : 'raise');
      // Una subida reabre la acción: los demás tienen que volver a hablar.
      next = {
        ...raised,
        currentBet: to,
        minRaise: increase >= state.minRaise ? increase : state.minRaise,
        seats: raised.seats.map((s, i) => ({ ...s, acted: i === seatIndex })),
      };
      break;
    }
  }
  const remaining = next.seats.map((s, i) => (inHand(s) ? i : -1)).filter((i) => i >= 0);
  if (remaining.length === 1) return awardUncontested(next, remaining[0]!);
  return continueFrom(next, seatIndex);
}

function continueFrom(state: PokerState, seatIndex: number): PokerState {
  const next = nextToAct(state, seatIndex);
  return next !== null ? { ...state, toAct: next, phase: 'betting' } : endStreet(state);
}

function nextToAct(state: PokerState, after: number): number | null {
  const actors = state.seats.filter(canAct).length;
  return nextIndex(state.seats.length, after, (i) => {
    const seat = state.seats[i]!;
    // Si solo queda uno con fichas y ya iguala, no hay nadie con quien apostar.
    return canAct(seat) && (!seat.acted || seat.bet < state.currentBet) && !(actors === 1 && seat.bet >= state.currentBet);
  });
}

function endStreet(input: PokerState): PokerState {
  let state: PokerState = {
    ...input,
    seats: input.seats.map((s) => ({ ...s, bet: 0, acted: false, lastAction: isAllIn(s) || s.folded ? s.lastAction : null })),
    currentBet: 0,
    minRaise: input.rules.bigBlind,
    toAct: null,
  };
  for (;;) {
    if (state.street === 'river') return showdown(state);
    state = dealNextStreet(state);
    // Con un solo jugador (o ninguno) que pueda apostar, se reparte el resto sin apuestas.
    if (state.seats.filter(canAct).length >= 2) {
      const first = nextIndex(state.seats.length, state.button, (i) => canAct(state.seats[i]!));
      return { ...state, toAct: first, phase: 'betting' };
    }
  }
}

function dealNextStreet(state: PokerState): PokerState {
  const [street, count]: [Street, number] = state.street === 'preflop' ? ['flop', 3] : state.street === 'flop' ? ['turn', 1] : ['river', 1];
  return { ...state, street, board: [...state.board, ...state.deck.slice(0, count)], deck: state.deck.slice(count) };
}

function awardUncontested(state: PokerState, winner: number): PokerState {
  const amount = pot(state);
  return {
    ...state,
    seats: state.seats.map((s, i) => ({ ...s, bet: 0, stack: i === winner ? s.stack + amount : s.stack })),
    phase: 'handOver',
    toAct: null,
    awards: [{ amount, winners: [winner], category: null }],
    showdown: false,
  };
}

function showdown(state: PokerState): PokerState {
  const values = new Map<number, number>();
  state.seats.forEach((s, i) => inHand(s) && values.set(i, evaluateHand([...s.hole, ...state.board])));
  const stacks = state.seats.map((s) => s.stack);
  const awards = sidePots(state).map(([amount, eligible]) => {
    const best = Math.max(...eligible.map((i) => values.get(i)!));
    const winners = clockwiseFromButton(state, eligible.filter((i) => values.get(i) === best));
    splitPot(state, amount, winners).forEach(([seat, share]) => (stacks[seat]! += share));
    return { amount, winners, category: categoryOf(best) };
  });
  return { ...state, seats: state.seats.map((s, i) => ({ ...s, stack: stacks[i]!, bet: 0 })), phase: 'handOver', toAct: null, awards, showdown: true };
}

function sidePots(state: PokerState): [number, number[]][] {
  const levels = [...new Set(state.seats.map((s) => s.committed).filter((c) => c > 0))].sort((a, b) => a - b);
  const pots: [number, number[]][] = [];
  let previous = 0;
  for (const level of levels) {
    const amount = state.seats.reduce((sum, s) => sum + Math.min(s.committed, level) - Math.min(s.committed, previous), 0);
    const eligible = state.seats.map((s, i) => (inHand(s) && s.committed >= level ? i : -1)).filter((i) => i >= 0);
    previous = level;
    if (amount === 0) continue;
    const last = pots[pots.length - 1];
    // Nadie que siga en la mano llega a este nivel: se suma al bote anterior.
    if (eligible.length === 0 || (last && last[1].join() === eligible.join())) {
      if (last) last[0] += amount;
    } else {
      pots.push([amount, eligible]);
    }
  }
  return pots;
}

function splitPot(state: PokerState, amount: number, winners: number[]): [number, number][] {
  const unit = state.rules.chipUnit;
  const units = Math.floor(amount / unit);
  const base = Math.floor(units / winners.length);
  const remainder = units % winners.length;
  return winners.map((seat, order) => [seat, (base + (order < remainder ? 1 : 0)) * unit]);
}

const clockwiseFromButton = (state: PokerState, indices: number[]) =>
  [...indices].sort((a, b) => ((a - state.button - 1 + state.seats.length) % state.seats.length) - ((b - state.button - 1 + state.seats.length) % state.seats.length));

// ── Bots ───────────────────────────────────────────────────────────────────────────────────────

export const SIMULATIONS = 160;

/** Equidad estimada por Monte Carlo frente a [opponents] manos aleatorias. */
export function equity(hole: Card[], board: Card[], opponents: number, random: RandomInt = secureRandom, simulations = SIMULATIONS): number {
  if (opponents === 0) return 1;
  const known = new Set([...hole, ...board].map((c) => c.rank + c.suit));
  const pool = standardDeck().filter((c) => !known.has(c.rank + c.suit));
  const needed = 5 - board.length + opponents * 2;
  let share = 0;
  for (let sim = 0; sim < simulations; sim++) {
    // Fisher–Yates parcial: solo se barajan las cartas que hacen falta.
    for (let i = 0; i < needed; i++) {
      const j = i + random(pool.length - i);
      [pool[i], pool[j]] = [pool[j]!, pool[i]!];
    }
    const fullBoard = [...board, ...pool.slice(0, 5 - board.length)];
    const mine = evaluateHand([...hole, ...fullBoard]);
    let best = true;
    let ties = 0;
    let offset = 5 - board.length;
    for (let o = 0; o < opponents; o++) {
      const theirs = evaluateHand([pool[offset]!, pool[offset + 1]!, ...fullBoard]);
      offset += 2;
      if (theirs > mine) {
        best = false;
        break;
      }
      if (theirs === mine) ties++;
    }
    if (best) share += 1 / (ties + 1);
  }
  return share / simulations;
}

export function botDecide(state: PokerState, seatIndex: number, random: RandomInt = secureRandom): PokerAction {
  const legal = legalActions(state, seatIndex);
  if (!legal) return { type: 'fold' };
  const seat = state.seats[seatIndex]!;
  const style = STYLE[seat.style];
  const opponents = state.seats.filter((s, i) => i !== seatIndex && inHand(s)).length;
  const eq = equity(seat.hole, state.board, opponents, random);
  // Reparto justo con N rivales: 1/(N+1). Los umbrales se escalan sobre él.
  const fair = 1 / (opponents + 1);
  const strength = eq + style.looseness;
  const valueThreshold = fair + (1 - fair) * 0.22;
  const raiseThreshold = fair + (1 - fair) * 0.38;
  const roll = random(1000) / 1000;
  const currentPot = pot(state);
  if (legal.callAmount === 0) {
    if (legal.canRaise && strength >= valueThreshold && roll < 0.35 + style.aggression * 0.6) return raise(state, legal, currentPot, strength >= raiseThreshold ? 0.75 : 0.5, random);
    // Farol de vez en cuando, más en los bots agresivos.
    if (legal.canRaise && roll < style.aggression * 0.1) return raise(state, legal, currentPot, 0.5, random);
    return { type: 'check' };
  }
  const potOdds = legal.callAmount / (currentPot + legal.callAmount);
  if (legal.canRaise && strength >= raiseThreshold && roll < style.aggression) return raise(state, legal, currentPot, 0.8, random);
  if (strength >= potOdds + 0.04) return { type: 'call' };
  // Pagar algo barato de vez en cuando para no ser previsible.
  if (legal.callAmount <= state.rules.bigBlind * 2 && roll < 0.15 + style.looseness) return { type: 'call' };
  return { type: 'fold' };
}

function raise(state: PokerState, legal: LegalActions, currentPot: number, fraction: number, random: RandomInt): PokerAction {
  const unit = state.rules.chipUnit;
  const jitter = 0.85 + random(31) / 100;
  const wanted = state.currentBet + Math.floor(currentPot * fraction * jitter);
  const to = Math.min(Math.max(Math.floor(wanted / unit) * unit, legal.minRaiseTo), legal.maxRaiseTo);
  return to >= legal.maxRaiseTo * 0.7 ? { type: 'raiseTo', amount: legal.maxRaiseTo } : { type: 'raiseTo', amount: to };
}

const BOT_NAMES = ['Valentina', 'Bruno', 'Carmen', 'Hugo', 'Lucía', 'Mateo', 'Elena', 'Diego', 'Sofía', 'Álvaro', 'Irene', 'Marcos'];
const STYLES: BotStyle[] = ['tight', 'balanced', 'loose', 'aggressive'];
export const SEATS = 6;

function newBot(rules: PokerRules, name: string, random: RandomInt): Seat {
  const steps = (rules.maxBuyIn - rules.minBuyIn) / rules.chipUnit;
  return {
    name,
    isHuman: false,
    stack: rules.minBuyIn + random(steps + 1) * rules.chipUnit,
    style: STYLES[random(STYLES.length)]!,
    hole: [],
    bet: 0,
    committed: 0,
    folded: false,
    acted: false,
    lastAction: null,
  };
}

export function createTable(rules: PokerRules, playerName: string, buyIn: number, random: RandomInt = secureRandom): PokerState {
  const names = shuffle(BOT_NAMES, random);
  const seats = Array.from({ length: SEATS }, (_, i) =>
    i === 0
      ? { name: playerName, isHuman: true, stack: buyIn, style: 'balanced' as BotStyle, hole: [], bet: 0, committed: 0, folded: false, acted: false, lastAction: null }
      : newBot(rules, names[i - 1]!, random),
  );
  return { rules, seats, button: random(SEATS), deck: [], board: [], street: 'preflop', phase: 'waiting', toAct: null, currentBet: 0, minRaise: 0, handNumber: 0, awards: [], showdown: false };
}

/** Mesa nueva: sustituye a los bots sin fichas por otros (cuando el jugador ha eliminado a todos). */
/** Ningún bot tiene fichas: el jugador se ha quedado con todo lo de la mesa. */
export const allBotsOut = (state: PokerState) => state.seats.every((s) => s.isHuman || s.stack === 0);

export function refillBots(state: PokerState, random: RandomInt = secureRandom): PokerState {
  const used = new Set(state.seats.map((s) => s.name));
  const available = shuffle(BOT_NAMES.filter((n) => !used.has(n)), random);
  return {
    ...state,
    seats: state.seats.map((seat) => (!seat.isHuman && seat.stack < state.rules.bigBlind ? newBot(state.rules, available.shift() ?? seat.name, random) : seat)),
  };
}
