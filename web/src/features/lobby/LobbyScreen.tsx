import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { AnimatePresence, m as motion } from 'motion/react';
import { EASE_OUT, SPRING } from '@/ui/motion';
import { useAuth } from '@/data/auth';
import { useWallet, useReadyWallet } from '@/data/wallet';
import { EconomyRules, rescueStatus } from '@/domain/economy';
import { claimable, dailyBonusStatus, dailyReward, levelProgress, MAX_REWARD_DAY, TITLE_NAMES } from '@/domain/progression';
import { chips, grouped, remaining } from '@/lib/format';
import { localEpochDay } from '@/lib/time';
import { Avatar } from '@/ui/Avatar';
import { Button } from '@/ui/Button';
import { Dialog } from '@/ui/Dialog';
import { play } from '@/audio/sound';
import { Chip } from '@/ui/Chip';
import { useCountUp } from '@/ui/ChipBalance';
import { IconClose, IconFlame, IconGift, IconMail } from '@/ui/icons';
import { DailyWheel } from './DailyWheel';
import { BaccaratArt, BlackjackArt, DiceArt, PokerArt, RouletteArt, SlotsArt, PlinkoArt, ScratchArt, VideoPokerArt } from './GameArt';

const GAMES = [
  { path: '/mesa/blackjack', name: 'Blackjack', description: 'Llega a 21 sin pasarte y gana al crupier.', Art: BlackjackArt },
  { path: '/mesa/ruleta', name: 'Ruleta', description: 'Ruleta europea de 37 números.', Art: RouletteArt },
  { path: '/mesa/slots', name: 'Slots', description: 'Cinco rodillos y líneas de premio.', Art: SlotsArt },
  { path: '/mesa/dados', name: 'Dados', description: 'Mayor, menor y combinaciones.', Art: DiceArt },
  { path: '/mesa/poker', name: 'Póker', description: 'Texas Hold’em contra bots.', Art: PokerArt },
  { path: '/mesa/baccarat', name: 'Baccarat', description: 'Jugador o banca: gana el que más se acerque a 9.', Art: BaccaratArt },
  { path: '/mesa/plinko', name: 'Plinko', description: 'Suelta la bola y mira dónde cae: hasta ×29.', Art: PlinkoArt },
  { path: '/mesa/rasca', name: 'Rasca y gana', description: 'Rasca el boleto con el dedo: tres iguales ganan hasta ×100.', Art: ScratchArt },
  { path: '/mesa/video-poker', name: 'Video póker', description: 'Jacks or Better: guarda cartas, cambia el resto y busca la escalera real.', Art: VideoPokerArt },
];

/** Hora actual que avanza sola (cuenta atrás de la recarga y cambio de día del bono). */
function useNow(intervalMs = 30_000): number {
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), intervalMs);
    const onFocus = () => setNow(Date.now());
    window.addEventListener('focus', onFocus);
    return () => {
      clearInterval(timer);
      window.removeEventListener('focus', onFocus);
    };
  }, [intervalMs]);
  return now;
}

