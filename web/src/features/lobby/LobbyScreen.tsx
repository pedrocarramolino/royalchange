import { useEffect, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { motion } from 'motion/react';
import { useAuth } from '@/data/auth';
import { useWallet, useReadyWallet } from '@/data/wallet';
import { EconomyRules, rescueStatus } from '@/domain/economy';
import { claimable, levelProgress, TITLE_NAMES } from '@/domain/progression';
import { chips, grouped, remaining } from '@/lib/format';
import { Avatar } from '@/ui/Avatar';
import { Button } from '@/ui/Button';
import { Chip } from '@/ui/Chip';
import { useCountUp } from '@/ui/ChipBalance';
import { IconClose, IconGift, IconMail } from '@/ui/icons';
import { DailyWheel } from './DailyWheel';
import { BaccaratArt, BlackjackArt, DiceArt, PokerArt, RouletteArt, SlotsArt, VideoPokerArt } from './GameArt';

const GAMES = [
  { path: '/mesa/blackjack', name: 'Blackjack', description: 'Llega a 21 sin pasarte y gana al crupier.', Art: BlackjackArt },
  { path: '/mesa/ruleta', name: 'Ruleta', description: 'Ruleta europea de 37 números.', Art: RouletteArt },
  { path: '/mesa/slots', name: 'Slots', description: 'Cinco rodillos y líneas de premio.', Art: SlotsArt },
  { path: '/mesa/dados', name: 'Dados', description: 'Mayor, menor y combinaciones.', Art: DiceArt },
  { path: '/mesa/poker', name: 'Póker', description: 'Texas Hold’em contra bots.', Art: PokerArt },
  { path: '/mesa/baccarat', name: 'Baccarat', description: 'Jugador o banca: gana el que más se acerque a 9.', Art: BaccaratArt },
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
        {user && !user.emailVerified && <VerificationCard />}
        {wallet && wallet.seq === 1 && !welcomeDismissed && (
          <Notice tone="felt" icon={<Chip value={1000} size={28} label="" />} onDismiss={() => setWelcomeDismissed(true)} dismissLabel="Entendido">
            ¡Bienvenido a la mesa! Te regalamos {chips(EconomyRules.WELCOME_GRANT)} para empezar. Son virtuales: no se compran, no se canjean y no tienen valor
            monetario.
          </Notice>
        )}
        {wallet && <RescueCard now={now} />}
        {wallet && <DailyWheel now={now} onHoldBalance={setHeldBalance} />}
        {wallet && claimable(wallet).length > 0 && (
          <Notice tone="gold" icon={<IconGift className="size-6 text-gold" />} action={{ label: 'Ver', onClick: () => navigate('/progreso') }}>
            {claimable(wallet).length === 1 ? 'Tienes un logro con recompensa por recoger.' : `Tienes ${claimable(wallet).length} logros con recompensa por recoger.`}
          </Notice>
        )}
      </div>

      <h2 className="mt-8 mb-3 text-xs font-bold tracking-[0.2em] text-gold uppercase">Mesas</h2>
      <div className="grid grid-cols-2 gap-3">
        {GAMES.map(({ path, name, description, Art }, i) => (
          <motion.button
            key={path}
            type="button"
            onClick={() => navigate(path)}
            initial={{ opacity: 0, y: 14 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.05 * i }}
            className={`group relative overflow-hidden rounded-3xl text-left shadow-[0_14px_30px_-14px_rgb(0_0_0/0.9)] ring-1 ring-gold/25 active:scale-[0.985] ${i === GAMES.length - 1 ? 'col-span-2' : ''}`}
            aria-label={`${name}. ${description}`}
          >
            <div className={`felt relative ${i === GAMES.length - 1 ? 'h-32' : 'h-36'}`}>
              <Art />
            </div>
            <div className="bg-gradient-to-b from-ink-2 to-ink-1 px-4 pt-3 pb-4">
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
  return (
    <section className="relative mt-5 overflow-hidden rounded-3xl ring-1 ring-gold/30" aria-label="Tu saldo y tu nivel">
      <div className="felt px-5 pt-5 pb-4">
        <p className="text-xs font-bold tracking-[0.2em] text-gold-light/80 uppercase">Tu saldo</p>
        {state === 'ready' ? (
          <p className="mt-1 flex items-baseline gap-2">
            <span className="tabular font-display text-[40px] leading-none font-bold text-gold-gradient" aria-label={chips(balance)}>
              {grouped(shown)}
            </span>
            <span className="text-sm font-semibold text-gold-light/80">fichas</span>
          </p>
        ) : state === 'loading' ? (
          <p className="mt-2 h-10 w-40 animate-pulse rounded-lg bg-white/10" aria-label="Cargando tu saldo" />
        ) : (
          <p className="mt-2 text-[15px] text-ivory">Tu saldo no está disponible ahora mismo. Vuelve a intentarlo en unos minutos.</p>
        )}
        <div className="pointer-events-none absolute top-4 right-4 flex -space-x-3" aria-hidden>
          <Chip value={5000} size={44} />
          <Chip value={1000} size={44} />
          <Chip value={100} size={44} />
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
          <motion.div className="h-full rounded-full metal-gold" initial={false} animate={{ width: `${Math.round(level.fraction * 100)}%` }} transition={{ duration: 0.6 }} />
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
    <div className={`flex items-center gap-3 rounded-2xl px-4 py-3 ring-1 ${tones[tone]}`} role="status">
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
    </div>
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
