import { create } from 'zustand';
import { doc, getDocFromCache, onSnapshot, writeBatch, type Unsubscribe } from 'firebase/firestore';
import { db } from '@/lib/firebase';
import { newId } from '@/lib/ids';
import { localEpochDay } from '@/lib/time';
import type { AchievementId, EconomyError, EconomyOperation, GameType, ProgressEvent, Wallet } from '@/domain/economy';
import { entryToData, walletFromData, walletToData } from '@/domain/documents';
import { applyOperation, openWallet, type WalletTransition } from '@/domain/transitions';
import { useAuth } from './auth';

export type WalletState = { status: 'loading' } | { status: 'ready'; wallet: Wallet } | { status: 'unavailable' };

export type OperationResult = { ok: true; wallet: Wallet } | { ok: false; error: EconomyError };

interface WalletStore {
  state: WalletState;
  /** Avisos de progreso pendientes de mostrar (subidas de nivel y logros). */
  events: ProgressEvent[];
  /** Mientras un juego anima su resultado, los avisos esperan: no deben adelantarlo. */
  eventsHeld: boolean;
  placeBet: (game: GameType, stake: number) => Promise<OperationResult>;
  settleRound: (payout: number) => Promise<OperationResult>;
  playInstantRound: (game: GameType, stake: number, payout: number) => Promise<OperationResult>;
  claimRescue: () => Promise<OperationResult>;
  claimDailyBonus: () => Promise<OperationResult>;
  /** Ruleta diaria: cobra la casilla [prize] en la que ha caído. */
  spinDailyWheel: (prize: number) => Promise<OperationResult>;
  claimAchievement: (id: AchievementId) => Promise<OperationResult>;
  /** Cobra la misión [index] (0, 1 o 2) del día, ya completada. */
  claimMission: (index: number) => Promise<OperationResult>;
  holdEvents: (held: boolean) => void;
  consumeEvent: () => void;
}

/**
 * Economía sobre Firestore:
 * - Cada operación se valida con el dominio contra la versión local más reciente del monedero y se
 *   escribe junto a su asiento en un único lote. Las operaciones van en serie.
 * - No se espera al servidor: Firestore aplica el lote en local al instante (también sin conexión)
 *   y lo sincroniza después. Si el servidor lo rechazara, Firestore deshace el cambio local.
 */
