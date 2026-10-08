import type { DailyMissions, GameType, Wallet } from './economy';

/*
 * Misiones diarias: tres retos al día (fácil, media y difícil) con fichas de recompensa. Las de
 * cada día salen de tres listas según el número de día, sin azar: la app y las reglas de Firestore
 * (`firebase/firestore.rules`, que repiten estas listas) saben siempre cuáles tocan. El progreso va
 * en el monedero y avanza con cada ronda cerrada.
 */

export type MissionKind = 'rounds' | 'wins' | 'stake';

export interface Mission {
  /** Rondas jugadas, rondas ganadas o fichas apostadas. */
  kind: MissionKind;
  /** Juego en el que cuenta, o '' para cualquiera. */
  game: GameType | '';
  target: number;
  reward: number;
}

const mission = (kind: MissionKind, game: GameType | '', target: number, reward: number): Mission => ({ kind, game, target, reward });

export const EASY_MISSIONS: Mission[] = [
  mission('rounds', '', 5, 250),
  mission('rounds', 'Slots', 5, 250),
  mission('rounds', 'Roulette', 3, 250),
  mission('rounds', 'Scratch', 3, 250),
  mission('rounds', 'Plinko', 5, 250),
  mission('rounds', 'Dice', 3, 250),
  mission('rounds', 'VideoPoker', 3, 250),
];

export const MEDIUM_MISSIONS: Mission[] = [
  mission('wins', '', 3, 500),
  mission('rounds', 'Blackjack', 5, 500),
  mission('wins', 'Roulette', 2, 500),
  mission('rounds', 'Baccarat', 5, 500),
  mission('wins', 'Slots', 3, 500),
  mission('rounds', 'Poker', 3, 500),
];

export const HARD_MISSIONS: Mission[] = [
  mission('stake', '', 5_000, 1_000),
  mission('wins', '', 8, 1_000),
  mission('rounds', '', 25, 1_000),
  mission('stake', '', 10_000, 1_500),
  mission('wins', 'Blackjack', 3, 1_000),
];

export const MISSIONS_PER_DAY = 3;

/** Las tres misiones del día [day] (días desde 1970-01-01). */
export function missionsFor(day: number): Mission[] {
  return [EASY_MISSIONS[day % EASY_MISSIONS.length]!, MEDIUM_MISSIONS[day % MEDIUM_MISSIONS.length]!, HARD_MISSIONS[day % HARD_MISSIONS.length]!];
}

/**
 * Si una ronda cuenta como victoria para las misiones: cobrar más de lo apostado o, en las slots y
 * la ruleta (donde se apuesta a varias líneas o números a la vez), cobrar cualquier premio.
 */
export function missionWin(game: GameType, stake: number, payout: number): boolean {
  return payout > stake || (payout > 0 && (game === 'Slots' || game === 'Roulette'));
}

/** Lo que avanza [m] con una ronda. */
export function missionGain(m: Mission, game: GameType, stake: number, payout: number): number {
  if (m.game !== '' && m.game !== game) return 0;
  if (m.kind === 'rounds') return 1;
  if (m.kind === 'wins') return missionWin(game, stake, payout) ? 1 : 0;
  return stake;
}

const empty = (day: number): DailyMissions => ({ day, progress: [0, 0, 0], claimed: [false, false, false] });

/**
 * Misiones tras una ronda cerrada. El día nunca retrocede (si el reloj del dispositivo va hacia
 * atrás, sigue contando el último); un día nuevo empieza de cero.
 */
export function missionsAfterRound(current: DailyMissions | undefined, today: number, game: GameType, stake: number, payout: number): DailyMissions {
  const day = Math.max(today, current?.day ?? today);
  const base = current && current.day === day ? current : empty(day);
  const set = missionsFor(day);
  return { day, progress: base.progress.map((p, i) => p + missionGain(set[i]!, game, stake, payout)), claimed: [...base.claimed] };
}

/** Misiones que ve el jugador hoy: las guardadas si son de hoy (o de un día posterior), si no, sin empezar. */
export function missionsToday(wallet: Wallet, today: number): DailyMissions & { missions: Mission[] } {
  const stored = wallet.missions;
  const current = stored && stored.day >= today ? stored : empty(today);
  return { ...current, missions: missionsFor(current.day) };
}

/** Índices de las misiones que [after] acaba de completar respecto a [before]. */
export function newlyCompletedMissions(before: DailyMissions | undefined, after: DailyMissions | undefined): number[] {
  if (!after) return [];
  const set = missionsFor(after.day);
  return set.flatMap((m, i) => {
    const was = before && before.day === after.day ? before.progress[i]! : 0;
    return was < m.target && after.progress[i]! >= m.target ? [i] : [];
  });
}

const GAME_ROUNDS: Record<GameType, (n: number) => string> = {
  Blackjack: (n) => `Juega ${n} manos de blackjack`,
  Roulette: (n) => `Juega ${n} tiradas en la ruleta`,
  Slots: (n) => `Juega ${n} tiradas en las slots`,
  Poker: (n) => `Juega ${n} manos de póker`,
  Dice: (n) => `Juega ${n} tiradas de dados`,
  Baccarat: (n) => `Juega ${n} manos de baccarat`,
  VideoPoker: (n) => `Juega ${n} manos de video póker`,
  Plinko: (n) => `Suelta ${n} bolas en el plinko`,
  Scratch: (n) => `Rasca ${n} boletos`,
};

const GAME_IN: Record<GameType, string> = {
  Blackjack: 'al blackjack',
  Roulette: 'en la ruleta',
  Slots: 'en las slots',
  Poker: 'al póker',
  Dice: 'a los dados',
  Baccarat: 'al baccarat',
  VideoPoker: 'al video póker',
  Plinko: 'en el plinko',
  Scratch: 'al rasca y gana',
};

/** 10000 → «10.000» (también con 4 cifras, que es-ES no agrupa). */
const grouped = (n: number) => String(n).replace(/\B(?=(\d{3})+(?!\d))/g, '.');

/** Texto de la misión: «Juega 5 manos de blackjack», «Gana 3 veces en la ruleta»… */
export function missionText(m: Mission): string {
  if (m.kind === 'stake') return `Apuesta ${grouped(m.target)} fichas en total`;
  if (m.kind === 'wins') return m.game ? `Gana ${m.target} veces ${GAME_IN[m.game]}` : `Gana ${m.target} rondas`;
  return m.game ? GAME_ROUNDS[m.game](m.target) : `Juega ${m.target} rondas`;
}
