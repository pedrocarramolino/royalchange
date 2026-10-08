import type { AchievementId, Wallet } from './economy';
import { ACHIEVEMENT_IDS } from './economy';

// ── Niveles ─────────────────────────────────────────────────────────────────────────────────
// Para alcanzar el nivel n hacen falta 50 · (n − 1)² puntos. El nivel no se guarda: se deriva.

export const MAX_LEVEL = 99;

export function xpForLevel(level: number): number {
  const steps = level - 1;
  return 50 * steps * steps;
}

export function levelFor(xp: number): number {
  let level = 1;
  while (level < MAX_LEVEL && xpForLevel(level + 1) <= xp) level++;
  return level;
}

export interface LevelProgress {
  level: number;
  xpIntoLevel: number;
  /** Experiencia que separa este nivel del siguiente; `null` en el máximo. */
  xpForNextLevel: number | null;
  /** Fracción completada del nivel actual, entre 0 y 1. */
  fraction: number;
  title: PlayerTitle;
}

export function levelProgress(xp: number): LevelProgress {
  const level = levelFor(xp);
  const start = xpForLevel(level);
  const next = level < MAX_LEVEL ? xpForLevel(level + 1) - start : null;
  const xpIntoLevel = xp - start;
  return {
    level,
    xpIntoLevel,
    xpForNextLevel: next,
    fraction: next === null ? 1 : Math.min(1, Math.max(0, xpIntoLevel / next)),
    title: titleFor(level),
  };
}

export type PlayerTitle = 'Novice' | 'Player' | 'Vip' | 'HighRoller' | 'Legend';

const TITLES: [PlayerTitle, number][] = [
  ['Novice', 1],
  ['Player', 5],
  ['Vip', 10],
  ['HighRoller', 25],
  ['Legend', 50],
];

export function titleFor(level: number): PlayerTitle {
  let title: PlayerTitle = 'Novice';
  for (const [name, minimum] of TITLES) if (level >= minimum) title = name;
  return title;
}

export const TITLE_NAMES: Record<PlayerTitle, string> = {
  Novice: 'Novato',
  Player: 'Jugador',
  Vip: 'VIP',
  HighRoller: 'High Roller',
  Legend: 'Leyenda',
};

// ── Experiencia ─────────────────────────────────────────────────────────────────────────────
// 10 fijos y un extra por tramos de apuesta (casi logarítmico). El resultado no influye.

const STAKE_TIERS: [number, number][] = [
  [25_000, 12],
  [5_000, 10],
  [1_000, 8],
  [500, 6],
  [100, 4],
  [50, 2],
];

export function xpForRound(stake: number): number {
  const tier = STAKE_TIERS.find(([minimum]) => stake >= minimum);
  return 10 + (tier ? tier[1] : 0);
}

export type RoundOutcome = 'win' | 'loss' | 'push';

/** [payout] incluye la apuesta devuelta: ganar es cobrar más de lo apostado. */
export function roundOutcome(stake: number, payout: number): RoundOutcome {
  if (payout > stake) return 'win';
  if (payout < stake) return 'loss';
  return 'push';
}

/** Progreso tras cerrar una ronda: experiencia, contadores y rachas. Un empate no toca la racha. */
export function afterRound(wallet: Wallet, stake: number, payout: number): Wallet {
  const outcome = roundOutcome(stake, payout);
  const streak = outcome === 'win' ? wallet.winStreak + 1 : outcome === 'loss' ? 0 : wallet.winStreak;
  return {
    ...wallet,
    xp: wallet.xp + xpForRound(stake),
    rounds: wallet.rounds + 1,
    wins: wallet.wins + (outcome === 'win' ? 1 : 0),
    losses: wallet.losses + (outcome === 'loss' ? 1 : 0),
    pushes: wallet.pushes + (outcome === 'push' ? 1 : 0),
    winStreak: streak,
    bestWinStreak: Math.max(wallet.bestWinStreak, streak),
  };
}

// ── Logros ──────────────────────────────────────────────────────────────────────────────────

export interface Achievement {
  id: AchievementId;
  name: string;
  description: string;
  reward: number;
  /** Valor actual y objetivo, para mostrar el avance. */
  progress: (wallet: Wallet) => [current: number, target: number];
}

const rounds = (n: number) => (w: Wallet): [number, number] => [w.rounds, n];
const wins = (n: number) => (w: Wallet): [number, number] => [w.wins, n];
const streak = (n: number) => (w: Wallet): [number, number] => [w.bestWinStreak, n];
const level = (n: number) => (w: Wallet): [number, number] => [levelFor(w.xp), n];
const balance = (n: number) => (w: Wallet): [number, number] => [w.highestBalance, n];
const daily = (n: number) => (w: Wallet): [number, number] => [w.dailyStreak, n];

