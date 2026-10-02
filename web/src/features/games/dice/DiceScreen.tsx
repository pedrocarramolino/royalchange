import { useEffect, useRef, useState } from 'react';
import { motion } from 'motion/react';
import { useWallet, useReadyWallet } from '@/data/wallet';
import { useSettings } from '@/data/settings';
import { DICE_RULES, diceBetName, isDouble, multiplierTenths, multiplierText, rollSum, throwDice, type DiceBet, type DiceThrow } from '@/engine/dice';
import { chips, grouped } from '@/lib/format';
import { play, resultSound, BIG_WIN_MULTIPLIER } from '@/audio/sound';
import { Button } from '@/ui/Button';
import { ChipStack } from '@/ui/Chip';
import { Die } from '@/features/lobby/GameArt';
import { Celebration, ResultBanner } from '../shared/Celebration';
import { ChipRack } from '../shared/ChipRack';
import { TableAction } from '../shared/TableAction';
import { IconRepeat, IconTrash, IconUndo } from '@/ui/icons';
import { GameShell, TableNotice, useTableSize } from '../shared/GameShell';
import { economyNotice, useHoldProgressEvents } from '../shared/session';

const CHIPS = [10, 50, 100, 500, 1000, 5000];
const TOP: { bet: DiceBet; title: string; subtitle?: string }[] = [
  { bet: 'low', title: 'Menor', subtitle: '2 – 6' },
  { bet: 'seven', title: 'Siete' },
  { bet: 'high', title: 'Mayor', subtitle: '8 – 12' },
  { bet: 'doubles', title: 'Dobles' },
];
const SUMS: DiceBet[][] = [
  ['sum:2', 'sum:3', 'sum:4', 'sum:5', 'sum:6'],
  ['sum:8', 'sum:9', 'sum:10', 'sum:11', 'sum:12'],
];
const ROLL_MS = 1500;

