const DAY_MS = 86_400_000;

/** Día natural de [date] en la zona horaria del dispositivo, como días desde 1970-01-01. */
export function localEpochDay(date: Date = new Date()): number {
  return Math.floor(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / DAY_MS);
}

/** Milisegundos que faltan para la medianoche local (cambio de día del bono diario). */
export function msUntilLocalMidnight(date: Date = new Date()): number {
  const midnight = new Date(date.getFullYear(), date.getMonth(), date.getDate() + 1);
  return midnight.getTime() - date.getTime();
}
