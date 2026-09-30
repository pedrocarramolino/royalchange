package com.royalchance.core.common.locale

/**
 * Nombre localizado de un país o región (código ISO 3166-1 alfa-2) en el idioma indicado
 * (etiqueta BCP 47, p. ej. `"es"`). Usa los datos CLDR de la plataforma, así que no hay que
 * mantener traducciones propias. Devuelve `null` si la plataforma no conoce el código.
 */
expect fun regionDisplayName(regionCode: String, languageTag: String): String?

/** Región configurada en el dispositivo (`"ES"`, `"MX"`…), o `null` si no se puede determinar. */
expect fun deviceRegionCode(): String?
