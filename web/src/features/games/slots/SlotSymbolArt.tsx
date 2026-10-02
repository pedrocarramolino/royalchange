import { memo } from 'react';
import type { SlotSymbol } from '@/engine/slots';
import { SuitPath } from '@/ui/Suit';

/** Símbolos de la máquina en SVG (caja de 100 × 100). */
export const SlotSymbolArt = memo(function SlotSymbolArt({ symbol, size }: { symbol: SlotSymbol; size: number }) {
  return (
    <svg viewBox="0 0 100 100" width={size} height={size} aria-hidden>
      <Art symbol={symbol} />
    </svg>
  );
});

function Art({ symbol }: { symbol: SlotSymbol }) {
  switch (symbol) {
    case 'Cherry':
      return (
        <g>
          <path d="M50 18 C46 34 38 46 30 58 M50 18 C56 36 62 46 70 56" stroke="#2f7a3a" strokeWidth="5" fill="none" strokeLinecap="round" />
          <path d="M50 18 q16 -10 26 2 q-14 6 -26 -2Z" fill="#3f9a4a" />
          <circle cx="30" cy="66" r="17" fill="#c8243c" />
          <circle cx="70" cy="64" r="17" fill="#d62a44" />
          <circle cx="24" cy="60" r="5" fill="#ffffff" opacity="0.55" />
          <circle cx="64" cy="58" r="5" fill="#ffffff" opacity="0.55" />
        </g>
      );
    case 'Club':
      return (
        <g transform="translate(14 12) scale(0.72)" fill="#1f8f63">
          <SuitPath suit="clubs" />
        </g>
      );
    case 'Heart':
      return (
        <g transform="translate(13 14) scale(0.74)" fill="#d62a44">
          <SuitPath suit="hearts" />
        </g>
      );
    case 'Spade':
      return (
        <g transform="translate(14 12) scale(0.72)" fill="#22242b">
          <SuitPath suit="spades" />
        </g>
      );
    case 'Diamond':
      return (
        <g>
          <path d="M50 10 L84 42 L50 90 L16 42 Z" fill="#3a8ee0" />
          <path d="M50 10 L84 42 L50 46 L16 42 Z" fill="#7cc0ff" />
          <path d="M50 46 L84 42 L50 90 Z" fill="#2a6fc0" />
          <path d="M34 26 L50 46 L66 26" stroke="#d8ecff" strokeWidth="2" fill="none" />
        </g>
      );
    case 'Bar':
      return (
        <g>
          <rect x="10" y="30" width="80" height="40" rx="8" fill="#231905" stroke="#e2c27f" strokeWidth="4" />
          <text x="50" y="50" dy="0.36em" textAnchor="middle" fontFamily="Cinzel, serif" fontWeight="900" fontSize="26" fill="#f3dfa2" letterSpacing="2">
            BAR
          </text>
        </g>
      );
    case 'Seven':
      return (
        <g>
          <text x="50" y="52" dy="0.36em" textAnchor="middle" fontFamily="Cinzel, serif" fontWeight="900" fontSize="86" fill="#c8243c" stroke="#e2c27f" strokeWidth="3" paintOrder="stroke">
            7
          </text>
        </g>
      );
    case 'Wild':
      return (
        <g>
          <path d="M14 70 L18 30 L36 48 L50 20 L64 48 L82 30 L86 70 Z" fill="url(#rc-slot-gold)" stroke="#8e6d2c" strokeWidth="3" strokeLinejoin="round" />
          <rect x="14" y="68" width="72" height="12" rx="3" fill="url(#rc-slot-gold)" stroke="#8e6d2c" strokeWidth="3" />
          <circle cx="50" cy="20" r="5" fill="#d62a44" />
          <circle cx="18" cy="30" r="4" fill="#3a8ee0" />
          <circle cx="82" cy="30" r="4" fill="#3a8ee0" />
          <circle cx="50" cy="56" r="6" fill="#d62a44" />
        </g>
      );
  }
}

export function SlotSvgDefs() {
  return (
    <svg width="0" height="0" style={{ position: 'absolute' }} aria-hidden>
      <defs>
        <linearGradient id="rc-slot-gold" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#fbefc8" />
          <stop offset="0.5" stopColor="#e2c27f" />
          <stop offset="1" stopColor="#a8813f" />
        </linearGradient>
      </defs>
    </svg>
  );
}
