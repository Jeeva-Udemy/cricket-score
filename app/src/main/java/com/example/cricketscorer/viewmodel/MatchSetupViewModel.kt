package com.example.cricketscorer.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cricketscorer.data.CloudDeviceIdStore
import com.example.cricketscorer.data.CricketRepository
import com.example.cricketscorer.data.DeviceMatchRoleStore
import com.example.cricketscorer.data.InningsEntity
import com.example.cricketscorer.data.MatchEntity
import com.example.cricketscorer.data.PlayerEntity
import com.example.cricketscorer.data.SquadEntity
import com.example.cricketscorer.model.TossDecision
import com.example.cricketscorer.sync.CloudSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MatchSetupViewModel(
    private val repository: CricketRepository,
    private val appContext: Context
) : ViewModel() {

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
        // req: striker/non-striker/opening bowler are mandatory — no silent
        // "Batsman 1" / "Bowler 1" placeholders. The user must either pick from the
        // squad dropdown or type a name for each.
        if (strikerName.isBlank()) {
            errorMessage = "Select or enter the striker's name."
            return
        }
        if (nonStrikerName.isBlank()) {
            errorMessage = "Select or enter the non-striker's name."
            return
        }
        if (strikerName.trim().equals(nonStrikerName.trim(), ignoreCase = true)) {
            errorMessage = "Striker and non-striker must be different players."
            return
        }
        if (openingBowlerName.isBlank()) {
            errorMessage = "Select or enter the opening bowler's name."
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
                // Validated non-blank above — no placeholder fallback needed.
                strikerName = strikerName.trim(),
                nonStrikerName = nonStrikerName.trim(),
                currentBowlerName = openingBowlerName.trim()
            )
            val inningsId = repository.createInnings(firstInnings)

            // req #3/#4: the device that creates the match is, by definition, the one whose
            // user just filled in Team A's details — record it locally as "Team A's device"
            // so this phone knows from the start whether it's the one allowed to score the
            // current innings once a second phone joins via Cloud Sync. (The joining device
            // picks its own team explicitly — see HomeViewModel.confirmJoinTeam.)
            DeviceMatchRoleStore.setMyTeam(appContext, matchId, teamA)

            // Cloud Sync: every match gets a share code up front so the Scoring screen can
            // show it immediately ("Match Code: XXXXXX") for the other phone to join via
            // Home > Join Shared Match. Best-effort — if there's no network or Firebase
            // isn't configured yet (see SYNC_SETUP.md), the initial push fails and we
            // simply leave the match without a share code; scoring still works purely
            // locally either way, ScoringViewModel only tries to sync when shareCode != null.
            runCatching {
                val code = CloudSync.generateShareCode()
                val matchWithCode = match.copy(matchId = matchId, shareCode = code)
                val snapshot = repository.getSnapshotForMatch(matchId).copy(matches = listOf(matchWithCode))
                // Must use the SAME stable per-device id that ScoringViewModel's listener will
                // use once it attaches for this match — otherwise this device won't recognize
                // this very first push as "self" and will replay it back over the first balls
                // scored, reverting them. See CloudDeviceIdStore.
                CloudSync.pushSnapshot(code, snapshot, deviceId = CloudDeviceIdStore.getDeviceId(appContext))
                repository.updateMatch(matchWithCode)
            }

            onCreated(matchId, inningsId)
        }
    }
}
