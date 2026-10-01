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
| Lenguaje | Kotlin 2.4.20, Coroutines 1.11, Flow, kotlinx-serialization 1.11, kotlinx-datetime 0.8 |
| UI | Compose Multiplatform 1.12.1, Material 3 (1.9.0, última estable), fuentes Cinzel y Manrope (OFL) |
| Navegación y estado | Navigation 3 (1.1.2), ViewModel multiplataforma (lifecycle 2.11.0) |
| Build | Gradle 9.7.0, AGP 9.3.3 (`com.android.kotlin.multiplatform.library`), convention plugins en `build-logic` |
| Datos | Firebase Auth + Firestore con caché offline, mediante los **SDK oficiales** de cada plataforma (Android BoM 34.19, JavaScript 12.19) detrás de una interfaz común |
| Hosting | Firebase Hosting (plan Spark, gratuito) |
| Tests | kotlin.test y kotlinx-coroutines-test en `commonTest`, ejecutados en JVM y en Wasm (Node.js o Chrome headless) |

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
| `feature:*` | `domain`, `engine:*`, `core:designsystem`, `core:ui`, `core:audio` | `data`, Firebase, otras features |
| `core:ui` | `core:designsystem`, `domain` | `data` |
| `shared` | todo (raíz de composición) | — |

Las features no se conocen entre sí: cuando una necesita abrir una pantalla de otra (p. ej.
Ajustes → documentos legales), recibe una función y es `shared` quien conoce la ruta.
Excepción controlada: los tests de `feature:auth` usan `:data` (repositorio en memoria) como doble.

Los módulos se crean cuando una fase los necesita. Existentes tras la Fase 3:

| Módulo | Contenido |
|---|---|
| `core:common` | `RandomGenerator` e ids aleatorios, `Outcome` (errores tipados), formato de números, nombres de países (CLDR) |
| `core:designsystem` | Tema "Noir & Oro", tipografía, iconos, palos de la baraja, ficha de casino, barras de avance, componentes, navegación adaptativa |
| `core:ui` | Avatares, selector de país, saldo de fichas, textos de niveles y logros, avisos de progreso |
| `core:testing` | Generadores aleatorios deterministas, `TestClock` |
| `domain` | Autenticación, reglas del registro, países, ajustes, `GameType`, economía (`Chips`, monedero, asientos, reglas) y progresión (niveles, experiencia, bono diario, logros) |
| `data` | Repositorios en memoria (escritorio y tests), incluida la economía, y ajustes persistentes (`KeyValueStore`) |
| `data:firebase` | Autenticación, perfil y economía con Firebase (Android y web); lógica común sobre pasarelas por plataforma |
| `feature:auth` | Bienvenida, inicio de sesión, registro completo, completar perfil, recuperar contraseña, legales |
| `feature:lobby` | Saludo, saldo, nivel, bono diario, avisos de cuenta y de logros, recarga gratuita y catálogo de juegos |
| `feature:profile` | Progreso: nivel, estadísticas, racha de bono diario y logros con el cobro de sus recompensas |
| `feature:history` | Pestaña de historial (estado vacío hasta la Fase 11) |
| `feature:settings` | Cuenta, tema, documentos legales, cerrar sesión y eliminar cuenta |
| `shared` | `App()`, `AppGraph` (DI manual) y flujos de navegación |

**Convention plugins** (`build-logic`):
- `royalchance.kmp.library`: Kotlin puro (JVM + Wasm). Android consume su variante JVM, así que no
  necesita el plugin de Android (verificado en la Fase 2).
- `royalchance.kmp.compose`: módulos con UI (Android + JVM + Wasm + Compose).
- `royalchance.kmp.feature`: `kmp.compose` + serialización y las dependencias comunes de una feature.

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

**SDK oficiales, no GitLive.** La versión estable del SDK de GitLive (2.7.0) no soporta Wasm; solo
una alpha lo hace. Se usan los SDK oficiales: `com.google.firebase` en Android y el paquete npm
`firebase` en la web (declarado con `external` de Kotlin/Wasm). La lógica (`FirebaseAuthRepository`,
`FirebaseEconomyRepository`) se escribe una vez sobre pasarelas (`AuthGateway`, `PlayerStore`,
`WalletStore`) que cada plataforma implementa.
El escritorio no incluye Firebase: usa los repositorios en memoria.

