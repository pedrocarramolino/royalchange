import { PlayingCard } from '@/ui/PlayingCard';
import { Chip } from '@/ui/Chip';
import { RED_NUMBERS, WHEEL_ORDER } from '@/features/games/roulette/wheel';

/** Ilustraciones de cada mesa para el vestíbulo. */
export function BlackjackArt() {
  return (
    <div className="relative h-full w-full">
      <div className="absolute top-1/2 left-1/2 -translate-x-[78%] -translate-y-1/2 -rotate-12">
        <PlayingCard rank="A" suit="spades" width={58} />
      </div>
      <div className="absolute top-1/2 left-1/2 -translate-x-[22%] -translate-y-[46%] rotate-[9deg]">
        <PlayingCard rank="K" suit="hearts" width={58} />
      </div>
      <div className="absolute right-3 bottom-2">
        <Chip value={100} size={30} />
      </div>
    </div>
  );
}

export function PokerArt() {
  return (
    <div className="relative h-full w-full">
      {(['Q', 'K', 'A'] as const).map((rank, i) => (
        <div key={rank} className="absolute top-1/2 left-1/2" style={{ transform: `translate(${-75 + i * 26}%, -50%) rotate(${(i - 1) * 12}deg)` }}>
          <PlayingCard rank={rank} suit={i === 1 ? 'diamonds' : 'clubs'} width={52} />
        </div>
      ))}
      <div className="absolute bottom-2 left-3">
        <Chip value={5000} size={28} />
      </div>
    </div>
  );
}

export function RouletteArt({ size = 92 }: { size?: number }) {
  const r = 50;
  return (
    <div className="grid h-full w-full place-items-center">
      <svg viewBox="0 0 100 100" width={size} height={size} aria-hidden className="drop-shadow-[0_8px_14px_rgb(0_0_0/0.6)]">
        <circle cx="50" cy="50" r="49" fill="#2a1a0f" stroke="#d4af6a" strokeWidth="1.5" />
        {WHEEL_ORDER.map((n, i) => {
          const a0 = ((i - 0.5) / 37) * Math.PI * 2 - Math.PI / 2;
          const a1 = ((i + 0.5) / 37) * Math.PI * 2 - Math.PI / 2;
          const outer = r - 5;
          const inner = r - 17;
          const p = (rad: number, a: number) => `${50 + rad * Math.cos(a)} ${50 + rad * Math.sin(a)}`;
          const fill = n === 0 ? '#0f7a4f' : RED_NUMBERS.has(n) ? '#b3263b' : '#15171d';
          return <path key={n} d={`M${p(outer, a0)} A${outer} ${outer} 0 0 1 ${p(outer, a1)} L${p(inner, a1)} A${inner} ${inner} 0 0 0 ${p(inner, a0)} Z`} fill={fill} />;
        })}
        <circle cx="50" cy="50" r="33" fill="#3a2414" stroke="#d4af6a" strokeWidth="1" />
        <circle cx="50" cy="50" r="10" fill="#d4af6a" />
        <path d="M50 22v56M22 50h56" stroke="#d4af6a" strokeWidth="2.5" strokeLinecap="round" />
        <circle cx="50" cy="9.5" r="3" fill="#f7f1e3" />
      </svg>
    </div>
  );
}

export function SlotsArt() {
  return (
    <div className="grid h-full w-full place-items-center">
      <div className="flex gap-1 rounded-xl border-2 border-gold/80 bg-[#0b0c10] p-1.5 shadow-[inset_0_0_14px_rgb(0_0_0/0.9)]">
        {[0, 1, 2].map((i) => (
          <div key={i} className="grid h-14 w-11 place-items-center rounded-md bg-gradient-to-b from-[#fbf6ea] via-white to-[#e6dccb]">
            <span className="font-display text-3xl font-bold text-ruby">7</span>
          </div>
        ))}
      </div>
    </div>
  );
}

export function Die({ value, size }: { value: number; size: number }) {
  const pips: Record<number, [number, number][]> = {
    1: [[50, 50]],
    2: [[28, 28], [72, 72]],
    3: [[28, 28], [50, 50], [72, 72]],
    4: [[28, 28], [72, 28], [28, 72], [72, 72]],
    5: [[28, 28], [72, 28], [50, 50], [28, 72], [72, 72]],
    6: [[28, 26], [72, 26], [28, 50], [72, 50], [28, 74], [72, 74]],
  };
  return (
    <svg viewBox="0 0 100 100" width={size} height={size} role="img" aria-label={`Dado: ${value}`}>
      <defs>
        <linearGradient id={`die-${size}`} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#ffffff" />
          <stop offset="1" stopColor="#e4dccb" />
        </linearGradient>
      </defs>
      <rect x="3" y="3" width="94" height="94" rx="20" fill={`url(#die-${size})`} stroke="#b9ab8c" strokeWidth="2" />
      {(pips[value] ?? []).map(([x, y], i) => (
        <circle key={i} cx={x} cy={y} r={value === 1 ? 11 : 9} fill={value === 1 ? '#b3263b' : '#16171c'} />
      ))}
    </svg>
  );
}

export function DiceArt() {
  return (
    <div className="relative h-full w-full">
      <div className="absolute top-1/2 left-1/2 -translate-x-[95%] -translate-y-[40%] -rotate-12 drop-shadow-[0_8px_10px_rgb(0_0_0/0.6)]">
        <Die value={5} size={46} />
      </div>
      <div className="absolute top-1/2 left-1/2 -translate-x-[5%] -translate-y-[62%] rotate-[14deg] drop-shadow-[0_8px_10px_rgb(0_0_0/0.6)]">
        <Die value={2} size={46} />
      </div>
    </div>
  );
}

export function BaccaratArt() {
  return (
    <div className="relative h-full w-full">
      <div className="absolute top-1/2 left-1/2 -translate-x-[86%] -translate-y-1/2 -rotate-[8deg]">
        <PlayingCard rank="9" suit="diamonds" width={52} />
      </div>
      <div className="absolute top-1/2 left-1/2 -translate-x-[8%] -translate-y-[44%] rotate-[8deg]">
        <PlayingCard rank="K" suit="clubs" width={52} />
      </div>
      <span className="absolute top-2.5 left-3 rounded-full bg-[#5b8de6] px-2 py-0.5 text-[10px] font-black tracking-wider text-white">J</span>
      <span className="absolute top-2.5 right-3 rounded-full bg-[#e0485e] px-2 py-0.5 text-[10px] font-black tracking-wider text-white">B</span>
      <div className="absolute right-3 bottom-2">
        <Chip value={500} size={28} />
      </div>
    </div>
  );
}
