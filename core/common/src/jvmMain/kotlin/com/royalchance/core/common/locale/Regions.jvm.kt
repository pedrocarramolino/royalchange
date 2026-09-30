package com.royalchance.core.common.locale

import java.util.Locale

// Android consume esta misma variante JVM; allí los nombres salen de los datos ICU del sistema.
actual fun regionDisplayName(regionCode: String, languageTag: String): String? {
    val region = runCatching { Locale.Builder().setRegion(regionCode).build() }.getOrNull() ?: return null
    val name = region.getDisplayCountry(Locale.forLanguageTag(languageTag))
    // Si la plataforma no conoce la región, Java devuelve el propio código.
    return name.takeIf { it.isNotBlank() && !it.equals(regionCode, ignoreCase = true) }
}

actual fun deviceRegionCode(): String? = Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase()
