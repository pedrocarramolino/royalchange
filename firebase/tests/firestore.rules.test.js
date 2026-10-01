// Tests de las reglas de seguridad de Firestore. Se ejecutan contra el emulador:
//   firebase emulators:exec --only firestore --project demo-royalchance "npm --prefix firebase/tests test"
import { after, before, beforeEach, describe, test } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, setDoc, writeBatch } from 'firebase/firestore';

const PROJECT_ID = 'demo-royalchance';
const CURRENT_YEAR = new Date().getUTCFullYear();

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

after(async () => {
  await env?.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
});

const consents = { termsVersion: '2026-09-30', privacyVersion: '2026-09-30', acceptedAtMillis: 1790000000000 };

function registeredProfile(uid, alias, overrides = {}) {
  return {
    uid,
    alias,
    aliasKey: alias.toLowerCase(),
    avatar: 'SpadeGold',
    countryCode: 'ES',
    birthYear: 1990,
    marketingOptIn: false,
    consents,
    createdAtMillis: 1790000000000,
    ...overrides,
  };
}

/** Reserva el alias y guarda el perfil en una única escritura, como hace la app. */
function register(db, uid, alias, overrides = {}) {
  const batch = writeBatch(db);
  batch.set(doc(db, `aliases/${alias.toLowerCase()}`), { uid, alias });
  batch.set(doc(db, `players/${uid}`), registeredProfile(uid, alias, overrides));
  return batch.commit();
}

const as = (uid) => env.authenticatedContext(uid).firestore();
const anonymous = () => env.unauthenticatedContext().firestore();

describe('players', () => {
  test('un jugador registra su perfil reservando su alias', async () => {
    await assertSucceeds(register(as('ana'), 'ana', 'AsDePicas'));
    await assertSucceeds(getDoc(doc(as('ana'), 'players/ana')));
  });

  test('nadie lee el perfil de otro jugador', async () => {
    await register(as('ana'), 'ana', 'AsDePicas');
    await assertFails(getDoc(doc(as('luis'), 'players/ana')));
    await assertFails(getDoc(doc(anonymous(), 'players/ana')));
  });

  test('no se puede escribir el perfil de otro jugador', async () => {
    await assertFails(register(as('luis'), 'ana', 'AsDePicas'));
  });

  test('no se puede guardar un perfil sin reservar el alias', async () => {
    await assertFails(setDoc(doc(as('ana'), 'players/ana'), registeredProfile('ana', 'AsDePicas')));
  });

  test('no se pueden añadir campos no previstos (p. ej. saldo)', async () => {
    await assertFails(register(as('ana'), 'ana', 'AsDePicas', { balance: 1000000 }));
  });

  test('menores de edad rechazados también en el servidor', async () => {
    await assertFails(register(as('ana'), 'ana', 'AsDePicas', { birthYear: CURRENT_YEAR - 10 }));
  });

  test('alias con formato inválido rechazado', async () => {
    await assertFails(register(as('ana'), 'ana', '7Ases'));
  });

  test('avatar desconocido rechazado', async () => {
    await assertFails(register(as('ana'), 'ana', 'AsDePicas', { avatar: 'Hacker' }));
  });

  test('el perfil debe estar completo', async () => {
    const db = as('ana');
    const { countryCode, ...withoutCountry } = registeredProfile('ana', 'AsDePicas');
    const batch = writeBatch(db);
    batch.set(doc(db, 'aliases/asdepicas'), { uid: 'ana', alias: 'AsDePicas' });
    batch.set(doc(db, 'players/ana'), withoutCountry);
    await assertFails(batch.commit());
    await assertFails(setDoc(doc(db, 'players/ana'), { uid: 'ana', consents, createdAtMillis: 1790000000000 }));
  });

  test('no existen perfiles de invitado', async () => {
    await assertFails(register(as('ana'), 'ana', 'AsDePicas', { guest: true }));
  });

  test('cada uno elimina su perfil y libera su alias', async () => {
    const db = as('ana');
    await register(db, 'ana', 'AsDePicas');
    const batch = writeBatch(db);
    batch.delete(doc(db, 'players/ana'));
    batch.delete(doc(db, 'aliases/asdepicas'));
    await assertSucceeds(batch.commit());
  });
});

