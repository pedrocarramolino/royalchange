export type LegalDocument = 'terminos' | 'privacidad' | 'borrar-cuenta';

export const LEGAL_DOCUMENTS: readonly LegalDocument[] = ['terminos', 'privacidad', 'borrar-cuenta'];

export const LEGAL_TITLE: Record<LegalDocument, string> = {
  terminos: 'Términos y condiciones',
  privacidad: 'Política de privacidad',
  'borrar-cuenta': 'Eliminar tu cuenta',
};

export const LEGAL_CONTACT = 'pedrocarramolino34@gmail.com';
export const LEGAL_UPDATED = 'Última actualización: 8 de octubre de 2026.';

export const LEGAL_BODY: Record<LegalDocument, [string, string][]> = {
  terminos: [
    ['Quién ofrece Royal Chance', `Royal Chance es una aplicación de Pedro Carramolino González (España). Contacto: ${LEGAL_CONTACT}.`],
    ['Qué es Royal Chance', 'Un juego de entretenimiento que simula juegos de casino con fichas virtuales. No es un servicio de apuestas ni de juego con dinero real.'],
    ['Fichas virtuales', 'Las fichas no tienen valor monetario. No se compran con dinero, no se pueden canjear, transferir ni vender, y no dan acceso a premios de ningún tipo. Ganar en Royal Chance no significa que vayas a ganar en un juego con dinero real.'],
    ['Edad mínima', 'Solo pueden usar Royal Chance las personas mayores de 18 años.'],
    ['Uso responsable', 'No está permitido manipular la aplicación, aprovechar errores, usar programas automáticos ni crear varias cuentas para obtener ventajas. Podemos suspender o eliminar las cuentas que incumplan estas normas. Los alias ofensivos o que suplanten a otras personas pueden ser cambiados o retirados.'],
    ['Tu cuenta', 'Eres responsable de mantener segura tu contraseña. Puedes eliminar tu cuenta en cualquier momento desde Ajustes; al hacerlo se borran tu progreso y tus datos.'],
    ['Disponibilidad', 'Royal Chance se ofrece tal cual, sin garantía de que esté siempre disponible o libre de errores. Podemos cambiar los juegos, las recompensas o el equilibrio de fichas para mejorar el juego.'],
    ['Cambios', 'Si modificamos estos términos de forma importante, te pediremos que los aceptes de nuevo antes de seguir jugando.'],
    ['Ley aplicable', 'Estos términos se rigen por la ley española, sin perjuicio de los derechos que te reconozca la ley de tu país de residencia como consumidor.'],
  ],
  privacidad: [
    ['Responsable', `Pedro Carramolino González (España). Contacto para cualquier cuestión de privacidad: ${LEGAL_CONTACT}.`],
    ['Datos que tratamos', 'Email, alias, avatar, país, año de nacimiento, preferencias, consentimientos y tu progreso en el juego (fichas, nivel, logros, misiones, historial y estadísticas).'],
    ['Para qué', 'Para crear y mantener tu cuenta, guardar tu progreso, verificar que eres mayor de edad, mostrar la clasificación semanal y, solo si lo aceptas, enviarte novedades.'],
    ['Clasificación semanal', 'Tu alias, tu avatar y tus ganancias de la semana aparecen en la clasificación, visible para los demás jugadores. Puedes dejar de aparecer cuando quieras desde Ajustes. Nunca se muestran tu email ni tu saldo.'],
    ['Base legal', 'La ejecución del servicio que solicitas y, para las comunicaciones, tu consentimiento, que puedes retirar cuando quieras.'],
    ['Dónde se guardan', 'En servicios de Google Firebase (Authentication y Firestore), con los datos alojados en la Unión Europea. Google actúa como encargado del tratamiento. No vendemos tus datos ni usamos publicidad o analítica de terceros.'],
    ['Seguridad', 'Los datos viajan cifrados (HTTPS) y solo tú puedes leer y modificar tu cuenta. Tu contraseña la gestiona Firebase Authentication: nunca la vemos.'],
    ['Cuánto tiempo', 'Mientras tu cuenta exista. Al eliminarla, borramos tus datos salvo que la ley obligue a conservar alguno.'],
    ['Menores', 'Royal Chance no está dirigido a menores de 18 años y no permite registrarse a quien no los tenga. Si sabemos de una cuenta de un menor, la eliminamos.'],
    ['Tus derechos', `Acceso, rectificación, supresión, portabilidad, oposición y limitación. Puedes ejercerlos escribiendo a ${LEGAL_CONTACT} y reclamar ante la Agencia Española de Protección de Datos (aepd.es).`],
  ],
  'borrar-cuenta': [
    ['Desde la aplicación', 'Entra en Royal Chance (app o web: royalchance-92769.web.app), abre Ajustes y pulsa «Eliminar cuenta». Te pediremos tu contraseña para confirmarlo y la cuenta se borra al momento.'],
    ['Sin acceso a la aplicación', `Escribe a ${LEGAL_CONTACT} desde el email de tu cuenta con el asunto «Eliminar cuenta de Royal Chance». La eliminaremos en un plazo máximo de 30 días y te lo confirmaremos por email.`],
    ['Qué se borra', 'Todo: tu cuenta, email, alias, avatar, país, año de nacimiento, preferencias, fichas, nivel, logros, misiones, historial y tu fila en la clasificación semanal. No conservamos ningún dato después.'],
  ],
};
