// Service worker de Royal Chance (PWA).
//
// - Navegación (index.html): red primero y caché como respaldo, para que la app abra sin conexión.
// - Resto de recursos propios (wasm, js, fuentes, iconos): caché primero y actualización en segundo
//   plano; la siguiente apertura usa la versión nueva.
// - Peticiones a otros orígenes (Firebase, etc.) nunca pasan por esta caché.
//
// Incrementa CACHE_VERSION al cambiar este archivo para descartar las cachés anteriores.
const CACHE_VERSION = 'v12';
const CACHE_PREFIX = 'royal-chance-';
const CACHE_NAME = CACHE_PREFIX + CACHE_VERSION;

const APP_SHELL = [
  './',
  'index.html',
  'styles.css',
  'ios-keyboard.js',
  'manifest.webmanifest',
  'icons/icon.svg',
  'icons/icon-192.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then((cache) => cache.addAll(APP_SHELL))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(
        keys
          .filter((key) => key.startsWith(CACHE_PREFIX) && key !== CACHE_NAME)
          .map((key) => caches.delete(key)),
      ))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;
  if (new URL(request.url).origin !== self.location.origin) return;

  if (request.mode === 'navigate') {
    event.respondWith(networkFirst(request));
  } else {
    event.respondWith(staleWhileRevalidate(event, request));
  }
});

async function networkFirst(request) {
  const cache = await caches.open(CACHE_NAME);
  try {
    const response = await fetch(request);
    if (response.status === 200) await cache.put(request, response.clone());
    return response;
  } catch (error) {
    return (await cache.match(request)) || (await cache.match('index.html')) || Response.error();
  }
}

async function staleWhileRevalidate(event, request) {
  const cache = await caches.open(CACHE_NAME);
  const cached = await cache.match(request);
  const update = fetch(request).then(async (response) => {
    if (response.status === 200) await cache.put(request, response.clone());
    return response;
  });

  if (cached) {
    event.waitUntil(update.catch(() => undefined));
    return cached;
  }
  return update;
}
