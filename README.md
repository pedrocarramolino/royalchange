# Royal Chance

Casino social multiplataforma con fichas virtuales **sin valor monetario**.
Kotlin Multiplatform + Compose Multiplatform: PWA (web), Android y escritorio (desarrollo).

Decisiones técnicas: [docs/architecture.md](docs/architecture.md).

## Requisitos

- JDK 17 o superior (probado con JDK 21).
- Android SDK (Android Studio o IntelliJ IDEA 2026.1.2+ con el plugin de Kotlin Multiplatform).
- Google Chrome (tests de módulos con UI y generación de iconos).
- Node.js + Firebase CLI (`npm install -g firebase-tools`) para desplegar.

El wrapper de Gradle descarga la versión correcta de Gradle; no hace falta instalarlo.

## Ejecutar

En Windows usa `.\gradlew.bat`; en macOS/Linux, `./gradlew`.

| Qué | Comando |
|---|---|
| Escritorio con Hot Reload | `.\gradlew.bat :desktopApp:hotRun` |
| Escritorio (normal) | `.\gradlew.bat :desktopApp:run` |
| Web en desarrollo (http://localhost:8080) | `.\gradlew.bat :webApp:wasmJsBrowserDevelopmentRun` |
| Android (dispositivo o emulador conectado) | `.\gradlew.bat :androidApp:installDebug` |
| Todos los tests (JVM + Wasm) | `.\gradlew.bat allTests` |

## Publicar la PWA

```bash
./gradlew :webApp:wasmJsBrowserDistribution
firebase deploy --only hosting
```

Primera vez: crea el proyecto en la consola de Firebase (plan Spark), ejecuta `firebase login`
y `firebase use --add` para vincularlo (genera `.firebaserc`, que sí se sube al repositorio).

Probar la build de producción en local, sin cuenta de Firebase:

```bash
firebase emulators:start --only hosting --project demo-royalchance
```

## Estructura

```
androidApp/          host Android
desktopApp/          host de escritorio (desarrollo, datos en memoria)
webApp/              host web: index.html, manifest, service worker, iconos
shared/              App(), AppGraph (inyección de dependencias) y navegación
core/common          utilidades puras: RandomGenerator, Outcome, nombres de países
core/designsystem    tema "Noir & Oro", tipografía, iconos y componentes
core/ui              componentes con conocimiento del dominio (avatares, país)
core/testing         dobles de prueba: generadores deterministas, TestClock
domain/              contratos y reglas de negocio (Kotlin puro)
data/                implementaciones de los repositorios
feature/auth         bienvenida, login, registro completo, recuperación, legales
feature/lobby        lobby del casino
feature/profile      progreso (Fase 6)
feature/history      historial (Fase 11)
feature/settings     ajustes y cuenta
build-logic/         convention plugins de Gradle
branding/            SVG maestros del icono y script de generación de PNG
docs/                arquitectura, decisiones y recursos de terceros
```

Hasta la Fase 4 los datos viven en memoria: al cerrar la app se pierden las cuentas creadas.
El inicio de sesión con Google está simulado (entra con una cuenta de ejemplo).

Regenerar los iconos de la PWA tras cambiar `branding/*.svg`:

```powershell
powershell -ExecutionPolicy Bypass -File branding/render-icons.ps1
```
