import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router';
import { LazyMotion, MotionConfig } from 'motion/react';
import { useAuth } from '@/data/auth';
import { useSettings } from '@/data/settings';
import { CardSvgDefs } from '@/ui/PlayingCard';
import { ChipSvgDefs } from '@/ui/Chip';
import { OrientationGate } from '@/ui/OrientationGate';
import { useOrientationStore } from '@/ui/orientation';
import { Splash } from '@/ui/Brand';
import { CasinoLayout } from '@/features/casino/CasinoLayout';
import { LobbyScreen } from '@/features/lobby/LobbyScreen';
import { ProgressScreen } from '@/features/progress/ProgressScreen';
import { HistoryScreen } from '@/features/history/HistoryScreen';
import { SettingsScreen } from '@/features/settings/SettingsScreen';
import { ProgressToasts } from '@/features/casino/ProgressToasts';

// Acceso (bienvenida, entrar, registro…): solo lo descarga quien no ha iniciado sesión.
const WelcomeScreen = lazy(() => import('@/features/auth/WelcomeScreen').then((m) => ({ default: m.WelcomeScreen })));
const LoginScreen = lazy(() => import('@/features/auth/LoginScreen').then((m) => ({ default: m.LoginScreen })));
const RegisterScreen = lazy(() => import('@/features/auth/RegisterScreen').then((m) => ({ default: m.RegisterScreen })));
const ForgotPasswordScreen = lazy(() => import('@/features/auth/ForgotPasswordScreen').then((m) => ({ default: m.ForgotPasswordScreen })));
const LegalScreen = lazy(() => import('@/features/auth/LegalScreen').then((m) => ({ default: m.LegalScreen })));

// Las mesas se cargan al abrirlas: la primera pantalla llega antes.
const LeaderboardScreen = lazy(() => import('@/features/leaderboard/LeaderboardScreen').then((m) => ({ default: m.LeaderboardScreen })));
const BlackjackScreen = lazy(() => import('@/features/games/blackjack/BlackjackScreen'));
const RouletteScreen = lazy(() => import('@/features/games/roulette/RouletteScreen'));
const SlotsScreen = lazy(() => import('@/features/games/slots/SlotsScreen'));
const DiceScreen = lazy(() => import('@/features/games/dice/DiceScreen'));
const PokerScreen = lazy(() => import('@/features/games/poker/PokerScreen'));
const BaccaratScreen = lazy(() => import('@/features/games/baccarat/BaccaratScreen'));
const VideoPokerScreen = lazy(() => import('@/features/games/videopoker/VideoPokerScreen'));
const PlinkoScreen = lazy(() => import('@/features/games/plinko/PlinkoScreen'));
const ScratchScreen = lazy(() => import('@/features/games/scratch/ScratchScreen'));

/**
 * Animaciones por partes: las pantallas usan la versión ligera de los componentes de Motion
 * (`m`, importada como `motion`) y el motor completo se descarga aparte, sin retrasar el arranque.
 */
const loadMotionFeatures = () => import('@/ui/motionFeatures').then((module) => module.default);

export function App() {
  const auth = useAuth((s) => s.state);
  // En las mesas, los avisos de progreso se pintan dentro de la mesa (que puede ir girada).
  const atTable = useOrientationStore((s) => s.landscapeRequests > 0);
  // «Reducir animaciones»: Motion quita los desplazamientos y escalas, y deja los fundidos.
  const reducedMotion = useSettings((s) => s.reducedMotion);

  let content;
  if (auth.status === 'loading') {
    content = <Splash />;
  } else if (auth.status === 'signedOut') {
    content = (
      <Suspense fallback={<Splash />}>
      <Routes>
        <Route path="/" element={<WelcomeScreen />} />
        <Route path="/entrar" element={<LoginScreen />} />
        <Route path="/registro" element={<RegisterScreen mode="register" />} />
        <Route path="/recuperar" element={<ForgotPasswordScreen />} />
        <Route path="/legal/:document" element={<LegalScreen />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      </Suspense>
    );
  } else if (!auth.user.profile) {
    // El registro se interrumpió tras crear la cuenta: falta el perfil.
    content = (
      <Suspense fallback={<Splash />}>
        <Routes>
          <Route path="/legal/:document" element={<LegalScreen />} />
          <Route path="*" element={<RegisterScreen mode="completeProfile" />} />
        </Routes>
      </Suspense>
    );
  } else {
    content = (
      <Suspense fallback={<Splash />}>
        <Routes>
          <Route element={<CasinoLayout />}>
            <Route path="/" element={<LobbyScreen />} />
            <Route path="/progreso" element={<ProgressScreen />} />
            <Route path="/clasificacion" element={<LeaderboardScreen />} />
            <Route path="/historial" element={<HistoryScreen />} />
            <Route path="/ajustes" element={<SettingsScreen />} />
          </Route>
          <Route path="/mesa/blackjack" element={<BlackjackScreen />} />
          <Route path="/mesa/ruleta" element={<RouletteScreen />} />
          <Route path="/mesa/slots" element={<SlotsScreen />} />
          <Route path="/mesa/dados" element={<DiceScreen />} />
          <Route path="/mesa/poker" element={<PokerScreen />} />
          <Route path="/mesa/baccarat" element={<BaccaratScreen />} />
          <Route path="/mesa/video-poker" element={<VideoPokerScreen />} />
          <Route path="/mesa/plinko" element={<PlinkoScreen />} />
          <Route path="/mesa/rasca" element={<ScratchScreen />} />
          <Route path="/legal/:document" element={<LegalScreen />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    );
  }

  return (
    <LazyMotion features={loadMotionFeatures} strict>
      <MotionConfig reducedMotion={reducedMotion ? 'always' : 'never'}>
        <CardSvgDefs />
        <ChipSvgDefs />
        <OrientationGate>
          {content}
          {auth.status === 'signedIn' && auth.user.profile && !atTable && <ProgressToasts />}
        </OrientationGate>
      </MotionConfig>
    </LazyMotion>
  );
}
