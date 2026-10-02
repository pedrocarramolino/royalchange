import { memo, useRef, type PointerEvent } from 'react';
import { motion } from 'motion/react';
import { colorOf } from './wheel';
import { betName, betNumbers, type RouletteBet } from '@/engine/roulette';
import { Chip } from '@/ui/Chip';
import { chipLabel } from '@/lib/format';

// Tapete horizontal, en unidades de casilla: 0 a la izquierda (1 de ancho), 12 columnas de 3
// números, 2:1 a la derecha; debajo, docenas (0,8 de alto) y apuestas sencillas (0,8).
const COLS = 14;
const ROWS = 4.6;
const EDGE = 0.26;

/** Fila (0 arriba) y columna (1–12) de un número del 1 al 36. */
function cellOf(n: number): { col: number; row: number } {
  const col = Math.ceil(n / 3);
  return { col, row: n % 3 === 0 ? 0 : n % 3 === 2 ? 1 : 2 };
}

/** Punto del tapete donde se colocan las fichas de una apuesta. */
export function anchorOf(bet: RouletteBet): { x: number; y: number } {
  const [kind, arg] = bet.split(':') as [string, string | undefined];
  const n = Number(arg);
  switch (kind) {
    case 'n': {
      if (n === 0) return { x: 0.5, y: 1.5 };
      const { col, row } = cellOf(n);
      return { x: col + 0.5, y: row + 0.5 };
    }
    case 'split': {
      const [a, b] = (arg ?? '').split('-').map(Number) as [number, number];
      const low = Math.min(a, b);
      const high = Math.max(a, b);
      if (low === 0) return { x: 1, y: cellOf(high).row + 0.5 };
      const c = cellOf(low);
      return high - low === 3 ? { x: c.col + 1, y: c.row + 0.5 } : { x: c.col + 0.5, y: c.row };
    }
    case 'corner': {
      const c = cellOf(n);
      return { x: c.col + 1, y: c.row };
    }
    case 'street':
      return { x: n + 0.5, y: 3 };
    case 'line':
      return { x: n + 1, y: 3 };
    case 'column':
      return { x: 13.5, y: 3 - n + 0.5 };
    case 'dozen':
      return { x: 1 + 4 * n - 2, y: 3.4 };
    default: {
      const order = ['low', 'even', 'red', 'black', 'odd', 'high'];
      return { x: 2 + 2 * order.indexOf(kind), y: 4.2 };
    }
  }
}

/** Apuesta según dónde se toca la zona de números: centro, borde (caballo, transversal) o esquina. */
function betAt(x: number, y: number): RouletteBet | null {
  if (x < 1) return 'n:0';
  if (x >= 13) return y < 3 ? `column:${3 - Math.floor(y)}` : null;
  if (y >= 3.8) {
    const order = ['low', 'even', 'red', 'black', 'odd', 'high'];
    return order[Math.min(5, Math.floor((x - 1) / 2))]!;
  }
  if (y >= 3.12) return `dozen:${Math.min(3, Math.floor((x - 1) / 4) + 1)}`;
  const fx = x - 1;
  const fy = Math.min(y, 3);
  const col = Math.min(11, Math.floor(fx));
  const row = Math.min(2, Math.floor(fy));
  const dx = fx - col;
  const dy = fy - row;
  const vl = dx > 1 - EDGE ? col + 1 : dx < EDGE ? col : null;
  const hl = dy > 1 - EDGE ? row + 1 : dy < EDGE ? row : null;
  if (vl !== null && hl !== null && vl >= 1 && vl <= 11) {
    if (hl === 3) return `line:${vl}`;
    if (hl >= 1 && hl <= 2) return `corner:${3 * vl - hl}`;
  }
  if (vl !== null && hl === null) {
    if (vl === 0) return `split:0-${3 - row}`;
    if (vl <= 11) {
      const n = 3 * vl - row;
      return `split:${n}-${n + 3}`;
    }
  }
  if (hl !== null && vl === null) {
    if (hl === 3) return `street:${col + 1}`;
    if (hl >= 1) {
      const n = 3 * (col + 1) - hl;
      return `split:${n}-${n + 1}`;
    }
  }
  return `n:${3 * (col + 1) - row}`;
}

