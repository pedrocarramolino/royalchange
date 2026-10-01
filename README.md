# Royal Chance

Casino social multiplataforma con fichas virtuales **sin valor monetario**.
Kotlin Multiplatform + Compose Multiplatform: PWA (web), Android y escritorio (desarrollo).

Versión 1.0.0 · Blackjack, ruleta europea, tragaperras, dados y póker Texas Hold'em contra bots,
con niveles, bono diario, logros, historial y estadísticas. En producción:
https://royalchance-92769.web.app

Decisiones técnicas: [docs/architecture.md](docs/architecture.md).

## Requisitos

- JDK 17 o superior (probado con JDK 21). Los emuladores de Firebase también lo usan.
- Android SDK (Android Studio o IntelliJ IDEA 2026.1.2+ con el plugin de Kotlin Multiplatform).
- Google Chrome (tests de módulos con UI y generación de iconos).
- Node.js + Firebase CLI (`npm install -g firebase-tools`).

El wrapper de Gradle descarga la versión correcta de Gradle; no hace falta instalarlo.

## Datos: emuladores o proyecto real

La web y Android usan Firebase (Auth + Firestore). El escritorio usa siempre datos en memoria.

Por defecto la app usa el **proyecto real** (`royalchance-92769`, configurado en
`firebase.properties`). Para desarrollar sin tocar datos reales, usa los **emuladores locales**:
arráncalos y compila con `-Proyalchance.firebase.emulators=true` (usan el proyecto aislado
`demo-royalchance`, que nunca llega a la nube).

```bash
firebase emulators:start --only auth,firestore --project demo-royalchance
```
```bash
./gradlew :webApp:wasmJsBrowserDevelopmentRun -Proyalchance.firebase.emulators=true
```

Para que sea el comportamiento por defecto en tu equipo, añade `royalchance.firebase.emulators=true`
a `~/.gradle/gradle.properties`.

Interfaz de los emuladores (usuarios, documentos, emails de verificación): http://127.0.0.1:4000

Desde el emulador de Android, la app llega a los emuladores de Firebase por `10.0.2.2`. En un
móvil físico hace falta el proyecto real.

## Ejecutar

En Windows usa `.\gradlew.bat`; en macOS/Linux, `./gradlew`.

