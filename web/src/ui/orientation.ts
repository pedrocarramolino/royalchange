import { useEffect, useSyncExternalStore } from 'react';
import { create } from 'zustand';

export interface Viewport {
  width: number;
  height: number;
}

function readViewport(): Viewport {
  const vv = window.visualViewport;
  return { width: Math.round(vv?.width ?? window.innerWidth), height: Math.round(vv?.height ?? window.innerHeight) };
}

let current = readViewport();
const listeners = new Set<() => void>();
const update = () => {
  const next = readViewport();
  if (next.width === current.width && next.height === current.height) return;
  current = next;
  listeners.forEach((l) => l());
};
window.addEventListener('resize', update);
window.addEventListener('orientationchange', () => setTimeout(update, 150));
window.visualViewport?.addEventListener('resize', update);
// Por si algún navegador no avisa del cambio de tamaño al girar.
window.matchMedia('(orientation: landscape)').addEventListener('change', update);

/** Tamaño visible de la ventana, reactivo. */
export function useViewport(): Viewport {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => current,
  );
}

/** Un móvil: lado corto por debajo de 600 px (tablets y escritorio usan cualquier orientación). */
export const isPhone = (v: Viewport) => Math.min(v.width, v.height) < 600;
export const isLandscape = (v: Viewport) => v.width > v.height;

interface OrientationStore {
  /** Pantallas montadas que piden horizontal (las mesas). */
  landscapeRequests: number;
}

export const useOrientationStore = create<OrientationStore>(() => ({ landscapeRequests: 0 }));

/** La pantalla que la llama se juega en horizontal (en el móvil). */
export function useRequireLandscape() {
  useEffect(() => {
    useOrientationStore.setState((s) => ({ landscapeRequests: s.landscapeRequests + 1 }));
    return () => useOrientationStore.setState((s) => ({ landscapeRequests: s.landscapeRequests - 1 }));
  }, []);
}

/**
 * Fija la orientación donde la plataforma lo permite (APK con Capacitor; en la web solo la PWA
 * instalada en Android). Safari en iOS no lo permite: ahí el aviso pide girar el móvil.
 */
export async function lockOrientation(landscape: boolean): Promise<void> {
  const capacitor = (window as { Capacitor?: { isNativePlatform?: () => boolean } }).Capacitor;
  if (capacitor?.isNativePlatform?.()) {
    // APK: el plugin nativo fija la orientación (en tablets se deja libre).
    if (Math.min(window.screen.width, window.screen.height) >= 600) return;
    const { ScreenOrientation } = await import('@capacitor/screen-orientation');
    await ScreenOrientation.lock({ orientation: landscape ? 'landscape' : 'portrait' }).catch(() => undefined);
    return;
  }
  try {
    const orientation = screen.orientation as ScreenOrientation & { lock?: (o: string) => Promise<void> };
    await orientation.lock?.(landscape ? 'landscape' : 'portrait');
  } catch {
    // Navegador que no lo permite (o no instalado): el aviso de girar hace el resto.
  }
}

function readAngle(): number {
  const angle = screen.orientation?.angle ?? (window as { orientation?: number }).orientation ?? 0;
  return ((angle % 360) + 360) % 360;
}

let currentAngle = readAngle();
const angleListeners = new Set<() => void>();
const updateAngle = () => {
  const next = readAngle();
  if (next === currentAngle) return;
  currentAngle = next;
  angleListeners.forEach((l) => l());
};
screen.orientation?.addEventListener?.('change', updateAngle);
window.addEventListener('orientationchange', updateAngle);
window.addEventListener('resize', updateAngle);

/**
 * Ángulo de la pantalla respecto a la posición natural del móvil: 0 (vertical), 90 (girado a la
 * izquierda, la parte de arriba del móvil queda a la izquierda) o 270 (girado a la derecha).
 */
export function useScreenAngle(): number {
  return useSyncExternalStore(
    (listener) => {
      angleListeners.add(listener);
      return () => angleListeners.delete(listener);
    },
    () => currentAngle,
  );
}
