import type { EconomyError, EconomyOperation, EntryKind, GameType, LedgerEntry, ProgressEvent, Wallet, AchievementId } from './economy';
import { EconomyRules, rescueStatus } from './economy';
import { achievement, afterRound, dailyBonusStatus, dailySpinStatus, isDailyPrize, levelFor, newlyUnlocked, sortAchievements } from './progression';

/** Resultado de una operación: monedero nuevo, asiento que lo justifica y avisos para la interfaz. */
export interface WalletTransition {
  wallet: Wallet;
  entry: LedgerEntry;
  events: ProgressEvent[];
}

export type TransitionResult = { ok: true; value: WalletTransition } | { ok: false; error: EconomyError };

/** Monedero nuevo con las fichas de bienvenida. */
export function openWallet(uid: string, entryId: string, nowMillis: number): WalletTransition {
  const grant = EconomyRules.WELCOME_GRANT;
  return {
    wallet: {
      uid,
      balance: grant,
      seq: 1,
      lastEntryId: entryId,
      xp: 0,
      rounds: 0,
      wins: 0,
      losses: 0,
      pushes: 0,
      winStreak: 0,
      bestWinStreak: 0,
      highestBalance: grant,
      dailyStreak: 0,
      unlocked: [],
      claimed: [],
    },
    entry: { id: entryId, seq: 1, kind: 'Welcome', amount: grant, balanceAfter: grant, createdAtMillis: nowMillis },
    events: [],
  };
}

/**
 * Aplica [operation] al monedero o explica por qué no es posible. Lógica pura: las reglas de
 * Firestore comprueban lo mismo en el servidor. Nunca deja el saldo en negativo.
 *
 * @param entryId id del asiento nuevo; también identifica la ronda que abra.
 */
export function applyOperation(wallet: Wallet, operation: EconomyOperation, entryId: string, nowMillis: number): TransitionResult {
  switch (operation.type) {
    case 'placeBet':
      return placeBet(wallet, operation.game, operation.stake, entryId, nowMillis);
    case 'settleRound':
      return settleRound(wallet, operation.payout, entryId, nowMillis);
    case 'instantRound':
      return instantRound(wallet, operation.game, operation.stake, operation.payout, entryId, nowMillis);
    case 'claimRescue':
      return claimRescue(wallet, entryId, nowMillis);
    case 'claimDailyBonus':
      return claimDailyBonus(wallet, operation.today, entryId, nowMillis);
    case 'spinDailyWheel':
      return spinDailyWheel(wallet, operation.today, operation.prize, entryId, nowMillis);
    case 'claimAchievement':
      return claimAchievement(wallet, operation.id, entryId, nowMillis);
  }
}

const fail = (error: EconomyError): TransitionResult => ({ ok: false, error });

function placeBet(wallet: Wallet, game: GameType, stake: number, entryId: string, now: number): TransitionResult {
  const round = wallet.openRound;
  const roundStake = (round?.stake ?? 0) + stake;
  if (stake < EconomyRules.MINIMUM_BET) return fail({ type: 'belowMinimumBet' });
  if (round && round.game !== game) return fail({ type: 'roundInProgress', game: round.game });
  if (roundStake > EconomyRules.MAXIMUM_ROUND_STAKE) return fail({ type: 'aboveMaximumStake' });
  if (stake > wallet.balance) return fail({ type: 'insufficientFunds' });
  const roundId = round?.id ?? entryId;
  return record(
    wallet,
    { ...wallet, balance: wallet.balance - stake, openRound: { id: roundId, game, stake: roundStake } },
    { id: entryId, kind: 'Bet', createdAtMillis: now, game, roundId, stake },
  );
}

function settleRound(wallet: Wallet, payout: number, entryId: string, now: number): TransitionResult {
  const round = wallet.openRound;
  if (!round) return fail({ type: 'noOpenRound' });
  if (payout > round.stake * EconomyRules.MAXIMUM_PAYOUT_MULTIPLIER) return fail({ type: 'payoutTooHigh' });
  const { openRound: _closed, ...rest } = wallet;
  return record(
    wallet,
    // La ronda cuenta al cerrarse, con todas sus apuestas (dobles y separaciones incluidas).
    afterRound({ ...rest, balance: wallet.balance + payout }, round.stake, payout),
    // La liquidación guarda lo apostado en toda la ronda: el historial la muestra completa.
    { id: entryId, kind: 'Settlement', createdAtMillis: now, game: round.game, roundId: round.id, stake: round.stake, payout },
  );
}

