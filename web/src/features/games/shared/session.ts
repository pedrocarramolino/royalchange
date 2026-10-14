import { useEffect } from 'react';
import { create } from 'zustand';
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

/*
 * Premios retenidos: el resultado se contabiliza antes de animarlo, así que el saldo real ya lo
 * incluye. La cabecera de la mesa resta lo retenido para no adelantar el premio: se suma cuando la
 * mesa lo enseña (bola parada, rodillos quietos, cartas vueltas…).
 */
const useHeldPayouts = create<{ total: number }>(() => ({ total: 0 }));

/** Fichas de premio que la cabecera aún no debe mostrar. */
export const useHeldPayout = () => useHeldPayouts((s) => s.total);

/** Olvida lo retenido (al salir de la mesa). */
export const resetHeldPayouts = () => useHeldPayouts.setState({ total: 0 });

/**
 * Retiene [amount] fichas de premio hasta llamar a la función que devuelve (se puede llamar varias
 * veces). Se pide antes de contabilizar la ronda, para que el saldo nuevo no asome ni un instante.
 */
export function holdPayout(amount: number): () => void {
  if (amount <= 0) return () => undefined;
  useHeldPayouts.setState((s) => ({ total: s.total + amount }));
  let released = false;
  return () => {
    if (released) return;
    released = true;
    useHeldPayouts.setState((s) => ({ total: Math.max(0, s.total - amount) }));
  };
}

/** Texto de un error de la economía visto desde una mesa. */
export function economyNotice(error: EconomyError): string {
  if (error.type === 'roundInProgress') {
    const names: Record<GameType, string> = { Blackjack: 'blackjack', Roulette: 'ruleta', Slots: 'slots', Poker: 'póker', Dice: 'dados', Baccarat: 'baccarat', VideoPoker: 'video póker', Plinko: 'plinko', Scratch: 'rasca y gana' };
    return `Tienes fichas en una mano de ${names[error.game]}. Termínala antes de jugar aquí.`;
  }
  return ECONOMY_ERROR_TEXT[error.type];
}

export const wait = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));
