import { draw, needsShuffle, secureRandom, shuffledShoe, type Card, type RandomInt, type Shoe } from './cards';

/**
 * Reglas de la mesa: zapato de 6 barajas con penetración del 75 %, el crupier se planta en todos
 * los 17 (también blandos) y mira si tiene blackjack, el blackjack paga 3:2, se dobla con las dos
 * primeras cartas (también tras separar) y se separa hasta 4 manos; los ases separados reciben
 * una sola carta. Sin seguro ni rendición. Con la apuesta máxima, doblando 4 manos se arriesgan
 * 80.000 fichas: dentro del máximo por ronda de la economía (100.000).
 */
export const BLACKJACK_RULES = {
  decks: 6,
  penetration: 0.75,
  minimumBet: 10,
  maximumBet: 10_000,
  /** De 10 en 10: el pago 3:2 siempre es entero. */
  betStep: 10,
  maxHands: 4,
} as const;

export interface HandValue {
  total: number;
  /** Un as cuenta como 11 sin pasarse. */
  soft: boolean;
}

export function points(card: Card): number {
  if (card.rank === 'A') return 1;
  if (card.rank === 'J' || card.rank === 'Q' || card.rank === 'K') return 10;
  return Number(card.rank);
}

export function handValue(cards: Card[]): HandValue {
  const hard = cards.reduce((sum, c) => sum + points(c), 0);
  const hasAce = cards.some((c) => c.rank === 'A');
  return hasAce && hard + 10 <= 21 ? { total: hard + 10, soft: true } : { total: hard, soft: false };
}

export interface PlayerHand {
  cards: Card[];
  /** Fichas en juego en esta mano (el doble si se ha doblado). */
  stake: number;
  doubled: boolean;
  /** Viene de separar: un 21 con dos cartas no es blackjack. */
  fromSplit: boolean;
  finished: boolean;
}

export const isBust = (hand: PlayerHand) => handValue(hand.cards).total > 21;
export const isBlackjack = (hand: PlayerHand) => !hand.fromSplit && hand.cards.length === 2 && handValue(hand.cards).total === 21;

export type HandOutcome = 'blackjack' | 'win' | 'push' | 'loss';

/** [payout] incluye la apuesta devuelta (0 si se pierde). */
export interface HandResult {
  outcome: HandOutcome;
  payout: number;
}

export type BlackjackPhase = 'betting' | 'playerTurn' | 'roundOver';
export type BlackjackMove = 'hit' | 'stand' | 'double' | 'split';

/** Estado completo de la mesa: serializable, se guarda tras cada acción y se reanuda al volver. */
export interface BlackjackState {
  shoe: Shoe | null;
  phase: BlackjackPhase;
  hands: PlayerHand[];
  activeHand: number;
  dealer: Card[];
  holeCardRevealed: boolean;
  results: HandResult[];
}

export const initialBlackjack = (): BlackjackState => ({
  shoe: null,
  phase: 'betting',
  hands: [],
  activeHand: 0,
  dealer: [],
  holeCardRevealed: false,
  results: [],
});

export const totalStake = (s: BlackjackState) => s.hands.reduce((sum, h) => sum + h.stake, 0);
export const totalPayout = (s: BlackjackState) => s.results.reduce((sum, r) => sum + r.payout, 0);

/** Movimientos permitidos ahora sobre la mano activa. */
export function availableMoves(state: BlackjackState): Set<BlackjackMove> {
  const moves = new Set<BlackjackMove>();
  if (state.phase !== 'playerTurn') return moves;
  const hand = state.hands[state.activeHand];
  if (!hand || hand.finished) return moves;
  moves.add('hit');
  moves.add('stand');
  if (hand.cards.length === 2) moves.add('double');
  if (canSplit(state, hand)) moves.add('split');
  return moves;
}

function canSplit(state: BlackjackState, hand: PlayerHand): boolean {
  if (hand.cards.length !== 2) return false;
  const [first, second] = hand.cards as [Card, Card];
  return state.hands.length < BLACKJACK_RULES.maxHands && points(first) === points(second) && !(hand.fromSplit && first.rank === 'A');
}

export type BlackjackAction = { type: 'deal'; bet: number } | { type: 'hit' } | { type: 'stand' } | { type: 'double' } | { type: 'split' };

