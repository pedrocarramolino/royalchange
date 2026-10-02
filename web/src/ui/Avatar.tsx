import type { AvatarId } from '@/domain/validation';
import { Suit, type SuitName } from './Suit';

const AVATAR_STYLE: Record<AvatarId, { suit: SuitName; color: string; name: string }> = {
  SpadeGold: { suit: 'spades', color: '#d4af6a', name: 'Pica dorada' },
  HeartRuby: { suit: 'hearts', color: '#e0485e', name: 'Corazón rubí' },
  DiamondGold: { suit: 'diamonds', color: '#e6c887', name: 'Diamante dorado' },
  ClubEmerald: { suit: 'clubs', color: '#5cc79e', name: 'Trébol esmeralda' },
  SpadeIvory: { suit: 'spades', color: '#f3ede0', name: 'Pica marfil' },
  HeartGold: { suit: 'hearts', color: '#f3dfa2', name: 'Corazón dorado' },
  DiamondRuby: { suit: 'diamonds', color: '#e0485e', name: 'Diamante rubí' },
  ClubGold: { suit: 'clubs', color: '#d4af6a', name: 'Trébol dorado' },
};

export const avatarName = (id: AvatarId) => AVATAR_STYLE[id].name;

/** Medallón con el palo del jugador. */
export function Avatar({ id, size = 48, selected }: { id: AvatarId; size?: number; selected?: boolean }) {
  const style = AVATAR_STYLE[id];
  return (
    <span
      className="relative grid shrink-0 place-items-center rounded-full"
      style={{
        width: size,
        height: size,
        background: 'radial-gradient(circle at 35% 30%, #1d4a3c 0%, #0b2a21 60%, #06150f 100%)',
        boxShadow: selected
          ? '0 0 0 2px #0e0f13, 0 0 0 4px #f3dfa2, 0 6px 16px -6px rgb(0 0 0 / 0.8)'
          : 'inset 0 0 0 1.5px rgb(212 175 106 / 0.7), 0 6px 16px -6px rgb(0 0 0 / 0.8)',
      }}
      aria-hidden
    >
      <span className="grid size-full place-items-center" style={{ color: style.color }}>
        <Suit suit={style.suit} className="size-[46%] drop-shadow-[0_2px_3px_rgb(0_0_0/0.6)]" />
      </span>
    </span>
  );
}
