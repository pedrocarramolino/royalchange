import { describe, expect, it } from 'vitest';
import type { Wallet } from './economy';
import { DAILY_WHEEL, levelFor, xpForRound } from './progression';
import { applyOperation, openWallet } from './transitions';
import { walletFromData, walletToData } from './documents';

const NOW = 1_790_000_000_000;
let counter = 0;
const id = () => `entry${++counter}`;

function wallet(changes: Partial<Wallet> = {}): Wallet {
  return { ...openWallet('uid', 'welcome', NOW).wallet, ...changes };
}

function ok(result: ReturnType<typeof applyOperation>) {
  if (!result.ok) throw new Error(`Se esperaba éxito: ${JSON.stringify(result.error)}`);
  return result.value;
}

describe('monedero nuevo', () => {
  it('tiene las fichas de bienvenida y el asiento 1', () => {
    const { wallet: w, entry } = openWallet('uid', 'w1', NOW);
    expect(w.balance).toBe(10_000);
    expect(w.seq).toBe(1);
    expect(w.highestBalance).toBe(10_000);
    expect(entry).toEqual({ id: 'w1', seq: 1, kind: 'Welcome', amount: 10_000, balanceAfter: 10_000, createdAtMillis: NOW });
  });
});

describe('rondas por turnos', () => {
  it('apostar abre la ronda y doblar suma a la misma', () => {
    const first = ok(applyOperation(wallet(), { type: 'placeBet', game: 'Blackjack', stake: 100 }, 'b1', NOW));
    expect(first.wallet.balance).toBe(9_900);
    expect(first.wallet.openRound).toEqual({ id: 'b1', game: 'Blackjack', stake: 100 });
    expect(first.entry).toMatchObject({ kind: 'Bet', amount: -100, stake: 100, roundId: 'b1', seq: 2 });

    const double = ok(applyOperation(first.wallet, { type: 'placeBet', game: 'Blackjack', stake: 100 }, 'b2', NOW));
    expect(double.wallet.openRound).toEqual({ id: 'b1', game: 'Blackjack', stake: 200 });
    expect(double.entry.roundId).toBe('b1');
  });

  it('no deja apostar en otro juego con una ronda abierta', () => {
    const open = ok(applyOperation(wallet(), { type: 'placeBet', game: 'Blackjack', stake: 100 }, id(), NOW));
    const result = applyOperation(open.wallet, { type: 'placeBet', game: 'Poker', stake: 100 }, id(), NOW);
    expect(result).toEqual({ ok: false, error: { type: 'roundInProgress', game: 'Blackjack' } });
  });

  it('liquidar cierra la ronda, cuenta la experiencia y guarda la apuesta total', () => {
    const open = ok(applyOperation(wallet(), { type: 'placeBet', game: 'Blackjack', stake: 200 }, 'r1', NOW));
    const settled = ok(applyOperation(open.wallet, { type: 'settleRound', payout: 500 }, 's1', NOW));
    expect(settled.wallet.openRound).toBeUndefined();
    expect('openRound' in walletToData(settled.wallet)).toBe(false);
    expect(settled.wallet.balance).toBe(10_300);
    expect(settled.wallet.xp).toBe(xpForRound(200));
    expect(settled.wallet).toMatchObject({ rounds: 1, wins: 1, winStreak: 1, bestWinStreak: 1 });
    expect(settled.entry).toMatchObject({ kind: 'Settlement', stake: 200, payout: 500, roundId: 'r1', game: 'Blackjack', amount: 500 });
    expect(settled.wallet.unlocked).toEqual(['FirstWin']);
    expect(settled.events).toContainEqual({ type: 'achievementUnlocked', id: 'FirstWin' });
  });

  it('un empate no rompe ni alarga la racha', () => {
    const w = wallet({ winStreak: 3, bestWinStreak: 3 });
    const open = ok(applyOperation(w, { type: 'placeBet', game: 'Blackjack', stake: 100 }, id(), NOW));
    const push = ok(applyOperation(open.wallet, { type: 'settleRound', payout: 100 }, id(), NOW));
    expect(push.wallet).toMatchObject({ winStreak: 3, pushes: 1 });
  });

  it('rechaza pagos por encima del techo global', () => {
    const open = ok(applyOperation(wallet(), { type: 'placeBet', game: 'Blackjack', stake: 10 }, id(), NOW));
    expect(applyOperation(open.wallet, { type: 'settleRound', payout: 10_001 }, id(), NOW)).toEqual({ ok: false, error: { type: 'payoutTooHigh' } });
  });
});

describe('rondas instantáneas', () => {
  it('apuesta y pago en un solo asiento', () => {
    const t = ok(applyOperation(wallet(), { type: 'instantRound', game: 'Roulette', stake: 100, payout: 3_600 }, 'i1', NOW));
    expect(t.wallet.balance).toBe(13_500);
    expect(t.entry).toMatchObject({ kind: 'InstantRound', roundId: 'i1', stake: 100, payout: 3_600, amount: 3_500 });
  });

  it('no permite apostar más del saldo ni por debajo del mínimo', () => {
    expect(applyOperation(wallet({ balance: 50 }), { type: 'instantRound', game: 'Dice', stake: 100, payout: 0 }, id(), NOW).ok).toBe(false);
    expect(applyOperation(wallet(), { type: 'instantRound', game: 'Dice', stake: 5, payout: 0 }, id(), NOW).ok).toBe(false);
  });
});