export function LobbyScreen() {
  const auth = useAuth((s) => s.state);
  const refreshUser = useAuth((s) => s.refreshUser);
  const walletState = useWallet((s) => s.state);
  const wallet = useReadyWallet();
  const navigate = useNavigate();
  const now = useNow();
  const [welcomeDismissed, setWelcomeDismissed] = useState(false);
  // Mientras gira la ruleta diaria, el saldo visible espera al resultado.
  const [heldBalance, setHeldBalance] = useState<number | null>(null);

  // El jugador puede haber confirmado su email fuera de la app.
  useEffect(() => {
    void refreshUser();
  }, [refreshUser]);

  const user = auth.status === 'signedIn' ? auth.user : null;
  const profile = user?.profile;

  return (
    <div className="safe-top safe-px-4 mx-auto w-full max-w-lg pb-8">
      <header className="flex items-center gap-3 pt-5">
        {profile && <Avatar id={profile.avatar} size={46} />}
        <div className="min-w-0 flex-1">
          <p className="truncate font-display text-xl font-semibold text-ivory">Hola, {profile?.alias}</p>
          <p className="text-sm text-ivory-dim">Elige tu mesa</p>
        </div>
      </header>

      <BalanceHero state={walletState.status} balance={heldBalance ?? wallet?.balance ?? 0} xp={wallet?.xp ?? 0} onProgress={() => navigate('/progreso')} />

      <div className="mt-4 flex flex-col gap-3">
        <AnimatePresence initial={false}>
        {user && !user.emailVerified && <VerificationCard key="verificar" />}
        {wallet && wallet.seq === 1 && !welcomeDismissed && (
          <Notice key="bienvenida" tone="felt" icon={<Chip value={1000} size={28} label="" />} onDismiss={() => setWelcomeDismissed(true)} dismissLabel="Entendido">
            ¡Bienvenido a la mesa! Te regalamos {chips(EconomyRules.WELCOME_GRANT)} para empezar. Son virtuales: no se compran, no se canjean y no tienen valor
            monetario.
          </Notice>
        )}
        {wallet && <RescueCard key="recarga" now={now} />}
        {wallet && <DailyBonusCard key="bono" now={now} />}
        {wallet && <DailyBonusDialog key="bono-dialogo" now={now} />}
        {wallet && <DailyWheel key="ruleta" now={now} onHoldBalance={setHeldBalance} />}
        {wallet && claimable(wallet).length > 0 && (
          <Notice key="logros" tone="gold" icon={<IconGift className="size-6 text-gold" />} action={{ label: 'Ver', onClick: () => navigate('/progreso') }}>
            {claimable(wallet).length === 1 ? 'Tienes un logro con recompensa por recoger.' : `Tienes ${claimable(wallet).length} logros con recompensa por recoger.`}
          </Notice>
        )}
        </AnimatePresence>
      </div>

      <h2 className="mt-8 mb-3 text-xs font-bold tracking-[0.2em] text-gold uppercase">Mesas</h2>
      <div className="grid grid-cols-2 gap-3">
        {GAMES.map(({ path, name, description, Art }, i) => (
          <motion.button
            key={path}
            type="button"
            onClick={() => navigate(path)}
            // Se abre a menudo: entrada corta y sutil al llegar a cada tarjeta (una sola vez), con la
            // columna derecha un pelín después.
            initial={{ opacity: 0, transform: 'translateY(12px)' }}
            whileInView={{ opacity: 1, transform: 'translateY(0px)' }}
            viewport={{ once: true, amount: 0.25 }}
            transition={{ duration: 0.3, ease: EASE_OUT, delay: i < 4 ? 0.04 * i : 0.05 * (i % 2) }}
            whileTap={{ scale: 0.97 }}
            className={`group relative isolate flex flex-col overflow-hidden rounded-3xl text-left shadow-[0_14px_30px_-14px_rgb(0_0_0/0.9)] ring-1 ring-gold/25 transition-shadow duration-200 ease-out [@media(hover:hover)_and_(pointer:fine)]:hover:shadow-[0_18px_34px_-12px_rgb(0_0_0/0.9),0_0_0_1px_rgb(212_175_106/0.5)] ${(i === GAMES.length - 1 && GAMES.length % 2 === 1) ? 'col-span-2' : ''}`}
            aria-label={`${name}. ${description}`}
          >
            {/* «isolate» y el recorte propio: Safari no siempre recorta las esquinas con contenido girado o animado. */}
            <div className={`felt relative shrink-0 overflow-hidden ${(i === GAMES.length - 1 && GAMES.length % 2 === 1) ? 'h-32' : 'h-36'}`}>
              <div className="size-full transition-transform duration-300 ease-out group-active:scale-[1.05] [@media(hover:hover)_and_(pointer:fine)]:group-hover:scale-[1.05]">
                <Art />
              </div>
            </div>
            {/* Rellena lo que sobra: si la tarjeta de al lado tiene una línea más de texto, no queda hueco arriba (un botón centra su contenido). */}
            <div className="w-full flex-1 bg-gradient-to-b from-ink-2 to-ink-1 px-4 pt-3 pb-4">
              <p className="font-display text-lg font-semibold text-gold-light">{name}</p>
              <p className="mt-0.5 line-clamp-2 text-[13px] leading-snug text-ivory-dim">{description}</p>
            </div>
          </motion.button>
        ))}
      </div>
    </div>
  );
}