/** Fichas que hay que apostar antes de aplicar la acción (0 si no requiere apuesta). */
export function stakeRequiredFor(state: BlackjackState, action: BlackjackAction): number {
  if (action.type === 'deal') return action.bet;
  if (action.type === 'double' || action.type === 'split') return state.hands[state.activeHand]?.stake ?? 0;
  return 0;
}

/** Lo que ha pasado, en orden: dirige las animaciones y los sonidos. */
export type BlackjackEvent =
  | { type: 'shoeShuffled' }
  | { type: 'cardToPlayer'; hand: number; card: Card }
  | { type: 'cardToDealer'; card: Card; faceDown: boolean }
  | { type: 'holeCardRevealed' }
  | { type: 'handSplit'; hand: number }
  | { type: 'handDoubled'; hand: number }
  | { type: 'handBusted'; hand: number }
  | { type: 'dealerBusted' }
  | { type: 'handSettled'; hand: number; result: HandResult };

export type BlackjackStep = { ok: true; state: BlackjackState; events: BlackjackEvent[] } | { ok: false; error: 'invalidBet' | 'notAllowed' };

export function validateBet(bet: number): boolean {
  return bet >= BLACKJACK_RULES.minimumBet && bet <= BLACKJACK_RULES.maximumBet && bet % BLACKJACK_RULES.betStep === 0;
}

/** Motor puro `(estado, acción) → (estado nuevo, eventos)`. El azar solo entra al barajar. */
export function applyBlackjack(state: BlackjackState, action: BlackjackAction, random: RandomInt = secureRandom): BlackjackStep {
  if (action.type === 'deal') return deal(state, action.bet, random);
  const move = action.type;
  if (!availableMoves(state).has(move)) return { ok: false, error: 'notAllowed' };
  const table = new Table(state);
  if (move === 'hit') hit(table);
  else if (move === 'stand') finishActive(table);
  else if (move === 'double') double(table);
  else split(table);
  return table.step();
}

function deal(state: BlackjackState, bet: number, random: RandomInt): BlackjackStep {
  if (state.phase === 'playerTurn') return { ok: false, error: 'notAllowed' };
  if (!validateBet(bet)) return { ok: false, error: 'invalidBet' };
  const table = new Table({ ...initialBlackjack(), shoe: state.shoe, phase: 'playerTurn' });
  if (!state.shoe || needsShuffle(state.shoe)) {
    table.state = { ...table.state, shoe: shuffledShoe(BLACKJACK_RULES.decks, BLACKJACK_RULES.penetration, random) };
    table.events.push({ type: 'shoeShuffled' });
  }
  // Orden real: jugador, crupier (vista), jugador, crupier (boca abajo).
  const first = table.draw();
  table.events.push({ type: 'cardToPlayer', hand: 0, card: first });
  const up = table.draw();
  table.events.push({ type: 'cardToDealer', card: up, faceDown: false });
  const second = table.draw();
  table.events.push({ type: 'cardToPlayer', hand: 0, card: second });
  const hole = table.draw();
  table.events.push({ type: 'cardToDealer', card: hole, faceDown: true });
  table.state = {
    ...table.state,
    hands: [{ cards: [first, second], stake: bet, doubled: false, fromSplit: false, finished: false }],
    dealer: [up, hole],
  };
  // El crupier mira si tiene blackjack: con él, o con el del jugador, la ronda termina ya.
  const dealerBlackjack = handValue(table.state.dealer).total === 21;
  const playerBlackjack = isBlackjack(table.state.hands[0]!);
  if (dealerBlackjack || playerBlackjack) {
    table.reveal();
    table.settle(dealerBlackjack);
  }
  return table.step();
}

function hit(table: Table) {
  const index = table.state.activeHand;
  table.giveCard(index);
  const hand = table.state.hands[index]!;
  if (isBust(hand)) {
    table.events.push({ type: 'handBusted', hand: index });
    finishActive(table);
  } else if (handValue(hand.cards).total === 21) {
    // Con 21 no hay nada que mejorar: se planta sola.
    finishActive(table);
  }
}

function double(table: Table) {
  const index = table.state.activeHand;
  table.updateHand(index, (h) => ({ ...h, stake: h.stake * 2, doubled: true }));
  table.events.push({ type: 'handDoubled', hand: index });
  table.giveCard(index);
  if (isBust(table.state.hands[index]!)) table.events.push({ type: 'handBusted', hand: index });
  finishActive(table);
}

