package com.example.cricketscorer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cricketscorer.data.CricketRepository
import com.example.cricketscorer.data.PlayerEntity
import com.example.cricketscorer.data.SquadEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the Squad tab: create/rename/delete saved team rosters, and
 * add/rename/delete players within whichever squad is currently expanded.
 */
class SquadViewModel(private val repository: CricketRepository) : ViewModel() {

    private val _squads = MutableStateFlow<List<SquadEntity>>(emptyList())
    val squads: StateFlow<List<SquadEntity>> = _squads.asStateFlow()

    // squadId -> players, kept for every squad the user has expanded this session
    private val _playersBySquad = MutableStateFlow<Map<Long, List<PlayerEntity>>>(emptyMap())
    val playersBySquad: StateFlow<Map<Long, List<PlayerEntity>>> = _playersBySquad.asStateFlow()

    private val playerObserveJobs = mutableMapOf<Long, Job>()

    init {
        viewModelScope.launch {
            repository.observeAllSquads().collect { list ->
                _squads.value = list
                // Start observing players for any squad we haven't subscribed to yet
                list.forEach { squad -> ensurePlayersObserved(squad.squadId) }
            }
        }
    }

    fun ensurePlayersObserved(squadId: Long) {
        if (playerObserveJobs.containsKey(squadId)) return
        playerObserveJobs[squadId] = viewModelScope.launch {
            repository.observePlayersForSquad(squadId).collect { players ->
                val map = _playersBySquad.value.toMutableMap()
                map[squadId] = players
                _playersBySquad.value = map
            }
        }
    }

    fun createSquad(teamName: String) {
        val trimmed = teamName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            repository.createSquad(SquadEntity(teamName = trimmed))
        }
    }

    fun renameSquad(squad: SquadEntity, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            repository.updateSquad(squad.copy(teamName = trimmed))
        }
    }

    fun deleteSquad(squadId: Long) {
        viewModelScope.launch {
            repository.deleteSquad(squadId)
        }
    }

    fun addPlayer(squadId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            repository.addPlayer(PlayerEntity(squadId = squadId, name = trimmed))
        }
    }

    fun renamePlayer(player: PlayerEntity, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            repository.updatePlayer(player.copy(name = trimmed))
        }
    }

    fun deletePlayer(playerId: Long) {
        viewModelScope.launch {
            repository.deletePlayer(playerId)
        }
    }
}
