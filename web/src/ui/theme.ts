import { useSettings, type ThemePreference } from '@/data/settings';

/*
 * Modo oscuro o claro: se marca en <html data-theme> (los colores están en styles/index.css) y en la
 * barra del navegador. «Automático» sigue al sistema y cambia en cuanto el sistema cambia.
 */
const THEME_COLOR = { dark: '#0E0F13', light: '#F3EEE3' } as const;
const systemDark = window.matchMedia('(prefers-color-scheme: dark)');

function resolve(preference: ThemePreference): 'dark' | 'light' {
  if (preference === 'system') return systemDark.matches ? 'dark' : 'light';
  return preference;
}

function apply() {
  const theme = resolve(useSettings.getState().theme);
  document.documentElement.dataset.theme = theme;
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', THEME_COLOR[theme]);
  document.querySelector('meta[name="color-scheme"]')?.setAttribute('content', theme);
}

/** Aplica el tema guardado y lo mantiene al día (llamar una vez, antes de pintar la app). */
export function startTheme() {
  apply();
  useSettings.subscribe((s, prev) => s.theme !== prev.theme && apply());
  systemDark.addEventListener('change', apply);
}
