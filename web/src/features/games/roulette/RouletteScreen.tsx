import { useRef, useState } from 'react';
import { useWallet, useReadyWallet } from '@/data/wallet';
import { useSettings } from '@/data/settings';
import { ROULETTE_RULES, spinRoulette, type RouletteBet, type RouletteSpin } from '@/engine/roulette';
import { chips, grouped } from '@/lib/format';
import { play, resultSound, BIG_WIN_MULTIPLIER } from '@/audio/sound';
import { Button } from '@/ui/Button';
import { Celebration, ResultBanner } from '../shared/Celebration';
import { ChipRack } from '../shared/ChipRack';
import { TableAction } from '../shared/TableAction';
import { IconRepeat, IconTrash, IconUndo } from '@/ui/icons';
import { GameShell, TableNotice, useTableSize } from '../shared/GameShell';
import { economyNotice, useHoldProgressEvents } from '../shared/session';
import { RouletteBoard } from './RouletteBoard';
import { RouletteWheel, type WheelSpin } from './RouletteWheel';
import { COLOR_NAME, colorOf } from './wheel';

const CHIPS = [10, 50, 100, 500, 1000, 5000];

export default function RouletteScreen() {
  const wallet = useReadyWallet();
  const playInstantRound = useWallet((s) => s.playInstantRound);
  const reducedMotion = useSettings((s) => s.reducedMotion);
  const table = useTableSize();
  const [chip, setChip] = useState(100);
  const [bets, setBets] = useState<Map<RouletteBet, number>>(new Map());
  const [history, setHistory] = useState<Map<RouletteBet, number>[]>([]);
  const [lastBets, setLastBets] = useState<Map<RouletteBet, number> | null>(null);
  const [spinning, setSpinning] = useState(false);
  const [busy, setBusy] = useState(false);
  const [wheelSpin, setWheelSpin] = useState<WheelSpin | null>(null);
  const [result, setResult] = useState<RouletteSpin | null>(null);
  const [showResult, setShowResult] = useState(false);
  const [recent, setRecent] = useState<number[]>([]);
  const [notice, setNotice] = useState<string | null>(null);
  const [celebrate, setCelebrate] = useState<number | null>(null);
  const pending = useRef<RouletteSpin | null>(null);
  useHoldProgressEvents(spinning);

  const total = [...bets.values()].reduce((s, v) => s + v, 0);
  const balance = wallet?.balance ?? 0;
  const canBet = !spinning && !busy;

  const place = (bet: RouletteBet) => {
    if (!canBet) return;
    // Al apostar tras una tirada, el tapete se limpia.
    const base = showResult ? new Map<RouletteBet, number>() : bets;
    const nextTotal = (showResult ? 0 : total) + chip;
    if (nextTotal > ROULETTE_RULES.maximumTotalBet) {
      setNotice(`El máximo por tirada es de ${chips(ROULETTE_RULES.maximumTotalBet)}.`);
      return;
    }
    if (nextTotal > balance) {
      setNotice('No tienes fichas suficientes para esa apuesta.');
      return;
    }
    setHistory((h) => [...h, base]);
    const next = new Map(base);
    next.set(bet, (next.get(bet) ?? 0) + chip);
    setBets(next);
    setShowResult(false);
    setResult(null);
    play('chip');
  };

  const undo = () => {
    const previous = history[history.length - 1];
    if (!previous) return;
    setHistory((h) => h.slice(0, -1));
    setBets(previous);
  };

  const clear = () => {
    if (!bets.size) return;
    setHistory((h) => [...h, bets]);
    setBets(new Map());
  };

  const repeat = () => {
    if (!lastBets) return;
    const amount = [...lastBets.values()].reduce((s, v) => s + v, 0);
    if (amount > balance) {
      setNotice('No tienes fichas suficientes para repetir la apuesta.');
      return;
    }
    setHistory((h) => [...h, bets]);
    setBets(new Map(lastBets));
    setShowResult(false);
    setResult(null);
    play('chip');
  };

  const spin = async () => {
    if (!canBet || bets.size === 0) return;
    setNotice(null);
    const placed = [...bets.entries()].map(([bet, stake]) => ({ bet, stake }));
    const outcome = spinRoulette(placed);
    if (!outcome.ok) {
      setNotice(outcome.error === 'aboveTableMaximum' ? `El máximo por tirada es de ${chips(ROULETTE_RULES.maximumTotalBet)}.` : 'Revisa tus apuestas.');
      return;
    }
    setBusy(true);
    // El resultado se contabiliza antes de animarlo.
    const booked = await playInstantRound('Roulette', outcome.spin.totalStake, outcome.spin.totalPayout);
    setBusy(false);
    if (!booked.ok) {
      setNotice(economyNotice(booked.error));
      return;
    }
    pending.current = outcome.spin;
    setLastBets(new Map(bets));
    setSpinning(true);
    setShowResult(false);
    setCelebrate(null);
    setWheelSpin({ id: Date.now(), number: outcome.spin.number });
    play('spin');
  };

  const onSettled = () => {
    const spin = pending.current;
    if (!spin) return;
    setSpinning(false);
    setResult(spin);
    setShowResult(true);
    setRecent((r) => [spin.number, ...r].slice(0, 12));
    setHistory([]);
    const net = spin.totalPayout - spin.totalStake;
    const big = spin.totalPayout >= BIG_WIN_MULTIPLIER * spin.totalStake;
    const sound = resultSound(net, big);
    if (sound) play(sound);
    if (big && net > 0) setCelebrate(Date.now());
  };

  // Medidas: la rueda a la izquierda, el tapete ocupa el resto.
  const tableHeight = Math.max(180, table.height - 8);
  const wheelSize = Math.min(tableHeight - 8, table.width * 0.3, 340);
  const boardWidth = Math.max(260, Math.min(table.width - wheelSize - 48, 900));
  const payouts = showResult && result ? new Map(result.results.map((r) => [r.bet, r.payout])) : null;
  const net = result ? result.totalPayout - result.totalStake : 0;
  // Mesa estrecha (iPhone en vertical o pequeño): acciones con icono y fichas más pequeñas.
  const compact = table.width < 720;
  const showTotal = table.width >= (compact ? 600 : 800);

  return (
    <GameShell
      title="Ruleta"
      notice={notice && <TableNotice onDismiss={() => setNotice(null)}>{notice}</TableNotice>}
      controls={
        <div className="flex h-[68px] items-center gap-2 px-3">
          <ChipRack selected={chip} onSelect={setChip} size={compact ? 32 : 38} values={CHIPS} disabled={!canBet} />
          {showTotal ? (
            <div className="mx-1 min-w-0 flex-1 text-center">
              <p className="text-[11px] font-semibold tracking-wider text-mute uppercase">Apuesta total</p>
              <p className="tabular font-bold text-gold-light">{chips(showResult ? 0 : total)}</p>
            </div>
          ) : (
            <div className="flex-1" />
          )}
          <TableAction label="Deshacer" icon={<IconUndo className="size-5" />} compact={compact} disabled={!canBet || history.length === 0 || showResult} onClick={undo} />
          <TableAction label="Borrar" icon={<IconTrash className="size-5" />} compact={compact} disabled={!canBet || bets.size === 0 || showResult} onClick={clear} />
          <TableAction label="Repetir" icon={<IconRepeat className="size-5" />} compact={compact} disabled={!canBet || !lastBets || (bets.size > 0 && !showResult)} onClick={repeat} />
          <Button className={compact ? 'min-w-24' : 'min-w-28'} loading={busy || spinning} disabled={bets.size === 0 || showResult} onClick={() => void spin()}>
            Girar
          </Button>
        </div>
      }
    >
      <div className="flex h-full items-center justify-center gap-4 px-3 pb-1">
        <div className="relative flex shrink-0 flex-col items-center">
          <RouletteWheel size={wheelSize} spin={wheelSpin} reducedMotion={reducedMotion} onSettled={onSettled} />
          {recent.length > 0 && (
            <div className="absolute -bottom-1 left-1/2 flex -translate-x-1/2 gap-1" aria-label={`Últimos números: ${recent.join(', ')}`}>
              {recent.slice(0, 6).map((n, i) => (
                <span
                  key={i}
                  className={`grid size-6 place-items-center rounded-full text-[11px] font-bold text-white ring-1 ring-gold/50 ${i === 0 ? 'scale-110' : 'opacity-80'}`}
                  style={{ background: n === 0 ? '#0f7a4f' : colorOf(n) === 'red' ? '#b3263b' : '#15171d' }}
                >
                  {n}
                </span>
              ))}
            </div>
          )}
        </div>
        <div className="relative">
          <RouletteBoard width={boardWidth} height={tableHeight} bets={showResult && result ? new Map(result.results.map((r) => [r.bet, r.stake])) : bets} winning={showResult && result ? result.number : null} payouts={payouts} disabled={!canBet} onPlace={place} />
          {showResult && result && (
            <div className="pointer-events-none absolute inset-x-0 top-[38%] flex -translate-y-1/2 justify-center">
              <ResultBanner
                net={net}
                big={net > 0}
                label={`${result.number} ${COLOR_NAME[colorOf(result.number)]} · ${net > 0 ? `ganas ${grouped(net)}` : net < 0 ? `pierdes ${grouped(-net)}` : 'sin cambios'}`}
              />
            </div>
          )}
        </div>
      </div>
      <Celebration trigger={celebrate} />
    </GameShell>
  );
}