interface BoardProps {
  width: number;
  height: number;
  bets: Map<RouletteBet, number>;
  /** Número ganador mientras se muestra el resultado. */
  winning: number | null;
  /** Pago de cada apuesta en la última tirada (para marcar ganadoras y perdedoras). */
  payouts: Map<RouletteBet, number> | null;
  disabled: boolean;
  onPlace: (bet: RouletteBet) => void;
}

export function RouletteBoard({ width, height, bets, winning, payouts, disabled, onPlace }: BoardProps) {
  const unit = Math.min(width / COLS, height / ROWS);
  const ref = useRef<HTMLDivElement>(null);
  const onPointerUp = (event: PointerEvent<HTMLDivElement>) => {
    if (disabled || !ref.current) return;
    const rect = ref.current.getBoundingClientRect();
    const bet = betAt((event.clientX - rect.left) / unit, (event.clientY - rect.top) / unit);
    if (bet && betNumbers(bet).length) onPlace(bet);
  };
  const winners = winning !== null ? new Set([winning]) : null;
  return (
    <div
      ref={ref}
      onPointerUp={onPointerUp}
      className="relative touch-manipulation select-none"
      style={{ width: unit * COLS, height: unit * ROWS }}
      role="group"
      aria-label="Tapete de apuestas: toca un número, entre dos números o en una esquina"
    >
      <BoardPrint unit={unit} winners={winners} />
      {/* Botones accesibles (lectores de pantalla y teclado): plenos y apuestas exteriores. */}
      <div className="sr-only">
        {[...Array.from({ length: 37 }, (_, i) => `n:${i}`), 'column:1', 'column:2', 'column:3', 'dozen:1', 'dozen:2', 'dozen:3', 'low', 'even', 'red', 'black', 'odd', 'high'].map((bet) => (
          <button key={bet} type="button" disabled={disabled} onClick={() => onPlace(bet)}>
            Apostar a {betName(bet)}
            {bets.get(bet) ? `, tienes ${bets.get(bet)} fichas` : ''}
          </button>
        ))}
      </div>
      {[...bets.entries()].map(([bet, amount]) => {
        const { x, y } = anchorOf(bet);
        const payout = payouts?.get(bet);
        const lost = payouts !== null && !payout;
        const size = Math.max(18, unit * 0.62);
        return (
          <motion.div
            key={bet}
            className="pointer-events-none absolute"
            style={{ left: x * unit - size / 2, top: y * unit - size / 2 }}
            initial={{ scale: 0.4, y: -12, opacity: 0 }}
            animate={{ scale: 1, y: 0, opacity: lost ? 0.25 : 1 }}
            transition={{ type: 'spring', stiffness: 500, damping: 26 }}
          >
            <Chip value={payout ?? amount} size={size} label={chipLabel(payout ?? amount)} />
            {payout ? <span className="absolute inset-0 animate-ping rounded-full ring-2 ring-gold-light" /> : null}
          </motion.div>
        );
      })}
    </div>
  );
}

