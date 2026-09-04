package com.example.cricketscorer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cricketscorer.data.CricketRepository
import com.example.cricketscorer.stats.PlayerStatsCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs both Player Stats and Rankings — same underlying per-player career aggregate (req:
 * "display the list of players and their details ... how much he scored, wickets taken, best
 * bowling figure" / "show the list of players by their ranking based on batting and bowling
 * performance separately"); the two screens just sort/present it differently. Recomputed fresh
 * from every match's ball events each time this ViewModel is created (see
 * [PlayerStatsCalculator]), so it can never drift from a correction made via Undo — there's no
 * separate stats table to keep in sync.
 */
class PlayerStatsViewModel(
    private val repository: CricketRepository
) : ViewModel() {

    private val _players = MutableStateFlow<List<PlayerStatsCalculator.PlayerCareerStats>>(emptyList())
    val players: StateFlow<List<PlayerStatsCalculator.PlayerCareerStats>> = _players.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            // Reuses the same full-database snapshot Backup/Resync already builds — a local
            // scorer app's data volumes make "fetch everything, aggregate in Kotlin" simpler
            // and safer than a bespoke set of stats queries, and it's already exactly the
            // matches/innings/ball_events this calculation needs.
            val snapshot = repository.getFullBackupSnapshot()
            _players.value = PlayerStatsCalculator.computePlayerStats(
                snapshot.matches, snapshot.innings, snapshot.ballEvents
            )
            _isLoading.value = false
        }
    }
}
