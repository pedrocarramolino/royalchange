import { useLayoutEffect, useRef, useState, useSyncExternalStore, type CSSProperties, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { AnimatePresence, motion } from 'motion/react';
import { useReadyWallet } from '@/data/wallet';
import { isLandscape, isPhone, useRequireLandscape, useScreenAngle, useViewport, type Viewport } from '@/ui/orientation';
import { ISLAND_CLEARANCE, rotatedSafeArea, useSafeArea } from '@/ui/safeArea';
import { ChipBalance } from '@/ui/ChipBalance';
import { PortalTarget } from '@/ui/Dialog';
import { BackIcon } from '@/ui/TopBar';
import { ProgressToasts } from '@/features/casino/ProgressToasts';

/** Tamaño de la mesa, siempre en horizontal: con el móvil en vertical, alto y ancho intercambiados. */
export function useGameViewport(): Viewport {
  const viewport = useViewport();
  return isPhone(viewport) && !isLandscape(viewport) ? { width: viewport.height, height: viewport.width } : viewport;
}

/**
 * Pasa una distancia medida en la pantalla (getBoundingClientRect) a distancia en la mesa. Con el
 * móvil en vertical la mesa va girada 90° en el sentido de las agujas del reloj: su eje x apunta hacia
 * abajo en la pantalla y su eje y, hacia la izquierda.
 */
export function useScreenToTable(): (dx: number, dy: number) => { x: number; y: number } {
  const viewport = useViewport();
  const rotated = isPhone(viewport) && !isLandscape(viewport);
  return rotated ? (dx, dy) => ({ x: dy, y: -dx }) : (dx, dy) => ({ x: dx, y: dy });
}

// Espacio real de la mesa (el <main> sin los márgenes de la muesca y la barra de inicio). Lo mide la
// GameShell montada; solo hay una a la vez.
let measuredTable: Viewport | null = null;
const tableListeners = new Set<() => void>();
function publishTableSize(next: Viewport | null) {
  if (next && measuredTable && next.width === measuredTable.width && next.height === measuredTable.height) return;
  measuredTable = next;
  tableListeners.forEach((l) => l());
}

/**
 * Ancho y alto disponibles para la mesa, entre la cabecera y los controles y dentro de los márgenes
 * seguros. Hasta que se mide, una estimación a partir de la pantalla.
 */
export function useTableSize(): Viewport {
  const viewport = useGameViewport();
  const measured = useSyncExternalStore(
    (listener) => {
      tableListeners.add(listener);
      return () => tableListeners.delete(listener);
    },
    () => measuredTable,
  );
  return measured ?? { width: viewport.width, height: Math.max(0, viewport.height - 48 - 68) };
}

interface GameShellProps {
  title: string;
  /** La mesa: ocupa todo el espacio entre la cabecera y los controles. */
  children: ReactNode;
  /** Barra de controles inferior. */
  controls?: ReactNode;
  /** Acciones extra en la cabecera (p. ej. tabla de pagos). */
  actions?: ReactNode;
  notice?: ReactNode;
  /** Fondo: tapete (por defecto) o sala oscura. */
  surface?: 'felt' | 'dark';
}

/**
 * Mesa a pantalla completa, siempre en horizontal: cabecera mínima (volver, nombre serigrafiado y
 * saldo), la mesa y una barra de controles abajo.
 *
 * Si el móvil está en vertical (Safari en iOS no deja fijar la orientación, o el giro automático
 * está bloqueado), la mesa se pinta girada 90°: basta con poner el móvil de lado, sin avisos.
 */
export function GameShell({ title, children, controls, actions, notice, surface = 'felt' }: GameShellProps) {
  useRequireLandscape();
  const navigate = useNavigate();
  const wallet = useReadyWallet();
  const viewport = useViewport();
  const [frame, setFrame] = useState<HTMLDivElement | null>(null);
  const rotated = isPhone(viewport) && !isLandscape(viewport);
  const insets = useSafeArea();
  const angle = useScreenAngle();
  const main = useRef<HTMLElement>(null);

  useLayoutEffect(() => {
    const element = main.current;
    if (!element) return;
    const measure = () => {
      const style = getComputedStyle(element);
      publishTableSize({
        width: Math.floor(element.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight)),
        height: Math.floor(element.clientHeight - parseFloat(style.paddingTop) - parseFloat(style.paddingBottom)),
      });
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => {
      observer.disconnect();
      publishTableSize(null);
    };
  }, []);

  // Girada 90° en el sentido de las agujas del reloj: el borde superior físico (muesca) queda a la
  // izquierda de la mesa y el inferior (barra de inicio), a la derecha.
  const rotatedStyle = {
    position: 'fixed',
    top: 0,
    left: 0,
    width: viewport.height,
    height: viewport.width,
    transform: `translateX(${viewport.width}px) rotate(90deg)`,
    transformOrigin: 'top left',
    // El lado izquierdo de la mesa cae sobre la parte de arriba del móvil (isla dinámica).
    ...rotatedSafeArea(insets, true, 'left'),
  } as CSSProperties;
  // Móvil en horizontal: la isla queda a la izquierda (girado a la izquierda) o a la derecha.
  const landscapeStyle =
    isPhone(viewport) && !rotated
      ? ({
          '--safe-left': `${angle === 270 ? insets.left : Math.max(insets.left, ISLAND_CLEARANCE)}px`,
          '--safe-right': `${angle === 270 ? Math.max(insets.right, ISLAND_CLEARANCE) : insets.right}px`,
        } as CSSProperties)
      : undefined;

  return (
    <PortalTarget.Provider value={frame}>
        <div
          ref={setFrame}
          style={rotated ? rotatedStyle : landscapeStyle}
          className={`relative flex flex-col overflow-hidden ${rotated ? '' : 'h-full'} ${surface === 'felt' ? 'felt' : 'bg-[radial-gradient(ellipse_at_50%_35%,#1d2029,#0b0c10_70%)]'}`}
        >
          <header className="safe-top safe-px-2 relative z-20 flex h-12 shrink-0 items-center gap-2" style={{ boxSizing: 'content-box' }}>
            <button
              type="button"
              onClick={() => navigate('/')}
              aria-label="Volver al casino"
              className="grid size-10 place-items-center rounded-full bg-black/30 text-ivory ring-1 ring-gold/30 hover:bg-black/45"
            >
              <BackIcon />
            </button>
            <h1 className="felt-print min-w-0 truncate text-[15px] font-semibold">{title}</h1>
            <div className="flex-1" />
            {actions}
            {wallet && (
              <div className="rounded-full bg-black/35 px-3 py-1.5 ring-1 ring-gold/30">
                <ChipBalance balance={wallet.balance} size="sm" />
              </div>
            )}
          </header>
          <main ref={main} className={`safe-x relative min-h-0 flex-1 ${controls ? '' : 'safe-bottom'}`}>{children}</main>
          {controls && (
            <footer className="safe-bottom safe-x relative z-20 shrink-0 border-t border-gold/20 bg-[linear-gradient(180deg,rgb(10_12_14/0.82),rgb(6_7_9/0.95))] backdrop-blur">
              {controls}
            </footer>
          )}
          <div className="pointer-events-none absolute inset-x-0 top-14 z-30 flex justify-center px-4" aria-live="assertive">
            <AnimatePresence>
              {notice && (
                <motion.div initial={{ y: -16, opacity: 0 }} animate={{ y: 0, opacity: 1 }} exit={{ y: -16, opacity: 0 }} className="pointer-events-auto">
                  {notice}
                </motion.div>
              )}
            </AnimatePresence>
          </div>
          {/* Avisos de nivel y logros dentro de la mesa: giran con ella. */}
          <ProgressToasts />
        </div>
    </PortalTarget.Provider>
  );
}

/** Aviso flotante sobre la mesa (sin fondos, ronda en otra mesa…). */
export function TableNotice({ children, onDismiss }: { children: ReactNode; onDismiss: () => void }) {
  return (
    <div role="alert" className="flex max-w-md items-center gap-3 rounded-2xl bg-[#3b0b15]/95 px-4 py-2.5 text-sm text-[#ffe3e6] shadow-xl ring-1 ring-ruby-bright/50">
      <span className="min-w-0 flex-1">{children}</span>
      <button type="button" onClick={onDismiss} className="shrink-0 rounded-lg px-2 py-1 text-xs font-bold text-gold-light hover:bg-white/10">
        Entendido
      </button>
    </div>
  );
}