function BalanceHero({ state, balance, xp, onProgress }: { state: string; balance: number; xp: number; onProgress: () => void }) {
  const shown = useCountUp(balance);
  const level = levelProgress(xp);
  // Cuando el saldo sube (bono, ruleta diaria, logros), «+N» sube desde la cifra y se desvanece.
  const previous = useRef<number | null>(null);
  const [gain, setGain] = useState<{ id: number; amount: number } | null>(null);
  useEffect(() => {
    if (state !== 'ready') return;
    if (previous.current !== null && balance > previous.current) setGain({ id: Date.now(), amount: balance - previous.current });
    previous.current = balance;
  }, [balance, state]);
  return (
    <section className="relative mt-5 overflow-hidden rounded-3xl ring-1 ring-gold/30" aria-label="Tu saldo y tu nivel">
      <div className="felt px-5 pt-5 pb-4">
        <p className="text-xs font-bold tracking-[0.2em] text-gold-light/80 uppercase">Tu saldo</p>
        {state === 'ready' ? (
          <p className="mt-1 flex items-baseline gap-2">
            <span className="tabular relative font-display text-[40px] leading-none font-bold text-gold-gradient" aria-label={chips(balance)}>
              {grouped(shown)}
              <AnimatePresence>
                {gain && (
                  <motion.span
                    key={gain.id}
                    className="pointer-events-none absolute -top-1 left-full ml-2 font-sans text-base font-bold whitespace-nowrap text-gold-light drop-shadow-[0_2px_4px_rgb(0_0_0/0.6)]"
                    aria-hidden
                    initial={{ opacity: 0, transform: 'translateY(6px)' }}
                    animate={{ opacity: [0, 1, 1, 0], transform: ['translateY(6px)', 'translateY(0px)', 'translateY(-10px)', 'translateY(-16px)'] }}
                    transition={{ duration: 1.4, times: [0, 0.15, 0.7, 1], ease: EASE_OUT }}
                    onAnimationComplete={() => setGain(null)}
                  >
                    +{grouped(gain.amount)}
                  </motion.span>
                )}
              </AnimatePresence>
            </span>
            <span className="text-sm font-semibold text-gold-light/80">fichas</span>
          </p>
        ) : state === 'loading' ? (
          <p className="mt-2 h-10 w-40 animate-pulse rounded-lg bg-white/10" aria-label="Cargando tu saldo" />
        ) : (
          <p className="mt-2 text-[15px] text-ivory">Tu saldo no está disponible ahora mismo. Vuelve a intentarlo en unos minutos.</p>
        )}
        <div className="pointer-events-none absolute top-4 right-4 flex -space-x-3" aria-hidden>
          {[5000, 1000, 100].map((value, i) => (
            <motion.div
              key={value}
              initial={{ opacity: 0, transform: 'translateY(-14px)' }}
              animate={{ opacity: 1, transform: 'translateY(0px)' }}
              transition={{ ...SPRING, delay: 0.1 + i * 0.08 }}
            >
              <Chip value={value} size={44} />
            </motion.div>
          ))}
        </div>
      </div>
      <button type="button" onClick={onProgress} className="block w-full bg-ink-2 px-5 py-3.5 text-left hover:bg-ink-3" aria-label={`Nivel ${level.level}, ${TITLE_NAMES[level.title]}. Ver tu progreso`}>
        <div className="flex items-center justify-between text-sm">
          <span className="font-semibold text-ivory">
            Nivel {level.level} · <span className="text-gold">{TITLE_NAMES[level.title]}</span>
          </span>
          <span className="tabular text-xs text-ivory-dim">
            {level.xpForNextLevel === null ? 'Nivel máximo alcanzado' : `${grouped(level.xpIntoLevel)} / ${grouped(level.xpForNextLevel)} XP`}
          </span>
        </div>
        <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-ink-4">
          {/* Escala horizontal (va por la GPU) en vez de animar el ancho. */}
          <motion.div
            className="h-full w-full origin-left rounded-full metal-gold"
            initial={{ transform: 'scaleX(0)' }}
            animate={{ transform: `scaleX(${level.fraction})` }}
            transition={{ duration: 0.8, ease: EASE_OUT, delay: 0.2 }}
          />
        </div>
      </button>
    </section>
  );
}

