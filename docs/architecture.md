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

Los módulos se crean cuando una fase los necesita. Existentes tras la Fase 12:

| Módulo | Contenido |
|---|---|
| `core:audio` | Efectos de sonido sintetizados en código (sin archivos) y su reproducción: AudioTrack (Android), javax.sound (escritorio), Web Audio (navegador) |
| `core:common` | `RandomGenerator` e ids aleatorios, `Outcome` (errores tipados), formato de números, nombres de países (CLDR) |
| `core:designsystem` | Tema "Noir & Oro", tipografía, iconos, palos de la baraja, naipe, ficha de casino, fichas de apuesta, barras de avance, componentes, navegación adaptativa, animaciones (reparto de cartas, lluvia de monedas, animaciones reducidas) |
| `core:ui` | Avatares, selector de país, saldo de fichas, nombres de juegos, textos de niveles y logros, avisos de progreso (con retención mientras una mesa anima) |
| `core:testing` | Generadores aleatorios deterministas, `TestClock` |
| `domain` | Autenticación, reglas del registro, países, ajustes, `GameType`, `GameSessionStore`, economía (`Chips`, monedero, asientos, reglas), progresión (niveles, experiencia, bono diario, logros) e historial (`LedgerSource`, líneas del historial, estadísticas por juego) |
| `engine:cards` | Cartas, baraja y zapato serializable con carta de corte |
| `engine:blackjack` | Motor de blackjack: reglas, valor de manos, acciones, eventos y liquidación |
| `engine:roulette` | Ruleta europea: rueda, apuestas (todas las del tapete), validación, giro y pagos |
| `engine:slots` | Tragaperras 5×3: tiras de los rodillos, 10 líneas, comodín y tabla de pagos |
| `engine:dice` | Dados: tirada de dos dados, apuestas sobre la suma y pagos |
| `engine:poker` | Texas Hold'em No-Limit: evaluador de manos, apuestas, botes laterales, bots y mesa |
| `data` | Repositorios en memoria (escritorio y tests), incluida la economía, ajustes persistentes (`KeyValueStore`), sesiones de mesa e historial con estadísticas resumidas en el dispositivo |
| `data:firebase` | Autenticación, perfil, economía y lectura del libro contable con Firebase (Android y web); lógica común sobre pasarelas por plataforma |
| `feature:auth` | Bienvenida, inicio de sesión, registro completo, completar perfil, recuperar contraseña, legales |
| `feature:lobby` | Saludo, saldo, nivel, bono diario, avisos de cuenta y de logros, recarga gratuita y catálogo de juegos (abre los disponibles) |
| `feature:blackjack` | Mesa de blackjack: apuesta con fichas, jugadas, animación del crupier y reanudación de la mano |
| `feature:roulette` | Mesa de ruleta: tapete, rueda animada, deshacer/repetir y últimos números |
| `feature:slots` | Tragaperras: rodillos animados, líneas premiadas, apuesta y tabla de pagos |
| `feature:dice` | Mesa de dados: tapete, dados animados, deshacer/repetir y últimas sumas |
| `feature:poker` | Mesa de póker: sentarse con fichas, mesa ovalada de seis asientos, acciones y subidas, bots |
| `feature:profile` | Progreso: nivel, estadísticas, racha de bono diario y logros con el cobro de sus recompensas |
| `feature:history` | Historial de rondas y movimientos (paginado) y estadísticas por juego |
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

## 8 bis. Blackjack (Fase 7)

**Reglas** (`BlackjackRules`): 6 barajas, se baraja al pasar la carta de corte (75 % del zapato),
el crupier se planta en todos los 17 y mira si tiene blackjack, blackjack 3:2, doblar con 2 cartas
(también tras separar), separar hasta 4 manos (los ases reciben una sola carta y 21 con ellos no es
blackjack), sin seguro ni rendición. Mesa: mínimo 10, máximo 10.000 por mano, en múltiplos de 10;
el máximo en juego (4 manos dobladas = 80.000) cabe en el límite de ronda de la economía.

