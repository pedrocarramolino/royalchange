import { useNavigate } from 'react-router';
import { useRequireLandscape } from '@/ui/orientation';
import { TopBar } from '@/ui/TopBar';

/** Provisional: se sustituye por la mesa completa. */
export default function SlotsScreen() {
  useRequireLandscape();
  const navigate = useNavigate();
  return (
    <div className="felt flex h-full flex-col">
      <TopBar title="Slots" onBack={() => navigate('/')} />
    </div>
  );
}