function Notice({
  children,
  icon,
  tone,
  action,
  onDismiss,
  dismissLabel,
}: {
  children: ReactNode;
  icon: ReactNode;
  tone: 'felt' | 'gold' | 'plain' | 'ruby';
  action?: { label: string; onClick: () => void; loading?: boolean };
  onDismiss?: () => void;
  dismissLabel?: string;
}) {
  const tones = {
    felt: 'bg-felt-deep/80 ring-emerald/30',
    gold: 'bg-gold/10 ring-gold/35',
    plain: 'bg-ink-2 ring-white/10',
    ruby: 'bg-ruby/15 ring-ruby/40',
  };
  return (
    <motion.div
      className={`flex items-center gap-3 rounded-2xl px-4 py-3 ring-1 ${tones[tone]}`}
      role="status"
      initial={{ opacity: 0, transform: 'translateY(-6px)' }}
      animate={{ opacity: 1, transform: 'translateY(0px)' }}
      exit={{ opacity: 0, transform: 'scale(0.98)', transition: { duration: 0.15, ease: EASE_OUT } }}
      transition={{ duration: 0.25, ease: EASE_OUT }}
    >
      <span className="shrink-0">{icon}</span>
      <p className="min-w-0 flex-1 text-sm leading-snug text-ivory">{children}</p>
      {action && (
        <Button size="sm" variant="secondary" onClick={action.onClick} loading={action.loading}>
          {action.label}
        </Button>
      )}
      {onDismiss && (
        <button type="button" onClick={onDismiss} className="grid size-9 shrink-0 place-items-center rounded-full text-ivory-dim hover:bg-white/10" aria-label={dismissLabel ?? 'Cerrar'}>
          <IconClose className="size-5" />
        </button>
      )}
    </motion.div>
  );
}

function VerificationCard() {
  const sendVerification = useAuth((s) => s.sendVerification);
  const refreshUser = useAuth((s) => s.refreshUser);
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);
  const run = async (task: () => Promise<unknown>) => {
    setBusy(true);
    await task();
    setBusy(false);
  };
  return sent ? (
    <Notice tone="plain" icon={<IconMail className="size-6 text-gold" />} action={{ label: 'Ya lo he confirmado', onClick: () => void run(refreshUser), loading: busy }}>
      Te hemos enviado un enlace de confirmación. Ábrelo y vuelve aquí.
    </Notice>
  ) : (
    <Notice
      tone="plain"
      icon={<IconMail className="size-6 text-gold" />}
      action={{ label: 'Reenviar', onClick: () => void run(async () => (await sendVerification()).ok && setSent(true)), loading: busy }}
    >
      Confirma tu email con el enlace que te enviamos al registrarte.
    </Notice>
  );
}

function RescueCard({ now }: { now: number }) {
  const wallet = useReadyWallet()!;
  const claimRescue = useWallet((s) => s.claimRescue);
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);
  const status = rescueStatus(wallet, now);
  if (status.type === 'notNeeded') return null;
  if (status.type === 'coolingDown') {
    return (
      <Notice tone="plain" icon={<Chip value={10} size={28} label="" />}>
        Tu próxima recarga gratuita de {chips(EconomyRules.RESCUE_GRANT)} estará lista en {remaining(status.availableAtMillis - now)}.
      </Notice>
    );
  }
  return (
    <Notice
      tone="ruby"
      icon={<Chip value={1000} size={28} label="" />}
      action={{
        label: 'Recoger',
        loading: busy,
        onClick: async () => {
          setBusy(true);
          const result = await claimRescue();
          setBusy(false);
          setFailed(!result.ok && result.error.type !== 'rescueNotNeeded');
        },
      }}
    >
      {failed ? 'No se pudo recoger la recarga. Vuelve a intentarlo.' : `Te has quedado sin fichas. Recoge ${chips(EconomyRules.RESCUE_GRANT)} gratis para seguir jugando.`}
    </Notice>
  );
}

