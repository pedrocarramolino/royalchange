import { draw, needsShuffle, secureRandom, shuffledShoe, type Card, type RandomInt, type Shoe } from './cards';

/*
 * Baccarat (Punto Banco) sin comisión: la banca gana 1:1, salvo cuando gana con 6, que paga 1:2.
 * Así, con fichas de 10, todos los pagos son enteros. Empate 8:1 y parejas 11:1.
 */

/** Valor de una carta: as 1, del 2 al 9 su número; el 10 y las figuras, 0. */
export function baccaratValue(card: Card): number {
  if (card.rank === 'A') return 1;
  if (card.rank === '10' || card.rank === 'J' || card.rank === 'Q' || card.rank === 'K') return 0;
  return Number(card.rank);
}

/** Puntuación de una mano: la última cifra de la suma. */
export const handTotal = (cards: Card[]) => cards.reduce((sum, card) => sum + baccaratValue(card), 0) % 10;

/**
 * ¿Pide la banca tercera carta? [playerThird] es el valor de la tercera carta del jugador, o `null`
 * si el jugador se plantó con dos.
 */
export function bankerDraws(bankerTotal: number, playerThird: number | null): boolean {
  if (playerThird === null) return bankerTotal <= 5;
  switch (bankerTotal) {
    case 0:
    case 1:
    case 2:
      return true;
    case 3:
      return playerThird !== 8;
    case 4:
      return playerThird >= 2 && playerThird <= 7;
    case 5:
      return playerThird >= 4 && playerThird <= 7;
    case 6:
      return playerThird === 6 || playerThird === 7;
    default:
      return false;
  }
}

export type CoupWinner = 'player' | 'banker' | 'tie';

/** Una mano: cartas en orden de llegada a cada lado (la tercera, si la hay, al final). */
export interface Coup {
  player: Card[];
  banker: Card[];
  playerTotal: number;
  bankerTotal: number;
  winner: CoupWinner;
  /** 8 o 9 con las dos primeras: nadie pide carta. */
  natural: boolean;
  playerPair: boolean;
  bankerPair: boolean;
}

/** Juega una mano con las cartas que va dando [next]: jugador, banca, jugador, banca y las terceras. */
export function playCoup(next: () => Card): Coup {
  const player = [next()];
  const banker = [next()];
  player.push(next());
  banker.push(next());
  const natural = handTotal(player) >= 8 || handTotal(banker) >= 8;
  if (!natural) {
    let playerThird: number | null = null;
    if (handTotal(player) <= 5) {
      const card = next();
      player.push(card);
      playerThird = baccaratValue(card);
    }
    if (bankerDraws(handTotal(banker), playerThird)) banker.push(next());
  }
  const playerTotal = handTotal(player);
  const bankerTotal = handTotal(banker);
  return {
    player,
    banker,
    playerTotal,
    bankerTotal,
    winner: playerTotal > bankerTotal ? 'player' : bankerTotal > playerTotal ? 'banker' : 'tie',
    natural,
    playerPair: player[0]!.rank === player[1]!.rank,
    bankerPair: banker[0]!.rank === banker[1]!.rank,
  };
}

export type BaccaratBet = 'player' | 'banker' | 'tie' | 'playerPair' | 'bankerPair';

export const BACCARAT_BETS: BaccaratBet[] = ['playerPair', 'player', 'tie', 'banker', 'bankerPair'];

export const BACCARAT_RULES = { chipUnit: 10, maximumTotalBet: 25_000, decks: 8, penetration: 0.8 } as const;

/** Fichas cobradas por [stake] en [bet] (apuesta incluida; 0 si se pierde). */
export function baccaratPayout(bet: BaccaratBet, stake: number, coup: Coup): number {
  switch (bet) {
    case 'player':
      return coup.winner === 'player' ? stake * 2 : coup.winner === 'tie' ? stake : 0;
    case 'banker':
      if (coup.winner === 'tie') return stake;
      if (coup.winner !== 'banker') return 0;
      return coup.bankerTotal === 6 ? stake + stake / 2 : stake * 2;
    case 'tie':
      return coup.winner === 'tie' ? stake * 9 : 0;
    case 'playerPair':
      return coup.playerPair ? stake * 12 : 0;
    case 'bankerPair':
      return coup.bankerPair ? stake * 12 : 0;
  }
}

export interface PlacedBaccaratBet {
  bet: BaccaratBet;
  stake: number;
}

export interface BaccaratRound {
  coup: Coup;
  results: (PlacedBaccaratBet & { payout: number })[];
  totalStake: number;
  totalPayout: number;
}

export type BaccaratError = 'noBets' | 'invalidBet' | 'aboveTableMaximum';

export function validateBaccarat(bets: PlacedBaccaratBet[]): BaccaratError | null {
  if (bets.length === 0) return 'noBets';
  if (bets.some((b) => !BACCARAT_BETS.includes(b.bet) || b.stake < BACCARAT_RULES.chipUnit || b.stake % BACCARAT_RULES.chipUnit !== 0)) return 'invalidBet';
  if (bets.reduce((sum, b) => sum + b.stake, 0) > BACCARAT_RULES.maximumTotalBet) return 'aboveTableMaximum';
  return null;
}

export function settleBaccarat(bets: PlacedBaccaratBet[], coup: Coup): BaccaratRound {
  const results = bets.map((b) => ({ ...b, payout: baccaratPayout(b.bet, b.stake, coup) }));
  return { coup, results, totalStake: results.reduce((s, r) => s + r.stake, 0), totalPayout: results.reduce((s, r) => s + r.payout, 0) };
}

/** Zapato de 8 barajas; se baraja de nuevo al pasar la carta de corte. */
export const newBaccaratShoe = (random: RandomInt = secureRandom) => shuffledShoe(BACCARAT_RULES.decks, BACCARAT_RULES.penetration, random);

/** Reparte una mano desde [shoe] (o uno nuevo si toca barajar) y liquida las apuestas. */
export function dealBaccarat(
  bets: PlacedBaccaratBet[],
  shoe: Shoe | null,
  random: RandomInt = secureRandom,
): { ok: true; round: BaccaratRound; shoe: Shoe } | { ok: false; error: BaccaratError } {
  const error = validateBaccarat(bets);
  if (error) return { ok: false, error };
  let current = !shoe || needsShuffle(shoe) ? newBaccaratShoe(random) : shoe;
  const coup = playCoup(() => {
    const [card, rest] = draw(current);
    current = rest;
    return card;
  });
  return { ok: true, round: settleBaccarat(bets, coup), shoe: current };
}

export const BACCARAT_BET_NAME: Record<BaccaratBet, string> = {
  player: 'Jugador',
  banker: 'Banca',
  tie: 'Empate',
  playerPair: 'Pareja del jugador',
  bankerPair: 'Pareja de la banca',
};
