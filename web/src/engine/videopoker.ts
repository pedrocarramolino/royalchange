import { secureRandom, shuffle, standardDeck, type Card, type RandomInt } from './cards';
import { categoryOf, evaluateHand } from './poker';

/*
 * Video póker «Jacks or Better» con la tabla 9/6 clásica: con juego perfecto devuelve un 99,5 %.
 * Se reparten 5 cartas de una baraja de 52, se guardan las que se quiera y se cambian las demás
 * (una sola vez). Paga según la mano final; la mínima es una pareja de jotas o más alta.
 */

export type VideoPokerHand = 'royalFlush' | 'straightFlush' | 'fourOfAKind' | 'fullHouse' | 'flush' | 'straight' | 'threeOfAKind' | 'twoPair' | 'jacksOrBetter';

/** Pago total por ficha apostada (apuesta incluida): una pareja de jotas devuelve lo apostado. */
export const PAYTABLE: { hand: VideoPokerHand; name: string; multiplier: number }[] = [
  { hand: 'royalFlush', name: 'Escalera real', multiplier: 800 },
  { hand: 'straightFlush', name: 'Escalera de color', multiplier: 50 },
  { hand: 'fourOfAKind', name: 'Póker', multiplier: 25 },
  { hand: 'fullHouse', name: 'Full', multiplier: 9 },
  { hand: 'flush', name: 'Color', multiplier: 6 },
  { hand: 'straight', name: 'Escalera', multiplier: 4 },
  { hand: 'threeOfAKind', name: 'Trío', multiplier: 3 },
  { hand: 'twoPair', name: 'Doble pareja', multiplier: 2 },
  { hand: 'jacksOrBetter', name: 'Jotas o más', multiplier: 1 },
];

const MULTIPLIER = new Map(PAYTABLE.map((row) => [row.hand, row.multiplier]));

/** Apuestas posibles (fichas por mano). */
export const VIDEO_POKER_BETS = [10, 20, 50, 100, 200, 500, 1_000, 2_000, 5_000];

/** Primer desempate de la puntuación del evaluador: rango de la pareja, del trío o la carta alta. */
const leadRank = (value: number) => Math.floor(value / 16 ** 4) % 16;

/** Mano que paga con estas 5 cartas, o `null` si no paga. */
export function classifyVideoPoker(cards: Card[]): VideoPokerHand | null {
  const value = evaluateHand(cards);
  switch (categoryOf(value)) {
    case 'StraightFlush':
      return leadRank(value) === 14 ? 'royalFlush' : 'straightFlush';
    case 'FourOfAKind':
      return 'fourOfAKind';
    case 'FullHouse':
      return 'fullHouse';
    case 'Flush':
      return 'flush';
    case 'Straight':
      return 'straight';
    case 'ThreeOfAKind':
      return 'threeOfAKind';
    case 'TwoPair':
      return 'twoPair';
    case 'OnePair':
      // Pareja de jotas (11) o más alta.
      return leadRank(value) >= 11 ? 'jacksOrBetter' : null;
    default:
      return null;
  }
}

export const videoPokerPayout = (bet: number, hand: VideoPokerHand | null) => (hand ? bet * MULTIPLIER.get(hand)! : 0);

/** Cartas que forman la jugada (para resaltarlas): las repetidas en parejas, tríos y póker; si no, las cinco. */
export function winningCards(cards: Card[], hand: VideoPokerHand | null): boolean[] {
  if (!hand) return cards.map(() => false);
  if (hand === 'jacksOrBetter' || hand === 'twoPair' || hand === 'threeOfAKind' || hand === 'fourOfAKind') {
    const counts = new Map<string, number>();
    cards.forEach((c) => counts.set(c.rank, (counts.get(c.rank) ?? 0) + 1));
    return cards.map((c) => counts.get(c.rank)! >= 2);
  }
  return cards.map(() => true);
}

/** Mano repartida: las 5 cartas y el resto de la baraja, en orden, para el cambio. */
export interface VideoPokerDeal {
  hand: Card[];
  deck: Card[];
}

export function dealVideoPoker(random: RandomInt = secureRandom): VideoPokerDeal {
  const deck = shuffle(standardDeck(), random);
  return { hand: deck.slice(0, 5), deck: deck.slice(5) };
}

/** Cambia las cartas no guardadas por las siguientes de la baraja. */
export function drawVideoPoker(deal: VideoPokerDeal, held: boolean[]): Card[] {
  let next = 0;
  return deal.hand.map((card, i) => (held[i] ? card : deal.deck[next++]!));
}