/** Bono diario: siete fichas, una por día de racha; la del día de hoy brilla hasta recogerla. */
function DailyBonusCard({ now }: { now: number }) {
  const wallet = useReadyWallet()!;
  const claimDailyBonus = useWallet((s) => s.claimDailyBonus);
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);
  const status = dailyBonusStatus(wallet, localEpochDay(new Date(now)), Date.now());

  const filled = status.type === 'available' ? status.streakDay - 1 : status.type === 'claimedToday' ? status.streakDay : 0;
  const todayIndex = status.type === 'available' ? status.streakDay : -1;

  const claim = async () => {
    setBusy(true);
    const result = await claimDailyBonus();
    setBusy(false);
    setFailed(!result.ok && result.error.type !== 'dailyBonusAlreadyClaimed');
  };

  return (
    <section className="panel rounded-3xl px-4 pt-4 pb-4" aria-label="Bono diario">
      <div className="flex items-center gap-2">
        <IconFlame className="size-5 text-gold" />
        <h3 className="flex-1 font-semibold text-ivory">
          {status.type === 'available' ? `Bono diario · Día ${status.streakDay}` : 'Bono diario'}
        </h3>
        {wallet.dailyStreak > 0 && status.type !== 'available' && (
          <span className="text-xs font-semibold text-gold">{wallet.dailyStreak === 1 ? 'Racha de 1 día' : `Racha de ${wallet.dailyStreak} días`}</span>
        )}
      </div>
      <BonusWeek filled={filled} todayIndex={todayIndex} />
      <p className="mt-3 text-sm text-ivory-dim">
        {failed
          ? 'No se pudo recoger el bono. Vuelve a intentarlo.'
          : status.type === 'available'
            ? `Recoge ${chips(status.reward)}. Vuelve cada día para que tu racha crezca.`
            : status.type === 'claimedToday'
              ? `Ya lo has recogido hoy. Mañana te esperan ${chips(status.nextReward)}.`
              : 'La fecha de tu dispositivo parece incorrecta. Corrígela para recoger el bono.'}
      </p>
      {status.type === 'available' && (
        <Button block className="mt-3" loading={busy} onClick={() => void claim()}>
          Recoger bono
        </Button>
      )}
    </section>
  );
}

/** Las siete fichas de la semana: las de la racha encendidas y la de hoy latiendo hasta recogerla. */
function BonusWeek({ filled, todayIndex }: { filled: number; todayIndex: number }) {
  return (
    <ol className="mt-3 grid grid-cols-7 gap-1.5" aria-label="Recompensas de la semana">
        {Array.from({ length: MAX_REWARD_DAY }, (_, i) => {
          const day = i + 1;
          const visualDay = Math.min(day, MAX_REWARD_DAY);
          const done = day <= Math.min(filled, MAX_REWARD_DAY);
          const today = day === Math.min(todayIndex, MAX_REWARD_DAY);
          return (
            <li key={day} className="flex flex-col items-center gap-1">
              <motion.span
                className={`grid place-items-center rounded-full ${today ? 'ring-2 ring-gold-light' : ''}`}
                // «transform» (no «scale»): lo anima el navegador fuera del hilo principal.
                animate={today ? { transform: ['scale(1)', 'scale(1.08)', 'scale(1)'] } : { transform: 'scale(1)' }}
                transition={today ? { repeat: Infinity, duration: 1.6, ease: 'easeInOut' } : undefined}
                style={{ opacity: done || today ? 1 : 0.32 }}
              >
                <Chip value={dailyReward(visualDay) >= 1000 ? 1000 : 500} size={34} label={dailyReward(visualDay) >= 1000 ? `${(dailyReward(visualDay) / 1000).toFixed(1).replace('.0', '')}K` : String(dailyReward(visualDay))} />
              </motion.span>
              <span className={`text-[10px] font-semibold ${today ? 'text-gold-light' : 'text-mute'}`}>{day === MAX_REWARD_DAY ? '7+' : `Día ${day}`}</span>
            </li>
          );
        })}
      </ol>
  );
}

