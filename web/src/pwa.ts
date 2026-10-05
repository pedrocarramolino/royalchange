import { registerSW } from 'virtual:pwa-register';
import { isNativeApp } from '@/lib/firebase';

/** Cada cuánto se busca versión nueva mientras la app está abierta y a la vista. */
const CHECK_EVERY_MS = 60_000;
/** Cada cuánto se vuelve a mirar si ya se puede recargar (el jugador ha salido de la mesa). */
const RETRY_MS = 3_000;

/** En una mesa no se recarga: se espera a que el jugador vuelva al casino. */
const atTable = () => location.pathname.includes('/mesa/');

/**
 * Service worker de la PWA (la app abre sin conexión y se actualiza sola). En el APK no hace falta:
 * la app va empaquetada. Tampoco en desarrollo ni en local (no se cachean compilaciones de prueba).
 *
 * Se busca versión nueva al volver a primer plano y cada minuto mientras se usa; cuando la hay, la
 * app se recarga sola en cuanto el jugador no está en una mesa (o ya, si está en segundo plano).
 */
export function registerServiceWorker() {
  const local = ['localhost', '127.0.0.1'].includes(location.hostname);
  if (isNativeApp || import.meta.env.DEV || local || !('serviceWorker' in navigator)) return;

  let applying = false;
  const applyWhenSafe = () => {
    if (applying) return;
    if (atTable() && document.visibilityState === 'visible') {
      setTimeout(applyWhenSafe, RETRY_MS);
      return;
    }
    applying = true;
    // Activa la versión nueva y recarga la página.
    void updateSW(true);
  };

  const updateSW = registerSW({
    immediate: true,
    onNeedRefresh: applyWhenSafe,
    // iOS deja la app instalada en segundo plano y al volver no la recarga: sin esto seguiría con la
    // versión anterior hasta cerrarla del todo.
    onRegisteredSW(_url, registration) {
      if (!registration) return;
      const check = () => {
        if (document.visibilityState === 'visible' && navigator.onLine !== false) void registration.update().catch(() => undefined);
      };
      document.addEventListener('visibilitychange', check);
      setInterval(check, CHECK_EVERY_MS);
    },
  });

  // Cachés de la versión anterior (Kotlin): ya no se usan.
  void caches
    ?.keys()
    .then((keys) => Promise.all(keys.filter((key) => key.startsWith('royal-chance-')).map((key) => caches.delete(key))))
    .catch(() => undefined);
}
