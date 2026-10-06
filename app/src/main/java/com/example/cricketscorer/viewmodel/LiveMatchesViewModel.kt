package com.example.cricketscorer.viewmodel

import androidx.lifecycle.ViewModel
import com.example.cricketscorer.sync.CloudSync
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveMatchesUiState(
    val loading: Boolean = true,
    val matches: List<CloudSync.LiveMatchSummary> = emptyList(),
    val error: String? = null
)

/** Backs the "Live Match" tab: every match being scored right now, on any phone. */
class LiveMatchesViewModel : ViewModel() {

    private val _state = MutableStateFlow(LiveMatchesUiState())
    val state: StateFlow<LiveMatchesUiState> = _state.asStateFlow()

    private var listener: ListenerRegistration? = null

    init { start() }

    fun start() {
        listener?.remove()
        _state.value = _state.value.copy(loading = true, error = null)
        listener = runCatching {
            CloudSync.listenLiveMatches(
                onUpdate = { _state.value = LiveMatchesUiState(loading = false, matches = it) },
                onError = { _state.value = _state.value.copy(loading = false, error = it) }
            )
        }.getOrElse {
            _state.value = LiveMatchesUiState(loading = false, error = "Live matches unavailable")
            null
        }
    }

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}