| Qué | Comando |
|---|---|
| Escritorio con Hot Reload | `.\gradlew.bat :desktopApp:hotRun` |
| Web en desarrollo (http://localhost:8080) | `.\gradlew.bat :webApp:wasmJsBrowserDevelopmentRun` |
| Android (dispositivo o emulador conectado) | `.\gradlew.bat :androidApp:installDebug` |
| Tests de Kotlin (unidad, UI y extremo a extremo; JVM + Wasm) | `.\gradlew.bat allTests` |
| Cobertura (`build/reports/kover/html`) | `.\gradlew.bat koverHtmlReport` |
| Tests de las reglas de Firestore | ver abajo |

Con el servidor web de desarrollo en marcha, `allTests` puede quedarse sin memoria: páralo antes
(y `.\gradlew.bat --stop`).

Tests de las reglas de seguridad (la primera vez: `npm --prefix firebase/tests install`):

```bash
firebase emulators:exec --only firestore --project demo-royalchance "npm --prefix firebase/tests test"
```

## Proyecto real de Firebase

Hecho: apps web y Android registradas y su configuración pública en `firebase.properties`;
Authentication con el proveedor **Email/contraseña** (el único que usa la app) y Firestore en `eur3`.
La app no usa `google-services.json` ni su plugin: se inicializa con esos valores.

Las reglas de seguridad están desplegadas. Tras cambiarlas (y pasar sus tests):

```bash
firebase deploy --only firestore:rules --project royalchance-92769
```

Recomendado: en Google Cloud Console, restringir cada API key a su app (dominios de la web y
paquete + SHA-1 de Android).

## Publicar la PWA

```bash
./gradlew :webApp:wasmJsBrowserDistribution
firebase deploy --only hosting --project royalchance-92769
```

Si cambias algún archivo de `webApp/src/wasmJsMain/resources` (HTML, scripts, iconos), sube
`CACHE_VERSION` en `sw.js`: así los dispositivos con la versión anterior en caché la descartan.
Los usuarios ven la versión nueva al segundo arranque (el service worker sirve primero la caché).

Probar la build de producción en local contra los emuladores:

```bash
firebase emulators:start --only auth,firestore,hosting --project demo-royalchance
```

## Publicar en Google Play

1. Crea la clave de subida (una sola vez; guárdala fuera del repositorio y haz copia):

   ```bash
   keytool -genkeypair -v -keystore royalchance-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Crea `keystore.properties` en la raíz (está en `.gitignore`):

   ```properties
   storeFile=../ruta/a/royalchance-upload.jks
   storePassword=…
   keyAlias=upload
   keyPassword=…
   ```

3. Genera el bundle firmado: `.\gradlew.bat :androidApp:bundleRelease` →
   `androidApp/build/outputs/bundle/release/androidApp-release.aab`. Sin `keystore.properties`
   sale sin firmar.
4. En Play Console activa *Play App Signing* y sube el `.aab`. Sube `versionCode` (androidApp) en
   cada publicación y mantén `versionName` igual a `AppInfo.VERSION` (shared).
5. Añade la huella SHA-1 de la clave de Play a la app Android en la consola de Firebase.

El release usa R8 (`androidApp/proguard-rules.pro`): prueba el `.aab`/`.apk` de release en un
dispositivo antes de publicarlo.

## Antes de publicar

- [ ] Sustituir los **textos legales** (Términos y Privacidad): los incluidos son un borrador
      marcado como tal en la app. Indicar el responsable del tratamiento.
- [ ] Revisar la política de **casino social** de Google Play (sin dinero real ni premios,
      clasificación de edad, declaración de que las fichas no tienen valor) y la sección de
      seguridad de los datos (email, alias, país, año de nacimiento, progreso).
- [ ] Restringir las API keys (ver arriba) y valorar Firebase **App Check**.
- [ ] Probar el release de Android en un dispositivo real y la PWA en iPhone (teclado,
      rendimiento) y Android.
- [ ] Revisar las cuotas del plan gratuito de Firebase (lecturas y escrituras de Firestore).

## Integración continua

`.github/workflows/ci.yml` (GitHub Actions) ejecuta en cada push a `main`/`develop` y en cada pull
request: todos los tests con su cobertura, los builds de Android, web y escritorio, y los tests de
las reglas en el emulador. No despliega: publicar sigue siendo manual.

## Estructura

```
androidApp/          host Android (Firebase)
desktopApp/          host de escritorio (desarrollo, datos en memoria)
webApp/              host web: index.html, manifest, service worker, iconos
shared/              App(), AppGraph (inyección de dependencias) y navegación
core/audio           efectos de sonido sintetizados y su reproducción por plataforma
core/common          utilidades puras: RandomGenerator, Outcome, nombres de países
core/designsystem    tema "Noir & Oro", tipografía, iconos, componentes y animaciones
core/ui              componentes con conocimiento del dominio (avatares, país, saldo)
core/testing         dobles de prueba: generadores deterministas, TestClock
domain/              contratos y reglas de negocio (Kotlin puro)
engine/*             motores de juego puros: cards, blackjack, roulette, slots, dice, poker
data/                repositorios en memoria, ajustes, sesiones de mesa e historial
data/firebase        Firebase con los SDK oficiales de Android y JavaScript
feature/auth         bienvenida, login, registro completo, recuperación, legales
feature/lobby        lobby del casino
feature/blackjack    mesa de blackjack
feature/roulette     ruleta europea
feature/slots        tragaperras
feature/dice         dados
feature/poker        póker contra bots
feature/profile      progreso: nivel, estadísticas y logros
feature/history      historial y estadísticas por juego
feature/settings     ajustes (tema, sonido, animaciones) y cuenta
firebase/            reglas de Firestore, índices y sus tests
build-logic/         convention plugins de Gradle
branding/            SVG maestros del icono y script de generación de PNG
docs/                arquitectura, decisiones y recursos de terceros
```

Regenerar los iconos de la PWA tras cambiar `branding/*.svg`:

```powershell
powershell -ExecutionPolicy Bypass -File branding/render-icons.ps1
```
