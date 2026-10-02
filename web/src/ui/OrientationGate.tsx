import { useEffect, useState, type CSSProperties, type ReactNode } from 'react';
import { PortalTarget } from './Dialog';
import { isLandscape, isPhone, lockOrientation, useOrientationStore, useScreenAngle, useViewport } from './orientation';
import { rotatedSafeArea, useSafeArea } from './safeArea';

/**
 * Orientación de la app, como una app nativa bloqueada: el casino y el resto de pantallas siempre
 * en vertical y las mesas siempre en horizontal, sin avisos de girar el móvil.
 *
 * - Donde se puede, se fija la orientación (APK con Capacitor, PWA instalada en Android).
 * - Donde no (Safari en iOS), el contenido se pinta girado para quedar alineado con el móvil: fuera
 *   de las mesas aquí, y en las mesas con el móvil en vertical en GameShell.
 *
 * El árbol de componentes es el mismo esté girado o no: al girar el móvil nada se reinicia.
 */
export function OrientationGate({ children }: { children: ReactNode }) {
  const wantsLandscape = useOrientationStore((s) => s.landscapeRequests > 0);
  const viewport = useViewport();
  const angle = useScreenAngle();
  const insets = useSafeArea();
  const [frame, setFrame] = useState<HTMLDivElement | null>(null);

  useEffect(() => {
    void lockOrientation(wantsLandscape);
  }, [wantsLandscape]);

  const rotate = isPhone(viewport) && isLandscape(viewport) && !wantsLandscape;
  // Girado a la derecha (270): la parte de arriba del móvil queda a la derecha → se gira 90° en el
  // sentido de las agujas del reloj. Girado a la izquierda (90, o sin dato): 90° en sentido contrario.
  const clockwise = angle === 270;
  const style: CSSProperties | undefined = rotate
    ? ({
        position: 'fixed',
        top: 0,
        left: 0,
        width: viewport.height,
        height: viewport.width,
        transformOrigin: 'top left',
        transform: clockwise ? `translateX(${viewport.width}px) rotate(90deg)` : `translateY(${viewport.height}px) rotate(-90deg)`,
        // La parte de arriba del contenido cae sobre la parte de arriba del móvil (isla dinámica).
        ...rotatedSafeArea(insets, clockwise, 'top'),
      } as CSSProperties)
    : undefined;

  return (
    <PortalTarget.Provider value={rotate ? frame : null}>
      <div ref={setFrame} style={style} className={rotate ? 'overflow-hidden bg-obsidian' : 'h-full'}>
        {children}
      </div>
    </PortalTarget.Provider>
  );
}
