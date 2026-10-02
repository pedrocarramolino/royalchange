import { useEffect } from 'react';
import { useAuth } from '@/data/auth';
import { useWallet, ECONOMY_ERROR_TEXT } from '@/data/wallet';
import type { EconomyError, GameType } from '@/domain/economy';

/** Estado de una mesa guardado en el dispositivo para reanudar la mano al volver. */
export function loadSession<T>(uid: string, game: GameType): T | null {
  try {
    const raw = localStorage.getItem(`royal-chance-mesa-${game}-${uid}`);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}

export function saveSession(uid: string, game: GameType, value: unknown) {
  try {
    localStorage.setItem(`royal-chance-mesa-${game}-${uid}`, JSON.stringify(value));
  } catch {
    // Sin almacenamiento: la mano no se podrá reanudar, pero se juega igual.
  }
}

export function clearSession(uid: string, game: GameType) {
  try {
    localStorage.removeItem(`royal-chance-mesa-${game}-${uid}`);
  } catch {
    // Nada que borrar.
  }
}

export function usePlayerId(): string | null {
  return useAuth((s) => (s.state.status === 'signedIn' ? s.state.user.uid : null));
}

/** Mientras la mesa anima un resultado, los avisos de nivel y logros esperan. */
export function useHoldProgressEvents(held: boolean) {
  const hold = useWallet((s) => s.holdEvents);
  useEffect(() => {
    hold(held);
    return () => hold(false);
  }, [held, hold]);
}

/** Texto de un error de la economía visto desde una mesa. */
export function economyNotice(error: EconomyError): string {
  if (error.type === 'roundInProgress') {
    const names: Record<GameType, string> = { Blackjack: 'blackjack', Roulette: 'ruleta', Slots: 'slots', Poker: 'póker', Dice: 'dados', Baccarat: 'baccarat', VideoPoker: 'video póker' };
    return `Tienes fichas en una mano de ${names[error.game]}. Termínala antes de jugar aquí.`;
  }
  return ECONOMY_ERROR_TEXT[error.type];
}

export const wait = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));
