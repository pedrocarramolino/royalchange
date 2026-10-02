import { describe, expect, it } from 'vitest';
import type { Card, Rank, SuitName } from './cards';
import { shuffle, standardDeck } from './cards';
import { applyBlackjack, availableMoves, handValue, initialBlackjack, type BlackjackState } from './blackjack';
import { betNumbers, roulettePayout, settleRoulette, validateRoulette } from './roulette';
import { dicePayout, settleDice, validateDice } from './dice';
import { evaluateLine, LINES, pay, PAYTABLE, settleSlots, STRIPS, SYMBOLS, type SlotSymbol } from './slots';
import { applyPoker, categoryOf, createTable, evaluateHand, legalActions, pot, startHand, POKER_TABLES, type PokerState } from './poker';

const c = (code: string): Card => {
  const suit: Record<string, SuitName> = { s: 'spades', h: 'hearts', d: 'diamonds', c: 'clubs' };
  return { rank: code.slice(0, -1) as Rank, suit: suit[code.slice(-1)]! };
};

/** Zapato preparado: las cartas salen en este orden. */
function stacked(...codes: string[]): BlackjackState {
  const cards = codes.map(c);
  while (cards.length < 60) cards.push(c('2c'));
  return { ...initialBlackjack(), shoe: { cards, cutIndex: cards.length, position: 0 } };
}

describe('blackjack', () => {
  it('valor de la mano con ases blandos', () => {
    expect(handValue([c('As'), c('6h')])).toEqual({ total: 17, soft: true });
    expect(handValue([c('As'), c('6h'), c('9d')])).toEqual({ total: 16, soft: false });
    expect(handValue([c('As'), c('As'), c('9d')])).toEqual({ total: 21, soft: true });
  });

  it('blackjack natural paga 3:2 y cierra la ronda', () => {
    // Jugador A, K; crupier 9, 7.
    const step = applyBlackjack(stacked('As', '9h', 'Kd', '7c'), { type: 'deal', bet: 100 });
    if (!step.ok) throw new Error();
    expect(step.state.phase).toBe('roundOver');
    expect(step.state.results).toEqual([{ outcome: 'blackjack', payout: 250 }]);
  });

  it('el crupier roba hasta 17 y se planta en 17 blando', () => {
    // Jugador 10, 8 (18); crupier 6, A (17 blando): se planta.
    const deal = applyBlackjack(stacked('10s', '6h', '8d', 'Ac'), { type: 'deal', bet: 50 });
    if (!deal.ok) throw new Error();
    const stand = applyBlackjack(deal.state, { type: 'stand' });
    if (!stand.ok) throw new Error();
    expect(stand.state.dealer).toHaveLength(2);
    expect(stand.state.results[0]).toEqual({ outcome: 'win', payout: 100 });
  });

  it('separar ases da una sola carta a cada mano', () => {
    const deal = applyBlackjack(stacked('As', '9h', 'Ad', '7c', 'Kh', '5s'), { type: 'deal', bet: 100 });
    if (!deal.ok) throw new Error();
    expect(availableMoves(deal.state).has('split')).toBe(true);
    const split = applyBlackjack(deal.state, { type: 'split' });
    if (!split.ok) throw new Error();
    expect(split.state.hands).toHaveLength(2);
    expect(split.state.phase).toBe('roundOver');
    // 21 tras separar no es blackjack: paga 1:1.
    expect(split.state.results[0]).toEqual({ outcome: 'win', payout: 200 });
  });

  it('doblar duplica la apuesta y reparte una carta', () => {
    const deal = applyBlackjack(stacked('5s', '9h', '6d', '7c', 'Kh', '2s'), { type: 'deal', bet: 100 });
    if (!deal.ok) throw new Error();
    const doubled = applyBlackjack(deal.state, { type: 'double' });
    if (!doubled.ok) throw new Error();
    expect(doubled.state.hands[0]!.stake).toBe(200);
    expect(doubled.state.hands[0]!.cards).toHaveLength(3);
  });

  it('rechaza apuestas fuera de la mesa', () => {
    expect(applyBlackjack(initialBlackjack(), { type: 'deal', bet: 15 })).toEqual({ ok: false, error: 'invalidBet' });
    expect(applyBlackjack(initialBlackjack(), { type: 'deal', bet: 20_000 })).toEqual({ ok: false, error: 'invalidBet' });
  });
});

describe('ruleta', () => {
  it('cada apuesta paga 36 entre los números que cubre', () => {
    expect(roulettePayout('n:17', 10, 17)).toBe(360);
    expect(roulettePayout('split:17-20', 10, 20)).toBe(180);
    expect(roulettePayout('corner:17', 10, 21)).toBe(90);
    expect(roulettePayout('street:6', 10, 18)).toBe(120);
    expect(roulettePayout('line:3', 10, 12)).toBe(60);
    expect(roulettePayout('dozen:2', 10, 13)).toBe(30);
    expect(roulettePayout('red', 10, 1)).toBe(20);
    expect(roulettePayout('red', 10, 0)).toBe(0);
  });

  it('valida las combinaciones de la mesa', () => {
    expect(betNumbers('split:3-4')).toEqual([]);
    expect(betNumbers('split:0-2')).toEqual([0, 2]);
    expect(betNumbers('corner:3')).toEqual([]);
    expect(validateRoulette([])).toBe('noBets');
    expect(validateRoulette([{ bet: 'n:5', stake: 15 }])).toBe('invalidBet');
    expect(validateRoulette([{ bet: 'n:5', stake: 30_000 }])).toBe('aboveTableMaximum');
  });

  it('liquida varias apuestas', () => {
    const spin = settleRoulette([{ bet: 'n:7', stake: 10 }, { bet: 'black', stake: 50 }], 7);
    expect(spin.totalStake).toBe(60);
    expect(spin.totalPayout).toBe(360);
  });
});