**Motor** (`engine:blackjack`): función pura `apply(estado, acción, random) → estado + eventos` o
error tipado. El zapato forma parte del estado, así que una mano se puede guardar y reanudar tal
cual. Simulación de 5.000 rondas en los tests: las cartas se conservan y los pagos cuadran.

**Flujo con la economía** (`BlackjackViewModel`):
1. Repartir: `placeBet` antes de enseñar cartas; doblar y separar son nuevas `placeBet` de la misma
   ronda. Sin saldo, la jugada se rechaza y la mano sigue.
2. Al terminar la ronda se liquida (`settleRound`) **antes** de animar al crupier; el saldo mostrado
   se congela hasta que acaba la animación y los avisos de progreso esperan (`ProgressEventGate`).
3. Tras cada acción se guarda la mesa en el dispositivo (`GameSessionStore`). Al volver: se reanuda
   la mano, o se completa una liquidación pendiente; si el monedero tiene una ronda de blackjack
   abierta sin mesa guardada (otro dispositivo, datos borrados), se da por perdida con
   `settleRound(0)` y se avisa.

Limitación aceptada: el cliente decide las cartas. El servidor acota el daño (límites de apuesta,
techo de pago, una ronda abierta a la vez), pero un cliente manipulado podría elegir resultados
dentro de esos límites. Con fichas sin valor es asumible; un servidor de juego exigiría Cloud
Functions (plan de pago).

## 8 ter. Ruleta (Fase 8)

**Reglas:** ruleta europea (0–36). Cada apuesta paga `36 / números cubiertos` veces lo apostado,
apuesta incluida: pleno 35:1, caballo 17:1, transversal 11:1, cuadro 8:1, seisena 5:1, docena y
columna 2:1, sencillas 1:1. Con el 0 las sencillas pierden enteras (sin *la partage*). La ventaja de
la casa es 1/37 en todas las apuestas (lo comprueban los tests recorriendo los 37 números). Mesa:
fichas de 10 en adelante, máximo 25.000 por giro; el pago máximo (36×) queda muy por debajo del
techo de 1.000× del servidor.

**Motor** (`engine:roulette`): el giro es el único punto con azar (un entero uniforme en 0–36);
validar y pagar es aritmética pura. Admite todas las apuestas del tapete; la pantalla ofrece en
esta versión plenos y apuestas exteriores (caballos, cuadros, transversales y seisenas exigen
tocar entre casillas, poco usable en móvil: se añadirán con un modo de apuesta propio).

**Flujo:** el motor decide el número → `playInstantRound` contabiliza apuesta y pago en un solo
asiento → la rueda gira. Si el cobro falla, no se muestra ningún número y las apuestas siguen en
la mesa. El resultado se muestra cuando la pantalla avisa de que la bola se ha parado (con un
margen de seguridad si la pantalla no anima, p. ej. en segundo plano); hasta entonces el saldo
mostrado y los avisos de progreso esperan. No hay ronda abierta: cerrar la app a mitad del giro
no pierde nada. Se guardan en el dispositivo los últimos 12 números y la última apuesta (Repetir).

## 8 quater. Slots y Dados (Fase 9)

**Slots** (`engine:slots`): 5 rodillos × 3 filas y 10 líneas fijas. Cada rodillo se para en una
posición uniforme de su tira de 32 símbolos (cereza 8, trébol 5, corazón 5, pica 4, diamante 3,
BAR 2, siete 2, corona-comodín 3). Una línea paga por la racha más larga desde el primer rodillo
(el comodín sustituye a cualquiera; si la racha de comodines solos paga más, se cobra esa); solo
las cerezas pagan con dos. Retorno teórico **97,27 %** con premio en el 16 % de las líneas: lo
calcula un test recorriendo con su probabilidad las 8⁵ combinaciones de una línea (los rodillos son
independientes y todas las líneas tienen la misma esperanza). Apuesta por línea de 1 a 500
(total 10–5.000); el mejor giro posible paga 500× la apuesta total, por debajo del techo de 1.000×.

