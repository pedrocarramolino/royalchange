import { registerSW } from 'virtual:pwa-register';
import { isNativeApp } from '@/lib/firebase';

/**
 * Service worker de la PWA (la app abre sin conexión y se actualiza sola). En el APK no hace falta:
 * la app va empaquetada. Tampoco en desarrollo ni en local (no se cachean compilaciones de prueba).
 */
export function registerServiceWorker() {
  const local = ['localhost', '127.0.0.1'].includes(location.hostname);
  if (isNativeApp || import.meta.env.DEV || local || !('serviceWorker' in navigator)) return;
  registerSW({
    immediate: true,
    // iOS deja la app instalada en segundo plano y al volver no la recarga: sin esto seguiría con la
    // versión anterior hasta cerrarla del todo. Se busca versión nueva cada vez que vuelve a primer plano.
    onRegisteredSW(_url, registration) {
      if (!registration) return;
      document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') void registration.update().catch(() => undefined);
      });
    },
  });
  // Cachés de la versión anterior (Kotlin): ya no se usan.
  void caches
    ?.keys()
    .then((keys) => Promise.all(keys.filter((key) => key.startsWith('royal-chance-')).map((key) => caches.delete(key))))
    .catch(() => undefined);
}
