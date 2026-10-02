import type { CSSProperties } from 'react';
import { motion } from 'motion/react';
import { PlayingCard, type Rank } from '@/ui/PlayingCard';
import type { SuitName } from '@/ui/Suit';
import { EASE_IN_OUT } from '@/ui/motion';

const FACE: CSSProperties = { backfaceVisibility: 'hidden', WebkitBackfaceVisibility: 'hidden' };

/**
 * Carta con sus dos caras en 3D: al pasar de boca abajo a boca arriba gira sobre su eje vertical y
 * se ve primero el dorso y después el frente, como una carta de verdad. Con [dealtFaceDown] llega
 * boca abajo y se destapa sola tras [delay] (baccarat, video póker).
 */
export function FlipCard({
  rank,
  suit,
  faceDown,
  width,
  delay = 0,
  dealtFaceDown = false,
}: {
  rank: Rank;
  suit: SuitName;
  faceDown: boolean;
  width: number;
  delay?: number;
  dealtFaceDown?: boolean;
}) {
  return (
    <div style={{ width, height: (width * 350) / 250, perspective: width * 6 }}>
      <motion.div
        className="relative size-full"
        style={{ transformStyle: 'preserve-3d' }}
        initial={dealtFaceDown ? { transform: 'rotateY(180deg)' } : false}
        animate={{ transform: faceDown ? 'rotateY(180deg)' : 'rotateY(0deg)' }}
        transition={{ duration: 0.45, ease: EASE_IN_OUT, delay }}
      >
        <div className="absolute inset-0" style={FACE} aria-hidden={faceDown}>
          <PlayingCard rank={rank} suit={suit} width={width} />
        </div>
        <div className="absolute inset-0" style={{ ...FACE, transform: 'rotateY(180deg)' }} aria-hidden={!faceDown}>
          <PlayingCard rank={rank} suit={suit} width={width} faceDown />
        </div>
      </motion.div>
    </div>
  );
}
