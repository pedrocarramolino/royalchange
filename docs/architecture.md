# Royal Chance — Arquitectura y decisiones

Casino social con fichas virtuales **sin valor monetario**: no hay dinero real, depósitos, retiradas,
premios ni conversión de fichas. Este documento registra las decisiones técnicas y su motivo.
Se actualiza al cerrar cada fase.

## 1. Plataformas

| Plataforma | Cómo se entrega | Estado |
|---|---|---|
| Web / PWA | Kotlin/Wasm + Compose, Firebase Hosting | Activa. Cubre iPhone, iPad, Mac, Windows y Android desde el navegador |
| Android | App nativa (APK/AAB) | Activa |
| Escritorio (JVM) | Entorno de desarrollo: Hot Reload y pruebas rápidas | Activa como herramienta; no se distribuye por ahora |
| iOS nativo | — | Aplazado hasta disponer de un Mac. La arquitectura lo admite sin cambios |

Navegadores soportados: los que implementan WasmGC (Chrome/Edge y Firefox actuales, Safari 18.2+).

## 2. Stack (versiones fijadas en `gradle/libs.versions.toml`)

| Área | Elección |
|---|---|
| Lenguaje | Kotlin 2.4.20, Coroutines, Flow |
| UI | Compose Multiplatform 1.12.1, Material 3 (1.9.0, última estable) |
| Build | Gradle 9.7.0, AGP 9.3.3 (`com.android.kotlin.multiplatform.library`), convention plugins en `build-logic` |
| Datos (Fase 4) | Firebase: Auth + Firestore con caché offline, vía GitLive firebase-kotlin-sdk (no existe SDK oficial KMP) |
| Hosting | Firebase Hosting (plan Spark, gratuito) |
| Tests | kotlin.test en `commonTest`, ejecutados en JVM y en Wasm (Node.js) |

Compatibilidad verificada: Kotlin 2.4.20 admite Gradle ≤ 9.7.0 y AGP ≤ 9.3.x.

## 3. Módulos y reglas de dependencia

```
androidApp ─┐
desktopApp ─┼─► shared ──► feature:* ──► engine:* ──► core:common
webApp ─────┘     │            ├──► domain ─────► core:common
                  │            └──► core:designsystem, core:audio
                  └──► data ──► domain
```

| Módulo | Puede depender de | Nunca de |
|---|---|---|
| `engine:*` | `core:common`, `engine:cards` | Compose, `domain`, `data` |
| `domain` | `core:common` | Compose, Firebase |
| `data` | `domain` (+ Firebase) | UI |
| `feature:*` | `domain`, `engine:*`, `core:designsystem`, `core:audio` | `data`, Firebase |
| `shared` | todo (raíz de composición) | — |

Los módulos se crean cuando una fase los necesita. Existentes hoy: `shared`, `androidApp`,
`desktopApp`, `webApp`, `core:common`, `core:testing`.

**Convention plugins** (`build-logic`):
- `royalchance.kmp.library`: Kotlin puro (JVM + Wasm). Android consume su variante JVM, así que no
  necesita el plugin de Android (verificado en la Fase 2).
- `royalchance.kmp.compose`: módulos con UI (Android + JVM + Wasm + Compose).

iOS se añadirá en un único punto (`KotlinMultiplatform.kt`) cuando haya un Mac.

## 4. Principios

1. Los motores de juego son Kotlin puro: `(estado, acción) → (nuevo estado, eventos)`. Sin UI ni datos.
2. Un único punto escribe el saldo (`EconomyRepository`); ningún módulo de UI puede tocar los datos.
3. El resultado se decide y se contabiliza **antes** de animarlo. La animación nunca es fuente de azar.
4. Aleatoriedad (`RandomGenerator`) y tiempo (`kotlin.time.Clock`) siempre inyectados.
5. Mínimas dependencias externas; cada una justificada aquí.

## 5. Aleatoriedad

- `RandomGenerator` (`core:common`): `nextInt`, `nextInt(from, until)`, `shuffled` (Fisher–Yates).
- `ProductionRandomGenerator`: `SecureRandom` (JVM/Android) y `crypto.getRandomValues` (web).
  Enteros acotados sin sesgo (muestreo por rechazo de la stdlib).
