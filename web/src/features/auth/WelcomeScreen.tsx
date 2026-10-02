import { useNavigate } from 'react-router';
import { motion } from 'motion/react';
import { Button } from '@/ui/Button';
import { PlayingCard, type Rank } from '@/ui/PlayingCard';
import type { SuitName } from '@/ui/Suit';
import { BrandMark, Wordmark } from '@/ui/Brand';

const HAND: [Rank, SuitName][] = [
  ['10', 'spades'],
  ['J', 'clubs'],
  ['Q', 'diamonds'],
  ['K', 'hearts'],
  ['A', 'spades'],
];

/** Bienvenida: una escalera real en abanico sobre el resplandor del tapete. */
export function WelcomeScreen() {
  const navigate = useNavigate();
  return (
    <div className="relative h-full overflow-y-auto">
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_90%_55%_at_50%_30%,rgb(15_91_69/0.55),transparent_70%)]" aria-hidden />
      <div className="safe-top safe-pb-8 safe-px-6 relative mx-auto flex min-h-full max-w-md flex-col">
        <div className="mt-8 flex items-center justify-center gap-3">
          <BrandMark size={40} />
          <Wordmark className="text-[15px]" />
        </div>

        <div className="relative mx-auto mt-10 h-56 w-72" aria-label="Escalera real de picas a ases" role="img">
          {HAND.map(([rank, suit], i) => {
            const angle = (i - 2) * 13;
            return (
              <motion.div
                key={rank}
                className="absolute bottom-0 left-1/2 origin-[50%_110%]"
                style={{ marginLeft: -58 }}
                initial={{ rotate: 0, y: 40, opacity: 0 }}
                animate={{ rotate: angle, y: Math.abs(i - 2) * 6, opacity: 1 }}
                transition={{ delay: 0.15 + i * 0.08, type: 'spring', damping: 18, stiffness: 140 }}
              >
                <PlayingCard rank={rank} suit={suit} width={116} />
              </motion.div>
            );
          })}
        </div>

        <h1 className="mt-12 text-center font-display text-[32px] leading-tight font-semibold text-gold-gradient">Bienvenido a la mesa</h1>
        <p className="mx-auto mt-3 max-w-xs text-center text-[15px] leading-relaxed text-ivory-dim">
          Blackjack, ruleta, póker, slots y dados. Juega con fichas virtuales, sube de nivel y consigue logros.
        </p>

        <div className="mt-auto flex flex-col gap-3 pt-10">
          <Button size="lg" block onClick={() => navigate('/registro')}>
            Crear cuenta
          </Button>
          <Button size="lg" variant="secondary" block onClick={() => navigate('/entrar')}>
            Iniciar sesión
          </Button>
          <p className="mt-3 text-center text-xs leading-relaxed text-mute">
            Solo para mayores de 18 años. Las fichas son virtuales: no se compran, no se canjean y no tienen valor monetario.
          </p>
        </div>
      </div>
    </div>
  );
}
