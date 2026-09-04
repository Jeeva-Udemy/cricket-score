package com.example.cricketscorer.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cricketscorer.data.BackupSnapshot
import com.example.cricketscorer.data.BallEventEntity
import com.example.cricketscorer.data.CloudDeviceIdStore
import com.example.cricketscorer.data.CricketRepository
import com.example.cricketscorer.data.DeviceMatchRoleStore
import com.example.cricketscorer.data.InningsEntity
import com.example.cricketscorer.data.MatchEntity
import com.example.cricketscorer.data.RoomStore
import com.example.cricketscorer.stats.PlayerStatsCalculator
import com.example.cricketscorer.sync.CloudSync
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State for the Rooms screen (Cloud Sync — req: two phones, one per team, playing several
 * matches back-to-back in the same room without re-sharing a code each time).
 */
sealed class RoomUiState {
    /** Not currently a member of any room (still may have rooms in [RoomsViewModel.roomHistory]
     *  from before). */
    object NotInRoom : RoomUiState()
    /** A create/join call is in flight. */
    object Working : RoomUiState()
    /**
     * A member of [roomCode], holding slot [mySlot] (1 or 2 — of at most two, req: "only 2
     * device should be able to join the room 1 for each team"). [devicesConnected] is how many
     * of the two slots are currently occupied (by either device). [currentMatchId] is set once
     * this device has learned of the room's live match (either because it just created one, or
     * because the room listener picked one up from the other device).
     */
    data class InRoom(
        val roomCode: String,
        val mySlot: Int,
        val devicesConnected: Int,
        val currentMatchId: Long? = null
    ) : RoomUiState()
    data class Error(val message: String) : RoomUiState()
}

/**
 * Backs the dedicated Rooms screen + Room Detail screen (req: "Show the Rooms created inside
 * the Rooms instead of showing it in the Home page and show the list of matches inside the
 * each Rooms that we created" — Rooms used to be a dialog opened from Home; they're now their
 * own screens, and each room lists every match ever played inside it via [matchesForRoom]).
 *
 * [deviceId] and [_roomState] are declared BEFORE `init {}` deliberately. This exact class used
 * to be part of HomeViewModel with those two declared AFTER its `init {}` block, which called
 * code depending on both — Kotlin runs property initializers and `init {}` blocks in strict
 * textual order, so constructing that ViewModel while the device already belonged to a room
 * threw a NullPointerException immediately. That crashed both "back out of Scoring to Home"
 * and "open Match History", since both recreate a HomeViewModel from scratch. Keep every
 * property `init {}` touches declared above it.
 */
