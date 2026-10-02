/** Reglas de los formularios de acceso. Las de Firestore repiten el alias y la mayoría de edad. */

export type FieldError =
  | 'required'
  | 'aliasTooShort'
  | 'aliasTooLong'
  | 'aliasInvalidCharacters'
  | 'aliasMustStartWithLetter'
  | 'emailInvalid'
  | 'passwordTooLong'
  | 'passwordRequirementsNotMet'
  | 'passwordsDoNotMatch'
  | 'birthDateInFuture'
  | 'birthDateImplausible'
  | 'underage'
  | 'mustAccept';

export const FIELD_ERROR_TEXT: Record<FieldError, string> = {
  required: 'Este campo es obligatorio',
  aliasTooShort: 'El alias debe tener al menos 3 caracteres',
  aliasTooLong: 'El alias no puede superar los 20 caracteres',
  aliasInvalidCharacters: 'Usa solo letras sin tilde, números y _',
  aliasMustStartWithLetter: 'El alias debe empezar por una letra',
  emailInvalid: 'Escribe un email válido',
  passwordTooLong: 'La contraseña es demasiado larga',
  passwordRequirementsNotMet: 'La contraseña no cumple los requisitos',
  passwordsDoNotMatch: 'Las contraseñas no coinciden',
  birthDateInFuture: 'La fecha no puede ser futura',
  birthDateImplausible: 'Revisa el año de nacimiento',
  underage: 'Debes tener 18 años o más para jugar',
  mustAccept: 'Debes aceptarlo para continuar',
};

export const ALIAS_MIN = 3;
export const ALIAS_MAX = 20;

export function validateAlias(alias: string): FieldError | null {
  const value = alias.trim();
  if (!value) return 'required';
  if (value.length < ALIAS_MIN) return 'aliasTooShort';
  if (value.length > ALIAS_MAX) return 'aliasTooLong';
  if (!/^[A-Za-z0-9_]+$/.test(value)) return 'aliasInvalidCharacters';
  if (!/^[A-Za-z]/.test(value)) return 'aliasMustStartWithLetter';
  return null;
}

const EMAIL = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/;

export function validateEmail(email: string): FieldError | null {
  const value = email.trim();
  if (!value) return 'required';
  if (!EMAIL.test(value)) return 'emailInvalid';
  return null;
}

export type PasswordRequirement = 'minLength' | 'lowercase' | 'uppercase' | 'digit';

export const PASSWORD_REQUIREMENT_TEXT: Record<PasswordRequirement, string> = {
  minLength: 'Al menos 8 caracteres',
  lowercase: 'Una letra minúscula',
  uppercase: 'Una letra mayúscula',
  digit: 'Un número',
};

export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 128;

export function unmetPasswordRequirements(password: string): PasswordRequirement[] {
  const unmet: PasswordRequirement[] = [];
  if (password.length < PASSWORD_MIN) unmet.push('minLength');
  if (!/\p{Ll}/u.test(password)) unmet.push('lowercase');
  if (!/\p{Lu}/u.test(password)) unmet.push('uppercase');
  if (!/\p{Nd}/u.test(password)) unmet.push('digit');
  return unmet;
}

export function validatePassword(password: string): FieldError | null {
  if (!password) return 'required';
  if (password.length > PASSWORD_MAX) return 'passwordTooLong';
  if (unmetPasswordRequirements(password).length) return 'passwordRequirementsNotMet';
  return null;
}

export type PasswordStrength = 'weak' | 'fair' | 'good' | 'strong';

/** Fortaleza orientativa: débil si no cumple los requisitos; luego suman longitud y símbolos. */
export function passwordStrength(password: string): PasswordStrength {
  if (unmetPasswordRequirements(password).length) return 'weak';
  const hasSymbol = /[^\p{L}\p{Nd}]/u.test(password);
  const score = [password.length >= 12, password.length >= 16, hasSymbol].filter(Boolean).length;
  return score === 0 ? 'fair' : score === 1 ? 'good' : 'strong';
}

export const MINIMUM_AGE = 18;
const MAXIMUM_PLAUSIBLE_AGE = 120;

/** Edad cumplida en [today] por alguien nacido en [birth] (fechas locales). */
export function ageOn(birth: Date, today: Date): number {
  let age = today.getFullYear() - birth.getFullYear();
  const beforeBirthday =
    today.getMonth() < birth.getMonth() || (today.getMonth() === birth.getMonth() && today.getDate() < birth.getDate());
  if (beforeBirthday) age--;
  return age;
}

export function validateBirthDate(birth: Date | null, today: Date): FieldError | null {
  if (!birth) return 'required';
  if (birth.getTime() > today.getTime()) return 'birthDateInFuture';
  const age = ageOn(birth, today);
  if (age > MAXIMUM_PLAUSIBLE_AGE) return 'birthDateImplausible';
  if (age < MINIMUM_AGE) return 'underage';
  return null;
}

/** Versiones vigentes de los documentos legales: cambiarlas obligará a pedir de nuevo la aceptación. */
export const LEGAL_VERSIONS = { terms: '2026-09-30', privacy: '2026-09-30' } as const;

/** Avatares predefinidos. Los ids son estables (las reglas los validan). */
export const AVATARS = ['SpadeGold', 'HeartRuby', 'DiamondGold', 'ClubEmerald', 'SpadeIvory', 'HeartGold', 'DiamondRuby', 'ClubGold'] as const;
export type AvatarId = (typeof AVATARS)[number];