export default function DiceScreen() {
  const wallet = useReadyWallet();
  const playInstantRound = useWallet((s) => s.playInstantRound);
  const reducedMotion = useSettings((s) => s.reducedMotion);
  const table = useTableSize();
  const [chip, setChip] = useState(50);
  const [bets, setBets] = useState<Map<DiceBet, number>>(new Map());
  const [history, setHistory] = useState<Map<DiceBet, number>[]>([]);
  const [lastBets, setLastBets] = useState<Map<DiceBet, number> | null>(null);
  const [rolling, setRolling] = useState(false);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<DiceThrow | null>(null);
  const [showResult, setShowResult] = useState(false);
  const [throwId, setThrowId] = useState(0);
  const [recent, setRecent] = useState<number[]>([]);
  const [notice, setNotice] = useState<string | null>(null);
  const [celebrate, setCelebrate] = useState<number | null>(null);
  useHoldProgressEvents(rolling);

  const total = [...bets.values()].reduce((s, v) => s + v, 0);
  const balance = wallet?.balance ?? 0;
  const canBet = !rolling && !busy;

  const place = (bet: DiceBet) => {
    if (!canBet) return;
    const base = showResult ? new Map<DiceBet, number>() : bets;
    const nextTotal = (showResult ? 0 : total) + chip;
    if (nextTotal > DICE_RULES.maximumTotalBet) return setNotice(`El máximo por tirada es de ${chips(DICE_RULES.maximumTotalBet)}.`);
    if (nextTotal > balance) return setNotice('No tienes fichas suficientes para esa apuesta.');
    setHistory((h) => [...h, base]);
    const next = new Map(base);
    next.set(bet, (next.get(bet) ?? 0) + chip);
    setBets(next);
    setShowResult(false);
    play('chip');
  };

  const roll = async () => {
    if (!canBet || bets.size === 0) return;
    setNotice(null);
    const outcome = throwDice([...bets.entries()].map(([bet, stake]) => ({ bet, stake })));
    if (!outcome.ok) return setNotice('Revisa tus apuestas.');
    setBusy(true);
    // El resultado se contabiliza antes de animarlo.
    const booked = await playInstantRound('Dice', outcome.result.totalStake, outcome.result.totalPayout);
    setBusy(false);
    if (!booked.ok) return setNotice(economyNotice(booked.error));
    setLastBets(new Map(bets));
    setResult(outcome.result);
    setShowResult(false);
    setCelebrate(null);
    setRolling(true);
    setThrowId(Date.now());
    play('dice');
    setTimeout(
      () => {
        setRolling(false);
        setShowResult(true);
        setHistory([]);
        setRecent((r) => [rollSum(outcome.result.roll), ...r].slice(0, 10));
        const net = outcome.result.totalPayout - outcome.result.totalStake;
        const big = outcome.result.totalPayout >= BIG_WIN_MULTIPLIER * outcome.result.totalStake;
        const sound = resultSound(net, big);
        if (sound) play(sound);
        if (big && net > 0) setCelebrate(Date.now());
      },
      reducedMotion ? 300 : ROLL_MS,
    );
  };

  const tableHeight = Math.max(180, table.height);
  const traySize = Math.min(tableHeight - 12, table.width * 0.3, 300);
  const shownBets = showResult && result ? new Map(result.results.map((r) => [r.bet, r.stake])) : bets;
  const payouts = showResult && result ? new Map(result.results.map((r) => [r.bet, r.payout])) : null;
  const net = result ? result.totalPayout - result.totalStake : 0;
  // Mesa estrecha (iPhone en vertical o pequeño): acciones con icono y fichas más pequeñas.
  const compact = table.width < 720;
  const showTotal = table.width >= (compact ? 600 : 800);
  const undo = () => {
    const previous = history[history.length - 1];
    if (!previous) return;
    setHistory((h) => h.slice(0, -1));
    setBets(previous);
  };
  const clear = () => {
    setHistory((h) => [...h, bets]);
    setBets(new Map());
  };
  const repeat = () => {
    if (!lastBets) return;
    const amount = [...lastBets.values()].reduce((s, v) => s + v, 0);
    if (amount > balance) return setNotice('No tienes fichas suficientes para repetir la apuesta.');
    setHistory((h) => [...h, bets]);
    setBets(new Map(lastBets));
    setShowResult(false);
    play('chip');
  };

  return (
    <GameShell
      title="Dados"
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
          <Button className={compact ? 'min-w-24' : 'min-w-28'} loading={busy || rolling} disabled={bets.size === 0 || showResult} onClick={() => void roll()}>
            Tirar
          </Button>
        </div>
      }
    >
      <div className="flex h-full items-center justify-center gap-4 px-3 pb-1">
        <div className="relative shrink-0">
          <DiceTray size={traySize} roll={result?.roll ?? { first: 5, second: 2 }} throwId={throwId} rolling={rolling} reducedMotion={reducedMotion} />
          {recent.length > 0 && (
            <div className="absolute -bottom-1 left-1/2 flex -translate-x-1/2 gap-1" aria-label={`Últimas sumas: ${recent.join(', ')}`}>
              {recent.slice(0, 7).map((n, i) => (
                <span key={i} className={`grid size-6 place-items-center rounded-full bg-black/60 text-[11px] font-bold text-ivory ring-1 ring-gold/40 ${i === 0 ? 'scale-110 text-gold-light' : 'opacity-75'}`}>
                  {n}
                </span>
              ))}
            </div>
          )}
        </div>
        <div className="relative flex min-w-0 flex-1 flex-col gap-2" style={{ maxWidth: 620, height: Math.min(tableHeight - 20, 300) }}>
          <div className="grid flex-[1.25] grid-cols-4 gap-2">
            {TOP.map(({ bet, title, subtitle }) => (
              <BetBox key={bet} bet={bet} title={title} subtitle={subtitle} stake={shownBets.get(bet)} payout={payouts?.get(bet)} result={showResult && result ? result : null} disabled={!canBet} onPlace={place} />
            ))}
          </div>
          {SUMS.map((row, i) => (
            <div key={i} className="grid flex-1 grid-cols-5 gap-2">
              {row.map((bet) => (
                <BetBox key={bet} bet={bet} title={bet.slice(4)} stake={shownBets.get(bet)} payout={payouts?.get(bet)} result={showResult && result ? result : null} disabled={!canBet} onPlace={place} />
              ))}
            </div>
          ))}
          {showResult && result && (
            <div className="pointer-events-none absolute inset-x-0 top-1/2 flex -translate-y-1/2 justify-center">
              <ResultBanner
                net={net}
                big={net > 0}
                label={`${result.roll.first} y ${result.roll.second} · suma ${rollSum(result.roll)}${isDouble(result.roll) ? ' · dobles' : ''} · ${net > 0 ? `ganas ${grouped(net)}` : net < 0 ? `pierdes ${grouped(-net)}` : 'sin cambios'}`}
              />
            </div>
          )}
        </div>
      </div>
      <Celebration trigger={celebrate} />
    </GameShell>
  );
}