class RoomsViewModel(
    private val repository: CricketRepository,
    private val appContext: Context
) : ViewModel() {

    private val deviceId = CloudDeviceIdStore.getDeviceId(appContext)

    private val _roomState = MutableStateFlow<RoomUiState>(RoomUiState.NotInRoom)
    val roomState: StateFlow<RoomUiState> = _roomState.asStateFlow()

    private val _roomHistory = MutableStateFlow<List<RoomStore.SavedRoom>>(emptyList())
    val roomHistory: StateFlow<List<RoomStore.SavedRoom>> = _roomHistory.asStateFlow()

    private var roomListener: ListenerRegistration? = null

    init {
        refreshRoomHistory()
        restoreActiveRoomIfAny()
    }

    private fun refreshRoomHistory() {
        _roomHistory.value = RoomStore.getRoomHistory(appContext)
    }

    /** The matches played inside [roomCode] (req: "show the list of matches inside the each
     *  Rooms") — every match created while this room was active reuses the room's code as its
     *  own `shareCode` (see MatchSetupViewModel.startMatch), so this is a straight lookup. */
    fun matchesForRoom(roomCode: String): Flow<List<MatchEntity>> =
        repository.observeMatchesForRoom(roomCode)

    /** Whether THIS device currently holds a slot in [roomCode] (vs. it just being in local
     *  history from a room this device has since exited) — gates whether Room Detail offers
     *  "Start Match" / "Exit Room" for it, vs. "Rejoin Room". */
    fun isActiveRoom(roomCode: String): Boolean =
        RoomStore.getActiveRoom(appContext)?.roomCode == roomCode

    /** req: "For each match we need to show who's the Player of the Match." Computed on demand
     *  from that match's own ball events — see [PlayerStatsCalculator]. Null for a match with
     *  no balls recorded, or one that isn't completed yet. */
    suspend fun playerOfTheMatch(match: MatchEntity): PlayerStatsCalculator.PlayerAward? {
        if (!match.isCompleted) return null
        val snapshot = repository.getSnapshotForMatch(match.matchId)
        return PlayerStatsCalculator.computePlayerOfTheMatch(match, snapshot.innings, snapshot.ballEvents)
    }

    /** req: "If we are playing multiple matches in a single room then we need to show who's
     *  the Player of the series." Only meaningful once at least two matches in the room have
     *  actually finished — otherwise there's nothing to compare yet. */
    suspend fun playerOfTheSeries(roomMatches: List<MatchEntity>): PlayerStatsCalculator.PlayerAward? {
        val completed = roomMatches.filter { it.isCompleted }
        if (completed.size < 2) return null
        val innings = mutableListOf<InningsEntity>()
        val ballEvents = mutableListOf<BallEventEntity>()
        completed.forEach { match ->
            val snapshot = repository.getSnapshotForMatch(match.matchId)
            innings += snapshot.innings
            ballEvents += snapshot.ballEvents
        }
        return PlayerStatsCalculator.computePlayerOfTheSeries(completed, innings, ballEvents)
    }

    /** Picks back up an already-joined room on this ViewModel's construction, so the user
     *  doesn't have to re-enter the code every time they come back to the Rooms screen. */
    private fun restoreActiveRoomIfAny() {
        val active = RoomStore.getActiveRoom(appContext) ?: return
        _roomState.value = RoomUiState.InRoom(active.roomCode, active.slot, devicesConnected = 1)
        // Belt-and-suspenders: attachRoomListener() calls straight into Firestore
        // synchronously (addSnapshotListener). This runs from init{} on every navigation into
        // Rooms/Room Detail, so a runtime hiccup there (e.g. Play Services not ready yet) must
        // never be allowed to throw out of a ViewModel constructor again — that's exactly the
        // class of bug that used to crash Home/Match History (see this class's header comment).
        runCatching { attachRoomListener(active.roomCode, active.slot) }
        viewModelScope.launch {
            runCatching { CloudSync.fetchRoom(active.roomCode) }
                .onSuccess { info -> if (info != null) applyRoomInfo(active.roomCode, active.slot, info) }
        }
    }

    /** Creates a brand-new room, claiming its first slot for this device. */
    fun createRoom() {
        _roomState.value = RoomUiState.Working
        viewModelScope.launch {
            runCatching { CloudSync.createRoom(deviceId) }
                .onSuccess { code ->
                    RoomStore.setActiveRoom(appContext, code, slot = 1)
                    RoomStore.upsertRoomHistory(appContext, RoomStore.SavedRoom(roomCode = code, slot = 1))
                    refreshRoomHistory()
                    _roomState.value = RoomUiState.InRoom(code, mySlot = 1, devicesConnected = 1)
                    attachRoomListener(code, 1)
                }
                .onFailure {
                    _roomState.value = RoomUiState.Error(
                        it.message ?: "Couldn't create a room. Check your connection and try again."
                    )
                }
        }
    }

    /** Called with the 6-character code shown on the other phone's Room screen (or scanned from
     *  its QR code), or to rejoin a room from this device's own history. Claims whichever slot
     *  is free (or this device's own slot, if it already had one) — see [CloudSync.joinRoom]
     *  for the "room is full" case. */
    fun joinRoomByCode(rawCode: String) {
        val code = rawCode.trim().uppercase()
        if (code.isBlank()) {
            _roomState.value = RoomUiState.Error("Enter the room code.")
            return
        }
        _roomState.value = RoomUiState.Working
        viewModelScope.launch {
            runCatching { CloudSync.joinRoom(code, deviceId) }
                .onSuccess { result ->
                    when (result) {
                        is CloudSync.JoinRoomResult.Joined -> {
                            RoomStore.setActiveRoom(appContext, code, result.slot)
                            RoomStore.upsertRoomHistory(
                                appContext,
                                RoomStore.SavedRoom(roomCode = code, slot = result.slot)
                            )
                            refreshRoomHistory()
                            applyRoomInfo(code, result.slot, result.info)
                            attachRoomListener(code, result.slot)
                        }
                        CloudSync.JoinRoomResult.Full -> _roomState.value = RoomUiState.Error(
                            "Room \"$code\" already has 2 devices. Ask a teammate to exit first."
                        )
                        CloudSync.JoinRoomResult.NotFound -> _roomState.value = RoomUiState.Error(
                            "No room found for code \"$code\". Double-check it on the other phone."
                        )
                    }
                }
                .onFailure {
                    _roomState.value = RoomUiState.Error(
                        it.message ?: "Couldn't reach the room. Check your connection and try again."
                    )
                }
        }
    }

    /** (Re)attaches the listener that mirrors the room's *current* match into local storage —
     *  this is what lets the second device pick up a brand-new match the moment the first
     *  device starts it. Also resolves which team THIS device is scoring for automatically
     *  from the room's slot/team mapping (see [CloudSync.setSlotTeams]), and caches both team
     *  names into [RoomStore]'s history so the Rooms list can show "Team A vs Team B" even
     *  offline. */
    /** See the flicker-fix comment at [attachRoomListener]'s [CloudSync.listen] call — true
     *  when [snapshot]'s match/innings/ball data already matches what's stored locally.
     *  Order-independent (compares as sets) and deliberately conservative: a false "different"
     *  just costs one redundant, harmless re-apply, never skips a real change. */
    private suspend fun isMatchSnapshotAlreadyApplied(matchId: Long, snapshot: BackupSnapshot): Boolean {
        val local = repository.getSnapshotForMatch(matchId)
        return local.matches.toSet() == snapshot.matches.toSet() &&
            local.innings.toSet() == snapshot.innings.toSet() &&
            local.ballEvents.toSet() == snapshot.ballEvents.toSet()
    }

    private fun attachRoomListener(code: String, mySlot: Int) {
        roomListener?.remove()
        roomListener = CloudSync.listen(code, deviceId) { snapshot ->
            viewModelScope.launch {
                val match = snapshot.matches.firstOrNull() ?: return@launch
                // req: "flickering issue ... lock the device and open it again to continue" —
                // once the match is open, ScoringViewModel attaches its OWN listener on this
                // same Firestore document and this one stays alive too (Room Detail is still on
                // the back stack underneath Scoring), so both can fire for the same remote
                // update. Applying unconditionally here caused a redundant Room write racing
                // against ScoringViewModel's own echo-detection, which could misread the
                // resulting Flow emission as a real local edit and push it straight back to
                // Firestore — a slow-motion ping-pong between the two devices that looked like
                // constant flicker until the screen was locked and reopened. Skipping the write
                // when the local DB already matches closes that race (see ScoringViewModel's
                // matching guard).
                if (!isMatchSnapshotAlreadyApplied(match.matchId, snapshot)) {
                    repository.applyMatchSnapshot(snapshot)
                }
                runCatching { CloudSync.fetchRoom(code) }.getOrNull()?.let { info ->
                    val myTeam = if (mySlot == 1) info.slot1Team else info.slot2Team
                    val otherTeam = if (mySlot == 1) info.slot2Team else info.slot1Team
                    if (myTeam != null) {
                        DeviceMatchRoleStore.setMyTeam(appContext, match.matchId, myTeam)
                        RoomStore.upsertRoomHistory(
                            appContext,
                            RoomStore.SavedRoom(roomCode = code, slot = mySlot, myTeam = myTeam, otherTeam = otherTeam)
                        )
                        refreshRoomHistory()
                    }
                    _roomState.value = RoomUiState.InRoom(code, mySlot, info.slotsFilled, match.matchId)
                }
            }
        }
    }

    private fun applyRoomInfo(code: String, mySlot: Int, info: CloudSync.RoomInfo) {
        val currentMatchId = (_roomState.value as? RoomUiState.InRoom)?.currentMatchId
        _roomState.value = RoomUiState.InRoom(code, mySlot, info.slotsFilled, currentMatchId)
        val myTeam = if (mySlot == 1) info.slot1Team else info.slot2Team
        val otherTeam = if (mySlot == 1) info.slot2Team else info.slot1Team
        if (myTeam != null) {
            RoomStore.upsertRoomHistory(
                appContext,
                RoomStore.SavedRoom(roomCode = code, slot = mySlot, myTeam = myTeam, otherTeam = otherTeam)
            )
            refreshRoomHistory()
        }
    }

    /** req: "there should be an Exit button to exit from the room" — e.g. the person scoring
     *  has to leave the ground mid-match and hands the phone's role off to someone else's
     *  device. Clears this device's active membership immediately/locally either way (the room
     *  stays visible in [roomHistory], just no longer "active" — see [isActiveRoom]), and
     *  best-effort frees the slot on Firestore so a third device can claim it. */
    fun exitRoom() {
        val current = _roomState.value as? RoomUiState.InRoom ?: return
        roomListener?.remove()
        roomListener = null
        RoomStore.clearActiveRoom(appContext)
        _roomState.value = RoomUiState.NotInRoom
        viewModelScope.launch {
            runCatching { CloudSync.exitRoom(current.roomCode, deviceId, current.mySlot) }
        }
    }

    fun dismissRoomError() {
        _roomState.value = RoomStore.getActiveRoom(appContext)?.let { active ->
            RoomUiState.InRoom(active.roomCode, active.slot, devicesConnected = 1)
        } ?: RoomUiState.NotInRoom
    }

    // ---------- Delete rooms (req #1: "I need to able to delete the Room and Matches inside
    // it ... So keep a delete button to select multiple ... rooms at the same time to delete") ----------

    private val _selectedRoomCodes = MutableStateFlow<Set<String>>(emptySet())
    val selectedRoomCodes: StateFlow<Set<String>> = _selectedRoomCodes.asStateFlow()

    fun toggleRoomSelection(roomCode: String) {
        val current = _selectedRoomCodes.value.toMutableSet()
        if (!current.remove(roomCode)) current.add(roomCode)
        _selectedRoomCodes.value = current
    }

    fun selectAllRooms() {
        _selectedRoomCodes.value = _roomHistory.value.map { it.roomCode }.toSet()
    }

    fun clearRoomSelection() {
        _selectedRoomCodes.value = emptySet()
    }

    /** Deletes every match ever played inside each room in [roomCodes] (locally — "reflect
     *  everywhere" for THIS device), the room's Firestore documents (so it can't be rejoined
     *  or resurface on another device), and drops it from this device's own room history. If a
     *  deleted room happens to be this device's currently active one, also clears that
     *  membership exactly like Exit Room does, so the UI never points at a room that no longer
     *  exists. */
    fun deleteRooms(roomCodes: Set<String>) {
        if (roomCodes.isEmpty()) return
        viewModelScope.launch {
            roomCodes.forEach { code ->
                val matchIds = repository.getMatchesForRoom(code).map { it.matchId }
                if (matchIds.isNotEmpty()) repository.deleteMatches(matchIds)
                runCatching { CloudSync.deleteRoom(code) }
                RoomStore.removeRoomFromHistory(appContext, code)
                if (RoomStore.getActiveRoom(appContext)?.roomCode == code) {
                    roomListener?.remove()
                    roomListener = null
                    RoomStore.clearActiveRoom(appContext)
                    _roomState.value = RoomUiState.NotInRoom
                }
            }
            _selectedRoomCodes.value = emptySet()
            refreshRoomHistory()
        }
    }

    // ---------- Delete matches inside a room ----------

    private val _selectedRoomMatchIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedRoomMatchIds: StateFlow<Set<Long>> = _selectedRoomMatchIds.asStateFlow()

    fun toggleRoomMatchSelection(matchId: Long) {
        val current = _selectedRoomMatchIds.value.toMutableSet()
        if (!current.remove(matchId)) current.add(matchId)
        _selectedRoomMatchIds.value = current
    }

    fun selectAllRoomMatches(matchIds: List<Long>) {
        _selectedRoomMatchIds.value = matchIds.toSet()
    }

    fun clearRoomMatchSelection() {
        _selectedRoomMatchIds.value = emptySet()
    }

    /** Deletes the selected matches from within a room's own match list — the room itself (and
     *  its other matches) is untouched. */
    fun deleteSelectedRoomMatches() {
        val ids = _selectedRoomMatchIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteMatches(ids)
            _selectedRoomMatchIds.value = emptySet()
        }
    }

    override fun onCleared() {
        super.onCleared()
        roomListener?.remove()
    }
}
