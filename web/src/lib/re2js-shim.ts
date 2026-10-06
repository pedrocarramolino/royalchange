/**
 * Sustituto mínimo de `re2js` (dependencia de Firestore de 214 kB). Firestore solo lo usa para evaluar
 * en local expresiones regulares de las consultas «pipeline», que la app no usa; si algún día se
 * usaran, esto las resuelve con las expresiones regulares del navegador.
 */
export const RE2JS = {
  compile(pattern: string) {
    const partial = new RegExp(pattern, 'u');
    const full = new RegExp(`^(?:${pattern})$`, 'u');
    return {
      test: (text: string) => partial.test(text),
      matches: (text: string) => full.test(text),
    };
  },
};
