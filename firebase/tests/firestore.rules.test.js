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

describe('resto de la base de datos', () => {
  test('cualquier otra colección está cerrada', async () => {
    await assertFails(setDoc(doc(as('ana'), 'wallets/ana'), { balance: 1 }));
    await assertFails(getDoc(doc(as('ana'), 'wallets/ana')));
  });
});
