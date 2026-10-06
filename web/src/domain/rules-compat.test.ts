/**
 * Compatibilidad del dominio en TypeScript con las reglas de seguridad de Firestore de producción:
 * cada operación se calcula con `applyOperation` y se escribe como lo hace la app. Si el código y
 * las reglas no coincidieran, la escritura se rechazaría.
 *
 * Solo se ejecuta contra el emulador (`npm run test:reglas`, desde la raíz del proyecto).
 */
import { readFileSync } from 'node:fs';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { assertFails, assertSucceeds, initializeTestEnvironment, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, getDoc, writeBatch, type Firestore } from 'firebase/firestore';
import type { EconomyOperation, Wallet } from './economy';
import { GAMES } from './economy';
import { missionsFor, missionsToday } from './missions';
import { entryToData, walletFromData, walletToData } from './documents';
import { applyOperation, openWallet, type WalletTransition } from './transitions';
import { localEpochDay } from '../lib/time';
import { newId } from '../lib/ids';

const emulator = process.env.FIRESTORE_EMULATOR_HOST;

describe.skipIf(!emulator)('reglas de Firestore', () => {
  let env: RulesTestEnvironment;

  beforeAll(async () => {
    env = await initializeTestEnvironment({
      projectId: 'demo-royalchance-compat',
      firestore: { rules: readFileSync(new URL('../../../firebase/firestore.rules', import.meta.url), 'utf8') },
    });
    await env.clearFirestore();
  });

  afterAll(async () => {
    await env?.cleanup();
  });

  async function register(db: Firestore, uid: string, alias: string) {
    const batch = writeBatch(db);
    batch.set(doc(db, `aliases/${alias.toLowerCase()}`), { uid, alias });
    batch.set(doc(db, `players/${uid}`), {
      uid,
      alias,
      aliasKey: alias.toLowerCase(),
      avatar: 'HeartRuby',
      countryCode: 'ES',
      birthYear: 1990,
      marketingOptIn: false,
      consents: { termsVersion: '2026-09-30', privacyVersion: '2026-09-30', acceptedAtMillis: Date.now() },
      createdAtMillis: Date.now(),
    });
    await assertSucceeds(batch.commit());
  }

  function write(db: Firestore, uid: string, transition: WalletTransition) {
    const batch = writeBatch(db);
    batch.set(doc(db, `wallets/${uid}`), walletToData(transition.wallet));
    batch.set(doc(db, `wallets/${uid}/ledger/${transition.entry.id}`), entryToData(transition.entry));
    return batch.commit();
  }

  async function read(db: Firestore, uid: string): Promise<Wallet> {
    const snapshot = await getDoc(doc(db, `wallets/${uid}`));
    return walletFromData(snapshot.data()!);
  }

  async function run(db: Firestore, uid: string, operation: EconomyOperation, now = Date.now()) {
    const current = await read(db, uid);
    const result = applyOperation(current, operation, newId(), now);
    if (!result.ok) throw new Error(`Operación rechazada por el dominio: ${JSON.stringify(operation)} → ${JSON.stringify(result.error)}`);
    await assertSucceeds(write(db, uid, result.value));
    return result.value;
  }

  it('acepta una sesión de juego completa calculada con el dominio en TypeScript', async () => {
    const uid = 'ana';
    const db = env.authenticatedContext(uid).firestore() as unknown as Firestore;
    await register(db, uid, 'AnaTs');
    await assertSucceeds(write(db, uid, openWallet(uid, newId(), Date.now())));

    await run(db, uid, { type: 'claimDailyBonus', today: localEpochDay() });
    await run(db, uid, { type: 'spinDailyWheel', today: localEpochDay(), prize: 2_500 });

    // Rondas instantáneas de todos los juegos: ganar, perder y empatar.
    const rounds: EconomyOperation[] = [
      { type: 'instantRound', game: 'Roulette', stake: 100, payout: 3_600 },
      { type: 'instantRound', game: 'Slots', stake: 100, payout: 0 },
      { type: 'instantRound', game: 'Dice', stake: 50, payout: 115 },
      { type: 'instantRound', game: 'Roulette', stake: 1_000, payout: 1_000 },
      { type: 'instantRound', game: 'Slots', stake: 500, payout: 50 },
      { type: 'instantRound', game: 'Dice', stake: 10, payout: 23 },
      { type: 'instantRound', game: 'Roulette', stake: 5_000, payout: 10_000 },
      { type: 'instantRound', game: 'Slots', stake: 10_000, payout: 60_000 },
      { type: 'instantRound', game: 'Baccarat', stake: 300, payout: 350 },
      { type: 'instantRound', game: 'Plinko', stake: 100, payout: 1_100 },
      { type: 'instantRound', game: 'Scratch', stake: 50, payout: 250 },
    ];
    for (const operation of rounds) await run(db, uid, operation);

    // Blackjack: apuesta, doblar y liquidar.
    await run(db, uid, { type: 'placeBet', game: 'Blackjack', stake: 200 });
    await run(db, uid, { type: 'placeBet', game: 'Blackjack', stake: 200 });
    await run(db, uid, { type: 'settleRound', payout: 800 });

    // Póker: cada ficha al bote es una apuesta; la mano se pierde.
    await run(db, uid, { type: 'placeBet', game: 'Poker', stake: 20 });
    await run(db, uid, { type: 'placeBet', game: 'Poker', stake: 40 });
    await run(db, uid, { type: 'placeBet', game: 'Poker', stake: 100 });
    await run(db, uid, { type: 'settleRound', payout: 0 });

    // Video póker: la apuesta al repartir y el pago al cambiar cartas.
    await run(db, uid, { type: 'placeBet', game: 'VideoPoker', stake: 100 });
    await run(db, uid, { type: 'settleRound', payout: 900 });

    // Ya hay 10 rondas o más: logros desbloqueados por el camino que se cobran ahora.
    const wallet = await read(db, uid);
    expect(wallet.rounds).toBe(14);
    expect(wallet.unlocked).toEqual(expect.arrayContaining(['FirstWin', 'Rounds10', 'Balance50k']));
    for (const id of wallet.unlocked) await run(db, uid, { type: 'claimAchievement', id });

    // Racha de victorias para desbloquear WinStreak5.
    for (let i = 0; i < 5; i++) await run(db, uid, { type: 'instantRound', game: 'Dice', stake: 10, payout: 23 });
    expect((await read(db, uid)).unlocked).toContain('WinStreak5');

    // Perderlo todo y pedir la recarga.
    const broke = await read(db, uid);
    let remaining = broke.balance;
    while (remaining >= 10) {
      const stake = Math.min(remaining, 100_000);
      await run(db, uid, { type: 'instantRound', game: 'Slots', stake, payout: 0 });
      remaining -= stake;
    }
    await run(db, uid, { type: 'claimRescue' });
    expect((await read(db, uid)).balance).toBe(remaining + 1_000);
  });

  it('las misiones avanzan igual en la app y en las reglas, todos los días del ciclo', async () => {
    const uid = 'mia';
    const db = env.authenticatedContext(uid).firestore() as unknown as Firestore;
    await register(db, uid, 'MiaTs');
    await assertSucceeds(write(db, uid, openWallet(uid, newId(), Date.now())));
    // 42 días seguidos cubren todas las posiciones de las tres listas (7, 6 y 5). Días pasados: las
    // rondas no tienen límite hacia atrás (se pueden sincronizar tarde); cobrar sí exige hoy.
    const first = Math.floor(Date.now() / 86_400_000) - 50;
    for (let day = first; day < first + 42; day++) {
      for (const game of GAMES) await run(db, uid, { type: 'instantRound', game, stake: 10, payout: day % 3 === 0 ? 0 : 20, today: day });
    }
  }, 120_000);

  it('se completan y cobran las misiones de hoy', async () => {
    const uid = 'teo';
    const db = env.authenticatedContext(uid).firestore() as unknown as Firestore;
    await register(db, uid, 'TeoTs');
    await assertSucceeds(write(db, uid, openWallet(uid, newId(), Date.now())));
    const today = Math.floor(Date.now() / 86_400_000);
    const set = missionsFor(today);
    // Rondas ganadas en cada juego hasta completar las tres (la difícil puede pedir 10.000 fichas).
    for (let k = 0; k < 30 && !missionsToday(await read(db, uid), today).progress.every((p, i) => p >= set[i]!.target); k++) {
      for (const game of GAMES) await run(db, uid, { type: 'instantRound', game, stake: 500, payout: 1_000, today });
    }
    const before = await read(db, uid);
    for (const index of [0, 1, 2]) await run(db, uid, { type: 'claimMission', index, today });
    const after = await read(db, uid);
    expect(after.missions!.claimed).toEqual([true, true, true]);
    expect(after.balance).toBe(before.balance + set.reduce((s, m) => s + m.reward, 0));
  }, 120_000);

  it('rechaza un giro de la ruleta diaria con un premio que no está en la ruleta', async () => {
    const uid = 'eva';
    const db = env.authenticatedContext(uid).firestore() as unknown as Firestore;
    await register(db, uid, 'EvaTs');
    await assertSucceeds(write(db, uid, openWallet(uid, newId(), Date.now())));
    const current = await read(db, uid);
    const result = applyOperation(current, { type: 'spinDailyWheel', today: localEpochDay(), prize: 10_000 }, newId(), Date.now());
    if (!result.ok) throw new Error('inesperado');
    // Mismo giro, pero declarando 20.000 fichas: saldo, asiento y máximo coherentes entre sí.
    const forged: WalletTransition = {
      ...result.value,
      wallet: { ...result.value.wallet, balance: current.balance + 20_000, highestBalance: current.balance + 20_000 },
      entry: { ...result.value.entry, amount: 20_000, balanceAfter: current.balance + 20_000 },
    };
    await assertFails(write(db, uid, forged));
    await assertSucceeds(write(db, uid, result.value));
  });

  it('rechaza un monedero manipulado aunque el asiento sea coherente', async () => {
    const uid = 'luis';
    const db = env.authenticatedContext(uid).firestore() as unknown as Firestore;
    await register(db, uid, 'LuisTs');
    await assertSucceeds(write(db, uid, openWallet(uid, newId(), Date.now())));
    const current = await read(db, uid);
    const result = applyOperation(current, { type: 'instantRound', game: 'Roulette', stake: 100, payout: 3_600 }, newId(), Date.now());
    if (!result.ok) throw new Error('inesperado');
    const tampered = { ...result.value, wallet: { ...result.value.wallet, xp: result.value.wallet.xp + 1_000 } };
    await assertFails(write(db, uid, tampered));
  });
});
