import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { AnimatePresence, motion } from 'motion/react';
import { EASE_OUT } from '@/ui/motion';
import { AUTH_ERROR_TEXT, useAuth } from '@/data/auth';
import { FIELD_ERROR_TEXT, validateEmail } from '@/domain/validation';
import { Button } from '@/ui/Button';
import { TextField } from '@/ui/Field';
import { AuthLayout, FormBanner } from './AuthLayout';

export function ForgotPasswordScreen() {
  const navigate = useNavigate();
  const sendPasswordReset = useAuth((s) => s.sendPasswordReset);
  const [email, setEmail] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const emailError = submitted ? validateEmail(email) : null;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitted(true);
    setError(null);
    if (validateEmail(email)) return;
    setBusy(true);
    const result = await sendPasswordReset(email);
    setBusy(false);
    if (result.ok) setSent(true);
    else setError(AUTH_ERROR_TEXT[result.error]);
  };

  return (
    <AuthLayout title="Recuperar contraseña" subtitle="Escribe el email de tu cuenta y te enviaremos un enlace para crear una contraseña nueva.">
      <AnimatePresence mode="wait" initial={false}>
      {sent ? (
        <motion.div
          key="enviado"
          className="flex flex-col gap-6"
          initial={{ opacity: 0, transform: 'translateY(8px)' }}
          animate={{ opacity: 1, transform: 'translateY(0px)' }}
          transition={{ duration: 0.3, ease: EASE_OUT }}
        >
          <FormBanner tone="success">Si existe una cuenta con ese email, recibirás un enlace en unos minutos. Revisa también la carpeta de spam.</FormBanner>
          <Button size="lg" variant="secondary" block onClick={() => navigate('/entrar', { replace: true })}>
            Volver a iniciar sesión
          </Button>
        </motion.div>
      ) : (
        <motion.form key="formulario" onSubmit={submit} className="flex flex-col gap-4" noValidate exit={{ opacity: 0, transition: { duration: 0.15 } }}>
          {error && <FormBanner>{error}</FormBanner>}
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
          <Button type="submit" size="lg" block loading={busy} className="mt-2">
            Enviar enlace
          </Button>
        </motion.form>
      )}
      </AnimatePresence>
    </AuthLayout>
  );
}
