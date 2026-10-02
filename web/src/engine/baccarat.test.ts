import { describe, expect, it } from 'vitest';
import type { Card, Rank } from './cards';
import { baccaratPayout, bankerDraws, dealBaccarat, handTotal, playCoup, validateBaccarat, type Coup } from './baccarat';

const c = (rank: Rank): Card => ({ rank, suit: 'spades' });
/** Mano con las cartas en orden de reparto: J1, B1, J2, B2, terceras. */
const coupOf = (...ranks: Rank[]) => {
  const cards = ranks.map(c);
  return playCoup(() => {
    const card = cards.shift();
    if (!card) throw new Error('faltan cartas');
    return card;
  });
};

describe('baccarat', () => {
  it('cuenta solo la última cifra: figuras y dieces valen 0, el as 1', () => {
    expect(handTotal([c('7'), c('8')])).toBe(5);
    expect(handTotal([c('K'), c('9')])).toBe(9);
    expect(handTotal([c('A'), c('Q'), c('10')])).toBe(1);
  });

  it('con un natural nadie pide carta', () => {
    const coup = coupOf('8', '3', 'K', '2');
    expect(coup).toMatchObject({ natural: true, playerTotal: 8, bankerTotal: 5, winner: 'player' });
    expect(coup.player).toHaveLength(2);
    expect(coup.banker).toHaveLength(2);
  });

  it('el jugador pide con 0–5 y se planta con 6–7; la banca sigue la tabla', () => {
    // Jugador 6, se planta; banca 5 pide (sin tercera del jugador, pide con 0–5).
    expect(coupOf('6', '2', 'K', '3', '4')).toMatchObject({ playerTotal: 6, bankerTotal: 9, winner: 'banker' });
    // Jugador 3 pide un 8; banca 3 no pide contra un 8.
    const c1 = coupOf('A', '3', '2', 'K', '8');
    expect(c1.player).toHaveLength(3);
    expect(c1.banker).toHaveLength(2);
    // Tabla de la banca con la tercera carta del jugador.
    expect(bankerDraws(6, 6)).toBe(true);
    expect(bankerDraws(6, 5)).toBe(false);
    expect(bankerDraws(5, 4)).toBe(true);
    expect(bankerDraws(5, 3)).toBe(false);
    expect(bankerDraws(4, 2)).toBe(true);
    expect(bankerDraws(4, 1)).toBe(false);
    expect(bankerDraws(2, 9)).toBe(true);
    expect(bankerDraws(7, null)).toBe(false);
  });

  it('paga jugador 1:1, banca 1:1 (con 6, 1:2), empate 8:1 y parejas 11:1', () => {
    const bankerSix: Coup = coupOf('7', 'K', 'Q', '6'); // jugador 7 se planta, banca 6 se planta: empate no, gana el jugador
    expect(bankerSix.winner).toBe('player');
    const bankerWinsWithSix = coupOf('5', '3', 'K', '3', 'K'); // jugador 5 pide un K (0), banca 6 no pide contra 0
    expect(bankerWinsWithSix).toMatchObject({ playerTotal: 5, bankerTotal: 6, winner: 'banker' });
    expect(baccaratPayout('banker', 100, bankerWinsWithSix)).toBe(150);
    expect(baccaratPayout('player', 100, bankerWinsWithSix)).toBe(0);

    // Orden de reparto J1, B1, J2, B2: el jugador recibe 4 y 4, la banca 4 y 4 (8 contra 8, con parejas).
    const tie = coupOf('4', '4', '4', '4');
    expect(tie).toMatchObject({ winner: 'tie', natural: true, playerPair: true, bankerPair: true });
    expect(baccaratPayout('tie', 10, tie)).toBe(90);
    expect(baccaratPayout('player', 10, tie)).toBe(10);
    expect(baccaratPayout('banker', 10, tie)).toBe(10);
    expect(baccaratPayout('playerPair', 10, tie)).toBe(120);
    expect(baccaratPayout('bankerPair', 10, tie)).toBe(120);

    const playerWins = coupOf('9', '2', 'K', '3');
    expect(baccaratPayout('player', 10, playerWins)).toBe(20);
  });

  it('valida las apuestas: múltiplos de 10 y máximo por mano', () => {
    expect(validateBaccarat([])).toBe('noBets');
    expect(validateBaccarat([{ bet: 'player', stake: 15 }])).toBe('invalidBet');
    expect(validateBaccarat([{ bet: 'player', stake: 20_000 }, { bet: 'tie', stake: 10_000 }])).toBe('aboveTableMaximum');
    const dealt = dealBaccarat([{ bet: 'banker', stake: 100 }], null);
    expect(dealt.ok).toBe(true);
  });

  it('probabilidades exactas (baraja infinita): banca 45,86 %, jugador 44,62 %, empate 9,52 %', () => {
    // Valor 0 (10 y figuras) con probabilidad 4/13; del 1 al 9, 1/13 cada uno.
    const RANK_OF: Rank[] = ['10', 'A', '2', '3', '4', '5', '6', '7', '8', '9'];
    const WEIGHT = [4, 1, 1, 1, 1, 1, 1, 1, 1, 1];
    const totals = { player: 0, banker: 0, tie: 0 };
    let all = 0;
    for (let code = 0; code < 1_000_000; code++) {
      let rest = code;
      let weight = 1;
      const digits: number[] = [];
      for (let i = 0; i < 6; i++) {
        digits.push(rest % 10);
        rest = Math.floor(rest / 10);
      }
      let used = 0;
      const coup = playCoup(() => c(RANK_OF[digits[used++]!]!));
      // Solo pesan las cartas usadas; las que sobran se cuentan una vez por cada valor posible.
      for (let i = 0; i < used; i++) weight *= WEIGHT[digits[i]!]!;
      if (digits.slice(used).some((d) => d !== 0)) continue;
      weight *= 13 ** (6 - used);
      totals[coup.winner] += weight;
      all += weight;
    }
    expect(all).toBe(13 ** 6);
    expect(totals.banker / all).toBeCloseTo(0.4586, 3);
    expect(totals.player / all).toBeCloseTo(0.4462, 3);
    expect(totals.tie / all).toBeCloseTo(0.0952, 3);
  });
});