export const ACHIEVEMENTS: Achievement[] = [
  { id: 'FirstWin', name: 'Primera victoria', description: 'Gana tu primera ronda.', reward: 250, progress: wins(1) },
  { id: 'Rounds10', name: 'Calentando motores', description: 'Juega 10 rondas.', reward: 250, progress: rounds(10) },
  { id: 'Rounds100', name: 'Habitual de la casa', description: 'Juega 100 rondas.', reward: 1_000, progress: rounds(100) },
  { id: 'Rounds1000', name: 'Veterano del tapete', description: 'Juega 1.000 rondas.', reward: 5_000, progress: rounds(1_000) },
  { id: 'Wins50', name: 'Mano ganadora', description: 'Gana 50 rondas.', reward: 1_000, progress: wins(50) },
  { id: 'WinStreak5', name: 'En racha', description: 'Gana 5 rondas seguidas.', reward: 1_000, progress: streak(5) },
  { id: 'WinStreak10', name: 'Imparable', description: 'Gana 10 rondas seguidas.', reward: 5_000, progress: streak(10) },
  { id: 'Level5', name: 'Ya eres Jugador', description: 'Alcanza el nivel 5.', reward: 500, progress: level(5) },
  { id: 'Level10', name: 'Trato VIP', description: 'Alcanza el nivel 10.', reward: 2_000, progress: level(10) },
  { id: 'Level25', name: 'Gran apostador', description: 'Alcanza el nivel 25 y conviértete en High Roller.', reward: 10_000, progress: level(25) },
  { id: 'Balance50k', name: 'Cincuenta mil', description: 'Reúne 50.000 fichas.', reward: 2_500, progress: balance(50_000) },
  { id: 'Balance250k', name: 'Cuarto de millón', description: 'Reúne 250.000 fichas.', reward: 10_000, progress: balance(250_000) },
  { id: 'DailyStreak7', name: 'Fiel a la cita', description: 'Cobra el bono diario 7 días seguidos.', reward: 1_000, progress: daily(7) },
  { id: 'DailyStreak30', name: 'Un mes en la mesa', description: 'Cobra el bono diario 30 días seguidos.', reward: 5_000, progress: daily(30) },
];

const BY_ID = new Map(ACHIEVEMENTS.map((a) => [a.id, a]));

export function achievement(id: AchievementId): Achievement {
  return BY_ID.get(id)!;
}

export function isMet(a: Achievement, wallet: Wallet): boolean {
  const [current, target] = a.progress(wallet);
  return current >= target;
}

/** Logros cuya condición se cumple y aún no estaban desbloqueados, en orden de catálogo. */
export function newlyUnlocked(wallet: Wallet): AchievementId[] {
  return ACHIEVEMENTS.filter((a) => !wallet.unlocked.includes(a.id) && isMet(a, wallet)).map((a) => a.id);
}

/** Orden de catálogo: el documento no cambia si los logros no cambian (las reglas lo comprueban). */
export function sortAchievements(ids: Iterable<AchievementId>): AchievementId[] {
  const set = new Set(ids);
  return ACHIEVEMENT_IDS.filter((id) => set.has(id));
}

export function claimable(wallet: Wallet): AchievementId[] {
  return wallet.unlocked.filter((id) => !wallet.claimed.includes(id));
}

// ── Bono diario ─────────────────────────────────────────────────────────────────────────────
// Un cobro por día natural del dispositivo. Racha +1 si el último fue ayer; si no, vuelve a 1.
// Premio 500 + 200 · (día − 1) hasta el día 7 (1.700); el día 8 vuelve a 500 y sube igual (ciclos
// de siete días mientras dure la racha).

export const MAX_REWARD_DAY = 7;

export function dailyReward(streakDay: number): number {
  return 500 + 200 * ((streakDay - 1) % MAX_REWARD_DAY);
}

export type DailyBonusStatus =
  | { type: 'available'; streakDay: number; reward: number }
  | { type: 'claimedToday'; streakDay: number; nextStreakDay: number; nextReward: number }
  | { type: 'clockMovedBack' };

export function dailyBonusStatus(wallet: Wallet, today: number, nowMillis: number): DailyBonusStatus {
  const lastDay = wallet.lastDailyDay;
  if (wallet.lastDailyAtMillis !== undefined && nowMillis < wallet.lastDailyAtMillis) return { type: 'clockMovedBack' };
  if (lastDay !== undefined && today < lastDay) return { type: 'clockMovedBack' };
  if (lastDay === today) {
    return {
      type: 'claimedToday',
      streakDay: wallet.dailyStreak,
      nextStreakDay: wallet.dailyStreak + 1,
      nextReward: dailyReward(wallet.dailyStreak + 1),
    };
  }
  const streakDay = lastDay === today - 1 ? wallet.dailyStreak + 1 : 1;
  return { type: 'available', streakDay, reward: dailyReward(streakDay) };
}

// ── Ruleta diaria ───────────────────────────────────────────────────────────────────────────
// Aparte del bono: un giro por día natural del dispositivo a una ruleta de 12 casillas iguales,
// todas con la misma probabilidad. No toca la racha del bono.

/**
 * Casillas de la ruleta diaria, en orden alrededor de la rueda. Las reglas de Firestore repiten los
 * importes (dailyWheelPrizes): si cambian, hay que cambiar los dos.
 */
export const DAILY_WHEEL: readonly number[] = [500, 250, 1_000, 500, 2_500, 250, 1_500, 500, 5_000, 250, 1_000, 10_000];

export const isDailyPrize = (prize: number) => DAILY_WHEEL.includes(prize);

export type DailySpinStatus = { type: 'available' } | { type: 'usedToday' } | { type: 'clockMovedBack' };

export function dailySpinStatus(wallet: Wallet, today: number, nowMillis: number): DailySpinStatus {
  if (wallet.lastSpinAtMillis !== undefined && nowMillis < wallet.lastSpinAtMillis) return { type: 'clockMovedBack' };
  if (wallet.lastSpinDay !== undefined && today < wallet.lastSpinDay) return { type: 'clockMovedBack' };
  return wallet.lastSpinDay === today ? { type: 'usedToday' } : { type: 'available' };
}
