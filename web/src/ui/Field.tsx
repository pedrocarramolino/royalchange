import { useId, useState, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { EASE_OUT } from './motion';

interface FieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size'> {
  label: string;
  error?: string | null;
  help?: string;
  trailing?: ReactNode;
}

const fieldBox =
  'w-full rounded-2xl border bg-ink-1 px-4 pt-6 pb-2 text-ivory outline-none transition-[border-color,box-shadow] duration-200 ease-out placeholder:text-mute/70 focus:border-gold focus:shadow-[0_0_0_3px_rgb(212_175_106/0.16)]';

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
      <AnimatePresence mode="wait" initial={false}>
        {error ? (
          <motion.p
            key="error"
            id={`${inputId}-error`}
            className="mt-1.5 px-1 text-sm text-ruby-bright"
            initial={{ opacity: 0, transform: 'translateY(-3px)' }}
            animate={{ opacity: 1, transform: 'translateY(0px)' }}
            exit={{ opacity: 0, transition: { duration: 0.1 } }}
            transition={{ duration: 0.2, ease: EASE_OUT }}
          >
            {error}
          </motion.p>
        ) : help ? (
          <motion.p
            key="help"
            id={`${inputId}-help`}
            className="mt-1.5 px-1 text-xs text-mute"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0, transition: { duration: 0.1 } }}
            transition={{ duration: 0.2, ease: EASE_OUT }}
          >
            {help}
          </motion.p>
        ) : null}
      </AnimatePresence>
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
      <label htmlFor={id} className="group flex cursor-pointer items-start gap-3 py-2">
        <input id={id} type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} className="peer sr-only" />
        <span
          className={`mt-0.5 grid size-6 shrink-0 place-items-center rounded-md border-2 transition-[background-color,border-color,scale] duration-150 ease-out group-active:scale-90 peer-focus-visible:outline-2 peer-focus-visible:outline-gold-light ${checked ? 'border-gold bg-gold text-on-gold' : error ? 'border-ruby-bright' : 'border-mute'}`}
          aria-hidden
        >
          {checked && (
            <svg viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="3.2" strokeLinecap="round" strokeLinejoin="round">
              {/* La marca se dibuja de izquierda a derecha. */}
              <motion.path d="M5 12.5l4.5 4.5L19 7.5" initial={{ pathLength: 0 }} animate={{ pathLength: 1 }} transition={{ duration: 0.22, ease: EASE_OUT }} />
            </svg>
          )}
        </span>
        <span className="text-[15px] leading-snug text-ivory">{children}</span>
      </label>
      <AnimatePresence initial={false}>
        {error && (
          <motion.p
            className="-mt-1 mb-1 pl-9 text-sm text-ruby-bright"
            initial={{ opacity: 0, transform: 'translateY(-3px)' }}
            animate={{ opacity: 1, transform: 'translateY(0px)' }}
            exit={{ opacity: 0, transition: { duration: 0.1 } }}
            transition={{ duration: 0.2, ease: EASE_OUT }}
          >
            {error}
          </motion.p>
        )}
      </AnimatePresence>
    </div>
  );
}

/** Interruptor de ajustes. */
export function Toggle({ checked, onChange, label, description }: { checked: boolean; onChange: (v: boolean) => void; label: string; description?: string }) {
  const id = useId();
  return (
    <label htmlFor={id} className="group flex cursor-pointer items-center gap-4 py-3">
      <span className="min-w-0 flex-1">
        <span className="block text-[15px] font-semibold text-ivory">{label}</span>
        {description && <span className="mt-0.5 block text-sm text-ivory-dim">{description}</span>}
      </span>
      <input id={id} type="checkbox" role="switch" checked={checked} onChange={(e) => onChange(e.target.checked)} className="peer sr-only" />
      <span
        className={`relative h-7 w-12 shrink-0 rounded-full transition-colors duration-200 ease-out peer-focus-visible:outline-2 peer-focus-visible:outline-gold-light ${checked ? 'bg-gold' : 'bg-ink-4'}`}
        aria-hidden
      >
        <span
          className={`absolute top-1 h-5 w-5 rounded-full bg-ivory shadow transition-[translate,width] duration-200 ease-[var(--ease-out)] group-active:w-6 ${
            checked ? 'translate-x-6 group-active:translate-x-5' : 'translate-x-1'
          }`}
        />
      </span>
    </label>
  );
}
