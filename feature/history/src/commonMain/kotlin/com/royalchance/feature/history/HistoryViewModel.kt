package com.royalchance.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.royalchance.core.common.result.Outcome
import com.royalchance.domain.economy.EconomyRepository
import com.royalchance.domain.economy.WalletState
import com.royalchance.domain.game.GameType
import com.royalchance.domain.history.GameStats
import com.royalchance.domain.history.HistoryItem
import com.royalchance.domain.history.HistoryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val loading: Boolean = true,
    val items: List<HistoryItem> = emptyList(),
    /** Asiento desde el que pedir la página siguiente; `null` si no hay más. */
    val nextBefore: Long? = null,
    val loadingMore: Boolean = false,
    val stats: Map<GameType, GameStats> = emptyMap(),
    /** No se pudo leer el historial (sin conexión): se ofrece reintentar. */
    val failed: Boolean = false,
)

/**
 * Historial de jugadas y estadísticas por juego. Se recarga solo cuando el monedero registra un
 * movimiento nuevo (al volver de una mesa, por ejemplo).
 */
class HistoryViewModel(
    private val historyRepository: HistoryRepository,
    economyRepository: EconomyRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            economyRepository.wallet
                .map { (it as? WalletState.Ready)?.wallet?.sequence }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { reload() }
        }
    }

    fun retry() = reload()

    fun loadMore() {
        val current = _state.value
        val before = current.nextBefore ?: return
        if (current.loadingMore || current.loading) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            when (val page = historyRepository.page(before)) {
                is Outcome.Success -> _state.update {
                    it.copy(items = it.items + page.value.items, nextBefore = page.value.nextBefore, loadingMore = false)
                }
                is Outcome.Failure -> _state.update { it.copy(loadingMore = false, failed = true) }
            }
        }
    }

    private fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = it.items.isEmpty(), failed = false) }
            val page = historyRepository.page()
            val stats = historyRepository.statistics()
            _state.update { current ->
                current.copy(
                    loading = false,
                    items = (page as? Outcome.Success)?.value?.items ?: current.items,
                    nextBefore = (page as? Outcome.Success)?.value?.nextBefore ?: current.nextBefore,
                    stats = (stats as? Outcome.Success)?.value ?: current.stats,
                    failed = page is Outcome.Failure,
                )
            }
        }
    }
}
