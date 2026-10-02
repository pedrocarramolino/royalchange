import { describe, expect, it } from 'vitest';
import type { Card, Rank, SuitName } from './cards';
import { classifyVideoPoker, dealVideoPoker, drawVideoPoker, PAYTABLE, videoPokerPayout, winningCards } from './videopoker';

const S: Record<string, SuitName> = { p: 'spades', c: 'hearts', d: 'diamonds', t: 'clubs' };
/** "Ap Kp" → as de picas, rey de picas… (p picas, c corazones, d diamantes, t tréboles). */
const hand = (text: string): Card[] =>
  text.split(' ').map((code) => ({ rank: code.slice(0, -1) as Rank, suit: S[code.slice(-1)]! }));

describe('video póker', () => {
  it('reconoce cada jugada de la tabla de pagos', () => {
    expect(classifyVideoPoker(hand('10p Jp Qp Kp Ap'))).toBe('royalFlush');
    expect(classifyVideoPoker(hand('9c 10c Jc Qc Kc'))).toBe('straightFlush');
    expect(classifyVideoPoker(hand('Ad 2d 3d 4d 5d'))).toBe('straightFlush');
    expect(classifyVideoPoker(hand('7p 7c 7d 7t 2p'))).toBe('fourOfAKind');
    expect(classifyVideoPoker(hand('3p 3c 3d Kt Kp'))).toBe('fullHouse');
    expect(classifyVideoPoker(hand('2t 7t 9t Jt Kt'))).toBe('flush');
    expect(classifyVideoPoker(hand('Ap 2c 3d 4t 5p'))).toBe('straight');
    expect(classifyVideoPoker(hand('10p Jc Qd Kt Ap'))).toBe('straight');
    expect(classifyVideoPoker(hand('9p 9c 9d 2t 5p'))).toBe('threeOfAKind');
    expect(classifyVideoPoker(hand('4p 4c 8d 8t Ap'))).toBe('twoPair');
  });

  it('solo paga la pareja de jotas o más alta', () => {
    expect(classifyVideoPoker(hand('Jp Jc 2d 5t 9p'))).toBe('jacksOrBetter');
    expect(classifyVideoPoker(hand('Ap Ac 2d 5t 9p'))).toBe('jacksOrBetter');
    expect(classifyVideoPoker(hand('10p 10c 2d 5t 9p'))).toBeNull();
    expect(classifyVideoPoker(hand('Ap Kc 2d 5t 9p'))).toBeNull();
  });

  it('paga por ficha según la tabla 9/6 y la escalera real a 800', () => {
    expect(PAYTABLE.map((r) => r.multiplier)).toEqual([800, 50, 25, 9, 6, 4, 3, 2, 1]);
    expect(videoPokerPayout(100, 'fullHouse')).toBe(900);
    expect(videoPokerPayout(100, 'jacksOrBetter')).toBe(100);
    expect(videoPokerPayout(100, null)).toBe(0);
    // Nunca supera el techo global de 1.000 veces la apuesta.
    expect(videoPokerPayout(5_000, 'royalFlush')).toBeLessThanOrEqual(5_000 * 1_000);
  });

  it('el cambio guarda las cartas retenidas y repone el resto en orden', () => {
    const deal = dealVideoPoker();
    expect(deal.hand).toHaveLength(5);
    expect(deal.deck).toHaveLength(47);
    const final = drawVideoPoker(deal, [true, false, true, false, false]);
    expect(final[0]).toEqual(deal.hand[0]);
    expect(final[2]).toEqual(deal.hand[2]);
    expect(final[1]).toEqual(deal.deck[0]);
    expect(final[3]).toEqual(deal.deck[1]);
    expect(final[4]).toEqual(deal.deck[2]);
    // Ninguna carta repetida.
    expect(new Set([...final, ...deal.deck.slice(3)].map((c) => `${c.rank}${c.suit}`)).size).toBe(49);
  });

  it('resalta solo las cartas que forman la jugada', () => {
    expect(winningCards(hand('Jp Jc 2d 5t 9p'), 'jacksOrBetter')).toEqual([true, true, false, false, false]);
    expect(winningCards(hand('2t 7t 9t Jt Kt'), 'flush')).toEqual([true, true, true, true, true]);
    expect(winningCards(hand('Ap Kc 2d 5t 9p'), null)).toEqual([false, false, false, false, false]);
  });
});
