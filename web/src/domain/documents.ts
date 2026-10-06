import type { AchievementId, DailyMissions, GameType, LedgerEntry, Wallet } from './economy';
import { ACHIEVEMENT_IDS, GAMES } from './economy';
import { sortAchievements } from './progression';

type Data = Record<string, unknown>;

const num = (value: unknown, fallback = 0): number => (typeof value === 'number' ? value : fallback);
const optNum = (value: unknown): number | undefined => (typeof value === 'number' ? value : undefined);

/** Ids desconocidos (de una versión más nueva de la app) se ignoran. */
function achievements(value: unknown): AchievementId[] {
  if (!Array.isArray(value)) return [];
  return sortAchievements(value.filter((id): id is AchievementId => (ACHIEVEMENT_IDS as readonly string[]).includes(id)));
}

function game(value: unknown): GameType | undefined {
  return (GAMES as readonly string[]).includes(value as string) ? (value as GameType) : undefined;
}

/**
 * Documento `wallets/{uid}` leído de Firestore. Los monederos anteriores a la progresión no tienen
 * sus campos: se leen con valores por defecto.
 */
export function walletFromData(data: Data): Wallet {
  const balance = num(data.balance);
  const wallet: Wallet = {
    uid: String(data.uid ?? ''),
    balance,
    seq: num(data.seq),
    lastEntryId: String(data.lastEntryId ?? ''),
    xp: num(data.xp),
    rounds: num(data.rounds),
    wins: num(data.wins),
    losses: num(data.losses),
    pushes: num(data.pushes),
    winStreak: num(data.winStreak),
    bestWinStreak: num(data.bestWinStreak),
    // Un monedero sin máximo guardado: como poco, es el saldo actual.
    highestBalance: Math.max(num(data.highestBalance), balance),
    dailyStreak: num(data.dailyStreak),
    unlocked: achievements(data.unlocked),
    claimed: achievements(data.claimed),
  };
  const round = data.openRound as Data | undefined;
  const roundGame = round ? game(round.game) : undefined;
  if (round && roundGame) wallet.openRound = { id: String(round.id), game: roundGame, stake: num(round.stake) };
  const rescue = optNum(data.lastRescueAtMillis);
  if (rescue !== undefined) wallet.lastRescueAtMillis = rescue;
  const lastDay = optNum(data.lastDailyDay);
  if (lastDay !== undefined) wallet.lastDailyDay = lastDay;
  const lastDaily = optNum(data.lastDailyAtMillis);
  if (lastDaily !== undefined) wallet.lastDailyAtMillis = lastDaily;
  const spinDay = optNum(data.lastSpinDay);
  if (spinDay !== undefined) wallet.lastSpinDay = spinDay;
  const spinAt = optNum(data.lastSpinAtMillis);
  if (spinAt !== undefined) wallet.lastSpinAtMillis = spinAt;
  const missions = missionsFromData(data.missions);
  if (missions) wallet.missions = missions;
  return wallet;
}

/** Misiones guardadas; si no tienen la forma esperada se ignoran (empiezan de cero con la siguiente ronda). */
function missionsFromData(value: unknown): DailyMissions | undefined {
  const data = value as Data | undefined;
  if (!data || typeof data.day !== 'number' || !Array.isArray(data.progress) || !Array.isArray(data.claimed)) return undefined;
  if (data.progress.length !== 3 || data.claimed.length !== 3) return undefined;
  return { day: data.day, progress: data.progress.map((p) => num(p)), claimed: data.claimed.map((c) => c === true) };
}

/** Monedero listo para escribir: sin campos `undefined` (Firestore los rechaza y las reglas no los esperan). */
export function walletToData(wallet: Wallet): Data {
  return stripUndefined({
    ...wallet,
    openRound: wallet.openRound ? { ...wallet.openRound } : undefined,
    missions: wallet.missions ? { day: wallet.missions.day, progress: [...wallet.missions.progress], claimed: [...wallet.missions.claimed] } : undefined,
  });
}

export function entryToData(entry: LedgerEntry): Data {
  return stripUndefined({ ...entry });
}

export function entryFromData(data: Data): LedgerEntry {
  const entry: LedgerEntry = {
    id: String(data.id),
    seq: num(data.seq),
    kind: data.kind as LedgerEntry['kind'],
    amount: num(data.amount),
    balanceAfter: num(data.balanceAfter),
    createdAtMillis: num(data.createdAtMillis),
  };
  const g = game(data.game);
  if (g) entry.game = g;
  if (typeof data.roundId === 'string') entry.roundId = data.roundId;
  const stake = optNum(data.stake);
  if (stake !== undefined) entry.stake = stake;
  const payout = optNum(data.payout);
  if (payout !== undefined) entry.payout = payout;
  if ((ACHIEVEMENT_IDS as readonly string[]).includes(data.achievementId as string)) entry.achievementId = data.achievementId as AchievementId;
  const mission = optNum(data.mission);
  if (mission !== undefined) entry.mission = mission;
  return entry;
}

function stripUndefined(data: Data): Data {
  const result: Data = {};
  for (const [key, value] of Object.entries(data)) if (value !== undefined) result[key] = value;
  return result;
}