describe('aliases', () => {
  test('un alias ya reservado no se puede quitar a su dueño', async () => {
    await register(as('ana'), 'ana', 'AsDePicas');
    await assertFails(register(as('luis'), 'luis', 'AsDePicas'));
    await assertFails(register(as('luis'), 'luis', 'asdepicas'));
  });

  test('cualquiera comprueba si un alias está libre, pero nadie lista la colección', async () => {
    await register(as('ana'), 'ana', 'AsDePicas');
    await assertSucceeds(getDoc(doc(anonymous(), 'aliases/asdepicas')));
    await assertFails(getDocs(collection(anonymous(), 'aliases')));
  });

  test('nadie borra el alias de otro jugador', async () => {
    await register(as('ana'), 'ana', 'AsDePicas');
    await assertFails(deleteDoc(doc(as('luis'), 'aliases/asdepicas')));
  });
});

// ── Economía ────────────────────────────────────────────────────────────────────────────────

const HOUR = 60 * 60 * 1000;

/** Prepara un estado sin pasar por las reglas. */
async function seed(path, data) {
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), path), data);
  });
}

/** Monedero de partida para probar movimientos (como si ya llevara cinco). */
function seedWallet(uid, fields = {}) {
  return seed(`wallets/${uid}`, { uid, balance: 10000, seq: 5, lastEntryId: 'anterior', ...fields });
}

/** Escribe un movimiento como la app: el monedero completo y su asiento, en un único lote. */
function move(db, uid, wallet, entry) {
  const batch = writeBatch(db);
  batch.set(doc(db, `wallets/${uid}`), { uid, ...wallet });
  batch.set(doc(db, `wallets/${uid}/ledger/${entry.id}`), { createdAtMillis: Date.now(), ...entry });
  return batch.commit();
}

function welcome(db, uid, { id = 'bienvenida', amount = 10000 } = {}) {
  return move(
    db,
    uid,
    { balance: amount, seq: 1, lastEntryId: id },
    { id, seq: 1, kind: 'Welcome', amount, balanceAfter: amount },
  );
}

/** Ronda instantánea sobre un monedero de 10.000 fichas y 5 movimientos. */
function spin(db, uid, { id = 'giro', stake = 100, payout = 0, balance = 10000 - stake + payout } = {}) {
  return move(
    db,
    uid,
    { balance, seq: 6, lastEntryId: id },
    { id, seq: 6, kind: 'InstantRound', amount: balance - 10000, balanceAfter: balance, game: 'Roulette', roundId: id, stake, payout },
  );
}

function rescue(db, uid, { balance = 5, at = Date.now() } = {}) {
  return move(
    db,
    uid,
    { balance: balance + 1000, seq: 6, lastEntryId: 'recarga', lastRescueAtMillis: at },
    { id: 'recarga', seq: 6, kind: 'Rescue', amount: 1000, balanceAfter: balance + 1000, createdAtMillis: at },
  );
}

