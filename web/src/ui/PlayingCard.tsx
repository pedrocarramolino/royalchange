import { memo } from 'react';
import { SuitPath, SUIT_NAME, isRed, type SuitName } from './Suit';

export type Rank = 'A' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' | '10' | 'J' | 'Q' | 'K';

const RANK_NAME: Record<Rank, string> = {
  A: 'as',
  '2': 'dos',
  '3': 'tres',
  '4': 'cuatro',
  '5': 'cinco',
  '6': 'seis',
  '7': 'siete',
  '8': 'ocho',
  '9': 'nueve',
  '10': 'diez',
  J: 'jota',
  Q: 'reina',
  K: 'rey',
};

export function cardName(rank: Rank, suit: SuitName): string {
  return `${RANK_NAME[rank]} de ${SUIT_NAME[suit]}`;
}

// Disposición clásica de los palos (x, y en % del área central). Los de la mitad de abajo, girados.
const PIPS: Partial<Record<Rank, [number, number][]>> = {
  '2': [[50, 10], [50, 90]],
  '3': [[50, 10], [50, 50], [50, 90]],
  '4': [[26, 10], [74, 10], [26, 90], [74, 90]],
  '5': [[26, 10], [74, 10], [50, 50], [26, 90], [74, 90]],
  '6': [[26, 10], [74, 10], [26, 50], [74, 50], [26, 90], [74, 90]],
  '7': [[26, 10], [74, 10], [50, 30], [26, 50], [74, 50], [26, 90], [74, 90]],
  '8': [[26, 10], [74, 10], [50, 30], [26, 50], [74, 50], [50, 70], [26, 90], [74, 90]],
  '9': [[26, 10], [74, 10], [26, 36], [74, 36], [50, 50], [26, 64], [74, 64], [26, 90], [74, 90]],
  '10': [[26, 10], [74, 10], [50, 23], [26, 36], [74, 36], [26, 64], [74, 64], [50, 77], [26, 90], [74, 90]],
};

const W = 250;
const H = 350;
// Área central de los palos.
const AREA = { x: 58, y: 52, w: 134, h: 246 };

interface CardProps {
  rank: Rank;
  suit: SuitName;
  faceDown?: boolean;
  /** Ancho en píxeles; el alto guarda la proporción 5:7. */
  width: number;
  highlight?: boolean;
  dimmed?: boolean;
  className?: string;
}

/** Carta de póker en SVG: nítida a cualquier tamaño. */
export const PlayingCard = memo(function PlayingCard({ rank, suit, faceDown, width, highlight, dimmed, className }: CardProps) {
  const height = (width * H) / W;
  const label = faceDown ? 'Carta boca abajo' : cardName(rank, suit);
  return (
    <svg
      viewBox={`0 0 ${W} ${H}`}
      width={width}
      height={height}
      role="img"
      aria-label={label}
      className={className}
      style={{
        filter: `drop-shadow(0 ${Math.max(1, width / 40)}px ${Math.max(2, width / 14)}px rgb(0 0 0 / 0.45))`,
        opacity: dimmed ? 0.45 : 1,
        transition: 'opacity 200ms',
      }}
    >
      {faceDown ? <CardBack /> : <CardFace rank={rank} suit={suit} />}
      {highlight && <rect x="3" y="3" width={W - 6} height={H - 6} rx="18" fill="none" stroke="#f3dfa2" strokeWidth="8" />}
    </svg>
  );
});

function CardFace({ rank, suit }: { rank: Rank; suit: SuitName }) {
  const color = isRed(suit) ? '#c8243c' : '#16171c';
  const pips = PIPS[rank];
  return (
    <g>
      <rect x="1" y="1" width={W - 2} height={H - 2} rx="20" fill="url(#rc-paper)" stroke="#cdbf9f" strokeWidth="2" />
      <Corner rank={rank} suit={suit} color={color} />
      <g transform={`rotate(180 ${W / 2} ${H / 2})`}>
        <Corner rank={rank} suit={suit} color={color} />
      </g>
      <g fill={color}>
        {pips &&
          pips.map(([px, py], i) => {
            const size = 46;
            const cx = AREA.x + (AREA.w * px) / 100;
            const cy = AREA.y + (AREA.h * py) / 100;
            const flip = py > 50;
            return (
              <g key={i} transform={`translate(${cx - size / 2} ${cy - size / 2}) ${flip ? `rotate(180 ${size / 2} ${size / 2})` : ''} scale(${size / 100})`}>
                <SuitPath suit={suit} />
              </g>
            );
          })}
        {rank === 'A' && (
          <g transform={`translate(${W / 2 - 55} ${H / 2 - 55}) scale(1.1)`}>
            <SuitPath suit={suit} />
          </g>
        )}
      </g>
      {(rank === 'J' || rank === 'Q' || rank === 'K') && <Court rank={rank} suit={suit} color={color} />}
    </g>
  );
}

