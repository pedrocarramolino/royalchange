import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '@/data/auth';
import { useWallet } from '@/data/wallet';
import type { EconomyError } from '@/domain/economy';
import { applyPoker, botDecide, createTable, POKER_TABLES, refillBots, startHand, type PokerAction, type PokerRules, type PokerState } from '@/engine/poker';
import { play } from '@/audio/sound';
import { economyNotice, loadSession, saveSession, clearSession, wait } from '../shared/session';

export const HERO = 0;
const BOT_THINKING_MS = 800;
const STREET_PAUSE_MS = 700;

/**
 * Sesión de póker guardada en el dispositivo.
 * - accounted: fichas del jugador ya apostadas en el monedero durante esta mano.
 * - stackAtStart: fichas del jugador en la mesa al empezar la mano.
 * - settled: la mano ya está liquidada en el monedero.
 */
interface PokerSession {
  table: PokerState;
  accounted: number;
  roundId: string | null;
  settled: boolean;
  stackAtStart: number;
}

/**
 * Mesa de póker: la mano, los bots y la contabilidad. Cada ficha que el jugador pone en el bote es
 * una apuesta en el monedero; la mano se liquida al terminar (o en cuanto el jugador se retira).
 */
export function usePokerTable() {
  const uid = useAuth((s) => (s.state.status === 'signedIn' ? s.state.user.uid : null));
  const alias = useAuth((s) => (s.state.status === 'signedIn' ? (s.state.user.profile?.alias ?? '') : ''));
  const walletState = useWallet((s) => s.state);
  const { placeBet, settleRound } = useWallet();
  const [table, setTable] = useState<PokerState | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const session = useRef<PokerSession | null>(null);
  const balance = useRef(0);
  const busyRef = useRef(false);
  const botTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const restored = useRef(false);

  balance.current = walletState.status === 'ready' ? walletState.wallet.balance : 0;

  const save = useCallback(() => {
    if (uid && session.current) saveSession(uid, 'Poker', session.current);
  }, [uid]);

  const commit = useCallback(
    (next: PokerState) => {
      session.current = { ...(session.current ?? { accounted: 0, roundId: null, settled: true, stackAtStart: 0 }), table: next };
      save();
      setTable(next);
    },
    [save],
  );

  const notify = (error: EconomyError) => setNotice(economyNotice(error));

  const settle = useCallback(
    async (state: PokerState) => {
      const current = session.current;
      if (!current) return;
      if (current.accounted === 0) {
        session.current = { ...current, settled: true };
        save();
        return;
      }
      const hero = state.seats[HERO]!;
      const payout = hero.folded || state.phase !== 'handOver' ? 0 : hero.stack - current.stackAtStart + current.accounted;
      const result = await settleRound(payout);
      if (result.ok || result.error.type === 'noOpenRound') {
        session.current = { ...session.current!, settled: true, roundId: null };
        save();
      } else {
        setNotice('No se pudo liquidar la mano todavía. Se reintentará en la próxima.');
      }
    },
    [save, settleRound],
  );

  /** Cobra en el monedero lo que el jugador ha puesto en el bote y liquida si su mano terminó. */
  const account = useCallback(
    async (next: PokerState): Promise<boolean> => {
      const current = session.current;
      if (!current) return false;
      const delta = next.seats[HERO]!.committed - current.accounted;
      if (delta > 0) {
        const placed = await placeBet('Poker', delta);
        if (!placed.ok) {
          notify(placed.error);
          return false;
        }
        session.current = { ...current, accounted: current.accounted + delta, roundId: placed.wallet.openRound?.id ?? current.roundId };
        play('chip');
      }
      const hero = next.seats[HERO]!;
      if ((next.phase === 'handOver' || hero.folded) && session.current && !session.current.settled) await settle(next);
      return true;
    },
    [placeBet, settle],
  );

  const act = useCallback(async (block: () => Promise<void>) => {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setNotice(null);
    try {
      await block();
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  }, []);

  // Turno de los bots: piensan un momento y actúan, hasta que le toca al jugador o acaba la mano.
  useEffect(() => {
    if (!table || busy) return;
    if (botTimer.current) clearTimeout(botTimer.current);
    if (table.phase === 'handOver') {
      if (session.current && !session.current.settled) void act(() => settle(table));
      return;
    }
    if (table.phase !== 'betting' || table.toAct === null || table.toAct === HERO) return;
    const seat = table.toAct;
    botTimer.current = setTimeout(() => {
      const current = session.current?.table;
      if (!current || current.toAct !== seat) return;
      const action = botDecide(current, seat);
      const next = applyPoker(current, seat, action);
      if (!next) return;
      if (action.type !== 'fold' && action.type !== 'check') play('chip');
      const streetChanged = next.street !== current.street || next.phase === 'handOver';
      if (streetChanged) {
        commit(next);
        if (next.board.length > current.board.length) play('card');
      } else {
        commit(next);
      }
    }, BOT_THINKING_MS + (table.street !== 'preflop' && table.seats.every((s) => s.bet === 0) ? STREET_PAUSE_MS : 0));
    return () => {
      if (botTimer.current) clearTimeout(botTimer.current);
    };
  }, [table, busy, commit, settle, act]);

  // Reanudar al entrar.
  useEffect(() => {
    if (restored.current || !uid || walletState.status === 'loading') return;
    restored.current = true;
    void (async () => {
      const wallet = walletState.status === 'ready' ? walletState.wallet : null;
      if (!wallet) {
        setNotice('No se puede acceder a tus fichas ahora mismo.');
        setLoading(false);
        return;
      }
      const saved = loadSession<PokerSession>(uid, 'Poker');
      const open = wallet.openRound?.game === 'Poker' ? wallet.openRound : null;
      const owned = saved && open && saved.roundId === open.id && !saved.settled ? saved : null;
      if (owned && owned.table.phase === 'betting') {
        session.current = owned;
      } else if (owned) {
        session.current = owned;
        await settle(owned.table);
      } else if (open) {
        // Fichas en una mano de póker sin su estado (otro dispositivo, datos borrados): se pierden.
        await settleRound(0);
        setNotice('La mano anterior no se pudo reanudar y se ha cerrado.');
        session.current = saved ? abandon(saved) : null;
      } else if (saved && saved.table.phase === 'betting' && (saved.settled || saved.accounted === 0)) {
        session.current = saved;
      } else if (saved && saved.table.phase === 'betting') {
        session.current = abandon(saved);
      } else {
        session.current = saved;
      }
      save();
      setTable(session.current?.table ?? null);
      setLoading(false);
    })();
  }, [uid, walletState, settle, settleRound, save]);

  const dealNextHand = useCallback(() => {
    const current = session.current?.table;
    if (!current || current.phase === 'betting') return;
    void act(async () => {
      // La pila en la mesa nunca supera el saldo real (pudo bajar jugando en otro dispositivo).
      const unit = current.rules.chipUnit;
      const refilled = refillBots(current);
      refilled.seats = refilled.seats.map((s, i) => (i === HERO ? { ...s, stack: Math.min(s.stack, Math.floor(balance.current / unit) * unit) } : s));
      if (refilled.seats[HERO]!.stack < refilled.rules.bigBlind) {
        commit(refilled);
        return;
      }
      const started = startHand(refilled);
      if (!started) return;
      session.current = { table: refilled, accounted: 0, roundId: null, settled: false, stackAtStart: refilled.seats[HERO]!.stack };
      play('card');
      play('card', 120);
      if (!(await account(started))) {
        session.current = { ...session.current, settled: true };
        return;
      }
      commit(started);
    });
  }, [act, account, commit]);

  const heroAction = useCallback(
    (action: PokerAction) => {
      const current = session.current?.table;
      if (!current || current.toAct !== HERO || current.phase !== 'betting') return;
      void act(async () => {
        const next = applyPoker(current, HERO, action);
        if (!next) return;
        if (!(await account(next))) return;
        if (next.board.length > current.board.length) play('card');
        commit(next);
      });
    },
    [act, account, commit],
  );

  const sitDown = useCallback(
    (tableIndex: number, buyIn: number) => {
      const rules: PokerRules = POKER_TABLES[tableIndex]!;
      if (buyIn < rules.minBuyIn || buyIn > balance.current) {
        setNotice('No tienes fichas suficientes para sentarte en esta mesa.');
        return;
      }
      const created = createTable(rules, alias || 'Tú', buyIn);
      session.current = { table: created, accounted: 0, roundId: null, settled: true, stackAtStart: buyIn };
      save();
      setTable(created);
      setTimeout(() => dealNextHand(), 300);
    },
    [alias, save, dealNextHand],
  );

  const standUp = useCallback(() => {
    const current = session.current?.table;
    if (current && current.phase === 'betting' && current.seats[HERO]!.hole.length > 0 && !current.seats[HERO]!.folded) return;
    if (botTimer.current) clearTimeout(botTimer.current);
    session.current = null;
    if (uid) clearSession(uid, 'Poker');
    setTable(null);
  }, [uid]);

  const rebuy = useCallback(() => {
    const current = session.current?.table;
    if (!current || current.phase === 'betting') return;
    const rules = current.rules;
    const target = Math.min(rules.maxBuyIn, Math.floor(balance.current / rules.chipUnit) * rules.chipUnit);
    if (target < rules.minBuyIn) {
      setNotice('No tienes fichas suficientes para recomprar.');
      return;
    }
    commit({ ...current, seats: current.seats.map((s, i) => (i === HERO ? { ...s, stack: Math.max(s.stack, target) } : s)) });
  }, [commit]);

  return { table, loading, busy, notice, dismissNotice: () => setNotice(null), sitDown, standUp, rebuy, dealNextHand, heroAction, wait };
}

/** Mano a medias que no puede continuar: cada uno recupera lo que puso y se espera la siguiente. */
function abandon(saved: PokerSession): PokerSession {
  const t = saved.table;
  const seats = t.seats.map((s, i) =>
    i === HERO
      ? { ...s, stack: saved.stackAtStart - saved.accounted, hole: [], bet: 0, committed: 0, folded: false }
      : { ...s, stack: s.stack + s.committed, hole: [], bet: 0, committed: 0, folded: false },
  );
  return { table: { ...t, seats, phase: 'waiting', toAct: null, board: [], awards: [] }, accounted: 0, roundId: null, settled: true, stackAtStart: 0 };
}
