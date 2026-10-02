import { useLayoutEffect, type RefObject } from 'react';
import { useSettings } from '@/data/settings';
import { EASE_OUT_CSS } from '@/ui/motion';
import { useScreenToTable } from './GameShell';

/**
 * Reparto de una carta: al aparecer, sale de [origin] (el zapato, el centro de la mesa…) y llega a
 * su sitio. Se mide en la pantalla y se pasa a la mesa, que puede ir girada. Hasta que le toca
 * (`delay`, en segundos) espera escondida en el origen. Con WAAPI y solo transform y opacidad: va
 * por la GPU. `tilt` es el giro con el que la carta queda en la mesa (en grados).
 *
 * La mesa lleva `data-table` y el origen se busca dentro de ella con el selector [origin].
 */
export function useDealFrom(ref: RefObject<HTMLElement | null>, origin: string, delay: number | undefined, tilt = 0) {
  const toTable = useScreenToTable();
  const reducedMotion = useSettings((s) => s.reducedMotion);

  // Solo al aparecer la carta.
  useLayoutEffect(() => {
    const element = ref.current;
    const from = element?.closest('[data-table]')?.querySelector(origin);
    if (delay === undefined || !element || !from) return;
    const a = from.getBoundingClientRect();
    const b = element.getBoundingClientRect();
    const { x, y } = toTable(a.left + a.width / 2 - (b.left + b.width / 2), a.top + a.height / 2 - (b.top + b.height / 2));
    const animation = element.animate(
      reducedMotion
        ? [{ opacity: 0 }, { opacity: 1 }]
        : [
            { transform: `translate(${x}px, ${y}px) rotate(${tilt - 14}deg) scale(0.92)`, opacity: 0 },
            { transform: `translate(0px, 0px) rotate(${tilt}deg) scale(1)`, opacity: 1 },
          ],
      { duration: reducedMotion ? 150 : 450, delay: delay * 1000, easing: EASE_OUT_CSS, fill: 'backwards' },
    );
    return () => animation.cancel();
  }, []);
}
