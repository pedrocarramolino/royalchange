import { randomInt } from '@/lib/ids';
import type { Rank } from '@/ui/PlayingCard';
import type { SuitName } from '@/ui/Suit';

export type { Rank, SuitName };

export interface Card {
  rank: Rank;
  suit: SuitName;
}

export const SUITS: SuitName[] = ['spades', 'hearts', 'diamonds', 'clubs'];
export const RANKS: Rank[] = ['2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K', 'A'];

/** Generador de enteros uniformes en [0, bound): criptográfico en la app, fijo en los tests. */
export type RandomInt = (bound: number) => number;
export const secureRandom: RandomInt = randomInt;

/** Una baraja de 52 cartas en orden fijo. */
export function standardDeck(): Card[] {
  return SUITS.flatMap((suit) => RANKS.map((rank) => ({ rank, suit })));
}

/** Fisher–Yates: todas las permutaciones igual de probables. */
export function shuffle<T>(items: T[], random: RandomInt = secureRandom): T[] {
  const result = [...items];
  for (let i = result.length - 1; i > 0; i--) {
    const j = random(i + 1);
    [result[i], result[j]] = [result[j]!, result[i]!];
  }
  return result;
}

/** Índice de rango (2 = 0 … as = 12), para comparar. */
export const rankIndex = (rank: Rank) => RANKS.indexOf(rank);

/**
 * Zapato de varias barajas. Se reparte desde el principio y, al pasar la carta de corte, se
 * baraja de nuevo antes de la siguiente ronda, como en un casino. Inmutable: se guarda y se reanuda.
 */
export interface Shoe {
  cards: Card[];
  cutIndex: number;
  position: number;
}

export function shuffledShoe(decks: number, penetration: number, random: RandomInt = secureRandom): Shoe {
  const cards = shuffle(Array.from({ length: decks }, standardDeck).flat(), random);
  return { cards, cutIndex: Math.floor(cards.length * penetration), position: 0 };
}

export const needsShuffle = (shoe: Shoe) => shoe.position >= shoe.cutIndex;

export function draw(shoe: Shoe): [Card, Shoe] {
  const card = shoe.cards[shoe.position];
  if (!card) throw new Error('Zapato vacío');
  return [card, { ...shoe, position: shoe.position + 1 }];
}
