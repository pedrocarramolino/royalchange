import { useMemo, useState, type FormEvent } from 'react';
import { AUTH_ERROR_TEXT, useAuth } from '@/data/auth';
import { countries, suggestedCountry } from '@/domain/countries';
import {
  AVATARS,
  FIELD_ERROR_TEXT,
  PASSWORD_REQUIREMENT_TEXT,
  passwordStrength,
  unmetPasswordRequirements,
  validateAlias,
  validateBirthDate,
  validateEmail,
  validatePassword,
  type AvatarId,
  type FieldError,
  type PasswordRequirement,
} from '@/domain/validation';
import { Avatar, avatarName } from '@/ui/Avatar';
import { Button } from '@/ui/Button';
import { Checkbox, PasswordField, SelectField, TextField } from '@/ui/Field';
import { AuthLayout, FormBanner, SectionTitle } from './AuthLayout';
import { LegalDialog } from './LegalScreen';
import type { LegalDocument } from './legal';

type Mode = 'register' | 'completeProfile';

const STRENGTH: Record<ReturnType<typeof passwordStrength>, { label: string; bars: number; color: string }> = {
  weak: { label: 'Contraseña débil', bars: 1, color: 'bg-ruby-bright' },
  fair: { label: 'Contraseña aceptable', bars: 2, color: 'bg-gold-deep' },
  good: { label: 'Contraseña buena', bars: 3, color: 'bg-gold' },
  strong: { label: 'Contraseña fuerte', bars: 4, color: 'bg-emerald' },
};

