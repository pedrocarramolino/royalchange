# Arquitectura de Royal Chance

## 1. Por qué React (versión 2)

La versión 1 se hizo con Kotlin Multiplatform + Compose (Wasm en la web). En Safari de iOS, donde
más se juega, Compose pinta en un canvas y eso trajo problemas difíciles: el teclado no se abría
(hizo falta superponer campos reales), los toques se perdían (`touch-action`), el rendimiento era
justo y las mesas se veían pobres. La versión 2 usa **React + TypeScript** con HTML/SVG reales:
teclado, accesibilidad, toques y desplazamiento nativos, y gráficos vectoriales nítidos.

- **Web (PWA)**: Vite 8, React 19, Tailwind 4, Motion (animaciones), Zustand (estado), React
  Router 7. Service worker con Workbox (`vite-plugin-pwa`).
- **Android**: la misma web empaquetada con **Capacitor 8** (`web/android`). Mismo `applicationId`
  que la app anterior (`com.royalchance.app`, `versionCode` 2) para que la actualice en Play.
- **Datos**: el mismo proyecto de Firebase y **las mismas reglas de Firestore** que la versión 1:
  las cuentas y las fichas existentes siguen valiendo.

## 2. Capas

```
domain/   reglas puras (sin React ni Firebase): economía, progresión, logros, validación
engine/   motores de juego puros: (estado, acción) → (estado nuevo, eventos)
data/     Firebase y almacenamiento local, expuestos como stores de Zustand
features/ pantallas
ui/       sistema visual compartido
```

Las capas puras tienen tests (`npm test`); el azar se inyecta (`RandomInt`), así que los motores se
prueban con barajas preparadas.

## 3. Economía (contrato con las reglas de Firestore)

- `wallets/{uid}`: saldo, número de movimiento (`seq`), ronda abierta y progresión (experiencia,
  contadores, rachas, logros). `wallets/{uid}/ledger/{id}`: asientos inmutables.
- Cada operación (`domain/transitions.ts`) produce el monedero nuevo y su asiento; se escriben en
  **un único lote**. Las reglas (`firebase/firestore.rules`) comprueban que el asiento justifica
  exactamente el cambio: `seq` consecutivo, importes, techo de pago (1.000×), recarga cada 4 h,
  bono diario contra el día del servidor, logros desbloqueados con sus condiciones.
- Campos opcionales **omitidos**, nunca `null` (las reglas distinguen "no está"); enteros (el SDK
  de JavaScript los guarda como `integer`); logros en orden de catálogo (la lista se compara tal
  cual).
- No se espera al servidor: Firestore aplica el lote en local al instante (también sin conexión) y
  lo sincroniza; si el servidor lo rechazara, deshace el cambio local. Las operaciones van en serie
  y siempre sobre la versión local más reciente (`getDocFromCache`).
- `src/domain/rules-compat.test.ts` (`npm run test:reglas`) ejecuta una sesión completa calculada
  con el dominio contra las reglas de producción en el emulador: si divergieran, falla.

Juegos:

- **Rondas instantáneas** (ruleta, slots, dados): el resultado se calcula y se contabiliza
  (`playInstantRound`) **antes** de animarlo.
- **Rondas por turnos** (blackjack, póker): cada ficha que se pone en la mesa es un `placeBet`
  (abre o amplía la ronda abierta); al terminar, `settleRound` con lo cobrado. La mesa se guarda en
  el dispositivo tras cada acción y se reanuda al volver; una ronda abierta sin su mesa (otro
  dispositivo, datos borrados) se cierra con pago 0.
- Póker: la pila en la mesa es lo máximo que se arriesga por mano; solo lo que va al bote sale del
  saldo. Si el jugador se retira, la mano se liquida ya (los bots siguen).

## 4. Motores de juego

| Juego | Reglas | Verificación |
|---|---|---|
| Blackjack | 6 barajas, 75 % de penetración, crupier se planta en 17 blando, 3:2, doblar, separar hasta 4 | Tests con zapatos preparados |
| Ruleta | Europea; plenos, caballos, transversales, cuadros, seisenas, columnas, docenas y sencillas | Pagos = 36 / números cubiertos |
| Slots | 5×3, 10 líneas, comodín | RTP exacto 97,27 % (cálculo sobre las frecuencias de los rodillos) |
| Dados | Menor, mayor, siete, dobles y sumas exactas; pagos en décimas | Pagos enteros con fichas de 10 |
| Póker | Hold'em sin límite, 6 asientos, botes laterales, bots por equidad Monte Carlo | Evaluador validado con las 2.598.960 manos de 5 cartas |

## 5. Interfaz

- **Identidad**: obsidiana, oro en tres tonos, tapete verde, rubí y esmeralda; Cinzel para títulos
  y leyendas serigrafiadas, Manrope para el resto. Cartas, fichas, ruleta, dados y símbolos son SVG
  propios (`ui/PlayingCard.tsx`, `ui/Chip.tsx`…), nítidos a cualquier tamaño.
- **Orientación**: mesas en horizontal, resto en vertical. `useRequireLandscape()` en cada mesa;
  `OrientationGate` fija la orientación donde se puede (APK con el plugin de Capacitor, PWA
  instalada en Android) y, en un móvil en la orientación equivocada, pide girarlo (Safari en iOS no
  permite fijarla).
- **Avisos de progreso** (subida de nivel, logros): se retienen mientras una mesa anima su
  resultado para no adelantarlo.
- **Sonido**: efectos sintetizados con Web Audio (sin archivos); el contexto se desbloquea con el
  primer toque (requisito de Safari).
- **Accesibilidad**: controles reales con etiquetas; el tapete de la ruleta tiene además botones
  ocultos para lectores de pantalla; `prefers-reduced-motion` y el ajuste "Animaciones reducidas".

## 6. Historial y estadísticas

El historial pagina el libro por `seq` (30 por página). Las estadísticas por juego se acumulan en
el dispositivo y solo se leen los asientos nuevos desde la última vez (los asientos no cambian).

## 7. Lecciones de la versión 1 que siguen valiendo

- En el navegador integrado de pruebas, `requestAnimationFrame` puede ir a 1–2 fps si la ventana
  está en segundo plano: las animaciones parecen rotas o los toques perdidos sin serlo.
- Probar siempre en el tamaño más justo: un iPhone SE en horizontal (667 × 320 útiles).
- Las reglas de Firestore tienen un límite de 1.000 expresiones por petición: por eso solo se evalúa
  el validador del tipo de asiento.
