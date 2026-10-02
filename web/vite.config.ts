import { fileURLToPath, URL } from 'node:url';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      // El service worker se actualiza solo: la siguiente apertura usa la versión nueva.
      registerType: 'autoUpdate',
      // En Capacitor (APK) la app ya va empaquetada: el registro se omite en src/pwa.ts.
      injectRegister: false,
      filename: 'sw.js',
      includeAssets: ['icons/*.png', 'icons/icon.svg', 'fonts/*.woff2'],
      manifest: {
        id: './',
        name: 'Royal Chance',
        short_name: 'Royal Chance',
        description: 'Casino social con fichas virtuales sin valor monetario.',
        lang: 'es',
        start_url: './',
        scope: './',
        display: 'standalone',
        orientation: 'any',
        background_color: '#0E0F13',
        theme_color: '#0E0F13',
        categories: ['games', 'entertainment'],
        icons: [
          { src: 'icons/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
          { src: 'icons/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
          { src: 'icons/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,woff2,png,svg,webmanifest}'],
        navigateFallback: 'index.html',
        cleanupOutdatedCaches: true,
        // Las peticiones a Firebase nunca pasan por la caché del service worker.
        navigateFallbackDenylist: [/^\/__/],
      },
    }),
  ],
  build: {
    target: 'es2022',
    chunkSizeWarningLimit: 1200,
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
});
