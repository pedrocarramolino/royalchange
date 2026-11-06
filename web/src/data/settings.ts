import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/** Apariencia de los menús: las mesas son siempre oscuras. */
export type ThemePreference = 'dark' | 'light' | 'system';

interface SettingsStore {
  theme: ThemePreference;
  setTheme: (value: ThemePreference) => void;
  soundEnabled: boolean;
  /** Sin celebraciones ni movimientos decorativos; las mesas siguen mostrando cada resultado. */
  reducedMotion: boolean;
  /** Vibración al jugar (cartas, fichas, premios…) en los móviles que vibran. */
  vibrationEnabled: boolean;
  setVibrationEnabled: (value: boolean) => void;
  /** Aparecer en la clasificación semanal (alias, avatar y ganancias, visibles para los demás). */
  leaderboardEnabled: boolean;
  setLeaderboardEnabled: (value: boolean) => void;
  setSoundEnabled: (value: boolean) => void;
  setReducedMotion: (value: boolean) => void;
}

/** Preferencias de este dispositivo (no viajan con la cuenta). */
export const useSettings = create<SettingsStore>()(
  persist(
    (set) => ({
      theme: 'dark',
      setTheme: (theme) => set({ theme }),
      soundEnabled: true,
      reducedMotion: typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches,
      vibrationEnabled: true,
      setVibrationEnabled: (vibrationEnabled) => set({ vibrationEnabled }),
      leaderboardEnabled: true,
      setLeaderboardEnabled: (leaderboardEnabled) => set({ leaderboardEnabled }),
      setSoundEnabled: (soundEnabled) => set({ soundEnabled }),
      setReducedMotion: (reducedMotion) => set({ reducedMotion }),
    }),
    { name: 'royal-chance-ajustes' },
  ),
);
