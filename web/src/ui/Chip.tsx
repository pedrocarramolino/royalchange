import { memo } from 'react';
import { chipLabel } from '@/lib/format';

/** Colores de cada valor, como en una mesa real. */
const CHIP_COLORS: Record<number, { base: string; edge: string; text: string }> = {
  10: { base: '#2f6fd1', edge: '#f4f1e8', text: '#ffffff' },
  50: { base: '#c0283a', edge: '#f4f1e8', text: '#ffffff' },
  100: { base: '#1d1f25', edge: '#d4af6a', text: '#f3dfa2' },
  500: { base: '#6b3fb3', edge: '#f4f1e8', text: '#ffffff' },
  1000: { base: '#b8892f', edge: '#1d1f25', text: '#231905' },
  5000: { base: '#127454', edge: '#f4f1e8', text: '#ffffff' },
  25000: { base: '#e7e1d2', edge: '#b3263b', text: '#5e1220' },
};

export const CHIP_VALUES = [10, 50, 100, 500, 1000, 5000, 25000];

/** Ficha más alta que no supera [amount] (para pintar montones). */
export function chipFor(amount: number): number {
  let value = CHIP_VALUES[0]!;
  for (const v of CHIP_VALUES) if (v <= amount) value = v;
  return value;
}

interface ChipProps {
  value: number;
  size: number;
  /** Texto en el centro; por defecto el valor corto (1K, 5K). */
  label?: string;
  className?: string;
}

/** Ficha de casino en SVG: canto con franjas, anillo interior y valor. */
export const Chip = memo(function Chip({ value, size, label, className }: ChipProps) {
  const color = CHIP_COLORS[chipFor(value)] ?? CHIP_COLORS[10]!;
  const text = label ?? chipLabel(value);
  const stripes = Array.from({ length: 8 }, (_, i) => i * 45);
  return (
    <svg viewBox="0 0 100 100" width={size} height={size} className={className} aria-hidden>
      <circle cx="50" cy="50" r="48" fill={color.base} />
      {stripes.map((angle) => (
        <rect key={angle} x="44" y="2" width="12" height="15" rx="2" fill={color.edge} transform={`rotate(${angle} 50 50)`} />
      ))}
      <circle cx="50" cy="50" r="48" fill="none" stroke="rgb(0 0 0 / 0.35)" strokeWidth="2" />
      <circle cx="50" cy="50" r="33" fill="none" stroke={color.edge} strokeOpacity="0.85" strokeWidth="2" strokeDasharray="4 3.2" />
      <circle cx="50" cy="50" r="28" fill={color.base} stroke="rgb(0 0 0 / 0.25)" strokeWidth="1.5" />
      <circle cx="50" cy="50" r="48" fill="url(#rc-chip-shine)" />
      <text
        x="50"
        y="50"
        dy="0.36em"
        textAnchor="middle"
        fontFamily="Manrope, system-ui, sans-serif"
        fontWeight="800"
        fontSize={text.length >= 4 ? 19 : text.length === 3 ? 23 : 27}
        fill={color.text}
      >
        {text}
      </text>
    </svg>
  );
});

/** Brillo compartido de las fichas. */
export function ChipSvgDefs() {
  return (
    <svg width="0" height="0" style={{ position: 'absolute' }} aria-hidden>
      <defs>
        <radialGradient id="rc-chip-shine" cx="35%" cy="28%" r="75%">
          <stop offset="0" stopColor="#ffffff" stopOpacity="0.28" />
          <stop offset="0.5" stopColor="#ffffff" stopOpacity="0.04" />
          <stop offset="1" stopColor="#000000" stopOpacity="0.22" />
        </radialGradient>
      </defs>
    </svg>
  );
}

/** Montón de fichas que representa una cantidad (descompuesta en fichas reales). */
export function ChipStack({ amount, size, max = 6 }: { amount: number; size: number; max?: number }) {
  const pieces: number[] = [];
  let rest = amount;
  for (const v of [...CHIP_VALUES].reverse()) {
    while (rest >= v && pieces.length < max) {
      pieces.push(v);
      rest -= v;
    }
  }
  if (pieces.length === 0) pieces.push(CHIP_VALUES[0]!);
  const offset = size * 0.1;
  return (
    <div className="relative" style={{ width: size, height: size + offset * (pieces.length - 1) }} aria-hidden>
      {pieces.reverse().map((value, i) => (
        <div key={i} className="absolute left-0" style={{ bottom: i * offset }}>
          <Chip value={value} size={size} />
        </div>
      ))}
    </div>
  );
}
