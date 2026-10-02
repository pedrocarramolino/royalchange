import type { Transition } from 'motion/react';

/*
 * Movimiento de la app: las mismas curvas que --ease-* en styles/index.css.
 * - Entrar o salir: EASE_OUT (arranca rápido; responde al instante).
 * - Moverse o transformarse en pantalla: EASE_IN_OUT.
 * - Lo que se puede interrumpir o tiene que sentirse vivo: SPRING.
 */
export const EASE_OUT: [number, number, number, number] = [0.23, 1, 0.32, 1];
export const EASE_IN_OUT: [number, number, number, number] = [0.77, 0, 0.175, 1];
export const EASE_OUT_CSS = 'cubic-bezier(0.23, 1, 0.32, 1)';
/** Rueda que gira varios segundos y se frena poco a poco (ruleta, ruleta diaria). */
export const EASE_SPIN_CSS = 'cubic-bezier(0.12, 0.6, 0.25, 1)';
export const EASE_IN_OUT_CSS = 'cubic-bezier(0.77, 0, 0.175, 1)';

/** Muelle al estilo de Apple: rebote apenas perceptible. */
export const SPRING: Transition = { type: 'spring', duration: 0.5, bounce: 0.2 };

/**
 * Ficha que se deja en el paño: cae un poco desde la mano y se asienta. Rápida y sutil, porque se
 * apuesta muchas veces por partida; nunca aparece de la nada (sin escalas pequeñas).
 */
export const CHIP_DROP = {
  initial: { opacity: 0, transform: 'translateY(-10px) scale(1.12)' },
  animate: { opacity: 1, transform: 'translateY(0px) scale(1)' },
  transition: { duration: 0.18, ease: EASE_OUT } satisfies Transition,
};
