@file:OptIn(ExperimentalWasmJsInterop::class)

package com.royalchance.data.settings

/**
 * Preferencias en `localStorage` del navegador. Si el almacenamiento no está disponible (modo
 * privado estricto, políticas del navegador), la app sigue funcionando con los valores por defecto.
 */
class BrowserKeyValueStore(private val prefix: String = "royalchance.") : KeyValueStore {
    override fun getString(key: String): String? = localStorageGet(prefix + key)

    override fun putString(key: String, value: String) {
        localStorageSet(prefix + key, value)
    }

    override fun remove(key: String) {
        localStorageRemove(prefix + key)
    }
}

private fun localStorageGet(key: String): String? =
    js("(() => { try { return globalThis.localStorage.getItem(key); } catch (e) { return null; } })()")

private fun localStorageRemove(key: String): Unit =
    js("(() => { try { globalThis.localStorage.removeItem(key); } catch (e) { } })()")

private fun localStorageSet(key: String, value: String): Unit =
    js("(() => { try { globalThis.localStorage.setItem(key, value); } catch (e) { } })()")