/** Registro completo con email y contraseña, o solo el perfil si la cuenta ya existe. */
export function RegisterScreen({ mode }: { mode: Mode }) {
  const { register, completeProfile, signOut } = useAuth();
  const [avatar, setAvatar] = useState<AvatarId>('SpadeGold');
  const [alias, setAlias] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [birth, setBirth] = useState('');
  const [country, setCountry] = useState(suggestedCountry);
  const [acceptsTerms, setAcceptsTerms] = useState(false);
  const [acknowledgesChips, setAcknowledgesChips] = useState(false);
  const [marketing, setMarketing] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [aliasTaken, setAliasTaken] = useState(false);
  const [legal, setLegal] = useState<LegalDocument | null>(null);
  const countryOptions = useMemo(() => countries().map((c) => ({ value: c.code, label: c.name })), []);

  const birthDate = birth ? new Date(`${birth}T12:00:00`) : null;
  const errors: Partial<Record<string, FieldError>> = {};
  const aliasError = validateAlias(alias);
  if (aliasError) errors.alias = aliasError;
  if (mode === 'register') {
    const emailError = validateEmail(email);
    if (emailError) errors.email = emailError;
    const passwordError = validatePassword(password);
    if (passwordError) errors.password = passwordError;
    if (!confirmation) errors.confirmation = 'required';
    else if (confirmation !== password) errors.confirmation = 'passwordsDoNotMatch';
  }
  const birthError = validateBirthDate(birthDate, new Date());
  if (birthError) errors.birth = birthError;
  if (!acceptsTerms) errors.terms = 'mustAccept';
  if (!acknowledgesChips) errors.chips = 'mustAccept';

  const show = (key: string) => (submitted && errors[key] ? FIELD_ERROR_TEXT[errors[key]!] : null);
  const unmet = new Set(unmetPasswordRequirements(password));
  const strength = STRENGTH[passwordStrength(password)];

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitted(true);
    setError(null);
    if (Object.keys(errors).length || !birthDate) {
      // Lleva al primer error para que no quede oculto más arriba.
      requestAnimationFrame(() => document.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus());
      return;
    }
    setBusy(true);
    const profile = { alias: alias.trim(), avatar, countryCode: country, birthYear: birthDate.getFullYear(), marketingOptIn: marketing };
    const result = mode === 'register' ? await register(email, password, profile) : await completeProfile(profile);
    setBusy(false);
    if (!result.ok) {
      if (result.error === 'aliasTaken') setAliasTaken(true);
      setError(AUTH_ERROR_TEXT[result.error]);
    }
  };

  return (
    <AuthLayout
      title={mode === 'register' ? 'Crear cuenta' : 'Completa tu perfil'}
      subtitle={mode === 'register' ? 'Tu cuenta guarda tus fichas, tu nivel y tus logros.' : 'Solo falta un paso para sentarte a la mesa.'}
      back={mode === 'register'}
    >
      <form onSubmit={submit} noValidate className="flex flex-col">
        {error && (
          <div className="mb-4">
            <FormBanner>{error}</FormBanner>
          </div>
        )}

        <SectionTitle>Tu perfil</SectionTitle>
        <fieldset>
          <legend className="mb-3 text-sm font-semibold text-ivory-dim">Elige tu avatar</legend>
          <div className="grid grid-cols-4 gap-3" role="radiogroup">
            {AVATARS.map((id) => (
              <button
                key={id}
                type="button"
                role="radio"
                aria-checked={avatar === id}
                aria-label={avatarName(id)}
                onClick={() => setAvatar(id)}
                className="grid place-items-center rounded-full py-1"
              >
                <Avatar id={id} size={56} selected={avatar === id} />
              </button>
            ))}
          </div>
        </fieldset>
        <TextField
          className="mt-5"
          label="Alias"
          autoComplete="nickname"
          autoCapitalize="none"
          autoCorrect="off"
          spellCheck={false}
          maxLength={20}
          value={alias}
          onChange={(e) => {
            setAlias(e.target.value);
            setAliasTaken(false);
          }}
          help="Nombre público. De 3 a 20 caracteres: letras, números y _"
          error={aliasTaken ? 'Ese alias ya está en uso' : show('alias')}
        />

        {mode === 'register' && (
          <>
            <SectionTitle>Acceso</SectionTitle>
            <div className="flex flex-col gap-4">
              <TextField
                label="Email"
                type="email"
                inputMode="email"
                autoComplete="email"
                autoCapitalize="none"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                error={show('email')}
              />
              <div>
                <PasswordField label="Contraseña" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} error={show('password')} />
                {password && (
                  <div className="mt-3 px-1" aria-live="polite">
                    <div className="flex gap-1.5" aria-hidden>
                      {[1, 2, 3, 4].map((bar) => (
                        <span key={bar} className={`h-1 flex-1 rounded-full ${bar <= strength.bars ? strength.color : 'bg-ink-4'}`} />
                      ))}
                    </div>
                    <p className="mt-2 text-xs font-semibold text-ivory-dim">{strength.label}</p>
                  </div>
                )}
                <ul className="mt-2 grid grid-cols-2 gap-x-3 gap-y-1 px-1">
                  {(Object.keys(PASSWORD_REQUIREMENT_TEXT) as PasswordRequirement[]).map((req) => {
                    const met = password.length > 0 && !unmet.has(req);
                    return (
                      <li key={req} className={`flex items-center gap-1.5 text-xs ${met ? 'text-emerald' : 'text-mute'}`}>
                        <span aria-hidden>{met ? '✓' : '·'}</span>
                        {PASSWORD_REQUIREMENT_TEXT[req]}
                        <span className="sr-only">{met ? ' (cumplido)' : ' (pendiente)'}</span>
                      </li>
                    );
                  })}
                </ul>
              </div>
              <PasswordField label="Repite la contraseña" autoComplete="new-password" value={confirmation} onChange={(e) => setConfirmation(e.target.value)} error={show('confirmation')} />
            </div>
          </>
        )}

        <SectionTitle>Datos personales</SectionTitle>
        <div className="flex flex-col gap-4">
          <TextField
            label="Fecha de nacimiento"
            type="date"
            value={birth}
            max={new Date().toISOString().slice(0, 10)}
            onChange={(e) => setBirth(e.target.value)}
            help="Debes ser mayor de edad. Solo guardamos el año."
            error={show('birth')}
            className="[&_input]:min-h-[58px]"
          />
          <SelectField label="País de residencia" value={country} onChange={(e) => setCountry(e.target.value)} options={countryOptions} />
        </div>

        <SectionTitle>Condiciones</SectionTitle>
        <Checkbox checked={acceptsTerms} onChange={setAcceptsTerms} error={show('terms')}>
          Acepto los <LegalLink onOpen={() => setLegal('terminos')}>Términos y condiciones</LegalLink> y la{' '}
          <LegalLink onOpen={() => setLegal('privacidad')}>Política de privacidad</LegalLink>.
        </Checkbox>
        <Checkbox checked={acknowledgesChips} onChange={setAcknowledgesChips} error={show('chips')}>
          Entiendo que las fichas son virtuales y no tienen valor monetario: no se compran, no se canjean y no dan premios.
        </Checkbox>
        <Checkbox checked={marketing} onChange={setMarketing}>
          Quiero recibir novedades de Royal Chance por email (opcional).
        </Checkbox>

        <Button type="submit" size="lg" block loading={busy} className="mt-6">
          {mode === 'register' ? 'Crear cuenta' : 'Guardar y continuar'}
        </Button>
        {mode === 'completeProfile' && (
          <Button variant="ghost" block className="mt-2" onClick={() => void signOut()}>
            Cerrar sesión
          </Button>
        )}
      </form>
      <LegalDialog document={legal} onClose={() => setLegal(null)} />
    </AuthLayout>
  );
}

function LegalLink({ onOpen, children }: { onOpen: () => void; children: string }) {
  return (
    <button
      type="button"
      className="font-semibold text-gold underline-offset-2 hover:underline"
      onClick={(event) => {
        // Abre el documento sin marcar la casilla.
        event.preventDefault();
        event.stopPropagation();
        onOpen();
      }}
    >
      {children}
    </button>
  );
}