describe('dados', () => {
  it('pagos en décimas, siempre enteros con fichas de 10', () => {
    expect(dicePayout('seven', 10, { first: 3, second: 4 })).toBe(58);
    expect(dicePayout('low', 10, { first: 1, second: 2 })).toBe(23);
    expect(dicePayout('sum:2', 10, { first: 1, second: 1 })).toBe(350);
    expect(settleDice([{ bet: 'doubles', stake: 20 }], { first: 5, second: 5 }).totalPayout).toBe(116);
    expect(validateDice([{ bet: 'sum:7' as never, stake: 10 }])).toBe('invalidBet');
  });
});

describe('tragaperras', () => {
  it('RTP exacto del 97,27 %', () => {
    // Las filas de cada rodillo son uniformes e independientes entre rodillos: la esperanza de una
    // línea es la misma para las diez. Se suma sobre las 8^5 combinaciones de símbolos.
    const freq = STRIPS.map((s) => SYMBOLS.map((sym) => s.filter((x) => x === sym).length / s.length));
    let expected = 0;
    const combo: SlotSymbol[] = [];
    const walk = (reel: number, p: number) => {
      if (reel === 5) {
        const win = evaluateLine(combo);
        if (win) expected += p * pay(win[0], win[1]);
        return;
      }
      SYMBOLS.forEach((sym, i) => {
        const q = freq[reel]![i]!;
        if (q === 0) return;
        combo[reel] = sym;
        walk(reel + 1, p * q);
      });
    };
    walk(0, 1);
    // Apuesta total = 10 líneas × apuesta de línea; premio esperado = 10 × esperanza por línea.
    expect((expected * LINES.length) / LINES.length).toBeCloseTo(0.9727, 4);
  });

  it('el comodín completa líneas y paga su propia tabla', () => {
    expect(evaluateLine(['Wild', 'Wild', 'Seven', 'Seven', 'Cherry'])).toEqual(['Seven', 4]);
    expect(evaluateLine(['Wild', 'Wild', 'Wild', 'Cherry', 'Club'])).toEqual(['Wild', 3]);
    expect(PAYTABLE.Wild[3]).toBe(500);
    const spin = settleSlots([0, 0, 0, 0, 0], 2);
    expect(spin.totalBet).toBe(20);
  });
});

describe('póker', () => {
  it('clasifica las manos y desempata', () => {
    expect(categoryOf(evaluateHand(['As', 'Ks', 'Qs', 'Js', '10s', '2d', '3c'].map(c)))).toBe('StraightFlush');
    expect(categoryOf(evaluateHand(['As', '2d', '3c', '4h', '5s', 'Kd', 'Kc'].map(c)))).toBe('Straight');
    expect(categoryOf(evaluateHand(['9s', '9d', '9c', 'Kh', 'Ks', 'Kd', '2c'].map(c)))).toBe('FullHouse');
    const pairAces = evaluateHand(['As', 'Ad', '9c', '7h', '4s'].map(c));
    const pairKings = evaluateHand(['Ks', 'Kd', 'Qc', 'Jh', '9s'].map(c));
    expect(pairAces).toBeGreaterThan(pairKings);
  });

  it('cuenta bien las categorías de las 2.598.960 manos de 5 cartas', { timeout: 60_000 }, () => {
    const deck = standardDeck();
    const counts = new Map<string, number>();
    for (let a = 0; a < 48; a++)
      for (let b = a + 1; b < 49; b++)
        for (let d = b + 1; d < 50; d++)
          for (let e = d + 1; e < 51; e++)
            for (let f = e + 1; f < 52; f++) {
              const cat = categoryOf(evaluateHand([deck[a]!, deck[b]!, deck[d]!, deck[e]!, deck[f]!]));
              counts.set(cat, (counts.get(cat) ?? 0) + 1);
            }
    expect(counts.get('StraightFlush')).toBe(40);
    expect(counts.get('FourOfAKind')).toBe(624);
    expect(counts.get('FullHouse')).toBe(3_744);
    expect(counts.get('Flush')).toBe(5_108);
    expect(counts.get('Straight')).toBe(10_200);
    expect(counts.get('ThreeOfAKind')).toBe(54_912);
    expect(counts.get('TwoPair')).toBe(123_552);
    expect(counts.get('OnePair')).toBe(1_098_240);
    expect(counts.get('HighCard')).toBe(1_302_540);
  });

  it('una mano completa conserva las fichas de la mesa', () => {
    let seed = 7;
    const random = (bound: number) => {
      seed = (seed * 1_103_515_245 + 12_345) % 2_147_483_648;
      return seed % bound;
    };
    let state: PokerState = createTable(POKER_TABLES[0]!, 'Tú', 1_000, random);
    const total = state.seats.reduce((s, x) => s + x.stack, 0);
    for (let hand = 0; hand < 30; hand++) {
      const started = startHand(state, random);
      if (!started) break;
      state = started;
      let guard = 0;
      while (state.phase === 'betting' && guard++ < 200) {
        const seat = state.toAct!;
        const legal = legalActions(state, seat)!;
        const action = legal.canCheck ? { type: 'check' as const } : random(3) === 0 ? { type: 'fold' as const } : { type: 'call' as const };
        state = applyPoker(state, seat, action)!;
      }
      expect(state.phase).toBe('handOver');
      expect(state.seats.reduce((s, x) => s + x.stack, 0) + pot(state) - state.awards.reduce((s, a) => s + a.amount, 0)).toBe(total);
    }
  });

  it('barajar no pierde cartas', () => {
    expect(new Set(shuffle(standardDeck()).map((x) => x.rank + x.suit)).size).toBe(52);
  });
});
