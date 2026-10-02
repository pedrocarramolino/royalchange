import type { ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { TopBar } from '@/ui/TopBar';

/** Pantallas de acceso: columna centrada que se desplaza, con el resplandor del tapete arriba. */
export function AuthLayout({ title, subtitle, children, back = true, onBack }: { title: string; subtitle?: string; children: ReactNode; back?: boolean; onBack?: () => void }) {
  const navigate = useNavigate();
  return (
    <div className="relative h-full overflow-y-auto overscroll-contain">
      <div className="pointer-events-none absolute inset-x-0 top-0 h-72 bg-[radial-gradient(ellipse_70%_100%_at_50%_0%,rgb(15_91_69/0.45),transparent)]" aria-hidden />
      <div className="safe-top safe-pb-8 safe-px-5 relative mx-auto flex min-h-full w-full max-w-md flex-col">
        {back ? <TopBar onBack={onBack ?? (() => navigate(-1))} className="-mx-3" /> : <div className="h-6" />}
        <h1 className="mt-2 font-display text-[28px] leading-tight font-semibold text-gold-gradient">{title}</h1>
        {subtitle && <p className="mt-2 text-[15px] leading-relaxed text-ivory-dim">{subtitle}</p>}
        <div className="mt-7 flex flex-1 flex-col">{children}</div>
      </div>
    </div>
  );
}

export function FormBanner({ children, tone = 'error' }: { children: ReactNode; tone?: 'error' | 'success' }) {
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={`rounded-2xl border px-4 py-3 text-[15px] ${tone === 'error' ? 'border-ruby/60 bg-ruby/15 text-[#ffd9dd]' : 'border-emerald/50 bg-emerald/10 text-[#c9f2df]'}`}
    >
      {children}
    </div>
  );
}

export function SectionTitle({ children }: { children: ReactNode }) {
  return <h2 className="mt-7 mb-3 text-xs font-bold tracking-[0.18em] text-gold uppercase first:mt-0">{children}</h2>;
}
