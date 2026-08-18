package com.example.cricketscorer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cricketscorer.data.CricketRepository
import com.example.cricketscorer.data.MatchEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: CricketRepository) : ViewModel() {

    private val _matches = MutableStateFlow<List<MatchEntity>>(emptyList())
    val matches: StateFlow<List<MatchEntity>> = _matches.asStateFlow()

    private val _selectedMatchIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedMatchIds: StateFlow<Set<Long>> = _selectedMatchIds.asStateFlow()

    val isSelectionMode: Boolean
        get() = _selectedMatchIds.value.isNotEmpty()

    init {
        observeMatches()
    }

    private fun observeMatches() {
        viewModelScope.launch {
            repository.observeAllMatches().collect { list ->
                _matches.value = list
                // Clean up selection if any selected matches no longer exist
                val existingIds = list.map { it.matchId }.toSet()
                _selectedMatchIds.value = _selectedMatchIds.value.filter { it in existingIds }.toSet()
            }
        }
    }

    fun toggleMatchSelection(matchId: Long) {
        val current = _selectedMatchIds.value.toMutableSet()
        if (current.contains(matchId)) {
            current.remove(matchId)
        } else {
            current.add(matchId)
        }
        _selectedMatchIds.value = current
    }

    fun selectAll() {
        _selectedMatchIds.value = _matches.value.map { it.matchId }.toSet()
    }

    fun clearSelection() {
        _selectedMatchIds.value = emptySet()
    }

    fun deleteSelectedMatches() {
        val idsToDelete = _selectedMatchIds.value.toList()
        if (idsToDelete.isEmpty()) return

        viewModelScope.launch {
            repository.deleteMatches(idsToDelete)
            _selectedMatchIds.value = emptySet()
        }
    }
}
