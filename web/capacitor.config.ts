import type { CapacitorConfig } from '@capacitor/cli';

/** App Android (APK): la misma web empaquetada. Mismo id que la app anterior para actualizarla. */
const config: CapacitorConfig = {
  appId: 'com.royalchance.app',
  appName: 'Royal Chance',
  webDir: 'dist',
  backgroundColor: '#0E0F13',
  server: {
    // La app empaquetada se identifica con el dominio de la web: si la API key de Firebase está
    // restringida por dominio, la APK también entra (los archivos se sirven igualmente del APK).
    hostname: 'royalchance-92769.web.app',
    androidScheme: 'https',
  },
  android: {
    // Sin contenido mixto ni depuración remota en las versiones publicadas.
    allowMixedContent: false,
  },
};

export default config;
