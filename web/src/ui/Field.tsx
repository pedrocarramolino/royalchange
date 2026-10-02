import { useId, useState, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from 'react';

interface FieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size'> {
  label: string;
  error?: string | null;
  help?: string;
  trailing?: ReactNode;
}

const fieldBox =
  'w-full rounded-2xl border bg-ink-1 px-4 pt-6 pb-2 text-ivory outline-none transition-colors placeholder:text-mute/70 focus:border-gold';

/** Campo de texto con etiqueta flotante fija, ayuda y error accesibles. */
export function TextField({ label, error, help, trailing, className = '', id, ...rest }: FieldProps) {
  const autoId = useId();
  const inputId = id ?? autoId;
  const describedBy = error ? `${inputId}-error` : help ? `${inputId}-help` : undefined;
  return (
    <div className={className}>
      <div className="relative">
        <label htmlFor={inputId} className="pointer-events-none absolute top-2 left-4 text-xs font-semibold text-ivory-dim">
          {label}
        </label>
        <input
          id={inputId}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={`${fieldBox} ${error ? 'border-ruby-bright' : 'border-line'} ${trailing ? 'pr-12' : ''}`}
          {...rest}
        />
        {trailing && <div className="absolute inset-y-0 right-1 flex items-center">{trailing}</div>}
      </div>
      {error ? (
        <p id={`${inputId}-error`} className="mt-1.5 px-1 text-sm text-ruby-bright">
          {error}
        </p>
      ) : help ? (
        <p id={`${inputId}-help`} className="mt-1.5 px-1 text-xs text-mute">
          {help}
        </p>
      ) : null}
    </div>
  );
}

export function PasswordField(props: Omit<FieldProps, 'type' | 'trailing'>) {
  const [visible, setVisible] = useState(false);
  return (
    <TextField
      {...props}
      type={visible ? 'text' : 'password'}
      autoCapitalize="none"
      autoCorrect="off"
      spellCheck={false}
      trailing={
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          className="grid size-11 place-items-center rounded-xl text-ivory-dim hover:text-gold"
          aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}
          aria-pressed={visible}
        >
          <EyeIcon open={!visible} />
        </button>
      }
    />
  );
}

function EyeIcon({ open }: { open: boolean }) {
  return (
    <svg viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden>
      <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z" />
      <circle cx="12" cy="12" r="3" />
      {!open && <path d="M4 4l16 16" />}
    </svg>
  );
}

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  error?: string | null;
  options: { value: string; label: string }[];
}

export function SelectField({ label, error, options, className = '', id, ...rest }: SelectProps) {
  const autoId = useId();
  const selectId = id ?? autoId;
  return (
    <div className={className}>
      <div className="relative">
        <label htmlFor={selectId} className="pointer-events-none absolute top-2 left-4 text-xs font-semibold text-ivory-dim">
          {label}
        </label>
        <select
          id={selectId}
          aria-invalid={error ? true : undefined}
          className={`${fieldBox} appearance-none pr-10 ${error ? 'border-ruby-bright' : 'border-line'}`}
          {...rest}
        >
          {options.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
        <svg viewBox="0 0 24 24" className="pointer-events-none absolute top-1/2 right-4 size-4 -translate-y-1/2 text-ivory-dim" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
          <path d="M6 9l6 6 6-6" />
        </svg>
      </div>
      {error && <p className="mt-1.5 px-1 text-sm text-ruby-bright">{error}</p>}
    </div>
  );
}

interface CheckProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  children: ReactNode;
  error?: string | null;
}

/** Casilla grande y cómoda de tocar, con el texto como parte de la zona táctil. */
export function Checkbox({ checked, onChange, children, error }: CheckProps) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id} className="flex cursor-pointer items-start gap-3 py-2">
        <input id={id} type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} className="peer sr-only" />
        <span
          className={`mt-0.5 grid size-6 shrink-0 place-items-center rounded-md border-2 transition-colors peer-focus-visible:outline-2 peer-focus-visible:outline-gold-light ${checked ? 'border-gold bg-gold text-on-gold' : error ? 'border-ruby-bright' : 'border-mute'}`}
          aria-hidden
        >
          {checked && (
            <svg viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="3.2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M5 12.5l4.5 4.5L19 7.5" />
            </svg>
          )}
        </span>
        <span className="text-[15px] leading-snug text-ivory">{children}</span>
      </label>
      {error && <p className="-mt-1 mb-1 pl-9 text-sm text-ruby-bright">{error}</p>}
    </div>
  );
}

/** Interruptor de ajustes. */
export function Toggle({ checked, onChange, label, description }: { checked: boolean; onChange: (v: boolean) => void; label: string; description?: string }) {
  const id = useId();
  return (
    <label htmlFor={id} className="flex cursor-pointer items-center gap-4 py-3">
      <span className="min-w-0 flex-1">
        <span className="block text-[15px] font-semibold text-ivory">{label}</span>
        {description && <span className="mt-0.5 block text-sm text-ivory-dim">{description}</span>}
      </span>
      <input id={id} type="checkbox" role="switch" checked={checked} onChange={(e) => onChange(e.target.checked)} className="peer sr-only" />
      <span
        className={`relative h-7 w-12 shrink-0 rounded-full transition-colors peer-focus-visible:outline-2 peer-focus-visible:outline-gold-light ${checked ? 'bg-gold' : 'bg-ink-4'}`}
        aria-hidden
      >
        <span className={`absolute top-1 size-5 rounded-full bg-ivory shadow transition-transform ${checked ? 'translate-x-6' : 'translate-x-1'}`} />
      </span>
    </label>
  );
}
