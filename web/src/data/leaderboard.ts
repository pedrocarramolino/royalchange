import {
  collection,
  deleteDoc,
  doc,
  getCountFromServer,
  getDocFromServer,
  getDocsFromServer,
  limit,
  orderBy,
  query,
  setDoc,
  waitForPendingWrites,
  where,
  writeBatch,
} from 'firebase/firestore';
import { db } from '@/lib/firebase';
import type { AvatarId } from '@/domain/validation';
import { AVATARS } from '@/domain/validation';
import type { PlayerProfile } from './auth';

/*
 * Clasificación semanal: las ganancias de la semana (saldo actual menos el saldo con el que se
 * entró en la clasificación esa semana). Las reglas de Firestore las recalculan con el saldo real
 * del monedero en cada escritura, así que no se pueden inventar. Fila pública:
 * `leaderboard/{semana}/players/{uid}`; saldo de entrada, privado: `wallets/{uid}/weeks/{semana}`.
 */

const DAY_MS = 86_400_000;
export const LEADERBOARD_SIZE = 50;

/** Semana (de lunes a domingo, en UTC) desde 1970: la misma cuenta que hacen las reglas. */
export function currentWeek(now = Date.now()): number {
  return Math.floor((now / DAY_MS + 3) / 7);
}

/** Momento en que termina la semana [week] (lunes a las 00:00 UTC). */
export function weekEndsAt(week: number): number {
  return ((week + 1) * 7 - 3) * DAY_MS;
}

export interface LeaderboardRow {
  uid: string;
  alias: string;
  avatar: AvatarId;
  score: number;
}

const avatarOf = (value: unknown): AvatarId => ((AVATARS as readonly string[]).includes(value as string) ? (value as AvatarId) : 'SpadeGold');

let lastSync = 0;
/** Última puntuación conocida (con su usuario): se devuelve si se pide otra vez muy seguido. */
let lastScore: { uid: string; score: number } | null = null;
/** Puesta al día en curso: quien pide otra mientras tanto espera a esta. */
let inflight: Promise<number | null> | null = null;

/**
 * Pone al día la fila del jugador: la primera vez de la semana guarda su saldo de entrada (el real,
 * del servidor) y entra con ganancias cero; después, ganancias = saldo − saldo de entrada. Espera a
 * que se suban sus jugadas: las reglas comparan con el saldo del servidor. Devuelve las ganancias,
 * o `null` si no se pudo (sin conexión…; se reintenta la próxima vez).
 */
export function syncLeaderboard(uid: string, profile: PlayerProfile, { force = false } = {}): Promise<number | null> {
  if (inflight) return inflight;
  if (!force && Date.now() - lastSync < 30_000) return Promise.resolve(lastScore?.uid === uid ? lastScore.score : null);
  lastSync = Date.now();
  inflight = doSync(uid, profile).finally(() => {
    inflight = null;
  });
  return inflight;
}

async function doSync(uid: string, profile: PlayerProfile): Promise<number | null> {
  try {
    await waitForPendingWrites(db);
    const week = currentWeek();
    const wallet = await getDocFromServer(doc(db, `wallets/${uid}`));
    if (!wallet.exists()) return null;
    const balance = Number(wallet.data().balance);
    const baseRef = doc(db, `wallets/${uid}/weeks/${week}`);
    const entryRef = doc(db, `leaderboard/${week}/players/${uid}`);
    const base = await getDocFromServer(baseRef);
    const row = { alias: profile.alias, avatar: profile.avatar, updatedAtMillis: Date.now() };
    if (!base.exists()) {
      const batch = writeBatch(db);
      batch.set(baseRef, { balance });
      batch.set(entryRef, { ...row, score: 0 });
      await batch.commit();
      lastScore = { uid, score: 0 };
      return 0;
    }
    const score = balance - Number(base.data().balance);
    await setDoc(entryRef, { ...row, score });
    lastScore = { uid, score };
    return score;
  } catch {
    lastSync = 0;
    return null;
  }
}

/** Quitarse de la clasificación (al volver, se cuenta desde el mismo saldo de entrada). */
export async function leaveLeaderboard(uid: string): Promise<void> {
  lastSync = 0;
  lastScore = null;
  await deleteDoc(doc(db, `leaderboard/${currentWeek()}/players/${uid}`)).catch(() => undefined);
}

/** Los mejores de la semana [week]. */
export async function topPlayers(week = currentWeek()): Promise<LeaderboardRow[]> {
  const snapshot = await getDocsFromServer(query(collection(db, `leaderboard/${week}/players`), orderBy('score', 'desc'), limit(LEADERBOARD_SIZE)));
  return snapshot.docs.map((d) => ({ uid: d.id, alias: String(d.data().alias), avatar: avatarOf(d.data().avatar), score: Number(d.data().score) }));
}

/** Puesto con unas ganancias [score]: uno más que los que tienen más. */
export async function rankFor(score: number, week = currentWeek()): Promise<number> {
  const above = await getCountFromServer(query(collection(db, `leaderboard/${week}/players`), where('score', '>', score)));
  return above.data().count + 1;
}