function BetBox({
  bet,
  title,
  subtitle,
  stake,
  payout,
  result,
  disabled,
  onPlace,
}: {
  bet: DiceBet;
  title: string;
  subtitle?: string | undefined;
  stake: number | undefined;
  payout: number | undefined;
  result: DiceThrow | null;
  disabled: boolean;
  onPlace: (bet: DiceBet) => void;
}) {
  const won = result !== null && payout !== undefined && payout > 0;
  const winningBox = result !== null && result.results.length >= 0 && boxWins(bet, result);
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => onPlace(bet)}
      aria-label={`Apostar a ${diceBetName(bet)}, paga ${multiplierText(multiplierTenths(bet))}${stake ? `. Tienes ${chips(stake)}` : ''}`}
      className={`relative flex flex-col items-center justify-center rounded-xl border-2 text-center transition-colors ${winningBox ? 'border-gold-light bg-gold/20' : 'border-gold-light/45 hover:bg-white/5'} ${disabled ? '' : 'active:bg-white/10'}`}
    >
      <span className="felt-print text-[13px] leading-tight font-bold text-gold-light/90">{title}</span>
      {subtitle && <span className="felt-print text-[10px] text-gold-light/70">{subtitle}</span>}
      <span className="tabular mt-0.5 text-[10px] font-semibold text-gold-light/60">{multiplierText(multiplierTenths(bet))}</span>
      {stake ? (
        <motion.span
          className="absolute -top-2 -right-2 flex items-center gap-0.5"
          initial={{ scale: 0.5, opacity: 0 }}
          animate={{ scale: 1, opacity: result && !won ? 0.3 : 1 }}
        >
          <ChipStack amount={won ? payout! : stake} size={24} max={3} />
          <span className="tabular rounded bg-black/60 px-1 text-[10px] font-bold text-gold-light">{grouped(won ? payout! : stake)}</span>
        </motion.span>
      ) : null}
    </button>
  );
}

function boxWins(bet: DiceBet, result: DiceThrow): boolean {
  const sum = rollSum(result.roll);
  if (bet === 'low') return sum <= 6;
  if (bet === 'high') return sum >= 8;
  if (bet === 'seven') return sum === 7;
  if (bet === 'doubles') return isDouble(result.roll);
  return sum === Number(bet.slice(4));
}

/** Bandeja forrada de cuero donde ruedan los dados: entran por la derecha, rebotan y se paran. */
function DiceTray({ size, roll, throwId, rolling, reducedMotion }: { size: number; roll: { first: number; second: number }; throwId: number; rolling: boolean; reducedMotion: boolean }) {
  const [faces, setFaces] = useState<[number, number]>([roll.first, roll.second]);
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);
  useEffect(() => {
    if (!rolling) {
      setFaces([roll.first, roll.second]);
      return;
    }
    // Las caras cambian mientras ruedan.
    timer.current = setInterval(() => setFaces([1 + Math.floor(Math.random() * 6), 1 + Math.floor(Math.random() * 6)]), 90);
    return () => {
      if (timer.current) clearInterval(timer.current);
    };
  }, [rolling, roll.first, roll.second]);

  const die = size * 0.27;
  const path = (i: number) =>
    reducedMotion
      ? { x: 0, y: 0, rotate: 0 }
      : {
          x: [size * 0.7, size * 0.15, -size * 0.05 + i * 8, 0],
          y: [-size * 0.25 + i * 30, size * 0.18 - i * 20, -size * 0.04, 0],
          rotate: [0, 260 + i * 70, 340 + i * 40, i === 0 ? -12 : 14],
        };
  return (
    <div
      className="relative grid place-items-center rounded-[28px] bg-[radial-gradient(ellipse_at_50%_40%,#5e1220,#3b0b15_60%,#22060c)] shadow-[0_18px_40px_-10px_rgb(0_0_0/0.85),inset_0_0_0_3px_rgb(212_175_106/0.7),inset_0_0_0_9px_#1b100a,inset_0_10px_30px_rgb(0_0_0/0.6)]"
      style={{ width: size, height: size * 0.82 }}
      aria-label={`Dados: ${roll.first} y ${roll.second}`}
      role="img"
    >
      <div className="flex gap-[6%]">
        {[0, 1].map((i) => (
          <motion.div key={`${throwId}-${i}`} initial={false} animate={rolling ? path(i) : { x: 0, y: 0, rotate: i === 0 ? -12 : 14 }} transition={{ duration: ROLL_MS / 1000, times: [0, 0.4, 0.75, 1], ease: 'easeOut' }} className="drop-shadow-[0_8px_8px_rgb(0_0_0/0.55)]">
            <Die value={faces[i]!} size={die} />
          </motion.div>
        ))}
      </div>
    </div>
  );
}
