package com.example.cricketscorer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cricketscorer.data.CricketRepository
import com.example.cricketscorer.stats.OverallDashboardCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Backs the Home "Dashboard" screen: all-time team and player numbers across every match. */
class OverallDashboardViewModel(private val repository: CricketRepository) : ViewModel() {

    private val _data = MutableStateFlow(OverallDashboardCalculator.DashboardData.EMPTY)
    val data: StateFlow<OverallDashboardCalculator.DashboardData> = _data.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        // Re-aggregate whenever matches change (new match, match completed, delete) or the
        // player merges change.
        viewModelScope.launch {
            repository.observeAllMatches().collect { recompute() }
        }
        viewModelScope.launch {
            repository.observePlayerMerges().collect { recompute() }
        }
    }

    private suspend fun recompute() {
        val snap = repository.getFullBackupSnapshot()
        _data.value = OverallDashboardCalculator.compute(
            snap.matches, snap.innings, snap.ballEvents, repository.getPlayerMerges()
        )
        _loading.value = false
    }
}
