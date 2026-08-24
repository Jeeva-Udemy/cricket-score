package com.example.cricketscorer.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cricketscorer.data.CricketRepository
import com.example.cricketscorer.data.InningsEntity
import com.example.cricketscorer.data.MatchEntity
import com.example.cricketscorer.data.PlayerEntity
import com.example.cricketscorer.data.SquadEntity
import com.example.cricketscorer.model.TossDecision
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MatchSetupViewModel(private val repository: CricketRepository) : ViewModel() {

    var teamAName by mutableStateOf("")
    var teamBName by mutableStateOf("")
    var strikerName by mutableStateOf("")
    var nonStrikerName by mutableStateOf("")
    var openingBowlerName by mutableStateOf("")
    var totalOvers by mutableStateOf("20")
    var playersPerTeam by mutableStateOf("11") // custom count — local games rarely field 11
    var tossWinnerTeam by mutableStateOf<String?>(null)
    var tossDecision by mutableStateOf(TossDecision.BAT)
    var errorMessage by mutableStateOf<String?>(null)

    // Squad picking: null means "no saved squad, type names manually"
    var selectedSquadA by mutableStateOf<SquadEntity?>(null)
        private set
    var selectedSquadB by mutableStateOf<SquadEntity?>(null)
        private set

    private val _squads = MutableStateFlow<List<SquadEntity>>(emptyList())
    val squads: StateFlow<List<SquadEntity>> = _squads.asStateFlow()

    private val _squadAPlayers = MutableStateFlow<List<PlayerEntity>>(emptyList())
    val squadAPlayers: StateFlow<List<PlayerEntity>> = _squadAPlayers.asStateFlow()

    private val _squadBPlayers = MutableStateFlow<List<PlayerEntity>>(emptyList())
    val squadBPlayers: StateFlow<List<PlayerEntity>> = _squadBPlayers.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAllSquads().collect { _squads.value = it }
        }
    }

    fun selectSquadForTeamA(squad: SquadEntity?) {
        selectedSquadA = squad
        if (squad != null) {
            teamAName = squad.teamName
            viewModelScope.launch {
                _squadAPlayers.value = repository.getPlayersForSquad(squad.squadId)
            }
        } else {
            _squadAPlayers.value = emptyList()
        }
    }

    fun selectSquadForTeamB(squad: SquadEntity?) {
        selectedSquadB = squad
        if (squad != null) {
            teamBName = squad.teamName
            viewModelScope.launch {
                _squadBPlayers.value = repository.getPlayersForSquad(squad.squadId)
            }
        } else {
            _squadBPlayers.value = emptyList()
        }
    }

    /**
     * Validates the form, persists the Match + its first Innings, then hands the
     * generated ids back via [onCreated] so the caller can navigate to scoring.
     */
    fun startMatch(onCreated: (matchId: Long, inningsId: Long) -> Unit) {
        val overs = totalOvers.toIntOrNull()
        val playerCount = playersPerTeam.toIntOrNull()

        if (teamAName.isBlank() || teamBName.isBlank()) {
            errorMessage = "Both team names are required."
            return
        }
        if (teamAName.trim().equals(teamBName.trim(), ignoreCase = true)) {
            errorMessage = "Team names must be different."
            return
        }
        if (overs == null || overs <= 0) {
            errorMessage = "Enter a valid number of overs."
            return
        }
        if (playerCount == null || playerCount < 2) {
            errorMessage = "Enter a valid number of players per team (at least 2)."
            return
        }
        val winner = tossWinnerTeam
        if (winner == null) {
            errorMessage = "Select the toss winner."
            return
        }
        errorMessage = null

        val teamA = teamAName.trim()
        val teamB = teamBName.trim()

        viewModelScope.launch {
            val match = MatchEntity(
                teamAName = teamA,
                teamBName = teamB,
                totalOvers = overs,
                playersPerTeam = playerCount,
                teamASquadId = selectedSquadA?.squadId,
                teamBSquadId = selectedSquadB?.squadId,
                tossWinnerTeam = winner,
                tossDecision = tossDecision,
                currentInningsNumber = 1
            )
            val matchId = repository.createMatch(match)

            val battingFirst: String
            val bowlingFirst: String
            val battingSquadId: Long?
            val bowlingSquadId: Long?
            if (tossDecision == TossDecision.BAT) {
                battingFirst = winner
                bowlingFirst = if (winner == teamA) teamB else teamA
            } else {
                bowlingFirst = winner
                battingFirst = if (winner == teamA) teamB else teamA
            }
            if (battingFirst == teamA) {
                battingSquadId = selectedSquadA?.squadId
                bowlingSquadId = selectedSquadB?.squadId
            } else {
                battingSquadId = selectedSquadB?.squadId
                bowlingSquadId = selectedSquadA?.squadId
            }

            val firstInnings = InningsEntity(
                matchId = matchId,
                inningsNumber = 1,
                battingTeam = battingFirst,
                bowlingTeam = bowlingFirst,
                battingSquadId = battingSquadId,
                bowlingSquadId = bowlingSquadId,
                strikerName = strikerName.ifBlank { "Batsman 1" },
                nonStrikerName = nonStrikerName.ifBlank { "Batsman 2" },
                currentBowlerName = openingBowlerName.ifBlank { "Bowler 1" }
            )
            val inningsId = repository.createInnings(firstInnings)

            onCreated(matchId, inningsId)
        }
    }
}
