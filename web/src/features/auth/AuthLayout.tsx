import type { ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { motion } from 'motion/react';
import { EASE_OUT } from '@/ui/motion';
import { TopBar } from '@/ui/TopBar';

/** Pantallas de acceso: columna centrada que se desplaza, con el resplandor del tapete arriba. */
export function AuthLayout({ title, subtitle, children, back = true, onBack }: { title: string; subtitle?: string; children: ReactNode; back?: boolean; onBack?: () => void }) {
  const navigate = useNavigate();
  return (
    <div className="relative h-full overflow-y-auto overscroll-contain">
      <div className="pointer-events-none absolute inset-x-0 top-0 h-72 bg-[radial-gradient(ellipse_70%_100%_at_50%_0%,rgb(15_91_69/0.45),transparent)]" aria-hidden />
      <div className="safe-top safe-pb-8 safe-px-5 relative mx-auto flex min-h-full w-full max-w-md flex-col">
        {back ? <TopBar onBack={onBack ?? (() => navigate(-1))} className="-mx-3" /> : <div className="h-6" />}
        <Rise delay={0}>
          <h1 className="mt-2 font-display text-[28px] leading-tight font-semibold text-gold-gradient">{title}</h1>
        </Rise>
        {subtitle && (
          <Rise delay={0.05}>
            <p className="mt-2 text-[15px] leading-relaxed text-ivory-dim">{subtitle}</p>
          </Rise>
        )}
        <Rise delay={0.1} className="mt-7 flex flex-1 flex-col">
          {children}
        </Rise>
      </div>
    </div>
  );
}

/** Entrada corta y sutil: sube un poco y aparece. */
function Rise({ delay, className, children }: { delay: number; className?: string; children: ReactNode }) {
  return (
    <motion.div
      className={className}
      initial={{ opacity: 0, transform: 'translateY(8px)' }}
      animate={{ opacity: 1, transform: 'translateY(0px)' }}
      transition={{ duration: 0.35, ease: EASE_OUT, delay }}
    >
      {children}
    </motion.div>
  );
}

/**
 * Aviso del formulario. El de error entra con una pequeña sacudida (como un «no» con la cabeza);
 * cambiando su `key` se vuelve a sacudir en cada intento fallido.
 */
export function FormBanner({ children, tone = 'error' }: { children: ReactNode; tone?: 'error' | 'success' }) {
  return (
    <motion.div
      role={tone === 'error' ? 'alert' : 'status'}
      className={`rounded-2xl border px-4 py-3 text-[15px] ${tone === 'error' ? 'border-ruby/60 bg-ruby/15 text-[#ffd9dd]' : 'border-emerald/50 bg-emerald/10 text-[#c9f2df]'}`}
      initial={{ opacity: 0, transform: 'translateX(0px)' }}
      animate={{
        opacity: 1,
        transform: tone === 'error' ? ['translateX(0px)', 'translateX(-7px)', 'translateX(6px)', 'translateX(-4px)', 'translateX(2px)', 'translateX(0px)'] : 'translateX(0px)',
      }}
      transition={{ duration: tone === 'error' ? 0.4 : 0.25, ease: EASE_OUT }}
    >
      {children}
    </motion.div>
  );
}

export function SectionTitle({ children }: { children: ReactNode }) {
  return <h2 className="mt-7 mb-3 text-xs font-bold tracking-[0.18em] text-gold uppercase first:mt-0">{children}</h2>;
}
