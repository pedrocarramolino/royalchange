import { NavLink, Outlet } from 'react-router';
import type { ComponentType, SVGProps } from 'react';
import { IconCasino, IconHistory, IconSettings, IconTrophy } from '@/ui/icons';
import { useReadyWallet } from '@/data/wallet';
import { claimable } from '@/domain/progression';

const TABS: { to: string; label: string; Icon: ComponentType<SVGProps<SVGSVGElement>> }[] = [
  { to: '/', label: 'Casino', Icon: IconCasino },
  { to: '/progreso', label: 'Progreso', Icon: IconTrophy },
  { to: '/historial', label: 'Historial', Icon: IconHistory },
  { to: '/ajustes', label: 'Ajustes', Icon: IconSettings },
];

/** Pantallas del casino (vertical): contenido desplazable y navegación inferior fija. */
export function CasinoLayout() {
  const wallet = useReadyWallet();
  const pending = wallet ? claimable(wallet).length : 0;
  return (
    <div className="flex h-full flex-col">
      <main className="min-h-0 flex-1 overflow-y-auto overscroll-contain">
        <Outlet />
      </main>
      <nav className="safe-bottom safe-x shrink-0 border-t border-gold/15 bg-ink-1/95 backdrop-blur" aria-label="Secciones">
        <ul className="mx-auto flex max-w-lg">
          {TABS.map(({ to, label, Icon }) => (
            <li key={to} className="flex-1">
              <NavLink
                to={to}
                end
                className={({ isActive }) =>
                  `group relative flex h-16 flex-col items-center justify-center gap-1 text-[11px] font-semibold tracking-wide transition-colors ${isActive ? 'text-gold-light' : 'text-mute hover:text-ivory-dim'}`
                }
              >
                {({ isActive }) => (
                  <>
                    <span className={`grid h-7 w-12 place-items-center rounded-full transition-colors ${isActive ? 'bg-gold/15' : ''}`}>
                      <Icon className="size-[22px]" />
                    </span>
                    {label}
                    {to === '/progreso' && pending > 0 && (
                      <span className="absolute top-2 left-1/2 ml-3 grid size-4 place-items-center rounded-full bg-ruby-bright text-[10px] font-bold text-white" aria-label={`${pending} recompensas por recoger`}>
                        {pending}
                      </span>
                    )}
                  </>
                )}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  );
}
