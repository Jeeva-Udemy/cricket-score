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
import com.example.cricketscorer.data.RoomStore
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

    // Rooms: req #5 — "If I create a match in Start Match it should not go into the room
    // match... For updating it with 2 devices for each team we have Room Match only." A match
    // must only be tied to a Room when it's explicitly started *from* that Room (Room Detail's
    // "Start Match"), never just because this device happens to be sitting in some active room
    // while the user taps Home's plain "Start Match". So this is no longer read implicitly at
    // construction time — the caller passes the room code explicitly via [configureForRoom].
    private var activeRoom by mutableStateOf<RoomStore.ActiveRoom?>(null)
        private set
    val activeRoomCode: String? get() = activeRoom?.roomCode

    private var roomConfigured = false

    /** Called once, right after this screen is shown (see MatchSetupScreen's
     *  LaunchedEffect(roomCode)). [roomCode] is null when reached from Home's "Start Match" —
     *  the match stays purely local/offline, single-device, and never becomes a Room match.
     *  When non-null (Room Detail's "Start Match"), it's cross-checked against the room this
     *  device is actually in before being trusted, so a stale/mismatched code can't sneak a
     *  match into the wrong room. Idempotent so recomposition can't re-run it. */
    fun configureForRoom(roomCode: String?) {
        if (roomConfigured) return
        roomConfigured = true
        if (roomCode == null) return
        activeRoom = RoomStore.getActiveRoom(appContext)?.takeIf { it.roomCode == roomCode }
    }

    // req: "select who's going to update the score for the 1st innings while creating the
    // match itself" — which team THIS device will score for. Only meaningful (and shown in
    // the UI) when [activeRoomCode] != null; a purely local match has only one device anyway.
    var scoringTeam by mutableStateOf<String?>(null)

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
        // req: when creating a match inside a Room, the user must explicitly say which team
        // THIS device is scoring the 1st innings for — no silent default to Team A.
        if (activeRoomCode != null && scoringTeam == null) {
            errorMessage = "Select which team you're scoring for."
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

            // req: only matches created inside a Room are shared live between two phones now
            // — Rooms replace the old "every match gets a share code" per-match sharing (see
            // SYNC_SETUP.md). A match created with no active room stays purely local/offline.
            val room = activeRoom
            if (room != null && activeRoomCode != null) {
                // req: which team THIS device scores the 1st innings for, chosen explicitly
                // above instead of always assuming Team A — the OTHER device in the room
                // learns it's scoring for whichever team is left over (see setSlotTeams /
                // HomeViewModel.attachRoomListener), no separate prompt needed on that phone.
                val myScoringTeam = scoringTeam ?: teamA
                val otherTeam = if (myScoringTeam == teamA) teamB else teamA

                // Best-effort — if there's no network, the initial push fails and we simply
                // leave the match without a share code; scoring still works purely locally
                // either way, ScoringViewModel only tries to sync when shareCode != null.
                runCatching {
                    val matchWithCode = match.copy(matchId = matchId, shareCode = activeRoomCode)
                    val snapshot = repository.getSnapshotForMatch(matchId).copy(matches = listOf(matchWithCode))
                    // Must use the SAME stable per-device id that ScoringViewModel's listener
                    // will use once it attaches for this match — otherwise this device won't
                    // recognize this very first push as "self" and will replay it back over
                    // the first balls scored, reverting them. See CloudDeviceIdStore.
                    val deviceId = CloudDeviceIdStore.getDeviceId(appContext)
                    CloudSync.pushSnapshot(activeRoomCode, snapshot, deviceId = deviceId)
                    repository.updateMatch(matchWithCode)
                    CloudSync.setSlotTeams(activeRoomCode, room.slot, myScoringTeam, otherTeam)
                }
                DeviceMatchRoleStore.setMyTeam(appContext, matchId, myScoringTeam)
            }

            onCreated(matchId, inningsId)
        }
    }
}
