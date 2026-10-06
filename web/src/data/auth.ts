import { create } from 'zustand';
import { FirebaseError } from 'firebase/app';
import {
  EmailAuthProvider,
  createUserWithEmailAndPassword,
  deleteUser,
  onAuthStateChanged,
  reauthenticateWithCredential,
  reload,
  sendEmailVerification,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signOut as firebaseSignOut,
  type User,
} from 'firebase/auth';
import { collection, doc, getDocFromServer, getDocsFromServer, onSnapshot, setDoc, writeBatch, type Unsubscribe } from 'firebase/firestore';
import { auth, db } from '@/lib/firebase';
import type { AvatarId } from '@/domain/validation';
import { AVATARS, LEGAL_VERSIONS } from '@/domain/validation';

/** Perfil público y de cumplimiento: documento `players/{uid}`. */
export interface PlayerProfile {
  alias: string;
  avatar: AvatarId;
  countryCode: string;
  /** Solo el año: basta para acreditar la mayoría de edad (minimización de datos). */
  birthYear: number;
  marketingOptIn: boolean;
}

export interface AuthUser {
  uid: string;
  email: string;
  emailVerified: boolean;
  /** `null` si el registro se interrumpió tras crear la cuenta: se pide completar el perfil. */
  profile: PlayerProfile | null;
}

export type AuthStatus = { status: 'loading' } | { status: 'signedOut' } | { status: 'signedIn'; user: AuthUser };

export type AuthError =
  | 'invalidCredentials'
  | 'emailInUse'
  | 'aliasTaken'
  | 'weakPassword'
  | 'tooManyAttempts'
  | 'network'
  | 'notSignedIn'
  | 'requiresRecentLogin'
  | 'unknown';

export const AUTH_ERROR_TEXT: Record<AuthError, string> = {
  invalidCredentials: 'Email o contraseña incorrectos.',
  emailInUse: 'Ya existe una cuenta con ese email.',
  aliasTaken: 'Ese alias ya está en uso.',
  weakPassword: 'La contraseña es demasiado débil.',
  tooManyAttempts: 'Demasiados intentos. Espera unos minutos y vuelve a probar.',
  network: 'Sin conexión. Comprueba tu red y vuelve a intentarlo.',
  notSignedIn: 'Tu sesión ha caducado. Vuelve a iniciar sesión.',
  requiresRecentLogin: 'Por seguridad, vuelve a iniciar sesión y repite la acción.',
  unknown: 'Algo ha fallado. Vuelve a intentarlo.',
};

export type AuthResult = { ok: true } | { ok: false; error: AuthError };

function toAuthError(error: unknown): AuthError {
  const code = error instanceof FirebaseError ? error.code : '';
  switch (code) {
    // "Usuario no encontrado" se presenta como credenciales incorrectas: no se revela qué emails existen.
    case 'auth/invalid-credential':
    case 'auth/wrong-password':
    case 'auth/user-not-found':
    case 'auth/invalid-email':
      return 'invalidCredentials';
    case 'auth/email-already-in-use':
      return 'emailInUse';
    case 'auth/weak-password':
      return 'weakPassword';
    case 'auth/too-many-requests':
      return 'tooManyAttempts';
    case 'auth/network-request-failed':
    case 'unavailable':
      return 'network';
    case 'auth/requires-recent-login':
      return 'requiresRecentLogin';
    default:
      return 'unknown';
  }
}

const aliasKey = (alias: string) => alias.trim().toLowerCase();

function profileFromData(data: Record<string, unknown> | undefined): PlayerProfile | null {
  if (!data) return null;
  return {
    alias: String(data.alias),
    // Un avatar retirado en una versión futura no debe dejar al jugador sin perfil.
    avatar: (AVATARS as readonly string[]).includes(data.avatar as string) ? (data.avatar as AvatarId) : 'SpadeGold',
    countryCode: String(data.countryCode),
    birthYear: Number(data.birthYear),
    marketingOptIn: Boolean(data.marketingOptIn),
  };
}

