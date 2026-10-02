import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router';
import { MotionConfig } from 'motion/react';
import { useAuth } from '@/data/auth';
import { useSettings } from '@/data/settings';
import { CardSvgDefs } from '@/ui/PlayingCard';
import { ChipSvgDefs } from '@/ui/Chip';
import { OrientationGate } from '@/ui/OrientationGate';
import { useOrientationStore } from '@/ui/orientation';
import { Splash } from '@/ui/Brand';
import { WelcomeScreen } from '@/features/auth/WelcomeScreen';
import { LoginScreen } from '@/features/auth/LoginScreen';
import { RegisterScreen } from '@/features/auth/RegisterScreen';
import { ForgotPasswordScreen } from '@/features/auth/ForgotPasswordScreen';
import { LegalScreen } from '@/features/auth/LegalScreen';
import { CasinoLayout } from '@/features/casino/CasinoLayout';
import { LobbyScreen } from '@/features/lobby/LobbyScreen';
import { ProgressScreen } from '@/features/progress/ProgressScreen';
import { HistoryScreen } from '@/features/history/HistoryScreen';
import { SettingsScreen } from '@/features/settings/SettingsScreen';
import { ProgressToasts } from '@/features/casino/ProgressToasts';

// Las mesas se cargan al abrirlas: la primera pantalla llega antes.
const BlackjackScreen = lazy(() => import('@/features/games/blackjack/BlackjackScreen'));
const RouletteScreen = lazy(() => import('@/features/games/roulette/RouletteScreen'));
const SlotsScreen = lazy(() => import('@/features/games/slots/SlotsScreen'));
const DiceScreen = lazy(() => import('@/features/games/dice/DiceScreen'));
const PokerScreen = lazy(() => import('@/features/games/poker/PokerScreen'));
const BaccaratScreen = lazy(() => import('@/features/games/baccarat/BaccaratScreen'));
const VideoPokerScreen = lazy(() => import('@/features/games/videopoker/VideoPokerScreen'));

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
      <Routes>
        <Route path="/" element={<WelcomeScreen />} />
        <Route path="/entrar" element={<LoginScreen />} />
        <Route path="/registro" element={<RegisterScreen mode="register" />} />
        <Route path="/recuperar" element={<ForgotPasswordScreen />} />
        <Route path="/legal/:document" element={<LegalScreen />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    );
  } else if (!auth.user.profile) {
    // El registro se interrumpió tras crear la cuenta: falta el perfil.
    content = (
      <Routes>
        <Route path="/legal/:document" element={<LegalScreen />} />
        <Route path="*" element={<RegisterScreen mode="completeProfile" />} />
      </Routes>
    );
  } else {
    content = (
      <Suspense fallback={<Splash />}>
        <Routes>
          <Route element={<CasinoLayout />}>
            <Route path="/" element={<LobbyScreen />} />
            <Route path="/progreso" element={<ProgressScreen />} />
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
          <Route path="/legal/:document" element={<LegalScreen />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    );
  }

  return (
    <MotionConfig reducedMotion={reducedMotion ? 'always' : 'never'}>
      <CardSvgDefs />
      <ChipSvgDefs />
      <OrientationGate>
        {content}
        {auth.status === 'signedIn' && auth.user.profile && !atTable && <ProgressToasts />}
      </OrientationGate>
    </MotionConfig>
  );
}
