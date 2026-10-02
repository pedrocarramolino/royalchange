export type SuitName = 'spades' | 'hearts' | 'diamonds' | 'clubs';

export const SUIT_SYMBOL: Record<SuitName, string> = { spades: '♠', hearts: '♥', diamonds: '♦', clubs: '♣' };
export const SUIT_NAME: Record<SuitName, string> = { spades: 'picas', hearts: 'corazones', diamonds: 'diamantes', clubs: 'tréboles' };
export const isRed = (suit: SuitName) => suit === 'hearts' || suit === 'diamonds';

/** Formas de los palos en una caja de 100 × 100, centradas. */
export function SuitPath({ suit }: { suit: SuitName }) {
  switch (suit) {
    case 'hearts':
      return <path d="M50 90C22 68 5 50 5 30 5 15 16 5 29 5c9 0 17 5 21 14C54 10 62 5 71 5c13 0 24 10 24 25 0 20-17 38-45 60Z" />;
    case 'diamonds':
      return <path d="M50 3C60 20 74 36 90 50 74 64 60 80 50 97 40 80 26 64 10 50 26 36 40 20 50 3Z" />;
    case 'spades':
      return (
        <path d="M50 3C62 21 95 39 95 62c0 14-11 23-23 23-8 0-15-4-19-11 1 10 5 17 13 22H34c8-5 12-12 13-22-4 7-11 11-19 11C16 85 5 76 5 62 5 39 38 21 50 3Z" />
      );
    case 'clubs':
      return (
        <g>
          <circle cx="50" cy="27" r="20" />
          <circle cx="26" cy="57" r="20" />
          <circle cx="74" cy="57" r="20" />
          <path d="M44 50h12c0 20 4 34 12 46H32c8-12 12-26 12-46Z" />
        </g>
      );
  }
}

export function Suit({ suit, className, title }: { suit: SuitName; className?: string; title?: string }) {
  return (
    <svg viewBox="0 0 100 100" className={className} aria-hidden={title ? undefined : true} role={title ? 'img' : undefined} fill="currentColor">
      {title && <title>{title}</title>}
      <SuitPath suit={suit} />
    </svg>
  );
}
