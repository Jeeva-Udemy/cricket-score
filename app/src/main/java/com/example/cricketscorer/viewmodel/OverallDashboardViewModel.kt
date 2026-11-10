package com.example.cricketscorer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cricketscorer.data.BackupSnapshot
import com.example.cricketscorer.data.CricketRepository
import com.example.cricketscorer.data.PlayerMergeEntity
import com.example.cricketscorer.stats.OverallDashboardCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backs the Home "Dashboard" screen: team and player numbers across every match ("All
 * matches"), or — picked from the day selector — only the matches played on one day.
 */
class OverallDashboardViewModel(private val repository: CricketRepository) : ViewModel() {

    /** One selectable day: [key] is yyyy-MM-dd (local time), [label] e.g. "Sat, 08 Nov 2026". */
    data class DayOption(val key: String, val label: String, val matches: Int)

    private val _data = MutableStateFlow(OverallDashboardCalculator.DashboardData.EMPTY)
    val data: StateFlow<OverallDashboardCalculator.DashboardData> = _data.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _days = MutableStateFlow<List<DayOption>>(emptyList())
    val days: StateFlow<List<DayOption>> = _days.asStateFlow()

    /** Null = all matches. */
    private val _selectedDay = MutableStateFlow<String?>(null)
    val selectedDay: StateFlow<String?> = _selectedDay.asStateFlow()

    private var snapshot: BackupSnapshot? = null
    private var merges: List<PlayerMergeEntity> = emptyList()

    init {
        // Re-aggregate whenever matches change (new match, match completed, delete) or the
        // player merges change.
        viewModelScope.launch {
            repository.observeAllMatches().collect { reload() }
        }
        viewModelScope.launch {
            repository.observePlayerMerges().collect { reload() }
        }
    }

    fun selectDay(dayKey: String?) {
        _selectedDay.value = dayKey
        recompute()
    }

    private suspend fun reload() {
        snapshot = repository.getFullBackupSnapshot()
        merges = repository.getPlayerMerges()
        recompute()
    }

    private fun recompute() {
        val snap = snapshot ?: return
        val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val labelFmt = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
        val byDay = snap.matches.groupBy { keyFmt.format(Date(it.createdAt)) }
        _days.value = byDay.entries
            .sortedByDescending { it.key }
            .map { (k, list) -> DayOption(k, labelFmt.format(Date(list.first().createdAt)), list.size) }

        // A day that no longer has any match (e.g. all deleted) falls back to "All matches".
        val day = _selectedDay.value?.takeIf { it in byDay }
        if (day != _selectedDay.value) _selectedDay.value = day

        val matches = if (day == null) snap.matches else byDay[day].orEmpty()
        val ids = matches.map { it.matchId }.toSet()
        val innings = snap.innings.filter { it.matchId in ids }
        val inningsIds = innings.map { it.inningsId }.toSet()
        val balls = snap.ballEvents.filter { it.inningsId in inningsIds }
        _data.value = OverallDashboardCalculator.compute(matches, innings, balls, merges)
        _loading.value = false
    }
}