export const useWallet = create<WalletStore>((set, get) => {
  let uid: string | null = null;
  let unsubscribe: Unsubscribe | null = null;
  /** Monederos que ya se intentó crear en esta sesión: un rechazo no se reintenta en bucle. */
  const creationAttempted = new Set<string>();
  /** Cola de operaciones: una tras otra, siempre sobre el último estado local. */
  let queue: Promise<unknown> = Promise.resolve();

  const serial = <T,>(task: () => Promise<T>): Promise<T> => {
    const run = queue.then(task, task);
    queue = run.catch(() => undefined);
    return run;
  };

  const write = (playerId: string, transition: WalletTransition): boolean => {
    try {
      const batch = writeBatch(db);
      batch.set(doc(db, `wallets/${playerId}`), walletToData(transition.wallet));
      batch.set(doc(db, `wallets/${playerId}/ledger/${transition.entry.id}`), entryToData(transition.entry));
      // La confirmación del servidor llega después; un rechazo lo deshace Firestore solo.
      batch.commit().catch(() => undefined);
      return true;
    } catch {
      return false;
    }
  };

  const observe = (playerId: string) => {
    let seen = false;
    unsubscribe = onSnapshot(
      doc(db, `wallets/${playerId}`),
      { includeMetadataChanges: true },
      (snapshot) => {
        if (snapshot.exists()) {
          seen = true;
          const wallet = walletFromData(snapshot.data());
          // Cada jugada genera varios avisos con los mismos datos (escritura local, confirmación del
          // servidor…): si no ha cambiado nada, no se repinta.
          const current = get().state;
          if (current.status === 'ready' && sameWallet(current.wallet, wallet)) return;
          set({ state: { status: 'ready', wallet } });
        } else if (!snapshot.metadata.fromCache) {
          // Desaparece uno que existía: la cuenta se está borrando. Nunca se recrea.
          if (seen) set({ state: { status: 'unavailable' } });
          else void serial(async () => createWallet(playerId));
        }
      },
      () => set({ state: { status: 'unavailable' } }),
    );
  };

  /** Primer acceso del jugador: monedero con las fichas de bienvenida. */
  const createWallet = (playerId: string) => {
    if (creationAttempted.has(playerId)) {
      set({ state: { status: 'unavailable' } });
      return;
    }
    creationAttempted.add(playerId);
    if (!write(playerId, openWallet(playerId, newId(), Date.now()))) set({ state: { status: 'unavailable' } });
  };

  const readLocal = async (playerId: string) => {
    // El último estado local ya está en memoria (se publica al escribir y con cada aviso de Firestore):
    // no hace falta leerlo de IndexedDB en cada jugada.
    const state = get().state;
    if (state.status === 'ready' && state.wallet.uid === playerId) return state.wallet;
    try {
      const snapshot = await getDocFromCache(doc(db, `wallets/${playerId}`));
      return snapshot.exists() ? walletFromData(snapshot.data()) : null;
    } catch {
      const state = get().state;
      return state.status === 'ready' ? state.wallet : null;
    }
  };

  const execute = (operation: EconomyOperation): Promise<OperationResult> =>
    serial(async () => {
      const playerId = uid;
      if (!playerId) return { ok: false, error: { type: 'walletUnavailable' } } as const;
      const current = await readLocal(playerId);
      if (!current) return { ok: false, error: { type: 'walletUnavailable' } } as const;
      const result = applyOperation(current, operation, newId(), Date.now());
      if (!result.ok) return result;
      if (!write(playerId, result.value)) return { ok: false, error: { type: 'walletUnavailable' } } as const;
      // Se publica ya: la interfaz no espera al siguiente aviso de Firestore.
      set((s) => ({ state: { status: 'ready', wallet: result.value.wallet }, events: [...s.events, ...result.value.events] }));
      return { ok: true, wallet: result.value.wallet } as const;
    });

  // El monedero sigue a la sesión: se observa el del jugador con perfil.
  useAuth.subscribe((store) => {
    const auth = store.state;
    const next = auth.status === 'signedIn' && auth.user.profile ? auth.user.uid : null;
    if (next === uid) return;
    unsubscribe?.();
    unsubscribe = null;
    uid = next;
    set({ state: next ? { status: 'loading' } : { status: auth.status === 'loading' ? 'loading' : 'unavailable' }, events: [] });
    if (next) observe(next);
  });

  return {
    state: { status: 'loading' },
    events: [],
    eventsHeld: false,
    placeBet: (game, stake) => execute({ type: 'placeBet', game, stake }),
    // Las rondas llevan el día del dispositivo: así cuentan para las misiones diarias.
    settleRound: (payout) => execute({ type: 'settleRound', payout, today: localEpochDay() }),
    playInstantRound: (game, stake, payout) => execute({ type: 'instantRound', game, stake, payout, today: localEpochDay() }),
    claimRescue: () => execute({ type: 'claimRescue' }),
    claimDailyBonus: () => execute({ type: 'claimDailyBonus', today: localEpochDay() }),
    spinDailyWheel: (prize) => execute({ type: 'spinDailyWheel', today: localEpochDay(), prize }),
    claimAchievement: (id) => execute({ type: 'claimAchievement', id }),
    claimMission: (index) => execute({ type: 'claimMission', index, today: localEpochDay() }),
    holdEvents: (held) => set({ eventsHeld: held }),
    consumeEvent: () => set((s) => ({ events: s.events.slice(1) })),
  };
});

/** Monedero listo o `null`. */
export function useReadyWallet(): Wallet | null {
  return useWallet((s) => (s.state.status === 'ready' ? s.state.wallet : null));
}

export const ECONOMY_ERROR_TEXT: Record<EconomyError['type'], string> = {
  walletUnavailable: 'No se puede acceder a tus fichas ahora mismo. Comprueba la conexión.',
  belowMinimumBet: 'La apuesta mínima es de 10 fichas.',
  aboveMaximumStake: 'Has superado el máximo por ronda.',
  insufficientFunds: 'No tienes fichas suficientes para esa apuesta.',
  roundInProgress: 'Tienes fichas en otra mesa. Termina esa ronda antes de jugar aquí.',
  noOpenRound: 'No hay ninguna ronda abierta.',
  payoutTooHigh: 'Pago no válido.',
  rescueNotNeeded: 'Aún tienes fichas para jugar.',
  rescueCoolingDown: 'La recarga gratuita aún no está disponible.',
  dailyBonusAlreadyClaimed: 'Ya has cobrado el bono de hoy.',
  dailyBonusClockMovedBack: 'La hora del dispositivo parece incorrecta. Ajústala para cobrar el bono.',
  invalidDailyPrize: 'Premio no válido.',
  dailySpinAlreadyUsed: 'Ya has girado la ruleta diaria hoy.',
  achievementLocked: 'Ese logro aún no está desbloqueado.',
  missionNotCompleted: 'Esa misión aún no está completada.',
  missionAlreadyClaimed: 'Ya has recogido esa misión.',
  achievementAlreadyClaimed: 'Ya has recogido esa recompensa.',
};

/** Mismo monedero: las escrituras siempre avanzan el número de asiento y cambian el último asiento. */
function sameWallet(a: Wallet, b: Wallet): boolean {
  return a.seq === b.seq && a.lastEntryId === b.lastEntryId && a.balance === b.balance && JSON.stringify(a) === JSON.stringify(b);
}