describe('recarga', () => {
  it('solo sin fichas y una vez cada 4 horas', () => {
    expect(applyOperation(wallet(), { type: 'claimRescue' }, id(), NOW)).toEqual({ ok: false, error: { type: 'rescueNotNeeded' } });
    const rescued = ok(applyOperation(wallet({ balance: 0 }), { type: 'claimRescue' }, id(), NOW));
    expect(rescued.wallet).toMatchObject({ balance: 1_000, lastRescueAtMillis: NOW });
    const again = applyOperation({ ...rescued.wallet, balance: 0 }, { type: 'claimRescue' }, id(), NOW + 1000);
    expect(again.ok).toBe(false);
  });
});

describe('tirada diaria', () => {
  it('paga la casilla en la que cae y sube la racha si se gira al día siguiente', () => {
    const day1 = ok(applyOperation(wallet(), { type: 'claimDailyBonus', today: 20_000, prize: 2_500 }, id(), NOW));
    expect(day1.wallet).toMatchObject({ dailyStreak: 1, lastDailyDay: 20_000, balance: 12_500 });
    const day2 = ok(applyOperation(day1.wallet, { type: 'claimDailyBonus', today: 20_001, prize: 250 }, id(), NOW + 86_400_000));
    expect(day2.wallet.dailyStreak).toBe(2);
    expect(day2.entry.amount).toBe(250);
  });

  it('la ruleta tiene 12 casillas con varios importes y solo acepta esos premios', () => {
    expect(DAILY_WHEEL).toHaveLength(12);
    expect(new Set(DAILY_WHEEL)).toEqual(new Set([250, 500, 1_000, 1_500, 2_500, 5_000, 10_000]));
    expect(applyOperation(wallet(), { type: 'claimDailyBonus', today: 20_000, prize: 20_000 }, id(), NOW)).toEqual({
      ok: false,
      error: { type: 'invalidDailyPrize' },
    });
  });

  it('no deja girar dos veces el mismo día ni con el reloj hacia atrás', () => {
    const day1 = ok(applyOperation(wallet(), { type: 'claimDailyBonus', today: 20_000, prize: 500 }, id(), NOW));
    expect(applyOperation(day1.wallet, { type: 'claimDailyBonus', today: 20_000, prize: 500 }, id(), NOW).ok).toBe(false);
    expect(applyOperation(day1.wallet, { type: 'claimDailyBonus', today: 20_001, prize: 500 }, id(), NOW - 1)).toEqual({
      ok: false,
      error: { type: 'dailyBonusClockMovedBack' },
    });
  });

  it('si se salta un día la racha vuelve a empezar', () => {
    const day1 = ok(applyOperation(wallet(), { type: 'claimDailyBonus', today: 20_000, prize: 500 }, id(), NOW));
    const day3 = ok(applyOperation(day1.wallet, { type: 'claimDailyBonus', today: 20_002, prize: 500 }, id(), NOW + 2 * 86_400_000));
    expect(day3.wallet.dailyStreak).toBe(1);
  });
});

describe('logros y niveles', () => {
  it('cobrar un logro desbloqueado paga su recompensa una sola vez', () => {
    const w = wallet({ unlocked: ['FirstWin'] });
    const claimed = ok(applyOperation(w, { type: 'claimAchievement', id: 'FirstWin' }, id(), NOW));
    expect(claimed.wallet.balance).toBe(10_250);
    expect(claimed.wallet.claimed).toEqual(['FirstWin']);
    expect(applyOperation(claimed.wallet, { type: 'claimAchievement', id: 'FirstWin' }, id(), NOW).ok).toBe(false);
    expect(applyOperation(w, { type: 'claimAchievement', id: 'Rounds10' }, id(), NOW)).toEqual({ ok: false, error: { type: 'achievementLocked' } });
  });

  it('los umbrales de nivel coinciden con las reglas', () => {
    expect(levelFor(799)).toBe(4);
    expect(levelFor(800)).toBe(5);
    expect(levelFor(4_050)).toBe(10);
    expect(levelFor(28_800)).toBe(25);
  });

  it('avisa de la subida de nivel', () => {
    const w = wallet({ xp: 45 });
    const t = ok(applyOperation(w, { type: 'instantRound', game: 'Slots', stake: 100, payout: 0 }, id(), NOW));
    expect(t.events).toContainEqual({ type: 'levelUp', level: 2 });
  });
});

describe('lectura de documentos', () => {
  it('un monedero antiguo sin progresión se lee con valores por defecto', () => {
    const w = walletFromData({ uid: 'u', balance: 4_000, seq: 7, lastEntryId: 'x' });
    expect(w).toMatchObject({ xp: 0, rounds: 0, highestBalance: 4_000, unlocked: [], claimed: [] });
    expect(w.openRound).toBeUndefined();
  });

  it('ignora logros desconocidos y respeta el orden de catálogo', () => {
    const w = walletFromData({ uid: 'u', balance: 1, seq: 1, lastEntryId: 'x', unlocked: ['Rounds10', 'Nuevo', 'FirstWin'] });
    expect(w.unlocked).toEqual(['FirstWin', 'Rounds10']);
  });
});
