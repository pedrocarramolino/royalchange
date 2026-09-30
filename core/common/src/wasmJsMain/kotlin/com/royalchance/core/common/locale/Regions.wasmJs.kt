@file:OptIn(ExperimentalWasmJsInterop::class)

package com.royalchance.core.common.locale

actual fun regionDisplayName(regionCode: String, languageTag: String): String? =
    intlRegionName(regionCode, languageTag)?.takeIf { !it.equals(regionCode, ignoreCase = true) }

actual fun deviceRegionCode(): String? = navigatorRegion()?.takeIf { it.length == 2 }?.uppercase()

// Intl.DisplayNames está disponible en todos los navegadores con WasmGC y en Node.js.
private fun intlRegionName(code: String, languageTag: String): String? = js(
    """(() => {
        try { return new Intl.DisplayNames([languageTag], { type: 'region' }).of(code) ?? null; }
        catch (e) { return null; }
    })()""",
)

private fun navigatorRegion(): String? = js(
    """(() => {
        try {
            const language = globalThis.navigator && globalThis.navigator.language;
            return language ? (new Intl.Locale(language).maximize().region ?? null) : null;
        } catch (e) { return null; }
    })()""",
)