**Dados** (`engine:dice`): dos dados y apuestas sobre la suma, con pagos (apuesta incluida) en
décimas para que con fichas de 10 sean enteros: menor 2–6 y mayor 8–12 a 2,3×, siete y dobles a
5,8×, suma exacta de 6,9× (6 u 8) a 35× (2 o 12). Todas devuelven entre el 95,8 % y el 97,2 %
(test con las 36 tiradas). Varias apuestas por tirada, máximo 25.000.

**Flujo:** igual que la ruleta: el motor decide, `playInstantRound` contabiliza y después se
anima; la pantalla avisa al ViewModel cuando los rodillos o los dados se paran (con margen de
seguridad). Los dados que se ven mientras ruedan siguen una secuencia fija: la animación nunca es
fuente de azar. Se guardan en el dispositivo la apuesta de la tragaperras y, en los dados, las
últimas sumas y la última apuesta.

## 8 quinquies. Póker contra bots (Fase 10)

**Reglas** (`engine:poker`): Texas Hold'em No-Limit, seis asientos (el jugador y cinco bots).
Mesas 10/20 (entrada 400–2.000) y 50/100 (2.000–10.000). Ciegas, en mano a dos el botón pone la
pequeña y habla primero antes del flop; subida mínima = la última subida completa; botes laterales
por niveles de aportación; empates repartidos en unidades de 10 con la sobrante al primer ganador a
la izquierda del botón. Simplificación documentada: tras un all-in corto, quien ya había hablado
puede volver a subir. Todas las cantidades son múltiplos de 10.

**Evaluador:** puntúa directamente 5–7 cartas (conteos y máscaras de bits, sin probar las 21
combinaciones). Un test recorre las 2.598.960 manos de cinco cartas y comprueba las frecuencias de
cada categoría y los 7.462 valores distintos.

**Bots:** estiman la equidad de su mano simulando 160 repartos del resto (solo ven sus cartas y
las comunitarias: no hacen trampa) y la comparan con las pot odds. Cuatro estilos (tight,
equilibrado, loose, agresivo) ajustan cuánto juegan, suben y farolean. Los que se quedan sin
fichas se sustituyen por otros entre manos. Un test juega cientos de manos entre bots comprobando
que las fichas se conservan.

**Contabilidad por mano** (decisión de esta fase): cada ficha que el jugador mete en el bote
(ciegas, igualar, subir) es un `placeBet` de la ronda de póker *antes* de mostrarse en la mesa, y
al terminar la mano —o al retirarse— `settleRound` paga lo que gana. Así solo está en riesgo lo que
ya está en el bote, cada mano cuenta para nivel y estadísticas, y no hace falta cambiar las reglas
de Firestore (todo es múltiplo de 10 ≥ la apuesta mínima; lo ganado nunca supera 6× lo aportado).
Las "fichas llevadas a la mesa" (regla de la Fase 1) son el tope que el jugador puede arriesgar por
mano y no salen del saldo hasta que se apuestan. La mesa se guarda en el dispositivo tras cada
acción para reanudarla; una mano con fichas en juego sin su estado (otro dispositivo) se da por
perdida, como en el blackjack. Limitación aceptada: la baraja de la mano está en el estado local.

## 8 sexies. Estadísticas e historial (Fase 11)

La fuente es el libro contable (`wallets/{uid}/ledger`), que ya registraba cada movimiento y es
inmutable. Desde esta fase la liquidación de una ronda por turnos guarda también lo apostado en
toda la ronda (`stake`): así cada ronda es un único asiento completo (liquidación o ronda
instantánea). Las reglas lo validan sin romper versiones anteriores de la app:
`e.get('stake', round.stake) == round.stake`.

- **Historial:** páginas de 30 líneas del asiento más reciente al más antiguo
  (`seq` descendente). Las apuestas sueltas no se muestran: se ven en su liquidación. Movimientos:
  bienvenida, recarga, bono diario y logros.
- **Estadísticas por juego:** rondas, % ganadas, balance (cobrado − apostado) y mejor ronda. Se
  guardan resumidas en el dispositivo con el último asiento contado y cada visita solo lee los
  asientos nuevos (`seq > último`): cuesta pocas lecturas aunque el libro crezca. En un
  dispositivo nuevo se lee el libro una vez. Alternativa descartada: contadores por juego en el
  monedero, que exigirían más validación en unas reglas ya cerca del límite de expresiones.
