import type { AchievementId, EntryKind, GameType, LedgerEntry } from './economy';

/** Una línea del historial: una ronda jugada o un movimiento de fichas ajeno al juego. */
export type HistoryItem =
  | { type: 'round'; seq: number; at: number; balanceAfter: number; game: GameType; stake: number | null; payout: number }
  | { type: 'movement'; seq: number; at: number; balanceAfter: number; kind: EntryKind; amount: number; achievementId?: AchievementId };

/** Líneas del historial; las apuestas sueltas de una ronda por turnos se ven en su liquidación. */
export function historyItems(entries: LedgerEntry[]): HistoryItem[] {
  const items: HistoryItem[] = [];
  for (const e of entries) {
    if (e.kind === 'Bet') continue;
    if (e.kind === 'InstantRound' || e.kind === 'Settlement') {
      if (!e.game) continue;
      items.push({ type: 'round', seq: e.seq, at: e.createdAtMillis, balanceAfter: e.balanceAfter, game: e.game, stake: e.stake ?? null, payout: e.payout ?? Math.max(0, e.amount) });
    } else {
      const item: HistoryItem = { type: 'movement', seq: e.seq, at: e.createdAtMillis, balanceAfter: e.balanceAfter, kind: e.kind, amount: e.amount };
      if (e.achievementId) item.achievementId = e.achievementId;
      items.push(item);
    }
  }
  return items;
}

export interface GameStats {
  rounds: number;
  wins: number;
  losses: number;
  pushes: number;
  staked: number;
  returned: number;
  /** Mayor ganancia neta en una ronda. */
  biggestWin: number;
}

export const emptyStats = (): GameStats => ({ rounds: 0, wins: 0, losses: 0, pushes: 0, staked: 0, returned: 0, biggestWin: 0 });

/**
 * Resumen acumulado del libro: estadísticas por juego y lo apostado en rondas aún sin liquidar.
 * Se actualiza solo con los asientos nuevos ([lastSeq] es el último ya contado).
 */
export interface StatsSnapshot {
  lastSeq: number;
  games: Partial<Record<GameType, GameStats>>;
  /** Apuestas por ronda abierta (para liquidaciones antiguas sin la apuesta en el asiento). */
  openStakes: Record<string, number>;
}

export const emptySnapshot = (): StatsSnapshot => ({ lastSeq: 0, games: {}, openStakes: {} });

/** Suma al resumen los asientos posteriores a [snapshot.lastSeq], en orden. */
export function accumulate(snapshot: StatsSnapshot, entries: LedgerEntry[]): StatsSnapshot {
  const games = { ...snapshot.games };
  const open = { ...snapshot.openStakes };
  let last = snapshot.lastSeq;
  for (const e of [...entries].sort((a, b) => a.seq - b.seq)) {
    if (e.seq <= last) continue;
    last = e.seq;
    if (!e.game) continue;
    if (e.kind === 'Bet') {
      if (!e.roundId) continue;
      open[e.roundId] = (open[e.roundId] ?? 0) + (e.stake ?? -e.amount);
    } else if (e.kind === 'InstantRound' || e.kind === 'Settlement') {
      const stake = e.stake ?? (e.roundId ? open[e.roundId] : undefined);
      if (stake === undefined || e.payout === undefined) continue;
      if (e.roundId) delete open[e.roundId];
      const stats = { ...(games[e.game] ?? emptyStats()) };
      const net = e.payout - stake;
      stats.rounds++;
      if (net > 0) stats.wins++;
      else if (net < 0) stats.losses++;
      else stats.pushes++;
      stats.staked += stake;
      stats.returned += e.payout;
      stats.biggestWin = Math.max(stats.biggestWin, net);
      games[e.game] = stats;
    }
  }
  return { lastSeq: last, games, openStakes: open };
}