function instantRound(wallet: Wallet, game: GameType, stake: number, payout: number, entryId: string, now: number): TransitionResult {
  if (stake < EconomyRules.MINIMUM_BET) return fail({ type: 'belowMinimumBet' });
  if (stake > EconomyRules.MAXIMUM_ROUND_STAKE) return fail({ type: 'aboveMaximumStake' });
  if (stake > wallet.balance) return fail({ type: 'insufficientFunds' });
  if (payout > stake * EconomyRules.MAXIMUM_PAYOUT_MULTIPLIER) return fail({ type: 'payoutTooHigh' });
  return record(
    wallet,
    // Una ronda abierta de otro juego no se toca: sus fichas siguen en la mesa.
    afterRound({ ...wallet, balance: wallet.balance - stake + payout }, stake, payout),
    { id: entryId, kind: 'InstantRound', createdAtMillis: now, game, roundId: entryId, stake, payout },
  );
}

function claimRescue(wallet: Wallet, entryId: string, now: number): TransitionResult {
  const status = rescueStatus(wallet, now);
  if (status.type === 'notNeeded') return fail({ type: 'rescueNotNeeded' });
  if (status.type === 'coolingDown') return fail({ type: 'rescueCoolingDown', availableAtMillis: status.availableAtMillis });
  return record(
    wallet,
    { ...wallet, balance: wallet.balance + EconomyRules.RESCUE_GRANT, lastRescueAtMillis: now },
    { id: entryId, kind: 'Rescue', createdAtMillis: now },
  );
}

function claimDailyBonus(wallet: Wallet, today: number, entryId: string, now: number): TransitionResult {
  const status = dailyBonusStatus(wallet, today, now);
  if (status.type === 'claimedToday') return fail({ type: 'dailyBonusAlreadyClaimed' });
  if (status.type === 'clockMovedBack') return fail({ type: 'dailyBonusClockMovedBack' });
  return record(
    wallet,
    { ...wallet, balance: wallet.balance + status.reward, dailyStreak: status.streakDay, lastDailyDay: today, lastDailyAtMillis: now },
    { id: entryId, kind: 'DailyBonus', createdAtMillis: now },
  );
}

function spinDailyWheel(wallet: Wallet, today: number, prize: number, entryId: string, now: number): TransitionResult {
  if (!isDailyPrize(prize)) return fail({ type: 'invalidDailyPrize' });
  const status = dailySpinStatus(wallet, today, now);
  if (status.type === 'usedToday') return fail({ type: 'dailySpinAlreadyUsed' });
  if (status.type === 'clockMovedBack') return fail({ type: 'dailyBonusClockMovedBack' });
  return record(
    wallet,
    { ...wallet, balance: wallet.balance + prize, lastSpinDay: today, lastSpinAtMillis: now },
    { id: entryId, kind: 'DailySpin', createdAtMillis: now },
  );
}

function claimAchievement(wallet: Wallet, id: AchievementId, entryId: string, now: number): TransitionResult {
  if (wallet.claimed.includes(id)) return fail({ type: 'achievementAlreadyClaimed' });
  if (!wallet.unlocked.includes(id)) return fail({ type: 'achievementLocked' });
  return record(
    wallet,
    { ...wallet, balance: wallet.balance + achievement(id).reward, claimed: sortAchievements([...wallet.claimed, id]) },
    { id: entryId, kind: 'AchievementReward', createdAtMillis: now, achievementId: id },
  );
}

/** Datos del asiento que no dependen del saldo resultante. */
interface Draft {
  id: string;
  kind: EntryKind;
  createdAtMillis: number;
  game?: GameType;
  roundId?: string;
  stake?: number;
  payout?: number;
  achievementId?: AchievementId;
}

/**
 * Cierra cualquier operación: numera el movimiento, actualiza el saldo máximo, desbloquea los
 * logros que se cumplan ahora y crea el asiento con la variación de saldo exacta.
 */
function record(before: Wallet, updated: Wallet, draft: Draft): TransitionResult {
  const withHighest: Wallet = { ...updated, highestBalance: Math.max(updated.highestBalance, updated.balance) };
  const unlockedNow = newlyUnlocked(withHighest);
  const next: Wallet = {
    ...withHighest,
    seq: before.seq + 1,
    lastEntryId: draft.id,
    unlocked: sortAchievements([...withHighest.unlocked, ...unlockedNow]),
  };
  const events: ProgressEvent[] = [];
  const level = levelFor(next.xp);
  if (level > levelFor(before.xp)) events.push({ type: 'levelUp', level });
  unlockedNow.forEach((id) => events.push({ type: 'achievementUnlocked', id }));

  const entry: LedgerEntry = {
    id: draft.id,
    seq: next.seq,
    kind: draft.kind,
    amount: next.balance - before.balance,
    balanceAfter: next.balance,
    createdAtMillis: draft.createdAtMillis,
  };
  if (draft.game !== undefined) entry.game = draft.game;
  if (draft.roundId !== undefined) entry.roundId = draft.roundId;
  if (draft.stake !== undefined) entry.stake = draft.stake;
  if (draft.payout !== undefined) entry.payout = draft.payout;
  if (draft.achievementId !== undefined) entry.achievementId = draft.achievementId;
  return { ok: true, value: { wallet: next, entry, events } };
}
