export type LegalDocument = 'terminos' | 'privacidad';

export const LEGAL_TITLE: Record<LegalDocument, string> = {
  terminos: 'Términos y condiciones',
  privacidad: 'Política de privacidad',
};

export const LEGAL_DRAFT_NOTICE =
  'Borrador pendiente de revisión legal. Este texto se sustituirá por la versión definitiva antes de publicar la aplicación.';

export const LEGAL_BODY: Record<LegalDocument, [string, string][]> = {
  terminos: [
    ['Qué es Royal Chance', 'Royal Chance es un juego de entretenimiento que simula juegos de casino con fichas virtuales. No es un servicio de apuestas.'],
    ['Fichas virtuales', 'Las fichas no tienen valor monetario. No se compran con dinero, no se pueden canjear, transferir ni vender, y no dan acceso a premios de ningún tipo.'],
    ['Edad mínima', 'Solo pueden usar Royal Chance las personas mayores de 18 años.'],
    ['Uso responsable', 'No está permitido manipular la aplicación, aprovechar errores, usar programas automáticos ni crear varias cuentas para obtener ventajas. Podemos suspender las cuentas que incumplan estas normas.'],
    ['Tu cuenta', 'Eres responsable de mantener segura tu contraseña. Puedes eliminar tu cuenta en cualquier momento desde Ajustes; al hacerlo se borran tu progreso y tus datos.'],
    ['Cambios', 'Si modificamos estos términos, te pediremos que los aceptes de nuevo antes de seguir jugando.'],
    ['Contacto', '[Datos de contacto del responsable]'],
  ],
  privacidad: [
    ['Responsable', '[Nombre y datos de contacto del responsable del tratamiento]'],
    ['Datos que tratamos', 'Email, alias, avatar, país, año de nacimiento, preferencias, consentimientos y tu progreso en el juego (fichas, nivel, logros, historial y estadísticas).'],
    ['Para qué', 'Para crear y mantener tu cuenta, guardar tu progreso, verificar que eres mayor de edad y, solo si lo aceptas, enviarte novedades.'],
    ['Base legal', 'La ejecución del servicio que solicitas y, para las comunicaciones, tu consentimiento, que puedes retirar cuando quieras.'],
    ['Dónde se guardan', 'En servicios de Google Firebase, con los datos alojados en la Unión Europea. No vendemos tus datos ni usamos publicidad o analítica de terceros.'],
    ['Cuánto tiempo', 'Mientras tu cuenta exista. Al eliminarla, borramos tus datos salvo que la ley obligue a conservar alguno.'],
    ['Tus derechos', 'Acceso, rectificación, supresión, portabilidad, oposición y limitación. Puedes ejercerlos escribiendo al responsable y reclamar ante la Agencia Española de Protección de Datos (aepd.es).'],
  ],
};
