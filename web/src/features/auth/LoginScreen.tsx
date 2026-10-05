import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { AUTH_ERROR_TEXT, useAuth } from '@/data/auth';
import { FIELD_ERROR_TEXT, validateEmail } from '@/domain/validation';
import { Button } from '@/ui/Button';
import { PasswordField, TextField } from '@/ui/Field';
import { AuthLayout, FormBanner } from './AuthLayout';

export function LoginScreen() {
  const navigate = useNavigate();
  const signIn = useAuth((s) => s.signIn);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  const emailError = submitted ? validateEmail(email) : null;
  const passwordError = submitted && !password ? 'required' : null;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitted(true);
    setError(null);
    if (validateEmail(email) || !password) return;
    setBusy(true);
    const result = await signIn(email, password);
    setBusy(false);
    if (!result.ok) {
      setError(AUTH_ERROR_TEXT[result.error]);
      setAttempt((n) => n + 1);
    }
  };

  return (
    <AuthLayout title="Iniciar sesión" subtitle="Te estábamos esperando. Tu mesa sigue preparada.">
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        {error && <FormBanner key={attempt}>{error}</FormBanner>}
        <TextField
          label="Email"
          type="email"
          inputMode="email"
          autoComplete="email"
          autoCapitalize="none"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={emailError && FIELD_ERROR_TEXT[emailError]}
        />
        <PasswordField
          label="Contraseña"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={passwordError && FIELD_ERROR_TEXT[passwordError]}
        />
        <Link to="/recuperar" className="self-end py-1 text-sm font-semibold text-gold hover:text-gold-light">
          ¿Has olvidado tu contraseña?
        </Link>
        <Button type="submit" size="lg" block loading={busy} className="mt-2">
          Iniciar sesión
        </Button>
      </form>
      <div className="mt-auto pt-10 text-center text-[15px] text-ivory-dim">
        ¿Aún no tienes cuenta?{' '}
        <button type="button" className="font-semibold text-gold hover:text-gold-light" onClick={() => navigate('/registro', { replace: true })}>
          Crear cuenta
        </button>
      </div>
    </AuthLayout>
  );
}