function split(table: Table) {
  const index = table.state.activeHand;
  const hand = table.state.hands[index]!;
  const [first, second] = hand.cards as [Card, Card];
  const hands = [...table.state.hands];
  hands[index] = { cards: [first], stake: hand.stake, doubled: false, fromSplit: true, finished: false };
  hands.splice(index + 1, 0, { cards: [second], stake: hand.stake, doubled: false, fromSplit: true, finished: false });
  table.state = { ...table.state, hands };
  table.events.push({ type: 'handSplit', hand: index });
  table.giveCard(index);
  table.giveCard(index + 1);
  if (first.rank === 'A') {
    // Ases separados: una sola carta cada uno y se plantan.
    table.updateHand(index, (h) => ({ ...h, finished: true }));
    table.updateHand(index + 1, (h) => ({ ...h, finished: true }));
    advance(table);
  } else {
    if (handValue(table.state.hands[index + 1]!.cards).total === 21) table.updateHand(index + 1, (h) => ({ ...h, finished: true }));
    if (handValue(table.state.hands[index]!.cards).total === 21) finishActive(table);
  }
}

function finishActive(table: Table) {
  table.updateHand(table.state.activeHand, (h) => ({ ...h, finished: true }));
  advance(table);
}

function advance(table: Table) {
  const { hands, activeHand } = table.state;
  let next = hands.findIndex((h, i) => i > activeHand && !h.finished);
  if (next < 0) next = hands.findIndex((h) => !h.finished);
  if (next >= 0) table.state = { ...table.state, activeHand: next };
  else dealerPlays(table);
}

function dealerPlays(table: Table) {
  table.reveal();
  // Si todas las manos se han pasado, el crupier no necesita robar.
  if (table.state.hands.some((h) => !isBust(h))) {
    // Se planta en cualquier 17, también blando.
    while (handValue(table.state.dealer).total < 17) {
      const card = table.draw();
      table.state = { ...table.state, dealer: [...table.state.dealer, card] };
      table.events.push({ type: 'cardToDealer', card, faceDown: false });
    }
    if (handValue(table.state.dealer).total > 21) table.events.push({ type: 'dealerBusted' });
  }
  table.settle(false);
}

function resultOf(hand: PlayerHand, dealerValue: number, dealerBlackjack: boolean): HandResult {
  const stake = hand.stake;
  const total = handValue(hand.cards).total;
  if (isBust(hand)) return { outcome: 'loss', payout: 0 };
  if (isBlackjack(hand) && dealerBlackjack) return { outcome: 'push', payout: stake };
  if (isBlackjack(hand)) return { outcome: 'blackjack', payout: stake + (stake * 3) / 2 };
  if (dealerBlackjack) return { outcome: 'loss', payout: 0 };
  if (dealerValue > 21 || total > dealerValue) return { outcome: 'win', payout: stake * 2 };
  if (total === dealerValue) return { outcome: 'push', payout: stake };
  return { outcome: 'loss', payout: 0 };
}

/** Estado en construcción durante una acción, con sus eventos en orden. */
class Table {
  events: BlackjackEvent[] = [];
  constructor(public state: BlackjackState) {}

  draw(): Card {
    if (!this.state.shoe) throw new Error('Mesa sin zapato');
    const [card, shoe] = draw(this.state.shoe);
    this.state = { ...this.state, shoe };
    return card;
  }

  giveCard(index: number) {
    const card = this.draw();
    this.updateHand(index, (h) => ({ ...h, cards: [...h.cards, card] }));
    this.events.push({ type: 'cardToPlayer', hand: index, card });
  }

  updateHand(index: number, transform: (h: PlayerHand) => PlayerHand) {
    const hands = [...this.state.hands];
    hands[index] = transform(hands[index]!);
    this.state = { ...this.state, hands };
  }

  reveal() {
    if (!this.state.holeCardRevealed) {
      this.state = { ...this.state, holeCardRevealed: true };
      this.events.push({ type: 'holeCardRevealed' });
    }
  }

  settle(dealerBlackjack: boolean) {
    const dealerValue = handValue(this.state.dealer).total;
    const results = this.state.hands.map((h) => resultOf(h, dealerValue, dealerBlackjack));
    this.state = { ...this.state, phase: 'roundOver', hands: this.state.hands.map((h) => ({ ...h, finished: true })), results };
    results.forEach((result, hand) => this.events.push({ type: 'handSettled', hand, result }));
  }

  step(): BlackjackStep {
    return { ok: true, state: this.state, events: this.events };
  }
}
