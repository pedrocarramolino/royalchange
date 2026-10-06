import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface SettingsStore {
  soundEnabled: boolean;
  /** Sin celebraciones ni movimientos decorativos; las mesas siguen mostrando cada resultado. */
  reducedMotion: boolean;
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
      soundEnabled: true,
      reducedMotion: typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches,
      leaderboardEnabled: true,
      setLeaderboardEnabled: (leaderboardEnabled) => set({ leaderboardEnabled }),
      setSoundEnabled: (soundEnabled) => set({ soundEnabled }),
      setReducedMotion: (reducedMotion) => set({ reducedMotion }),
    }),
    { name: 'royal-chance-ajustes' },
  ),
);
