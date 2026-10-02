import { Suit } from './Suit';

/** Emblema de Royal Chance: pica dorada en medallón. */
export function BrandMark({ size = 72 }: { size?: number }) {
  return (
    <span
      className="grid shrink-0 place-items-center rounded-full"
      style={{
        width: size,
        height: size,
        background: 'radial-gradient(circle at 40% 30%, #1b5a46 0%, #0b2a21 55%, #050d0a 100%)',
        boxShadow: 'inset 0 0 0 2px rgb(212 175 106 / 0.85), inset 0 0 0 5px #0e0f13, inset 0 0 0 6px rgb(212 175 106 / 0.4), 0 12px 30px -10px rgb(0 0 0 / 0.9)',
      }}
      aria-hidden
    >
      <span className="text-gold-gradient">
        <svg viewBox="0 0 100 100" style={{ width: size * 0.46, height: size * 0.46 }}>
          <defs>
            <linearGradient id="rc-brand-gold" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stopColor="#fbefc8" />
              <stop offset="0.5" stopColor="#d9b46c" />
              <stop offset="1" stopColor="#9c7a3c" />
            </linearGradient>
          </defs>
          <g fill="url(#rc-brand-gold)">
            <path d="M50 3C62 21 95 39 95 62c0 14-11 23-23 23-8 0-15-4-19-11 1 10 5 17 13 22H34c8-5 12-12 13-22-4 7-11 11-19 11C16 85 5 76 5 62 5 39 38 21 50 3Z" />
          </g>
        </svg>
      </span>
    </span>
  );
}

export function Wordmark({ className = '' }: { className?: string }) {
  return <span className={`font-display font-semibold tracking-[0.32em] text-gold-gradient ${className}`}>ROYAL CHANCE</span>;
}

/** Pantalla de arranque mientras se recupera la sesión. */
export function Splash() {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-5 bg-obsidian" role="status" aria-label="Cargando Royal Chance">
      <div className="animate-pulse">
        <BrandMark size={88} />
      </div>
      <Wordmark className="text-lg" />
    </div>
  );
}

export { Suit };
