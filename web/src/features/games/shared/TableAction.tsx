import type { ReactNode } from 'react';
import { Button } from '@/ui/Button';

/**
 * Acción secundaria de la barra de una mesa (deshacer, borrar, repetir): con su nombre si cabe y,
 * en una mesa estrecha, solo el icono (el nombre queda para lectores de pantalla y como título).
 */
export function TableAction({ label, icon, compact, disabled, onClick }: { label: string; icon: ReactNode; compact: boolean; disabled: boolean; onClick: () => void }) {
  if (!compact) {
    return (
      <Button variant="ghost" size="sm" disabled={disabled} onClick={onClick}>
        {label}
      </Button>
    );
  }
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      disabled={disabled}
      onClick={onClick}
      className="grid size-10 shrink-0 place-items-center rounded-xl text-gold transition-colors hover:bg-gold/10 active:bg-gold/15 disabled:opacity-40"
    >
      {icon}
    </button>
  );
}