**Configuración.** `firebase.properties` (valores públicos) genera `FirebaseProjectConfig`. Por
defecto la app usa el proyecto real (`royalchance-92769`, Firestore en `eur3`); con
`-Proyalchance.firebase.emulators=true` usa los emuladores locales con el proyecto aislado
`demo-royalchance`.

**Modelo.** `players/{uid}` (perfil del jugador), `aliases/{aliasKey}` (reserva del alias) y
`wallets/{uid}` con su libro contable `wallets/{uid}/ledger/{entryId}` (sección 7). El alias se
reserva y el perfil se guarda en una única escritura atómica; las reglas impiden reservar un alias
ajeno.

- Firebase Auth solo con email y contraseña. Firestore con caché offline como almacenamiento local-first.
- Cada operación económica cuesta 2 escrituras de documento (monedero y asiento); cuota gratuita:
  20.000 escrituras/día en total.
- Reglas de seguridad (`firebase/firestore.rules`, con 34 tests en `firebase/tests`): solo el
  propietario accede a sus datos, forma exacta de cada documento (no se pueden añadir campos),
  alias válido y único, avatar conocido, mayoría de edad comprobada con la hora del servidor,
  cada movimiento de fichas justificado y dentro de los límites, y la progresión coherente con
  cada movimiento (sección 8).
- Las reglas tienen un límite de 1.000 expresiones evaluadas por petición: la validación de un
  movimiento elige el validador por el tipo de asiento y solo revisa los logros si cambian.
- Limitación conocida: sin Cloud Functions (plan Blaze) el cliente decide los resultados; los
  rankings no serán fiables hasta tener lógica en servidor. Los motores puros podrán ejecutarse allí.
- Región de Firestore: UE (irreversible una vez creada la base de datos).
- Sin Firebase Analytics (evita banner de consentimiento por rastreo).

## 7. Economía de fichas (Fase 5)

**Reglas** (`EconomyRules` en `domain`, repetidas en `firestore.rules`):

| Concepto | Valor |
|---|---|
| Fichas de bienvenida | 10.000, al crear el monedero (primer acceso con perfil completo) |
| Apuesta mínima | 10 |
| Máximo en juego por ronda | 100.000 (suma de dobles, separaciones o apuestas de un giro); cada juego fijará límites de mesa menores |
| Techo de pago | 1.000 × la apuesta de la ronda (la ruleta paga como mucho 36×; el techo acota a un cliente manipulado) |
| Recarga gratuita | 1.000 fichas si el saldo no llega a la apuesta mínima y no hay fichas en la mesa; una cada 4 horas |

**Operaciones con intención** (`EconomyRepository`, único punto que mueve fichas; no existe "fijar saldo"):
- `placeBet` abre una ronda por turnos (Blackjack) o añade fichas a la abierta (doblar, separar).
  Solo puede haber una ronda abierta; sus fichas ya no cuentan en el saldo.
- `settleRound` la liquida; el pago incluye la apuesta devuelta (0 si se pierde).
- `playInstantRound` contabiliza apuesta y pago a la vez (ruleta, slots, dados), con el resultado
  ya decidido y **antes** de animarlo: cerrar la app a mitad del giro no deshace nada.
- `claimRescue` cobra la recarga gratuita.
- La lógica es pura (`WalletTransitions`) y la comparten el repositorio en memoria y el de Firebase.
  Las operaciones se aplican en serie (`Mutex`): dos apuestas simultáneas nunca gastan dos veces.

**Libro contable.** Cada operación deja un asiento inmutable (tipo, variación, saldo resultante,
número de movimiento, juego, ronda, apuesta y pago). La suma de los asientos es el saldo. Será la
fuente del historial (Fase 11).

**Firestore.** `wallets/{uid}` y `wallets/{uid}/ledger/{entryId}`, escritos en un único lote:
2 escrituras de documento por operación (con la cuota gratuita, unas 10.000 rondas diarias en
total). Las reglas exigen que el número de movimiento suba exactamente en uno, que el asiento sea
nuevo, que cuadre con el cambio de saldo y que respete las reglas de su tipo; los asientos no se
pueden modificar ni crear sueltos, y repetir un id no cobra dos veces. La recarga se valida con la
hora del servidor (`request.time`): adelantar el reloj del dispositivo no sirve.

