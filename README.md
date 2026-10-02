# Royal Chance

Casino social con fichas virtuales **sin valor monetario**: blackjack, ruleta europea,
tragaperras, dados y póker Texas Hold'em contra bots, con niveles, bono diario, logros, historial y
estadísticas. En producción: https://royalchance-92769.web.app

Versión 2.0.0 · React + TypeScript (web instalable) y la misma app empaquetada para Android con
Capacitor. Los datos están en Firebase (Authentication + Firestore). Decisiones técnicas:
[docs/architecture.md](docs/architecture.md).

> La versión 1.x (Kotlin Multiplatform + Compose) está en el historial de git, etiqueta `v1.0.0`.

## Requisitos

- Node.js 24 (incluye npm) y Firebase CLI: `npm install -g firebase-tools`.
- Para la APK: JDK 21 y el Android SDK (Android Studio). Los emuladores de Firebase también usan
  el JDK.

```bash
cd web
npm install
```

## Desarrollo

Todo se ejecuta desde `web/`, salvo los emuladores (desde la raíz).

| Qué | Comando |
|---|---|
| Web contra los **emuladores** (recomendado) | `npm run dev:emuladores` → http://localhost:5173 |
| Web contra el **proyecto real** | `npm run dev` |
| Tipos | `npm run typecheck` |
| Tests (dominio, motores de juego) | `npm test` |
| Compatibilidad del dominio con las reglas de Firestore | `npm run test:reglas` |
| Compilación de producción (`web/dist`) | `npm run build` |

Emuladores de Firebase (proyecto aislado `demo-royalchance`, nunca toca la nube), desde la raíz:

```bash
firebase emulators:start --only auth,firestore --project demo-royalchance
```

Interfaz de los emuladores (usuarios, documentos, emails de verificación): http://127.0.0.1:4000

Tests de las reglas de seguridad (la primera vez: `npm --prefix firebase/tests install`). Ojo:
borran los datos del emulador, no los lances contra el que usas para desarrollar.

```bash
firebase emulators:exec --only firestore --project demo-royalchance "npm --prefix firebase/tests test"
```

## Proyecto real de Firebase

`royalchance-92769`: Authentication con **Email/contraseña** y Firestore en `eur3`. La
configuración pública de la web está en `web/.env` (no da acceso a nada: la seguridad la ponen las
reglas de `firebase/firestore.rules`). Tras cambiar las reglas y pasar sus tests:

```bash
firebase deploy --only firestore:rules --project royalchance-92769
```

## Publicar la web (PWA)

```bash
npm --prefix web run build
firebase deploy --only hosting --project royalchance-92769
```

El service worker (Workbox) se actualiza solo: los usuarios ven la versión nueva en el siguiente
arranque.

## Android (APK con Capacitor)

```bash
cd web
npm run build
npx cap sync android
cd android
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

Abrir en Android Studio: `npx cap open android`. Cada vez que cambie la web: `npm run build` y
`npx cap sync android`.

### Publicar en Google Play

1. Crea la clave de subida (una sola vez; guárdala fuera del repositorio y haz copia):

   ```bash
   keytool -genkeypair -v -keystore royalchance-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Crea `keystore.properties` en la raíz del repositorio (está en `.gitignore`); las rutas son
   relativas a la raíz:

   ```properties
   storeFile=../ruta/a/royalchance-upload.jks
   storePassword=…
   keyAlias=upload
   keyPassword=…
   ```

3. `cd web/android && ./gradlew bundleRelease` →
   `app/build/outputs/bundle/release/app-release.aab` (sin `keystore.properties` sale sin firmar).
4. En Play Console activa *Play App Signing* y sube el `.aab`. Sube `versionCode` en
   `web/android/app/build.gradle` en cada publicación (la 2.0.0 es el 2) y mantén `versionName`
   igual que `version` en `web/package.json`.

## Antes de publicar

- [ ] Sustituir los **textos legales** (Términos y Privacidad): los incluidos son un borrador
      marcado como tal en la app. Indicar el responsable del tratamiento.
- [ ] Revisar la política de **casino social** de Google Play (sin dinero real ni premios,
      clasificación de edad, declaración de que las fichas no tienen valor) y la sección de
      seguridad de los datos (email, alias, país, año de nacimiento, progreso).
- [ ] Restringir la API key web en Google Cloud Console a los dominios de la web
      (`royalchance-92769.web.app`, `royalchance-92769.firebaseapp.com`; la APK se identifica con
      el primero) y valorar Firebase **App Check**.
- [ ] Probar la APK de release en un móvil real y la PWA en iPhone y Android.
- [ ] Revisar las cuotas del plan gratuito de Firebase (lecturas y escrituras de Firestore).

## Integración continua

`.github/workflows/ci.yml` (GitHub Actions), en cada push a `main`/`develop` y en cada pull request:
tipos, tests, compilación de la web, APK de depuración y de release, tests de las reglas y de la
compatibilidad del dominio con ellas. No despliega: publicar sigue siendo manual.

## Estructura

```
web/                       la app (Vite + React 19 + TypeScript + Tailwind 4)
  src/domain/              reglas puras: economía, progresión, logros, validación, historial
  src/engine/              motores de juego puros: cartas, blackjack, ruleta, slots, dados, póker
  src/data/                Firebase: sesión y perfil, monedero, historial; ajustes del dispositivo
  src/features/            pantallas: acceso, casino, progreso, historial, ajustes y las mesas
  src/ui/                  sistema visual: cartas, fichas, botones, campos, diálogos, orientación
  src/audio/               efectos de sonido sintetizados (Web Audio)
  public/                  iconos, fuentes y licencias
  android/                 proyecto Android de Capacitor
firebase/                  reglas de Firestore, índices y sus tests
branding/                  SVG maestros del icono y script de generación de PNG
docs/                      arquitectura y recursos de terceros
```

Regenerar los iconos tras cambiar `branding/*.svg`:

```powershell
powershell -ExecutionPolicy Bypass -File branding/render-icons.ps1
```
