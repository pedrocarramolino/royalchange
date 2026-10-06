import { describe, expect, it } from 'vitest';
import { EASY_MISSIONS, HARD_MISSIONS, MEDIUM_MISSIONS, missionGain, missionsAfterRound, missionsFor, missionsToday, missionText, newlyCompletedMissions } from './missions';
import { applyOperation, openWallet } from './transitions';
import type { Wallet } from './economy';

const DAY = 20_000;

/** Monedero recién creado (con un id de asiento fijo). */
const fresh = (): Wallet => openWallet('ana', 'bienvenida', 0).wallet;

/** Juega rondas instantáneas de [game] hasta completar lo que pide la misión fácil del día. */
function play(wallet: Wallet, game: Parameters<typeof missionGain>[1], times: number, today = DAY, stake = 100, payout = 0): Wallet {
  let w = wallet;
  for (let k = 0; k < times; k++) {
    const r = applyOperation(w, { type: 'instantRound', game, stake, payout, today }, `r${k}-${Math.random()}`, 0);
    if (!r.ok) throw new Error(r.error.type);
    w = r.value.wallet;
  }
  return w;
}

describe('misiones diarias', () => {
  it('cada día tiene tres misiones, una de cada lista, y se repiten por ciclos', () => {
    const set = missionsFor(DAY);
    expect(set).toEqual([EASY_MISSIONS[DAY % 7], MEDIUM_MISSIONS[DAY % 6], HARD_MISSIONS[DAY % 5]]);
    expect(missionsFor(DAY + 210)).toEqual(set);
    for (const m of [...EASY_MISSIONS, ...MEDIUM_MISSIONS, ...HARD_MISSIONS]) {
      expect(m.target).toBeGreaterThan(0);
      expect(m.reward % 10).toBe(0);
    }
  });

  it('cada misión cuenta lo suyo: rondas, victorias o fichas, y solo en su juego', () => {
    const rounds = { kind: 'rounds' as const, game: 'Roulette' as const, target: 3, reward: 250 };
    expect(missionGain(rounds, 'Roulette', 100, 0)).toBe(1);
    expect(missionGain(rounds, 'Slots', 100, 0)).toBe(0);
    const wins = { kind: 'wins' as const, game: '' as const, target: 3, reward: 500 };
    expect(missionGain(wins, 'Slots', 100, 200)).toBe(1);
    expect(missionGain(wins, 'Slots', 100, 100)).toBe(0);
    const stake = { kind: 'stake' as const, game: '' as const, target: 5000, reward: 1000 };
    expect(missionGain(stake, 'Dice', 250, 0)).toBe(250);
  });

  it('un día nuevo empieza de cero y el día nunca retrocede', () => {
    const first = missionsAfterRound(undefined, DAY, 'Roulette', 100, 0);
    expect(first.day).toBe(DAY);
    expect(first.claimed).toEqual([false, false, false]);
    const next = missionsAfterRound({ ...first, claimed: [true, false, false] }, DAY + 1, 'Roulette', 100, 0);
    expect(next.day).toBe(DAY + 1);
    expect(next.claimed).toEqual([false, false, false]);
    const back = missionsAfterRound(next, DAY, 'Roulette', 100, 0);
    expect(back.day).toBe(DAY + 1);
  });

  it('las rondas avanzan las misiones, avisan al completarlas y se cobran una sola vez', () => {
    // La misión fácil de un día con «Juega 5 rondas» (cualquier juego).
    const day = [...Array(7).keys()].map((k) => DAY + k).find((d) => missionsFor(d)[0]!.game === '' && missionsFor(d)[0]!.kind === 'rounds')!;
    const target = missionsFor(day)[0]!.target;
    let wallet = play(fresh(), 'Dice', target - 1, day);
    expect(missionsToday(wallet, day).progress[0]).toBe(target - 1);
    expect(applyOperation(wallet, { type: 'claimMission', index: 0, today: day }, 'x', 0)).toEqual({ ok: false, error: { type: 'missionNotCompleted' } });

    const last = applyOperation(wallet, { type: 'instantRound', game: 'Dice', stake: 100, payout: 0, today: day }, 'ultima', 0);
    if (!last.ok) throw new Error(last.error.type);
    expect(last.value.events).toContainEqual({ type: 'missionCompleted', day, index: 0 });
    wallet = last.value.wallet;

    const claim = applyOperation(wallet, { type: 'claimMission', index: 0, today: day }, 'cobro', 0);
    if (!claim.ok) throw new Error(claim.error.type);
    expect(claim.value.wallet.balance).toBe(wallet.balance + missionsFor(day)[0]!.reward);
    expect(claim.value.wallet.missions!.claimed).toEqual([true, false, false]);
    expect(claim.value.wallet.missions!.progress).toEqual(wallet.missions!.progress);
    expect(claim.value.entry).toMatchObject({ kind: 'MissionReward', mission: 0, amount: missionsFor(day)[0]!.reward });
    expect(applyOperation(claim.value.wallet, { type: 'claimMission', index: 0, today: day }, 'otra', 0)).toEqual({ ok: false, error: { type: 'missionAlreadyClaimed' } });

    // Al día siguiente, misiones nuevas sin empezar: lo de ayer ya no se cobra.
    expect(missionsToday(claim.value.wallet, day + 1).progress).toEqual([0, 0, 0]);
    expect(applyOperation(wallet, { type: 'claimMission', index: 0, today: day + 1 }, 'tarde', 0).ok).toBe(false);
  });

  it('una ronda sin día (versión anterior) no toca las misiones; apostar tampoco', () => {
    const wallet = play(fresh(), 'Roulette', 1);
    const noDay = applyOperation(wallet, { type: 'instantRound', game: 'Roulette', stake: 100, payout: 0 }, 'sin-dia', 0);
    if (!noDay.ok) throw new Error(noDay.error.type);
    expect(noDay.value.wallet.missions).toEqual(wallet.missions);
    const bet = applyOperation(wallet, { type: 'placeBet', game: 'Blackjack', stake: 100 }, 'apuesta', 0);
    if (!bet.ok) throw new Error(bet.error.type);
    expect(bet.value.wallet.missions).toEqual(wallet.missions);
    // La ronda por turnos cuenta al liquidarse, con su juego y todo lo apostado.
    const settle = applyOperation(bet.value.wallet, { type: 'settleRound', payout: 200, today: DAY }, 'liquida', 0);
    if (!settle.ok) throw new Error(settle.error.type);
    const set = missionsFor(DAY);
    expect(settle.value.wallet.missions!.progress).toEqual(wallet.missions!.progress.map((p, i) => p + missionGain(set[i]!, 'Blackjack', 100, 200)));
  });

  it('solo avisa de las misiones que se completan en esa ronda', () => {
    const set = missionsFor(DAY);
    const before = { day: DAY, progress: set.map((m) => m.target - 1), claimed: [false, false, false] };
    const after = { ...before, progress: set.map((m, i) => (i === 1 ? m.target : m.target - 1)) };
    expect(newlyCompletedMissions(before, after)).toEqual([1]);
    expect(newlyCompletedMissions(after, after)).toEqual([]);
  });

  it('los textos se entienden', () => {
    expect(missionText({ kind: 'rounds', game: 'Blackjack', target: 5, reward: 500 })).toBe('Juega 5 manos de blackjack');
    expect(missionText({ kind: 'wins', game: 'Roulette', target: 2, reward: 500 })).toBe('Gana 2 veces en la ruleta');
    expect(missionText({ kind: 'wins', game: '', target: 8, reward: 1000 })).toBe('Gana 8 rondas');
    expect(missionText({ kind: 'stake', game: '', target: 10_000, reward: 1500 })).toBe('Apuesta 10.000 fichas en total');
    expect(missionText({ kind: 'rounds', game: 'Scratch', target: 3, reward: 250 })).toBe('Rasca 3 boletos');
  });
});