**Sin conexión.** No se espera al servidor: Firestore aplica el lote en local al instante y lo
sincroniza después. La siguiente operación lee la versión local más reciente (la cola de Firestore
garantiza el orden). Si el servidor rechazara un lote, Firestore deshace el cambio local y el saldo
vuelve al último válido.

**Borrado de cuenta.** Perfil, alias y monedero en un lote; después, los asientos restantes. Las
reglas solo permiten borrar el monedero junto con el perfil (no se puede reiniciar el saldo para
cobrar otra bienvenida) y los asientos cuando el monedero ya no existe.

**Pendiente:** fichas en la mesa de póker (sentarse y levantarse, Fase 10). El saldo mostrado durante una animación (que no revele el resultado antes de tiempo) se
resolverá con el primer juego (Fase 7).

## 8. Progresión (Fase 6)

La progresión se guarda en el mismo documento que el saldo: cada ronda los cambia a la vez y así
sigue siendo una única escritura. Las mismas operaciones de `EconomyRepository` la actualizan
(`WalletTransitions`), y las reglas de Firestore comprueban cada cambio contra el asiento.

**Experiencia y niveles** (`ExperienceRules`, `Levels`):
- Cada ronda cerrada da 10 puntos más un extra por tramos de apuesta: +2 desde 50, +4 desde 100,
  +6 desde 500, +8 desde 1.000, +10 desde 5.000 y +12 desde 25.000 (casi logarítmico). El
  resultado no influye: falsear victorias no da experiencia.
- Nivel `n` a partir de `50 · (n − 1)²` puntos (nivel 5 ≈ 60 rondas, 10 ≈ 300, 25 ≈ 2.000).
  Máximo 99. El nivel se deriva de la experiencia; no se guarda.
- Títulos: Novato (1), Jugador (5), VIP (10), High Roller (25) y Leyenda (50).

**Contadores:** rondas, victorias, derrotas, empates (ganar es cobrar más de lo apostado), racha
de victorias (un empate no la rompe ni la alarga), mejor racha y saldo máximo alcanzado. Una ronda
por turnos cuenta al liquidarse, con todo lo apostado en ella.

**Bono diario** (`DailyBonusRules`): por día natural en la zona horaria del dispositivo, una vez
al día. Si el último cobro fue ayer, la racha sube; si se salta un día, vuelve a 1. Premio
500 + 200 · (día − 1), con tope a partir del día 7 (1.700). Si el reloj marca una hora anterior al
último cobro, queda bloqueado. El servidor exige que el día del dispositivo esté a un día como mucho
del día UTC del servidor: adelantar la fecha no adelanta bonos.

**Logros** (`Achievements`): catálogo de 14 con id estable, condición tipada y recompensa (de 250
a 10.000 fichas): primera victoria, 10/100/1.000 rondas, 50 victorias, rachas de 5 y 10, niveles
5/10/25, saldos de 50.000 y 250.000, y 7 y 30 días seguidos de bono. Se desbloquean solos en la
operación que cumple la condición (las reglas la comprueban) y la recompensa se recoge después,
una sola vez, como una operación más con su asiento.

**Avisos:** `EconomyRepository.events` emite subidas de nivel y logros desbloqueados; un aviso
no bloqueante los muestra sobre cualquier pantalla del casino.

## 9. Autenticación (Fase 3: interfaz y reglas; Fase 4: Firebase)

Decisiones confirmadas: **solo email y contraseña** (sin modo invitado ni Google Sign-In, retirados
a petición del producto) y la verificación de email no bloquea el juego.

- **Registro**: alias único, email, contraseña + confirmación (requisitos y medidor de fortaleza),
  fecha de nacimiento (verificación 18+, se guarda solo el año), país, avatar predefinido,
  aceptación de Términos y Política de privacidad, aviso de fichas sin valor monetario,
  comunicaciones opcionales (desmarcadas por defecto) y verificación de email.
- **Login**: email + contraseña, mostrar contraseña y recuperación de contraseña.
- **Cuenta**: cerrar sesión y **eliminar cuenta** (exigido por Google Play y por el RGPD), que se
  confirma con la contraseña porque Firebase exige una autenticación reciente.
