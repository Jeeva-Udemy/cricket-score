package com.example.cricketscorer.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cricketscorer.data.CricketRepository
import com.example.cricketscorer.data.PlayerMergeEntity
import com.example.cricketscorer.data.RecentPlayersStore
import com.example.cricketscorer.data.TeamRole
import com.example.cricketscorer.stats.DuplicateNameSuggester
import com.example.cricketscorer.sync.TeamSyncManager
import com.example.cricketscorer.stats.PlayerIdentity
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
 * from every match's ball events (see [PlayerStatsCalculator]), so it can never drift from a
 * correction made via Undo — there's no separate stats table to keep in sync.
 *
 * Also owns "Merge players": select two or more entries that are really the same person
 * (usually a spelling mismatch typed by hand) and combine them into one. Stats recompute
 * immediately whenever merges change.
 */
class PlayerStatsViewModel(
    private val repository: CricketRepository,
    private val appContext: Context
) : ViewModel() {

    private val _players = MutableStateFlow<List<PlayerStatsCalculator.PlayerCareerStats>>(emptyList())
    val players: StateFlow<List<PlayerStatsCalculator.PlayerCareerStats>> = _players.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _merges = MutableStateFlow<List<PlayerMergeEntity>>(emptyList())
    val merges: StateFlow<List<PlayerMergeEntity>> = _merges.asStateFlow()

    /** Selected [PlayerStatsCalculator.PlayerCareerStats.playerKey]s while in merge mode. */
    private val _selectedKeys = MutableStateFlow<Set<String>>(emptySet())
    val selectedKeys: StateFlow<Set<String>> = _selectedKeys.asStateFlow()

    private val _mergeMode = MutableStateFlow(false)
    val mergeMode: StateFlow<Boolean> = _mergeMode.asStateFlow()

    /** Snackbar text; [undoMergeIds] non-empty = show an "Undo" action that reverts that merge. */
    data class UiMessage(val text: String, val undoMergeIds: List<Long> = emptyList())

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    /** Merge and Revert are only for Admin and Manager (Share Data roles). */
    private val _canMerge = MutableStateFlow(false)
    val canMerge: StateFlow<Boolean> = _canMerge.asStateFlow()

    init {
        viewModelScope.launch {
            val role = TeamSyncManager(repository, appContext).effectiveRole()
            _canMerge.value = role == TeamRole.ADMIN || role == TeamRole.MANAGER
        }
        viewModelScope.launch {
            repository.observePlayerMerges().collect { merges ->
                _merges.value = merges
                recompute(merges)
            }
        }
    }

    private suspend fun recompute(merges: List<PlayerMergeEntity>) {
        // Reuses the same full-database snapshot Backup/Resync already builds — a local
        // scorer app's data volumes make "fetch everything, aggregate in Kotlin" simpler
        // and safer than a bespoke set of stats queries.
        val snapshot = repository.getFullBackupSnapshot()
        _players.value = PlayerStatsCalculator.computePlayerStats(
            snapshot.matches, snapshot.innings, snapshot.ballEvents, merges
        )
        val validKeys = _players.value.map { it.playerKey }.toSet()
        _selectedKeys.value = _selectedKeys.value.filter { it in validKeys }.toSet()
        _isLoading.value = false
    }

    /** Likely spelling mismatches to offer as one-tap selections in merge mode. */
    fun suggestions(): List<DuplicateNameSuggester.Suggestion> =
        DuplicateNameSuggester.suggest(_players.value)

    fun setMergeMode(enabled: Boolean) {
        if (enabled && !_canMerge.value) return
        _mergeMode.value = enabled
        if (!enabled) _selectedKeys.value = emptySet()
    }

    fun toggleSelection(playerKey: String) {
        if (!_canMerge.value) return
        _mergeMode.value = true
        val current = _selectedKeys.value
        _selectedKeys.value = if (playerKey in current) current - playerKey else current + playerKey
    }

    fun selectOnly(keys: Collection<String>) {
        if (!_canMerge.value) return
        _mergeMode.value = true
        _selectedKeys.value = keys.toSet()
    }

    fun selectedPlayers(): List<PlayerStatsCalculator.PlayerCareerStats> =
        _players.value.filter { it.playerKey in _selectedKeys.value }

    /**
     * Combines [selected] into ONE player called [finalName], belonging to [target]'s team.
     * Nothing in the match data is rewritten — merge rows are added, so "Undo" in Merged
     * Players restores the separate entries exactly.
     */
    fun mergePlayers(
        selected: List<PlayerStatsCalculator.PlayerCareerStats>,
        target: PlayerStatsCalculator.PlayerCareerStats,
        finalName: String
    ) {
        val name = PlayerIdentity.clean(finalName)
        if (!_canMerge.value || name.isBlank() || selected.size < 2) return
        val now = System.currentTimeMillis()
        val rows = selected.mapNotNull { p ->
            val isTarget = p.playerKey == target.playerKey
            if (isTarget && p.playerName == name) return@mapNotNull null // already the final name
            PlayerMergeEntity(
                fromName = p.playerName,
                fromTeam = p.team,
                toName = name,
                toTeam = target.team,
                createdAt = now
            )
        }
        viewModelScope.launch {
            val newIds = repository.addPlayerMerges(rows)
            // Stop the misspelt names being offered in the batsman/bowler dropdowns again.
            val misspelt = selected.flatMap { it.mergedNames + it.playerName }
                .filter { PlayerIdentity.keyPart(it) != PlayerIdentity.keyPart(name) }
            RecentPlayersStore.removeNames(appContext, misspelt)
            RecentPlayersStore.addName(appContext, name)
            _message.value = UiMessage("Merged ${selected.size} entries into $name.", undoMergeIds = newIds)
            setMergeMode(false)
        }
    }

    /** Revert: splits merged players back apart (Admin / Manager only). */
    fun undoMerges(mergeIds: List<Long>) {
        if (!_canMerge.value || mergeIds.isEmpty()) return
        viewModelScope.launch {
            repository.deletePlayerMerges(mergeIds)
            _message.value = UiMessage(if (mergeIds.size == 1) "Merge reverted." else "${mergeIds.size} merges reverted.")
        }
    }

    /** Reverts every merge that combined entries into [player]. */
    fun revertPlayer(player: PlayerStatsCalculator.PlayerCareerStats) {
        val ids = _merges.value.filter {
            PlayerIdentity.keyPart(it.toName) == PlayerIdentity.keyPart(player.playerName) &&
                PlayerIdentity.keyPart(it.toTeam) == PlayerIdentity.keyPart(player.team)
        }.map { it.mergeId }
        undoMerges(ids)
    }

    fun isMergedPlayer(player: PlayerStatsCalculator.PlayerCareerStats): Boolean =
        _merges.value.any {
            PlayerIdentity.keyPart(it.toName) == PlayerIdentity.keyPart(player.playerName) &&
                PlayerIdentity.keyPart(it.toTeam) == PlayerIdentity.keyPart(player.team)
        }

    fun revertAll() = undoMerges(_merges.value.map { it.mergeId })

    fun clearMessage() {
        _message.value = null
    }
}