- Se recarga sola cuando el monedero registra un movimiento nuevo.
- Las estadísticas generales (nivel, rachas, saldo máximo) siguen en la pestaña Progreso.

## 8 septies. Sonido y animaciones (Fase 12)

**Sonido** (`core:audio`): los efectos se **sintetizan en código** (tonos, campanas y ruido filtrado
con envolventes) en vez de usar archivos: sin licencias ni descargas y deterministas (un test
comprueba duración, volumen y que terminan sin chasquido). Efectos: ficha, carta, giro (bola que
se frena), rodillo que se para, dados, premio, premio grande y ronda perdida. Cada plataforma solo
reproduce las muestras: `AudioTrack` estático (Android), `Clip` de javax.sound (escritorio) y
Web Audio (navegador; el contexto se crea con el primer toque, como exige Safari).

- La raíz proporciona `LocalSoundPlayer`, que respeta la preferencia "Sonido" (Ajustes) en cada
  reproducción. Las fichas suenan solas; las mesas usan `SoundOnChange`/`SoundOnIncrease`, que
  solo suenan ante cambios: volver a una mesa con un resultado a la vista no lo repite.
- En la tragaperras solo suenan los premios (perder es lo habitual).

**Animaciones:** cartas que entran repartidas (caen, giran y aparecen), saldo que cuenta hasta el
valor nuevo y lluvia de monedas para premios de 10× o más (blackjack natural en el blackjack; botes
de 25 ciegas o más en el póker). Las trayectorias salen del índice de cada moneda (ángulo áureo):
la animación nunca usa azar. **Animaciones reducidas** (Ajustes) quita las decorativas y conserva
las que cuentan el resultado (rueda, rodillos, dados, crupier).

## 8 octies. Optimización (Fase 13)

Medido antes de cambiar nada:

| Recurso | Tamaño | Transferido |
|---|---|---|
| Motor gráfico (Skia, Wasm) | 8,6 MB | 2,6 MB (Brotli de Hosting) |
| App (Kotlin/Wasm) | 6,0 MB | 1,3 MB |
| JavaScript (SDK de Firebase…) | 1,3 MB | ~0,25 MB |
| APK de Android (release) | 14,5 MB → **3,6 MB** con R8 | — |

Los `.wasm` llevan hash en el nombre y se sirven con caché de un año (`immutable`); el service
worker los guarda: solo se descargan la primera vez.

Cambios:
- **iOS, teclado:** el script ya no recoloca sus inputs en cada fotograma (era la única tarea
  que trabajaba sin parar en el iPhone): un `MutationObserver` sobre la capa de accesibilidad de
  Compose los mueve solo cuando algo cambia, con respaldo cada segundo y escribiendo solo los
  estilos que cambian. En reposo, la app no pide ningún fotograma (medido).
- **Densidad de pintado limitada a 2** en la web: en pantallas ×3 se pintan 2,25 veces menos
  píxeles por fotograma, con una diferencia de nitidez imperceptible en un móvil.
- **Ruleta:** las 37 etiquetas de la rueda se miden una vez por tamaño (`drawWithCache`).
- **Póker:** las simulaciones de los bots se calculan fuera del hilo de la interfaz (Android y
  escritorio; en la web es el mismo hilo). Sin tareas largas (>50 ms) medidas en una mano.
- **Preconexión** a los servidores de Firebase mientras se descarga la app.
- **Android:** R8 con reducción de recursos (`proguard-rules.pro`: serializadores y nombres de
  enums que se guardan). Pendiente verificar el APK de release en un dispositivo.
- Corregido de paso: el botón secundario aplicaba el margen dos veces y en botones estrechos el
  texto salía con una letra por línea.
- Duplicación aceptada: los ViewModels de ruleta y dados comparten estructura (tapete con
  deshacer/repetir); se mantienen separados porque sus tipos de apuesta difieren y ambos tienen
  tests. Extraerlo añadiría genéricos sin simplificar.

## 8 nonies. Tests y cobertura (Fase 14)