interface AuthStore {
  state: AuthStatus;
  /** Durante el registro o el borrado se congela el estado publicado (Firebase pasa por "usuario sin perfil"). */
  changing: boolean;
  signIn: (email: string, password: string) => Promise<AuthResult>;
  register: (email: string, password: string, profile: PlayerProfile) => Promise<AuthResult>;
  completeProfile: (profile: PlayerProfile) => Promise<AuthResult>;
  updateProfile: (profile: PlayerProfile) => Promise<AuthResult>;
  sendPasswordReset: (email: string) => Promise<AuthResult>;
  sendVerification: () => Promise<AuthResult>;
  refreshUser: () => Promise<AuthResult>;
  signOut: () => Promise<void>;
  deleteAccount: (password: string) => Promise<AuthResult>;
}

let observed: AuthStatus = { status: 'loading' };

export const useAuth = create<AuthStore>((set, get) => {
  const publish = (state: AuthStatus) => {
    // El mismo usuario otra vez (avisos de Firestore sin cambios): no se repinta toda la app.
    if (JSON.stringify(state) === JSON.stringify(observed)) return;
    observed = state;
    if (!get().changing) set({ state });
  };

  const changingAccount = async (block: () => Promise<AuthResult>): Promise<AuthResult> => {
    set({ changing: true });
    try {
      return await block();
    } catch (error) {
      return { ok: false, error: toAuthError(error) };
    } finally {
      set({ changing: false, state: observed });
    }
  };

  const attempt = async (block: () => Promise<unknown>): Promise<AuthResult> => {
    try {
      await block();
      return { ok: true };
    } catch (error) {
      return { ok: false, error: toAuthError(error) };
    }
  };

  const toUser = (user: User, profile: PlayerProfile | null): AuthUser => ({
    uid: user.uid,
    email: user.email ?? '',
    emailVerified: user.emailVerified,
    profile,
  });

  let unsubscribeProfile: Unsubscribe | null = null;
  let lastProfile: PlayerProfile | null = null;

  let retryTimer: ReturnType<typeof setTimeout> | null = null;

  /**
   * Escucha el perfil del jugador. Un error corta la escucha para siempre en Firestore; justo al
   * crear la cuenta, la primera lectura puede fallar porque la sesión nueva aún no ha llegado a la
   * base de datos. Por eso se vuelve a intentar unas veces: si no, el perfil recién guardado no se
   * vería hasta recargar la app.
   */
  const watchProfile = (user: User, attemptNumber: number) => {
    unsubscribeProfile = onSnapshot(
      doc(db, `players/${user.uid}`),
      { includeMetadataChanges: true },
      (snapshot) => {
        // "No existe" solo cuenta si lo confirma el servidor (sin conexión la caché diría que no).
        if (!snapshot.exists() && snapshot.metadata.fromCache) return;
        lastProfile = profileFromData(snapshot.data());
        publish({ status: 'signedIn', user: toUser(user, lastProfile) });
      },
      // Sin acceso al perfil: se muestra la cuenta sin él (nunca se bloquea) y se reintenta.
      () => {
        publish({ status: 'signedIn', user: toUser(user, null) });
        if (attemptNumber >= 5) return;
        retryTimer = setTimeout(() => {
          if (auth.currentUser?.uid === user.uid) watchProfile(user, attemptNumber + 1);
        }, 600 * (attemptNumber + 1));
      },
    );
  };

  onAuthStateChanged(auth, (user) => {
    unsubscribeProfile?.();
    unsubscribeProfile = null;
    if (retryTimer) clearTimeout(retryTimer);
    retryTimer = null;
    if (!user) {
      publish({ status: 'signedOut' });
      return;
    }
    watchProfile(user, 0);
  });

  const saveProfile = async (profile: PlayerProfile): Promise<AuthResult> => {
    const user = auth.currentUser;
    if (!user) return { ok: false, error: 'notSignedIn' };
    const key = aliasKey(profile.alias);
    const now = Date.now();
    const batch = writeBatch(db);
    batch.set(doc(db, `aliases/${key}`), { uid: user.uid, alias: profile.alias.trim() });
    batch.set(doc(db, `players/${user.uid}`), {
      uid: user.uid,
      alias: profile.alias.trim(),
      aliasKey: key,
      avatar: profile.avatar,
      countryCode: profile.countryCode,
      birthYear: profile.birthYear,
      marketingOptIn: profile.marketingOptIn,
      consents: { termsVersion: LEGAL_VERSIONS.terms, privacyVersion: LEGAL_VERSIONS.privacy, acceptedAtMillis: now },
      createdAtMillis: now,
    });
    try {
      await batch.commit();
      return { ok: true };
    } catch (error) {
      // Otro jugador reservó el alias entre la comprobación y la escritura: las reglas lo rechazan.
      if (error instanceof FirebaseError && error.code === 'permission-denied') return { ok: false, error: 'aliasTaken' };
      return { ok: false, error: toAuthError(error) };
    }
  };

  const aliasExists = async (alias: string) => (await getDocFromServer(doc(db, `aliases/${aliasKey(alias)}`))).exists();

  return {
    state: { status: 'loading' },
    changing: false,

    signIn: (email, password) => attempt(() => signInWithEmailAndPassword(auth, email.trim(), password)),

    register: (email, password, profile) =>
      changingAccount(async () => {
        if (await aliasExists(profile.alias)) return { ok: false, error: 'aliasTaken' };
        await createUserWithEmailAndPassword(auth, email.trim(), password);
        const saved = await saveProfile(profile);
        // El email de verificación es un extra: si falla, el jugador puede reenviarlo.
        if (saved.ok && auth.currentUser) await sendEmailVerification(auth.currentUser).catch(() => undefined);
        return saved;
      }),

    completeProfile: (profile) =>
      changingAccount(async () => {
        if (await aliasExists(profile.alias)) return { ok: false, error: 'aliasTaken' };
        return saveProfile(profile);
      }),

    updateProfile: async (profile) => {
      const current = get().state;
      if (current.status !== 'signedIn' || !current.user.profile) return { ok: false, error: 'notSignedIn' };
      const user = auth.currentUser;
      if (!user) return { ok: false, error: 'notSignedIn' };
      // El alias no cambia aquí: solo avatar y preferencias, con el mismo formato validado.
      return attempt(() =>
        setDoc(doc(db, `players/${user.uid}`), { avatar: profile.avatar, marketingOptIn: profile.marketingOptIn }, { merge: true }),
      );
    },

    sendPasswordReset: async (email) => {
      const result = await attempt(() => sendPasswordResetEmail(auth, email.trim()));
      // Misma respuesta exista o no la cuenta: no se revela qué emails están registrados.
      return !result.ok && result.error === 'invalidCredentials' ? { ok: true } : result;
    },

    sendVerification: () =>
      attempt(async () => {
        if (!auth.currentUser) throw new Error('sin sesión');
        await sendEmailVerification(auth.currentUser);
      }),

    refreshUser: () =>
      attempt(async () => {
        const user = auth.currentUser;
        if (!user) return;
        await reload(user);
        // onAuthStateChanged no avisa de la verificación del email: se publica a mano.
        publish({ status: 'signedIn', user: toUser(user, lastProfile) });
      }),

    signOut: async () => {
      await firebaseSignOut(auth).catch(() => undefined);
    },

    deleteAccount: (password) =>
      changingAccount(async () => {
        const user = auth.currentUser;
        if (!user?.email) return { ok: false, error: 'notSignedIn' };
        await reauthenticateWithCredential(user, EmailAuthProvider.credential(user.email, password));
        const current = get().state;
        const key = current.status === 'signedIn' && current.user.profile ? aliasKey(current.user.profile.alias) : null;
        const ledger = await getDocsFromServer(collection(db, `wallets/${user.uid}/ledger`));
        // Clasificación: saldos de entrada de cada semana (privados) y sus filas públicas.
        const weeks = await getDocsFromServer(collection(db, `wallets/${user.uid}/weeks`));
        const entries = [
          ...ledger.docs.map((d) => d.ref),
          ...weeks.docs.flatMap((d) => [d.ref, doc(db, `leaderboard/${d.id}/players/${user.uid}`)]),
        ];
        // Primer lote: perfil, alias y monedero (y los asientos que quepan). Los asientos restantes
        // después: las reglas solo los dejan borrar cuando el monedero ya no existe.
        const first = writeBatch(db);
        first.delete(doc(db, `players/${user.uid}`));
        if (key) first.delete(doc(db, `aliases/${key}`));
        first.delete(doc(db, `wallets/${user.uid}`));
        const head = entries.slice(0, 497);
        head.forEach((ref) => first.delete(ref));
        await first.commit();
        for (let i = head.length; i < entries.length; i += 500) {
          const batch = writeBatch(db);
          entries.slice(i, i + 500).forEach((ref) => batch.delete(ref));
          await batch.commit();
        }
        await deleteUser(user);
        return { ok: true };
      }),
  };
});
