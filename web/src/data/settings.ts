import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface SettingsStore {
  soundEnabled: boolean;
  /** Sin celebraciones ni movimientos decorativos; las mesas siguen mostrando cada resultado. */
  reducedMotion: boolean;
  setSoundEnabled: (value: boolean) => void;
  setReducedMotion: (value: boolean) => void;
}

/** Preferencias de este dispositivo (no viajan con la cuenta). */
export const useSettings = create<SettingsStore>()(
  persist(
    (set) => ({
      soundEnabled: true,
      reducedMotion: typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches,
      setSoundEnabled: (soundEnabled) => set({ soundEnabled }),
      setReducedMotion: (reducedMotion) => set({ reducedMotion }),
    }),
    { name: 'royal-chance-ajustes' },
  ),
);