- `TestRandomGenerator` (semilla) y `ScriptedRandomGenerator` (valores forzados) en `core:testing`.

## 6. Datos e integridad (Firebase, plan Spark)

- Inicio de sesión con Firebase Auth. Firestore con caché offline como almacenamiento local-first.
- Un documento por jugador (saldo, progreso, contadores); historial archivado por bloques.
  Objetivo: ~1 escritura por ronda (cuota gratuita: 20.000 escrituras/día en total).
- Reglas de seguridad: solo el propietario accede a sus datos, saldo ≥ 0, límites de variación y
  bono diario validado con la hora del servidor (`request.time`).
- Limitación conocida: sin Cloud Functions (plan Blaze) el cliente decide los resultados; los
  rankings no serán fiables hasta tener lógica en servidor. Los motores puros podrán ejecutarse allí.
- Región de Firestore: UE (irreversible una vez creada la base de datos).
- Sin Firebase Analytics (evita banner de consentimiento por rastreo).

## 7. Autenticación (requisito añadido en la Fase 2)

Login y registro completo. Propuesta pendiente de confirmar:

- **Registro**: alias único, email, contraseña + confirmación (requisitos y medidor de fortaleza),
  fecha de nacimiento (verificación 18+, se guarda solo el año), país, avatar predefinido,
  aceptación de Términos y Política de privacidad, aviso de fichas sin valor monetario,
  comunicaciones opcionales (desmarcadas por defecto) y verificación de email.
- **Login**: email + contraseña, mostrar contraseña, recuperación de contraseña, Google Sign-In,
  modo invitado opcional con vinculación posterior.
- **Cuenta**: cerrar sesión y **eliminar cuenta** (exigido por Google Play y por el RGPD).
- Avatares predefinidos: subir fotos requeriría Cloud Storage, que exige plan de pago.

## 8. Navegación

Navigation 3 (estable en Compose Multiplatform desde 1.10), con rutas `@Serializable` registradas
para serialización polimórfica (necesario fuera de Android).

```
Arranque ─► ¿sesión? ─ no ─► Bienvenida ─► Login | Registro | (Invitado)
                      └ sí ─► Shell adaptativo
Shell: Casino (lobby) · Progreso · Historial · Ajustes
       Juegos a pantalla completa desde el lobby
```

Layout: barra inferior (<600 dp), rail (600–840 dp), panel lateral + panel de información (≥840 dp).

## 9. Plan de fases

| Fase | Contenido | Estado |
|---|---|---|
| 1 | Análisis y arquitectura | Hecha |
| 2 | Proyecto KMP: Gradle, Android, escritorio, PWA, Hosting | Hecha |
| 3 | Sistema de diseño, navegación y pantallas de login/registro (con datos simulados) | — |
| 4 | Firebase: autenticación real + Firestore + reglas de seguridad | — |
| 5 | Economía de fichas | — |
| 6 | Niveles, bono diario, rachas y logros | — |
| 7 | Blackjack | — |
| 8 | Ruleta | — |
| 9 | Slots y Dados | — |
| 10 | Póker contra bots | — |
| 11 | Estadísticas e historial | — |
| 12 | Sonido y animaciones avanzadas | — |
| 13 | Optimización | — |
| 14 | Cobertura de tests | — |
| 15 | Builds y despliegue | — |

## 10. Reglas de juego acordadas (Fase 1)

- Blackjack: 6 barajas, crupier se planta en 17 blando, blackjack 3:2, doblar con 2 cartas,
  split hasta 4 manos (ases: una carta), sin seguro ni rendición en la v1.
- Ruleta europea (0–36); con 0 pierden las apuestas sencillas.
- Póker: No-Limit Texas Hold'em, 6 asientos, fichas llevadas del saldo a la mesa.
- Bono diario: día natural; la racha se reinicia si falta un día; 500 + 200·(día−1) con tope en el día 7.
- Bono de rescate cuando el saldo baja de la apuesta mínima.
- Fichas: `Long`, apuesta mínima 10, denominaciones 10/50/100/500/1K/5K/25K.
- DI manual (composition root en `shared`).