/** El paño serigrafiado: casillas, números, docenas, sencillas y rombos rojo y negro. */
const BoardPrint = memo(function BoardPrint({ unit, winners }: { unit: number; winners: Set<number> | null }) {
  const W = COLS * unit;
  const H = ROWS * unit;
  const line = 'rgb(243 223 162 / 0.75)';
  const fontSize = unit * 0.36;
  const cells = [];
  for (let n = 1; n <= 36; n++) {
    const { col, row } = cellOf(n);
    const color = colorOf(n);
    const win = winners?.has(n);
    cells.push(
      <g key={n}>
        <rect x={col * unit} y={row * unit} width={unit} height={unit} fill={win ? 'rgb(243 223 162 / 0.35)' : 'transparent'} stroke={line} strokeWidth="1" />
        <ellipse cx={(col + 0.5) * unit} cy={(row + 0.5) * unit} rx={unit * 0.33} ry={unit * 0.3} fill={color === 'red' ? '#b3263b' : '#15171d'} stroke={win ? '#f3dfa2' : 'none'} strokeWidth="2" />
        <text x={(col + 0.5) * unit} y={(row + 0.5) * unit} dy="0.35em" textAnchor="middle" fontFamily="Manrope, sans-serif" fontWeight="800" fontSize={fontSize} fill="#f7f1e3">
          {n}
        </text>
      </g>,
    );
  }
  const outside = [
    ['low', '1–18'],
    ['even', 'PAR'],
    ['red', ''],
    ['black', ''],
    ['odd', 'IMPAR'],
    ['high', '19–36'],
  ];
  return (
    <svg width={W} height={H} className="pointer-events-none absolute inset-0" aria-hidden>
      {/* Cero */}
      <path d={`M${unit} 0 L${unit * 0.35} 0 Q0 ${1.5 * unit} ${unit * 0.35} ${3 * unit} L${unit} ${3 * unit} Z`} fill={winners?.has(0) ? 'rgb(243 223 162 / 0.35)' : '#0f7a4f'} stroke={line} strokeWidth="1" />
      <text x={unit * 0.62} y={1.5 * unit} dy="0.35em" textAnchor="middle" fontFamily="Manrope, sans-serif" fontWeight="800" fontSize={fontSize * 1.1} fill="#f7f1e3">
        0
      </text>
      {cells}
      {/* Columnas 2:1 */}
      {[0, 1, 2].map((row) => (
        <g key={row}>
          <rect x={13 * unit} y={row * unit} width={unit} height={unit} fill="transparent" stroke={line} />
          <text x={13.5 * unit} y={(row + 0.5) * unit} dy="0.35em" textAnchor="middle" fontFamily="Cinzel, serif" fontWeight="700" fontSize={fontSize * 0.85} fill="rgb(243 223 162 / 0.9)">
            2:1
          </text>
        </g>
      ))}
      {/* Docenas */}
      {[1, 2, 3].map((d) => (
        <g key={d}>
          <rect x={(1 + 4 * (d - 1)) * unit} y={3 * unit} width={4 * unit} height={0.8 * unit} fill="transparent" stroke={line} />
          <text x={(1 + 4 * d - 2) * unit} y={3.4 * unit} dy="0.35em" textAnchor="middle" fontFamily="Cinzel, serif" fontWeight="700" fontSize={fontSize * 0.85} fill="rgb(243 223 162 / 0.9)">
            {d === 1 ? '1ª 12' : d === 2 ? '2ª 12' : '3ª 12'}
          </text>
        </g>
      ))}
      {/* Sencillas */}
      {outside.map(([key, label], i) => (
        <g key={key}>
          <rect x={(1 + 2 * i) * unit} y={3.8 * unit} width={2 * unit} height={0.8 * unit} fill="transparent" stroke={line} />
          {key === 'red' || key === 'black' ? (
            <path
              d={`M${(2 + 2 * i) * unit} ${3.88 * unit} L${(2.55 + 2 * i) * unit} ${4.2 * unit} L${(2 + 2 * i) * unit} ${4.52 * unit} L${(1.45 + 2 * i) * unit} ${4.2 * unit} Z`}
              fill={key === 'red' ? '#b3263b' : '#15171d'}
              stroke="rgb(243 223 162 / 0.7)"
            />
          ) : (
            <text x={(2 + 2 * i) * unit} y={4.2 * unit} dy="0.35em" textAnchor="middle" fontFamily="Cinzel, serif" fontWeight="700" fontSize={fontSize * 0.8} fill="rgb(243 223 162 / 0.9)">
              {label}
            </text>
          )}
        </g>
      ))}
    </svg>
  );
});
