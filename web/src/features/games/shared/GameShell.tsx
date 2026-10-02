import type { ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { AnimatePresence, motion } from 'motion/react';
import { useReadyWallet } from '@/data/wallet';
import { useRequireLandscape } from '@/ui/orientation';
import { ChipBalance } from '@/ui/ChipBalance';
import { BackIcon } from '@/ui/TopBar';

interface GameShellProps {
  title: string;
  /** La mesa: ocupa todo el espacio entre la cabecera y los controles. */
  children: ReactNode;
  /** Barra de controles inferior. */
  controls?: ReactNode;
  /** Acciones extra en la cabecera (p. ej. tabla de pagos). */
  actions?: ReactNode;
  notice?: ReactNode;
  /** Fondo de la mesa: tapete (por defecto) u otro. */
  surface?: 'felt' | 'dark';
}

/**
 * Mesa a pantalla completa, en horizontal: cabecera mínima (volver, nombre serigrafiado y saldo),
 * la mesa y una barra de controles abajo. Pensada para la poca altura de un móvil girado.
 */
export function GameShell({ title, children, controls, actions, notice, surface = 'felt' }: GameShellProps) {
  useRequireLandscape();
  const navigate = useNavigate();
  const wallet = useReadyWallet();
  return (
    <div className={`relative flex h-full flex-col overflow-hidden ${surface === 'felt' ? 'felt' : 'bg-[radial-gradient(ellipse_at_50%_35%,#1d2029,#0b0c10_70%)]'}`}>
      <header className="safe-top safe-x relative z-20 flex h-12 shrink-0 items-center gap-2 px-2">
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
      <main className="safe-x relative min-h-0 flex-1">{children}</main>
      {controls && <footer className="safe-bottom safe-x relative z-20 shrink-0 border-t border-gold/20 bg-[linear-gradient(180deg,rgb(10_12_14/0.82),rgb(6_7_9/0.95))] backdrop-blur">{controls}</footer>}
      <div className="pointer-events-none absolute inset-x-0 top-14 z-30 flex justify-center px-4" aria-live="assertive">
        <AnimatePresence>
          {notice && (
            <motion.div initial={{ y: -16, opacity: 0 }} animate={{ y: 0, opacity: 1 }} exit={{ y: -16, opacity: 0 }} className="pointer-events-auto">
              {notice}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
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
