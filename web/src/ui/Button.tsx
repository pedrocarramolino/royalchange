import type { ButtonHTMLAttributes, ReactNode } from 'react';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';
type Size = 'md' | 'lg' | 'sm';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  icon?: ReactNode;
  block?: boolean;
}

const VARIANTS: Record<Variant, string> = {
  primary: 'metal-gold font-bold active:brightness-95 disabled:opacity-40 disabled:saturate-50',
  secondary:
    'border border-gold/70 text-gold-light bg-ink-2/60 font-semibold hover:bg-gold/10 active:bg-gold/15 disabled:opacity-40',
  ghost: 'text-gold font-semibold hover:bg-gold/10 active:bg-gold/15 disabled:opacity-40',
  danger: 'bg-ruby text-ivory font-semibold hover:brightness-110 active:brightness-95 disabled:opacity-40',
};

const SIZES: Record<Size, string> = {
  sm: 'h-10 px-3 text-sm rounded-xl',
  md: 'h-12 px-4 text-[15px] rounded-2xl',
  lg: 'h-14 px-6 text-base rounded-2xl',
};

/** Botón de una sola línea: si el texto no cabe se acorta con puntos, nunca salta de línea. */
export function Button({ variant = 'primary', size = 'md', loading, icon, block, className = '', children, disabled, ...rest }: ButtonProps) {
  return (
    <button
      type="button"
      {...rest}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={`relative inline-flex min-w-0 select-none items-center justify-center gap-2 whitespace-nowrap transition-[filter,background-color,transform] duration-150 active:scale-[0.98] ${VARIANTS[variant]} ${SIZES[size]} ${block ? 'w-full' : ''} ${className}`}
    >
      {loading ? <Spinner /> : icon}
      <span className={`truncate ${loading ? 'opacity-0' : ''}`}>{children}</span>
      {loading && <span className="sr-only">Cargando</span>}
    </button>
  );
}

export function Spinner({ className = '' }: { className?: string }) {
  return (
    <span
      className={`absolute size-5 animate-spin rounded-full border-2 border-current border-t-transparent ${className}`}
      aria-hidden
    />
  );
}
