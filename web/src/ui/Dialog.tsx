import { createContext, useContext, useEffect, useId, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { AnimatePresence, motion } from 'motion/react';

/** Dónde se pintan los diálogos: el documento o, en una mesa girada, la propia mesa (para que giren con ella). */
export const PortalTarget = createContext<HTMLElement | null>(null);

interface DialogProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  actions?: ReactNode;
  /** Hoja que sube desde abajo (móvil) en lugar de ventana centrada. */
  sheet?: boolean;
}

/** Diálogo modal accesible: foco atrapado, Escape para cerrar y fondo que cierra. */
export function Dialog({ open, onClose, title, children, actions, sheet }: DialogProps) {
  const titleId = useId();
  const target = useContext(PortalTarget);
  const panel = useRef<HTMLDivElement>(null);
  const previous = useRef<Element | null>(null);

  useEffect(() => {
    if (!open) return;
    previous.current = document.activeElement;
    const timer = setTimeout(() => panel.current?.focus(), 30);
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
      if (event.key === 'Tab' && panel.current) {
        const focusable = panel.current.querySelectorAll<HTMLElement>('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
        if (!focusable.length) return;
        const first = focusable[0]!;
        const last = focusable[focusable.length - 1]!;
        if (event.shiftKey && document.activeElement === first) {
          last.focus();
          event.preventDefault();
        } else if (!event.shiftKey && document.activeElement === last) {
          first.focus();
          event.preventDefault();
        }
      }
    };
    document.addEventListener('keydown', onKey);
    return () => {
      clearTimeout(timer);
      document.removeEventListener('keydown', onKey);
      (previous.current as HTMLElement | null)?.focus?.();
    };
  }, [open, onClose]);

  return createPortal(
    <AnimatePresence>
      {open && (
        <motion.div
          className={`fixed inset-0 z-50 flex ${sheet ? 'items-end' : 'items-center'} justify-center bg-black/75 p-0 ${sheet ? '' : 'px-5'}`}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
        >
          <motion.div
            ref={panel}
            role="dialog"
            aria-modal="true"
            aria-labelledby={titleId}
            tabIndex={-1}
            onClick={(e) => e.stopPropagation()}
            className={`panel max-h-[88dvh] w-full overflow-y-auto outline-none ${sheet ? 'safe-pb-5 max-w-lg rounded-t-3xl px-5 pt-5' : 'max-w-md rounded-3xl p-6'}`}
            initial={sheet ? { y: 60, opacity: 0 } : { scale: 0.94, opacity: 0 }}
            animate={sheet ? { y: 0, opacity: 1 } : { scale: 1, opacity: 1 }}
            exit={sheet ? { y: 60, opacity: 0 } : { scale: 0.96, opacity: 0 }}
            transition={{ type: 'spring', damping: 26, stiffness: 320 }}
          >
            <h2 id={titleId} className="font-display text-xl font-semibold text-gold-light">
              {title}
            </h2>
            <div className="mt-3 text-[15px] leading-relaxed text-ivory-dim">{children}</div>
            {actions && <div className="mt-6 flex flex-col gap-2">{actions}</div>}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>,
    target ?? document.body,
  );
}
