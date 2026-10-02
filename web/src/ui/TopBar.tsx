import type { ReactNode } from 'react';

export function BackIcon() {
  return (
    <svg viewBox="0 0 24 24" className="size-6" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M15 5l-7 7 7 7" />
    </svg>
  );
}

/** Barra superior de pantalla: volver, título y acciones. */
export function TopBar({ title, onBack, backLabel = 'Volver', actions, className = '' }: { title?: ReactNode; onBack?: () => void; backLabel?: string; actions?: ReactNode; className?: string }) {
  return (
    <header className={`flex h-14 shrink-0 items-center gap-1 px-2 ${className}`}>
      {onBack && (
        <button type="button" onClick={onBack} aria-label={backLabel} className="grid size-11 place-items-center rounded-full text-ivory hover:bg-white/5 active:bg-white/10">
          <BackIcon />
        </button>
      )}
      {title && <h1 className={`min-w-0 flex-1 truncate font-display text-lg font-semibold tracking-wide text-ivory ${onBack ? '' : 'pl-3'}`}>{title}</h1>}
      {!title && <div className="flex-1" />}
      {actions && <div className="flex shrink-0 items-center gap-2 pr-2">{actions}</div>}
    </header>
  );
}