| Nivel | Dónde | Qué prueba |
|---|---|---|
| Unidad | `commonTest` (JVM y Wasm) | Motores de juego, dominio (economía, progresión, historial), repositorios, ViewModels |
| UI de componentes | `jvmTest` de `core:designsystem` y `core:ui` | Fichas, cartas, botones, saldo, avisos de progreso retenidos |
| Extremo a extremo | `shared/src/jvmTest/AppTest` | La app completa con repositorios en memoria: registro, inicio de sesión, bono diario, progreso, una partida de cada juego (comprobando el asiento en el libro), historial y ajustes |
| Reglas de Firestore | `firebase/tests` (emulador) | 35 tests de seguridad y validación de cada movimiento |

Los tests de UI usan `runComposeUiTest` en escritorio (rápidos y medidos por Kover); el plugin
de convención de Compose añade `ui-test`, el runtime gráfico del sistema y `kotlinx-coroutines-swing`
(hilo principal de los ViewModels).

**Cobertura** (Kover 0.9.11, tests de JVM; `./gradlew koverHtmlReport` → `build/reports/kover/html`):
de 35,6 % a **87,5 % de líneas** (85,7 % de instrucciones, 64,4 % de ramas). Motores, dominio y
datos rondan el 95–100 %; la navegación, el 97 %. `data:firebase` no tiene objetivo JVM: sus 29
tests se ejecutan en Android y Wasm pero no entran en el informe.

Los tests de extremo a extremo destaparon un fallo de accesibilidad: las tarjetas del lobby y los
botones −/+ de la tragaperras usaban `clearAndSetSemantics` sin declarar la acción, así que con
VoiceOver/TalkBack no se podían activar. Corregido.

Cómo ejecutar:

```bash
./gradlew allTests                       # unidad + UI (JVM y Wasm)
./gradlew koverHtmlReport                # cobertura
firebase emulators:exec --only firestore --project demo-royalchance "cd firebase/tests && npm test"
```

## 8 decies. Builds y despliegue (Fase 15)

- **Versión 1.0.0** (`versionName` de Android y `AppInfo.VERSION`).
- **Web:** `wasmJsBrowserDistribution` → Firebase Hosting (`.wasm` con hash y caché de un año;
  HTML y JS revalidados). Cada cambio en los recursos del host sube `CACHE_VERSION` del service
  worker.
- **Android:** `bundleRelease` con R8 (AAB de 8,1 MB; APK de 3,6 MB). La firma se lee de
  `keystore.properties` (no versionado); pensado para *Play App Signing*.
- **Reglas de Firestore:** desplegadas tras pasar sus 35 tests en el emulador.
- **CI** (GitHub Actions, `.github/workflows/ci.yml`): tests y cobertura, builds de las tres
  plataformas y tests de reglas. Sin despliegue automático.
- Escritorio: herramienta de desarrollo, no se distribuye (decisión de la Fase 1).
- Lista de comprobación antes de publicar en el README (textos legales definitivos, política de
  casino social de Google Play, API keys, App Check, prueba en dispositivos reales).

## 8 undecies. Orientación y mesas en el móvil

- **Mesas en horizontal, resto en vertical.** Cada mesa llama a `RequireLandscape(onBack)`;
  `OrientationGate` (en `App`) fija la orientación donde se puede (Android: `requestedOrientation`,
  salvo tablets; web: `screen.orientation.lock`, que Safari en iOS no permite) y, en un móvil
  (lado corto < 600 dp) en la orientación equivocada, tapa la pantalla con un aviso para girarlo.
  En una mesa el aviso trae «Volver al casino» por si el giro automático está bloqueado.
- **`GameTableLayout`:** en un móvil en horizontal los controles van en un panel lateral derecho
  (40 % del ancho, 260–360 dp, con scroll) y la mesa ocupa el resto (Blackjack, Slots, Póker).
- **Ruleta y Dados en el móvil girado:** sin panel lateral (el tapete no cabía). Tapete compacto a
  lo ancho (ruleta horizontal como en una mesa real: 0 a la izquierda, 3×12, docenas y sencillas
  debajo; dados en 3 filas), rueda/dados y resultado a la izquierda y `BetActionBar` (fichas,
  deshacer/borrar/repetir y girar/tirar en una fila) abajo.