- Avatares predefinidos: subir fotos requeriría Cloud Storage, que exige plan de pago.
- Si el registro se interrumpe tras crear la cuenta y antes de guardar el perfil (p. ej. otro
  jugador reserva el alias en ese instante), la cuenta completa su perfil (mismo formulario sin
  email ni contraseña) antes de acceder al casino.
- Los errores de inicio de sesión y de recuperación no revelan si un email está registrado.
- Los textos legales incluidos son un **borrador** marcado como tal en la app: deben sustituirse por
  la versión revisada antes de publicar. Cada aceptación guarda la versión del documento y la fecha.

## 10. Navegación

Navigation 3 (estable en Compose Multiplatform desde 1.10), con rutas `@Serializable` registradas
para serialización polimórfica (necesario fuera de Android).

```
Arranque ─► ¿sesión? ─ no ─► Bienvenida ─► Login | Registro
                      └ sí ─► Shell adaptativo
Shell: Casino (lobby) · Progreso · Historial · Ajustes
       Juegos a pantalla completa desde el lobby
```

Layout: barra inferior (<600 dp), rail (600–840 dp), panel lateral + panel de información (≥840 dp).

Implementación:
- La raíz (`App`) no navega al iniciar o cerrar sesión: deriva el flujo (carga, acceso, completar
  perfil, casino) del estado de sesión, así que es imposible quedarse en el casino sin sesión.
- Cada flujo tiene su propia pila de Navigation 3; los ViewModels se asocian a cada entrada de la
  pila y se destruyen al salir de ella.
- La pila del casino siempre empieza en el lobby: "atrás" desde cualquier pestaña vuelve a él.
- Las pantallas apiladas sobre una pestaña (p. ej. los documentos legales desde los ajustes)
  ocultan la navegación principal. El contenido se traslada con `movableContentOf` para no perder su estado
  al cambiar de diseño (móvil ↔ escritorio) ni al mostrar/ocultar la navegación.

## 11. Plan de fases

| Fase | Contenido | Estado |
|---|---|---|
| 1 | Análisis y arquitectura | Hecha |
| 2 | Proyecto KMP: Gradle, Android, escritorio, PWA, Hosting | Hecha |
| 3 | Sistema de diseño, navegación y pantallas de login/registro (con datos simulados) | Hecha |
| 4 | Firebase: autenticación real + Firestore + reglas de seguridad | Hecha |
| 5 | Economía de fichas | Hecha |
| 6 | Niveles, bono diario, rachas y logros | Hecha |
| 7 | Blackjack | — |
| 8 | Ruleta | — |
| 9 | Slots y Dados | — |
| 10 | Póker contra bots | — |
| 11 | Estadísticas e historial | — |
| 12 | Sonido y animaciones avanzadas | — |
| 13 | Optimización | — |
| 14 | Cobertura de tests | — |
| 15 | Builds y despliegue | — |

## 12. Reglas de juego acordadas (Fase 1)

- Blackjack: 6 barajas, crupier se planta en 17 blando, blackjack 3:2, doblar con 2 cartas,
  split hasta 4 manos (ases: una carta), sin seguro ni rendición en la v1.
- Ruleta europea (0–36); con 0 pierden las apuestas sencillas.
- Póker: No-Limit Texas Hold'em, 6 asientos, fichas llevadas del saldo a la mesa.
- Bono diario: día natural; la racha se reinicia si falta un día; 500 + 200·(día−1) con tope en el día 7.
- Bono de rescate cuando el saldo baja de la apuesta mínima.
- Fichas: `Long`, apuesta mínima 10, denominaciones 10/50/100/500/1K/5K/25K.
- DI manual (composition root en `shared`).

## 13. Problemas conocidos

- El SDK de Firebase añade unos 210 KB comprimidos a la PWA.

- **Clics perdidos en pruebas automatizadas de la web**: si el puntero salta y pulsa en el mismo
  instante (así actúan las herramientas de automatización), Compose para web puede ignorar esa
  primera pulsación; con un movimiento previo del ratón responde siempre. Hay que confirmar en
  dispositivos reales (táctil y ratón) en la Fase 4.
- **Aviso en consola** `Accessing memory via wasmExports is deprecated`: procede de una biblioteca
  de Compose, no del código del proyecto. Desaparecerá al actualizar Compose.
- En la web, los textos se cargan de forma asíncrona la primera vez que se muestra cada pantalla
  (un instante sin texto durante la transición).
