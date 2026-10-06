/**
 * Economía de Royal Chance. Las reglas de seguridad de Firestore (`firebase/firestore.rules`)
 * repiten estos valores para validarlos en el servidor: si cambia uno, hay que cambiar los dos.
 *
 * Las fichas son enteros (sin decimales): la apuesta mínima y las fichas son múltiplos de 10 y
 * todos los pagos salen enteros.
 */
export const EconomyRules = {
  /** Fichas de bienvenida al crear el monedero. */
  WELCOME_GRANT: 10_000,
  MINIMUM_BET: 10,
  /** Máximo de fichas en juego en una ronda (dobles, separaciones y apuestas de un giro incluidas). */
  MAXIMUM_ROUND_STAKE: 100_000,
  /** Techo global: ninguna ronda paga más que su apuesta por este factor. */
  MAXIMUM_PAYOUT_MULTIPLIER: 1_000,
  /** Recarga gratuita cuando el saldo no llega a la apuesta mínima… */
  RESCUE_GRANT: 1_000,
  /** …como mucho una vez cada este intervalo. */
  RESCUE_COOLDOWN_MS: 4 * 60 * 60 * 1000,
} as const;

/** Juegos del casino. Los ids son estables: se guardan en la base de datos. */
export const GAMES = ['Blackjack', 'Roulette', 'Slots', 'Poker', 'Dice', 'Baccarat', 'VideoPoker', 'Plinko', 'Scratch'] as const;
export type GameType = (typeof GAMES)[number];

/** Tipos de asiento contable. Los nombres son estables: las reglas los validan. */
export type EntryKind = 'Welcome' | 'Bet' | 'Settlement' | 'InstantRound' | 'Rescue' | 'DailyBonus' | 'DailySpin' | 'AchievementReward' | 'MissionReward';

/** Logros, en orden de catálogo. Los ids son estables. */
export const ACHIEVEMENT_IDS = [
  'FirstWin',
  'Rounds10',
  'Rounds100',
  'Rounds1000',
  'Wins50',
  'WinStreak5',
  'WinStreak10',
  'Level5',
  'Level10',
  'Level25',
  'Balance50k',
  'Balance250k',
  'DailyStreak7',
  'DailyStreak30',
] as const;
export type AchievementId = (typeof ACHIEVEMENT_IDS)[number];

/**
 * Misiones del día [day]: progreso y cobro de cada una (ver domain/missions.ts). Siempre tres de
 * cada, en el orden fácil, media y difícil.
 */
export interface DailyMissions {
  day: number;
  progress: number[];
  claimed: boolean[];
}

/** Ronda por turnos (blackjack, póker) con fichas en la mesa, aún sin liquidar. */
export interface OpenRound {
  id: string;
  game: GameType;
  stake: number;
}

/**
 * Monedero: documento `wallets/{uid}` tal cual se guarda. La progresión va en el mismo documento
 * para que una ronda sea una única escritura. Los campos opcionales se omiten (nunca `null`): las
 * reglas distinguen "no está" de "está".
 *
 * @property seq número de movimientos: cada escritura lo incrementa exactamente en uno.
 * @property lastDailyDay día del último bono (días desde 1970-01-01 en la zona del dispositivo).
 */
export interface Wallet {
  uid: string;
  balance: number;
  seq: number;
  lastEntryId: string;
  openRound?: OpenRound;
  lastRescueAtMillis?: number;
  xp: number;
  rounds: number;
  wins: number;
  losses: number;
  pushes: number;
  winStreak: number;
  bestWinStreak: number;
  highestBalance: number;
  dailyStreak: number;
  lastDailyDay?: number;
  lastDailyAtMillis?: number;
  /** Día y hora del último giro de la ruleta diaria (independiente del bono y su racha). */
  lastSpinDay?: number;
  lastSpinAtMillis?: number;
  unlocked: AchievementId[];
  claimed: AchievementId[];
  /** Misiones diarias: aparece con la primera ronda que se juega con ellas. */
  missions?: DailyMissions;
}

/** Asiento contable inmutable: `wallets/{uid}/ledger/{id}`. */
export interface LedgerEntry {
  id: string;
  seq: number;
  kind: EntryKind;
  /** Variación del saldo: positiva si entran fichas, negativa si salen. */
  amount: number;
  balanceAfter: number;
  createdAtMillis: number;
  game?: GameType;
  roundId?: string;
  /** Fichas apostadas (Bet, InstantRound) o de toda la ronda (Settlement). */
  stake?: number;
  /** Fichas cobradas, apuesta devuelta incluida (Settlement, InstantRound). */
  payout?: number;
  achievementId?: AchievementId;
  /** Misión cobrada (MissionReward): 0, 1 o 2. */
  mission?: number;
}

/** Operaciones con intención que admite el monedero. No existe "fijar el saldo". */
export type EconomyOperation =
  | { type: 'placeBet'; game: GameType; stake: number }
  /** [today]: día del dispositivo, para las misiones diarias (sin él no avanzan). */
  | { type: 'settleRound'; payout: number; today?: number }
  | { type: 'instantRound'; game: GameType; stake: number; payout: number; today?: number }
  | { type: 'claimRescue' }
  | { type: 'claimDailyBonus'; today: number }
  /** Ruleta diaria: [prize] es la casilla en la que ha caído. */
  | { type: 'spinDailyWheel'; today: number; prize: number }
  | { type: 'claimAchievement'; id: AchievementId }
  | { type: 'claimMission'; index: number; today: number };

export type EconomyError =
  | { type: 'walletUnavailable' }
  | { type: 'belowMinimumBet' }
  | { type: 'aboveMaximumStake' }
  | { type: 'insufficientFunds' }
  | { type: 'roundInProgress'; game: GameType }
  | { type: 'noOpenRound' }
  | { type: 'payoutTooHigh' }
  | { type: 'rescueNotNeeded' }
  | { type: 'rescueCoolingDown'; availableAtMillis: number }
  | { type: 'dailyBonusAlreadyClaimed' }
  | { type: 'dailyBonusClockMovedBack' }
  | { type: 'invalidDailyPrize' }
  | { type: 'dailySpinAlreadyUsed' }
  | { type: 'achievementLocked' }
  | { type: 'achievementAlreadyClaimed' }
  | { type: 'missionNotCompleted' }
  | { type: 'missionAlreadyClaimed' };

/** Avisos para la interfaz tras una operación. */
export type ProgressEvent =
  | { type: 'levelUp'; level: number }
  | { type: 'achievementUnlocked'; id: AchievementId }
  | { type: 'missionCompleted'; day: number; index: number };

export type RescueStatus =
  | { type: 'notNeeded' }
  | { type: 'available' }
  | { type: 'coolingDown'; availableAtMillis: number };

export function rescueStatus(wallet: Wallet, nowMillis: number): RescueStatus {
  if (wallet.balance >= EconomyRules.MINIMUM_BET || wallet.openRound) return { type: 'notNeeded' };
  const last = wallet.lastRescueAtMillis;
  if (last === undefined) return { type: 'available' };
  const availableAtMillis = last + EconomyRules.RESCUE_COOLDOWN_MS;
  return nowMillis >= availableAtMillis ? { type: 'available' } : { type: 'coolingDown', availableAtMillis };
}