/** Días ya avisados con el diálogo del bono (uno por día y jugador, aunque se cierre sin recoger). */
const promptKey = (uid: string) => `royal-chance-bono-avisado-${uid}`;

/**
 * Al abrir la app por primera vez en el día, si el bono está disponible, se ofrece en un diálogo.
 * Si se cierra sin recogerlo, ese día ya no vuelve a salir (sigue en su tarjeta del lobby).
 */
function DailyBonusDialog({ now }: { now: number }) {
  const wallet = useReadyWallet()!;
  const uid = wallet.uid;
  const claimDailyBonus = useWallet((s) => s.claimDailyBonus);
  const today = localEpochDay(new Date(now));
  const status = dailyBonusStatus(wallet, today, Date.now());
  const [open, setOpen] = useState(false);
  // Se apunta como visto al cerrarlo (no al abrirlo): si la tarjeta se vuelve a montar mientras
  // carga la cuenta, el diálogo vuelve a salir en vez de perderse.
  const close = () => {
    try {
      localStorage.setItem(promptKey(uid), String(today));
    } catch {
      // Sin almacenamiento: puede volver a salir al recargar.
    }
    setOpen(false);
  };
  const [busy, setBusy] = useState(false);
  const [claimed, setClaimed] = useState<number | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (status.type !== 'available') return;
    try {
      if (localStorage.getItem(promptKey(uid)) === String(today)) return;
    } catch {
      // Sin almacenamiento: se ofrece igualmente.
    }
    setOpen(true);
    // Solo al cambiar de día (o de jugador): no reabrir tras cada cambio de saldo.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [uid, today]);

  const claim = async () => {
    if (status.type !== 'available') return;
    const reward = status.reward;
    setBusy(true);
    const result = await claimDailyBonus();
    setBusy(false);
    if (result.ok) {
      setClaimed(reward);
      play('win');
    } else if (result.error.type === 'dailyBonusAlreadyClaimed') {
      close();
    } else {
      setFailed(true);
    }
  };

  const streakDay = status.type === 'available' ? status.streakDay : wallet.dailyStreak;
  return (
    <Dialog
      open={open}
      onClose={() => close()}
      title={claimed !== null ? '¡Bono recogido!' : `Bono diario · Día ${streakDay}`}
      actions={
        claimed !== null || status.type !== 'available' ? (
          <Button block size="lg" onClick={() => close()}>
            A jugar
          </Button>
        ) : (
          <>
            <Button block size="lg" loading={busy} onClick={() => void claim()}>
              Recoger {chips(status.reward)}
            </Button>
            <Button block variant="ghost" onClick={() => close()}>
              Ahora no
            </Button>
          </>
        )
      }
    >
      <BonusWeek filled={claimed !== null ? Math.min(streakDay, MAX_REWARD_DAY) : streakDay - 1} todayIndex={claimed !== null ? -1 : streakDay} />
      <p className="mt-4">
        {failed
          ? 'No se pudo recoger el bono. Vuelve a intentarlo.'
          : claimed !== null
            ? `Has sumado ${chips(claimed)}. ${streakDay > 1 ? `Llevas ${streakDay} días seguidos: vuelve` : 'Vuelve'} mañana para que tu racha crezca.`
            : streakDay > 1
              ? `Llevas ${streakDay} días seguidos. Recoge tu bono para mantener la racha.`
              : 'Recoge tu bono de hoy y vuelve cada día para que tu racha crezca.'}
      </p>
    </Dialog>
  );
}
