const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';

/**
 * Identificador aleatorio alfanumérico de 20 caracteres (como los ids automáticos de Firestore),
 * con fuente criptográfica: la probabilidad de colisión es despreciable incluso sin servidor.
 */
export function newId(length = 20): string {
  const bytes = new Uint8Array(length * 2);
  crypto.getRandomValues(bytes);
  let id = '';
  for (let i = 0; id.length < length; i++) {
    if (i >= bytes.length) {
      crypto.getRandomValues(bytes);
      i = 0;
    }
    const byte = bytes[i]!;
    // Rechazo de los bytes que sesgarían la distribución (256 no es múltiplo de 62).
    if (byte < 248) id += ALPHABET[byte % ALPHABET.length];
  }
  return id;
}

/** Entero aleatorio uniforme en [0, bound) con fuente criptográfica. */
export function randomInt(bound: number): number {
  if (bound <= 0) throw new Error(`Límite no válido: ${bound}`);
  const limit = Math.floor(0x1_0000_0000 / bound) * bound;
  const buffer = new Uint32Array(1);
  for (;;) {
    crypto.getRandomValues(buffer);
    if (buffer[0]! < limit) return buffer[0]! % bound;
  }
}
