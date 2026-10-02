/** 1234567 → "1.234.567" (también los miles de 4 cifras, que Intl en español no agrupa). */
export function grouped(value: number): string {
  const sign = value < 0 ? '−' : '';
  const digits = Math.abs(Math.trunc(value)).toString();
  return sign + digits.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}

/** "1 ficha" / "2.500 fichas". */
export function chips(value: number): string {
  return `${grouped(value)} ${Math.abs(value) === 1 ? 'ficha' : 'fichas'}`;
}

/** Valor corto de una ficha: 500, 1K, 25K, 1M. */
export function chipLabel(value: number): string {
  if (value >= 1_000_000) return `${value / 1_000_000}M`;
  if (value >= 1_000) return `${value / 1_000}K`;
  return String(value);
}

/** Variación con signo: "+1.200", "−300", "0". */
export function signed(value: number): string {
  if (value > 0) return `+${grouped(value)}`;
  return grouped(value);
}

const dateTime = new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
const dateOnly = new Intl.DateTimeFormat('es-ES', { weekday: 'long', day: 'numeric', month: 'long' });
const timeOnly = new Intl.DateTimeFormat('es-ES', { hour: '2-digit', minute: '2-digit' });

export const formatDateTime = (millis: number) => dateTime.format(millis);
export const formatDay = (millis: number) => dateOnly.format(millis);
export const formatTime = (millis: number) => timeOnly.format(millis);

/** Duración restante: "3 h 20 min", "12 min", "menos de 1 min". */
export function remaining(ms: number): string {
  const minutes = Math.ceil(ms / 60_000);
  if (minutes < 1) return 'menos de 1 min';
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) return `${rest} min`;
  return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`;
}
