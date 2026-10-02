import { useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { useAuth } from '@/data/auth';
import { useSettings } from '@/data/settings';
import { AVATARS, type AvatarId } from '@/domain/validation';
import { Avatar, avatarName } from '@/ui/Avatar';
import { Button } from '@/ui/Button';
import { Dialog } from '@/ui/Dialog';
import { PasswordField, Toggle } from '@/ui/Field';

export const APP_VERSION = '2.0.0';

export function SettingsScreen() {
  const auth = useAuth((s) => s.state);
  const { sendVerification, signOut, deleteAccount, updateProfile } = useAuth();
  const { soundEnabled, reducedMotion, setSoundEnabled, setReducedMotion } = useSettings();
  const navigate = useNavigate();
  const [confirmSignOut, setConfirmSignOut] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [password, setPassword] = useState('');
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [verificationSent, setVerificationSent] = useState(false);
  const [avatarOpen, setAvatarOpen] = useState(false);

  if (auth.status !== 'signedIn' || !auth.user.profile) return null;
  const { user } = auth;
  const profile = auth.user.profile;

  const remove = async () => {
    if (!password) {
      setDeleteError('Escribe tu contraseña para confirmar.');
      return;
    }
    setDeleting(true);
    setDeleteError(null);
    const result = await deleteAccount(password);
    setDeleting(false);
    if (!result.ok) {
      setDeleteError(
        result.error === 'invalidCredentials'
          ? 'Contraseña incorrecta.'
          : result.error === 'requiresRecentLogin'
            ? 'Por seguridad, cierra sesión, vuelve a entrar y repite la acción.'
            : 'No se pudo completar la acción. Vuelve a intentarlo.',
      );
    }
  };

  const chooseAvatar = async (avatar: AvatarId) => {
    setAvatarOpen(false);
    await updateProfile({ ...profile, avatar });
  };

  return (
    <div className="safe-top safe-px-4 mx-auto max-w-lg pb-10">
      <h1 className="px-1 pt-6 font-display text-2xl font-semibold text-gold-gradient">Ajustes</h1>

      <Section title="Cuenta">
        <div className="flex items-center gap-4 py-2">
          <button type="button" onClick={() => setAvatarOpen(true)} aria-label="Cambiar avatar" className="rounded-full">
            <Avatar id={profile.avatar} size={56} />
          </button>
          <div className="min-w-0 flex-1">
            <p className="truncate font-display text-lg font-semibold text-ivory">{profile.alias}</p>
            <p className="truncate text-sm text-ivory-dim">{user.email}</p>
            <p className={`mt-0.5 text-xs font-semibold ${user.emailVerified ? 'text-emerald' : 'text-gold'}`}>{user.emailVerified ? 'Email verificado' : 'Email sin verificar'}</p>
          </div>
        </div>
        {!user.emailVerified &&
          (verificationSent ? (
            <p className="py-2 text-sm text-ivory-dim">Te hemos enviado un enlace de confirmación. Ábrelo y vuelve a la app.</p>
          ) : (
            <Button variant="secondary" size="sm" className="my-2" onClick={async () => (await sendVerification()).ok && setVerificationSent(true)}>
              Verificar email
            </Button>
          ))}
      </Section>

      <Section title="Juego">
        <Toggle checked={soundEnabled} onChange={setSoundEnabled} label="Sonido" description="Efectos de cartas, fichas, ruleta y premios." />
        <div className="h-px bg-white/5" />
        <Toggle
          checked={reducedMotion}
          onChange={setReducedMotion}
          label="Animaciones reducidas"
          description="Sin celebraciones ni movimientos decorativos. Las mesas siguen mostrando cada resultado."
        />
      </Section>

      <Section title="Información">
        <RowButton onClick={() => navigate('/legal/terminos')}>Términos y condiciones</RowButton>
        <RowButton onClick={() => navigate('/legal/privacidad')}>Política de privacidad</RowButton>
        <p className="py-3 text-sm text-mute">Versión {APP_VERSION}</p>
      </Section>

      <div className="mt-6 flex flex-col gap-2">
        <Button variant="secondary" block onClick={() => setConfirmSignOut(true)}>
          Cerrar sesión
        </Button>
        <Button variant="ghost" block className="text-ruby-bright" onClick={() => setConfirmDelete(true)}>
          Eliminar cuenta
        </Button>
      </div>

      <Dialog
        open={confirmSignOut}
        onClose={() => setConfirmSignOut(false)}
        title="¿Cerrar sesión?"
        actions={
          <>
            <Button onClick={() => void signOut()}>Cerrar sesión</Button>
            <Button variant="ghost" onClick={() => setConfirmSignOut(false)}>
              Cancelar
            </Button>
          </>
        }
      >
        Podrás volver a entrar con tu email y contraseña.
      </Dialog>

      <Dialog
        open={confirmDelete}
        onClose={() => {
          setConfirmDelete(false);
          setPassword('');
          setDeleteError(null);
        }}
        title="¿Eliminar tu cuenta?"
        actions={
          <>
            <Button variant="danger" loading={deleting} onClick={() => void remove()}>
              Eliminar definitivamente
            </Button>
            <Button variant="ghost" onClick={() => setConfirmDelete(false)}>
              Cancelar
            </Button>
          </>
        }
      >
        <p>Se borrarán de forma permanente tu cuenta, tus fichas, tu progreso y tus datos. Esta acción no se puede deshacer.</p>
        <PasswordField
          className="mt-4"
          label="Contraseña"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          help="Escribe tu contraseña para confirmar."
          error={deleteError}
        />
      </Dialog>

      <Dialog open={avatarOpen} onClose={() => setAvatarOpen(false)} title="Elige tu avatar" sheet>
        <div className="grid grid-cols-4 gap-3 py-2">
          {AVATARS.map((id) => (
            <button key={id} type="button" onClick={() => void chooseAvatar(id)} aria-label={avatarName(id)} aria-pressed={profile.avatar === id} className="grid place-items-center rounded-full py-1">
              <Avatar id={id} size={58} selected={profile.avatar === id} />
            </button>
          ))}
        </div>
      </Dialog>
    </div>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="mt-6">
      <h2 className="mb-2 px-1 text-xs font-bold tracking-[0.2em] text-gold uppercase">{title}</h2>
      <div className="panel rounded-3xl px-4 py-1">{children}</div>
    </section>
  );
}

function RowButton({ children, onClick }: { children: ReactNode; onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} className="flex w-full items-center justify-between border-b border-white/5 py-3.5 text-left text-[15px] text-ivory">
      {children}
      <svg viewBox="0 0 24 24" className="size-4 text-mute" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
        <path d="M9 6l6 6-6 6" />
      </svg>
    </button>
  );
}
