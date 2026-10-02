import { useEffect, type ReactNode } from 'react';
import { motion } from 'motion/react';
import { Button } from './Button';
import { isLandscape, isPhone, lockOrientation, useOrientationStore, useViewport } from './orientation';

/**
 * Orientación de la app: vertical, salvo en las mesas, que van en horizontal. Donde se puede se
 * fija; en un móvil en la orientación equivocada se muestra encima un aviso para girarlo (en una
 * mesa, con un botón para salir por si el giro automático está bloqueado).
 */
export function OrientationGate({ children, onLeaveTable }: { children: ReactNode; onLeaveTable: () => void }) {
  const wantsLandscape = useOrientationStore((s) => s.landscapeRequests > 0);
  const viewport = useViewport();

  useEffect(() => {
    void lockOrientation(wantsLandscape);
  }, [wantsLandscape]);

  const wrong = isPhone(viewport) && isLandscape(viewport) !== wantsLandscape;
  return (
    <>
      <div className="h-full" aria-hidden={wrong || undefined} inert={wrong || undefined}>
        {children}
      </div>
      {wrong && <RotatePrompt toLandscape={wantsLandscape} onLeave={wantsLandscape ? onLeaveTable : undefined} />}
    </>
  );
}

function RotatePrompt({ toLandscape, onLeave }: { toLandscape: boolean; onLeave?: () => void }) {
  return (
    <div className="safe-top safe-bottom fixed inset-0 z-[60] flex flex-col items-center justify-center gap-6 bg-obsidian px-8 text-center" role="alertdialog" aria-live="polite">
      <motion.div
        className="h-24 w-14 rounded-xl border-[3px] border-gold bg-ink-2 shadow-[0_0_30px_-6px_rgb(212_175_106/0.5)]"
        animate={{ rotate: toLandscape ? [0, 90, 90, 0] : [90, 0, 0, 90] }}
        transition={{ duration: 2.4, repeat: Infinity, ease: 'easeInOut', times: [0, 0.4, 0.7, 1] }}
        aria-hidden
      />
      <div>
        <h2 className="font-display text-2xl font-semibold text-gold-light">{toLandscape ? 'Gira el móvil para jugar' : 'Pon el móvil en vertical'}</h2>
        <p className="mt-2 text-ivory-dim">{toLandscape ? 'Las mesas se juegan en horizontal.' : 'Esta pantalla se usa en vertical.'}</p>
      </div>
      {onLeave && (
        <Button variant="secondary" onClick={onLeave}>
          Volver al casino
        </Button>
      )}
    </div>
  );
}
