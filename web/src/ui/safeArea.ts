import { useSyncExternalStore } from 'react';

/** Márgenes seguros del dispositivo (muesca, isla dinámica, barra de inicio) en píxeles. */
export interface SafeArea {
  top: number;
  right: number;
  bottom: number;
  left: number;
}

/**
 * Margen mínimo hacia el borde de la isla dinámica cuando el contenido va girado: la barra de
 * estado y la isla ocupan esa franja aunque Safari informe de 0 (p. ej. en el navegador).
 */
export const ISLAND_CLEARANCE = 48;

// Se miden con un elemento de prueba que usa env() directamente: más fiable que env() dentro de
// variables CSS, que Safari no siempre resuelve.
const probe = document.createElement('div');
probe.setAttribute('aria-hidden', 'true');
probe.style.cssText =
  'position:fixed;left:0;top:0;width:0;height:0;visibility:hidden;pointer-events:none;' +
  'padding:env(safe-area-inset-top) env(safe-area-inset-right) env(safe-area-inset-bottom) env(safe-area-inset-left)';
document.body.appendChild(probe);

function measure(): SafeArea {
  const style = getComputedStyle(probe);
  return {
    top: parseFloat(style.paddingTop) || 0,
    right: parseFloat(style.paddingRight) || 0,
    bottom: parseFloat(style.paddingBottom) || 0,
    left: parseFloat(style.paddingLeft) || 0,
  };
}

let current = measure();
const listeners = new Set<() => void>();

/** Publica los márgenes medidos como variables CSS (las usan .safe-top, .safe-x…). */
function apply() {
  const root = document.documentElement.style;
  root.setProperty('--safe-top', `${current.top}px`);
  root.setProperty('--safe-right', `${current.right}px`);
  root.setProperty('--safe-bottom', `${current.bottom}px`);
  root.setProperty('--safe-left', `${current.left}px`);
}

function update() {
  const next = measure();
  if (next.top === current.top && next.right === current.right && next.bottom === current.bottom && next.left === current.left) return;
  current = next;
  apply();
  listeners.forEach((l) => l());
}

apply();
window.addEventListener('resize', update);
// iOS actualiza los márgenes un poco después de girar.
window.addEventListener('orientationchange', () => {
  update();
  setTimeout(update, 250);
  setTimeout(update, 700);
});
window.visualViewport?.addEventListener('resize', update);

export function useSafeArea(): SafeArea {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => current,
  );
}

/**
 * Variables de márgenes para un contenido girado 90°: cada lado del contenido toma el margen del
 * borde físico que le cae, y el lado de la isla (la parte de arriba del móvil) nunca baja de
 * [ISLAND_CLEARANCE].
 *
 * @param clockwise giro de 90° en el sentido de las agujas del reloj (si no, en sentido contrario).
 * @param islandSide lado del contenido que cae sobre la parte de arriba del móvil.
 */
export function rotatedSafeArea(insets: SafeArea, clockwise: boolean, islandSide: 'top' | 'left'): Record<string, string> {
  // Sentido horario: arriba ← derecha, derecha ← abajo, abajo ← izquierda, izquierda ← arriba.
  // Antihorario: arriba ← izquierda, derecha ← arriba, abajo ← derecha, izquierda ← abajo.
  const mapped = clockwise
    ? { top: insets.right, right: insets.bottom, bottom: insets.left, left: insets.top }
    : { top: insets.left, right: insets.top, bottom: insets.right, left: insets.bottom };
  mapped[islandSide] = Math.max(mapped[islandSide], ISLAND_CLEARANCE);
  return {
    '--safe-top': `${mapped.top}px`,
    '--safe-right': `${mapped.right}px`,
    '--safe-bottom': `${mapped.bottom}px`,
    '--safe-left': `${mapped.left}px`,
  };
}