- **Póker en mesa baja:** cartas de cada asiento al lado del nombre, la apuesta dentro del asiento y
  cartas comunitarias más pequeñas; botones de apuesta rápida en 2×2 si el panel es estrecho.
- **Botones en una línea:** el texto se reduce (hasta 11 sp) en vez de partirse. Ojo: el
  autoajuste de `BasicText` solo detecta que no cabe con `maxLines = 1` y salto permitido; con
  `softWrap = false` y elipsis nunca encoge.

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
| 7 | Blackjack | Hecha |
| 8 | Ruleta | Hecha |
| 9 | Slots y Dados | Hecha |
| 10 | Póker contra bots | Hecha |
| 11 | Estadísticas e historial | Hecha |
| 12 | Sonido y animaciones avanzadas | Hecha |
| 13 | Optimización | Hecha |
| 14 | Cobertura de tests | Hecha |
| 15 | Builds y despliegue | Hecha |

## 12. Reglas de juego acordadas (Fase 1)

- Blackjack: 6 barajas, crupier se planta en todos los 17 (también el blando), blackjack 3:2, doblar con 2 cartas,
  split hasta 4 manos (ases: una carta), sin seguro ni rendición en la v1.
- Ruleta europea (0–36); con 0 pierden las apuestas sencillas.
- Póker: No-Limit Texas Hold'em, 6 asientos, fichas llevadas del saldo a la mesa.
- Bono diario: día natural; la racha se reinicia si falta un día; 500 + 200·(día−1) con tope en el día 7.
- Bono de rescate cuando el saldo baja de la apuesta mínima.
- Fichas: `Long`, apuesta mínima 10, denominaciones 10/50/100/500/1K/5K/25K.
- DI manual (composition root en `shared`).

## 13. Problemas conocidos

- El SDK de Firebase añade unos 210 KB comprimidos a la PWA.
- **Teclado en iPhone/iPad (resuelto en la web, `webApp/.../ios-keyboard.js`):** Safari en iOS no
  abre el teclado cuando Compose enfoca su campo oculto por programa (tampoco dentro del toque). Se
  coloca un `<input>` real sobre cada campo de texto de Compose (posiciones de su capa de
  accesibilidad, `role="textbox"`): el dedo lo toca, iOS abre el teclado, los eventos de puntero se
  reenvían al canvas y el foco pasa al campo de Compose. El input debe tener opacidad 1 (fondo,
  texto y cursor transparentes): con opacidad casi nula Safari lo considera oculto. Con
  `?diagnostico=teclado` se ven los inputs y un registro en pantalla. Revisar al actualizar Compose.
- Los tests de reglas borran la base de datos del emulador: no hay que lanzarlos contra el
  emulador que usa la app en desarrollo (`firebase emulators:exec` arranca uno propio).
- En Wasm, los errores de JavaScript no son `Exception`: donde se llama a la API de Firebase se
  captura `Throwable` (dejando pasar la cancelación).
- Con el servidor de desarrollo web en marcha, `allTests` puede agotar la memoria del daemon de
  Kotlin al enlazar los ejecutables de test de Wasm: conviene parar el servidor (y `./gradlew --stop`)
  antes de lanzar todos los tests. El daemon de Kotlin usa 5 GB y Gradle ejecuta 4 tareas a la vez
  (`gradle.properties`, Fase 13).

- **Clics perdidos en pruebas automatizadas de la web**: si el puntero salta y pulsa en el mismo
  instante (así actúan las herramientas de automatización), Compose para web puede ignorar esa
  primera pulsación; con un movimiento previo del ratón responde siempre. Hay que confirmar en
  dispositivos reales (táctil y ratón) en la Fase 4.
- **Aviso en consola** `Accessing memory via wasmExports is deprecated`: procede de una biblioteca
  de Compose, no del código del proyecto. Desaparecerá al actualizar Compose.
- En la web, los textos se cargan de forma asíncrona la primera vez que se muestra cada pantalla
  (un instante sin texto durante la transición).
