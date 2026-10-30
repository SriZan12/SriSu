package com.srisu.srisu.features.home.profile.presentation.state

import com.srisu.srisu.core.data.remote.ApiError
import com.srisu.srisu.features.auth.data.remote.response.InterestResponse
import com.srisu.srisu.features.home.profile.data.CatalogueResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class InterestCatalogueState(
    val loading: Boolean = true,
    val items: List<InterestResponse.Interest?> = emptyList(),
    val offline: Boolean = false,
    val error: ApiError? = null,
    val loaded: Boolean = false,
)

class InterestCatalogueStateHolder(
    private val scope: CoroutineScope,
    private val load: suspend (Boolean) -> CatalogueResult,
) {
    private val _state = MutableStateFlow(InterestCatalogueState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    fun refresh(force: Boolean = false) {
        job?.cancel()
        job = scope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = load(force)
            ensureActive()
            _state.value = InterestCatalogueState(
                loading = false, items = result.value?.interests.orEmpty(), offline = result.offline,
                error = result.error, loaded = result.value != null,
            )
        }
    }
}