function Corner({ rank, suit, color }: { rank: Rank; suit: SuitName; color: string }) {
  return (
    <g fill={color}>
      <text
        x="30"
        y="58"
        textAnchor="middle"
        fontFamily="Cinzel, Georgia, serif"
        fontWeight="700"
        fontSize={rank === '10' ? 44 : 52}
        letterSpacing={rank === '10' ? -4 : 0}
      >
        {rank}
      </text>
      <g transform="translate(14 68) scale(0.32)">
        <SuitPath suit={suit} />
      </g>
    </g>
  );
}

/** Figuras: marco dorado, inicial en Cinzel y emblema del palo. */
function Court({ rank, suit, color }: { rank: 'J' | 'Q' | 'K'; suit: SuitName; color: string }) {
  const x = AREA.x - 4;
  const y = AREA.y - 4;
  const w = AREA.w + 8;
  const h = AREA.h + 8;
  return (
    <g>
      <rect x={x} y={y} width={w} height={h} rx="10" fill={isRed(suit) ? '#fbe9e4' : '#ece8de'} stroke="#b8924a" strokeWidth="4" />
      <rect x={x + 8} y={y + 8} width={w - 16} height={h - 16} rx="6" fill="none" stroke="#d4af6a" strokeWidth="2" />
      <Crown rank={rank} x={W / 2} y={y + 44} />
      <text x={W / 2} y={H / 2 + 34} textAnchor="middle" fontFamily="Cinzel, Georgia, serif" fontWeight="700" fontSize="104" fill={color}>
        {rank}
      </text>
      <g fill={color} transform={`translate(${W / 2 - 22} ${y + h - 70}) scale(0.44)`}>
        <SuitPath suit={suit} />
      </g>
    </g>
  );
}

function Crown({ rank, x, y }: { rank: 'J' | 'Q' | 'K'; x: number; y: number }) {
  const points = rank === 'K' ? 5 : rank === 'Q' ? 3 : 0;
  if (points === 0) {
    // Jota: penacho.
    return <path d={`M${x - 26} ${y + 12}q26-34 52 0q-10-6-26-6t-26 6Z`} fill="#b8924a" />;
  }
  const width = 64;
  const left = x - width / 2;
  const step = width / (points - 1);
  let d = `M${left} ${y + 16}`;
  for (let i = 0; i < points; i++) {
    const px = left + i * step;
    d += ` L${px} ${y - 10} L${px + step / 2} ${y + 6}`;
  }
  d = d.replace(/ L[\d.]+ [\d.]+$/, '');
  d += ` L${left + width} ${y + 16} Z`;
  return (
    <g fill="#c9a24f" stroke="#8e6d2c" strokeWidth="2">
      <path d={d} />
      <rect x={left} y={y + 14} width={width} height={8} rx="2" />
    </g>
  );
}

/** Reverso: rubí oscuro con celosía dorada y monograma. */
function CardBack() {
  return (
    <g>
      <rect x="1" y="1" width={W - 2} height={H - 2} rx="20" fill="#f7f1e3" stroke="#cdbf9f" strokeWidth="2" />
      <rect x="14" y="14" width={W - 28} height={H - 28} rx="12" fill="url(#rc-lattice)" stroke="#d4af6a" strokeWidth="4" />
      <circle cx={W / 2} cy={H / 2} r="44" fill="#3b0b15" stroke="#d4af6a" strokeWidth="4" />
      <text x={W / 2} y={H / 2 + 15} textAnchor="middle" fontFamily="Cinzel, Georgia, serif" fontWeight="700" fontSize="42" fill="#e6c887">
        RC
      </text>
    </g>
  );
}

/** Degradados y tramas compartidos por todas las cartas: se pintan una vez en la raíz de la app. */
export function CardSvgDefs() {
  return (
    <svg width="0" height="0" style={{ position: 'absolute' }} aria-hidden>
      <defs>
        <linearGradient id="rc-paper" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#fffdf7" />
          <stop offset="1" stopColor="#efe8d8" />
        </linearGradient>
        <pattern id="rc-lattice" width="20" height="20" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
          <rect width="20" height="20" fill="#5e1220" />
          <path d="M0 10h20M10 0v20" stroke="#b8924a" strokeOpacity="0.55" strokeWidth="1.6" />
        </pattern>
      </defs>
    </svg>
  );
}