describe('wallets', () => {
  test('un jugador con perfil crea su monedero con las fichas de bienvenida', async () => {
    const db = as('ana');
    await register(db, 'ana', 'AsDePicas');
    await assertSucceeds(welcome(db, 'ana'));
    await assertSucceeds(getDoc(doc(db, 'wallets/ana')));
  });

  test('sin perfil no hay monedero, y la bienvenida no se puede inflar ni repetir', async () => {
    const db = as('ana');
    await assertFails(welcome(db, 'ana'));

    await register(db, 'ana', 'AsDePicas');
    await assertFails(welcome(db, 'ana', { amount: 1000000 }));
    await welcome(db, 'ana');
    await assertFails(welcome(db, 'ana', { id: 'otra-bienvenida' }));
  });

  test('una apuesta abre la ronda y su liquidación la cierra', async () => {
    const db = as('ana');
    await seedWallet('ana');

    await assertSucceeds(move(
      db, 'ana',
      { balance: 9500, seq: 6, lastEntryId: 'apuesta', openRound: { id: 'apuesta', game: 'Blackjack', stake: 500 } },
      { id: 'apuesta', seq: 6, kind: 'Bet', amount: -500, balanceAfter: 9500, game: 'Blackjack', roundId: 'apuesta', stake: 500 },
    ));
    await assertSucceeds(move(
      db, 'ana',
      { balance: 10500, seq: 7, lastEntryId: 'pago' },
      { id: 'pago', seq: 7, kind: 'Settlement', amount: 1000, balanceAfter: 10500, game: 'Blackjack', roundId: 'apuesta', payout: 1000 },
    ));
  });

  test('una ronda instantánea cobra la apuesta y paga el premio a la vez', async () => {
    await seedWallet('ana');
    await assertSucceeds(spin(as('ana'), 'ana', { stake: 100, payout: 3600 }));
  });

  test('el saldo no cambia sin un asiento que lo justifique', async () => {
    const db = as('ana');
    await seedWallet('ana');

    await assertFails(setDoc(doc(db, 'wallets/ana'), { uid: 'ana', balance: 999999, seq: 6, lastEntryId: 'nada' }));
    // Asiento que no cuadra: dice ganar 100 y el saldo sube 5.000.
    await assertFails(spin(db, 'ana', { stake: 100, payout: 200, balance: 15000 }));
  });

  test('ni saldo negativo, ni apuestas fuera de límites, ni pagos imposibles', async () => {
    const db = as('ana');
    await seedWallet('ana', { balance: 50 });
    await assertFails(move(
      db, 'ana',
      { balance: -50, seq: 6, lastEntryId: 'giro' },
      { id: 'giro', seq: 6, kind: 'InstantRound', amount: -100, balanceAfter: -50, game: 'Dice', roundId: 'giro', stake: 100, payout: 0 },
    ));

    await seedWallet('ana');
    await assertFails(spin(db, 'ana', { stake: 5 }));
    await assertFails(spin(db, 'ana', { stake: 10, payout: 10010 }));
    await assertFails(spin(db, 'ana', { stake: 100001, payout: 100001, balance: 10000 }));
  });

  test('los asientos son inmutables y nunca van sueltos', async () => {
    const db = as('ana');
    await seedWallet('ana');
    await assertFails(setDoc(doc(db, 'wallets/ana/ledger/suelto'), {
      id: 'suelto', seq: 6, kind: 'Rescue', amount: 1000, balanceAfter: 11000, createdAtMillis: Date.now(),
    }));

    await spin(db, 'ana', { id: 'giro' });
    await assertFails(setDoc(doc(db, 'wallets/ana/ledger/giro'), {
      id: 'giro', seq: 6, kind: 'InstantRound', amount: 5000, balanceAfter: 15000, createdAtMillis: Date.now(),
    }));
    await assertFails(deleteDoc(doc(db, 'wallets/ana/ledger/giro')));
  });

  test('repetir una operación no la cobra dos veces', async () => {
    const db = as('ana');
    await seedWallet('ana');
    await assertSucceeds(spin(db, 'ana', { stake: 100, payout: 200 }));
    await assertFails(spin(db, 'ana', { stake: 100, payout: 200 }));
  });

  test('la recarga gratuita solo llega sin fichas y una vez cada 4 horas del servidor', async () => {
    const db = as('ana');
    await seedWallet('ana', { balance: 100 });
    await assertFails(rescue(db, 'ana', { balance: 100 }));

    await seedWallet('ana', { balance: 5, lastRescueAtMillis: Date.now() - HOUR });
    await assertFails(rescue(db, 'ana'));

    await seedWallet('ana', { balance: 5, lastRescueAtMillis: Date.now() - 5 * HOUR });
    // Adelantar la fecha del dispositivo no sirve: el servidor la rechaza.
    await assertFails(rescue(db, 'ana', { at: Date.now() + 2 * HOUR }));
    await assertSucceeds(rescue(db, 'ana'));
  });

  test('nadie lee ni mueve el monedero de otro jugador', async () => {
    await seedWallet('ana');
    await assertFails(getDoc(doc(as('luis'), 'wallets/ana')));
    await assertFails(getDoc(doc(anonymous(), 'wallets/ana')));
    await assertFails(spin(as('luis'), 'ana', { stake: 100, payout: 200 }));
  });

  test('eliminar la cuenta borra monedero y asientos, nunca el saldo por separado', async () => {
    const db = as('ana');
    await register(db, 'ana', 'AsDePicas');
    await welcome(db, 'ana');

    // Borrar solo el monedero permitiría volver a cobrar la bienvenida.
    await assertFails(deleteDoc(doc(db, 'wallets/ana')));
    await assertFails(deleteDoc(doc(db, 'wallets/ana/ledger/bienvenida')));

    const batch = writeBatch(db);
    batch.delete(doc(db, 'players/ana'));
    batch.delete(doc(db, 'aliases/asdepicas'));
    batch.delete(doc(db, 'wallets/ana'));
    batch.delete(doc(db, 'wallets/ana/ledger/bienvenida'));
    await assertSucceeds(batch.commit());
  });
});

describe('resto de la base de datos', () => {
  test('cualquier otra colección está cerrada', async () => {
    await assertFails(setDoc(doc(as('ana'), 'leaderboard/ana'), { balance: 1 }));
    await assertFails(getDoc(doc(as('ana'), 'leaderboard/ana')));
  });
});
