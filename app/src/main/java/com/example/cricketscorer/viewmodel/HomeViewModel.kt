package com.example.cricketscorer.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cricketscorer.backup.BackupSerializer
import com.example.cricketscorer.backup.DriveBackupManager
import com.example.cricketscorer.data.CloudDeviceIdStore
import com.example.cricketscorer.data.CricketRepository
import com.example.cricketscorer.data.DeviceMatchRoleStore
import com.example.cricketscorer.data.MatchEntity
import com.example.cricketscorer.data.RoomStore
import com.example.cricketscorer.sync.CloudSync
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the pending Google sign-in was for, so we know what to do once it completes. */
private enum class PendingBackupAction { BACKUP, RESYNC }

sealed class BackupUiState {
    object Idle : BackupUiState()
    object SigningIn : BackupUiState()
    object BackingUp : BackupUiState()
    object Resyncing : BackupUiState()
    data class Success(val message: String) : BackupUiState()
    data class Error(val message: String) : BackupUiState()
}

/**
 * State for the "Room" dialog (Cloud Sync — req: two phones, one per team, playing several
 * matches back-to-back in the same room without re-sharing a code each time).
 */
sealed class RoomUiState {
    /** Not currently a member of any room. */
    object NotInRoom : RoomUiState()
    /** A create/join call is in flight. */
    object Working : RoomUiState()
    /**
     * A member of [roomCode], holding slot [mySlot] (1 or 2 — of at most two, req: "only 2
     * device should be able to join the room 1 for each team"). [devicesConnected] is how many
     * of the two slots are currently occupied (by either device). [currentMatchId] is set once
     * this device has learned of the room's live match (either because it just created one, or
     * because the room listener picked one up from the other device) — used to offer "Open
     * Current Match" without a separate join step.
     */
    data class InRoom(
        val roomCode: String,
        val mySlot: Int,
        val devicesConnected: Int,
        val currentMatchId: Long? = null
    ) : RoomUiState()
    data class Error(val message: String) : RoomUiState()
}

class HomeViewModel(
    private val repository: CricketRepository,
    private val appContext: Context
) : ViewModel() {

    private val _matches = MutableStateFlow<List<MatchEntity>>(emptyList())
    val matches: StateFlow<List<MatchEntity>> = _matches.asStateFlow()

    private val _selectedMatchIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedMatchIds: StateFlow<Set<Long>> = _selectedMatchIds.asStateFlow()

    val isSelectionMode: Boolean
        get() = _selectedMatchIds.value.isNotEmpty()

    private val driveBackupManager by lazy { DriveBackupManager(appContext) }
    private var pendingAction: PendingBackupAction? = null

    private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    val isSignedInToDrive: Boolean
        get() = driveBackupManager.getLastSignedInAccount() != null

    init {
        observeMatches()
        restoreActiveRoomIfAny()
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

    // ---------- Backup & Resync ----------

    /** Call to start (or continue) a backup; launches sign-in first if needed. */
    fun requestBackup(): Intent? = requestAction(PendingBackupAction.BACKUP)

    /** Call to start (or continue) a resync; launches sign-in first if needed. */
    fun requestResync(): Intent? = requestAction(PendingBackupAction.RESYNC)

    /** Returns a sign-in Intent to launch if one is needed, or null if we can proceed immediately. */
    private fun requestAction(action: PendingBackupAction): Intent? {
        val account = driveBackupManager.getLastSignedInAccount()
        return if (account != null) {
            runAction(action)
            null
        } else {
            pendingAction = action
            _backupState.value = BackupUiState.SigningIn
            driveBackupManager.getSignInIntent()
        }
    }

    /** Call from the sign-in ActivityResult callback with the returned Intent data. */
    fun onSignInResult(data: Intent?) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            task.getResult(ApiException::class.java)
            val action = pendingAction
            pendingAction = null
            if (action != null) {
                runAction(action)
            } else {
                _backupState.value = BackupUiState.Idle
            }
        } catch (e: ApiException) {
            pendingAction = null
            _backupState.value = BackupUiState.Error(describeSignInFailure(e))
        } catch (e: Exception) {
            pendingAction = null
            _backupState.value = BackupUiState.Error("Google sign-in was cancelled or failed.")
        }
    }

    /**
     * Turns a Google Sign-In [ApiException] into a message that actually says what went
     * wrong, instead of the old one-size-fits-all "cancelled or failed" text that made this
     * impossible to diagnose. In particular, status code 10 (DEVELOPER_ERROR) — which is
     * what you get when the app's OAuth client / SHA-1 fingerprint isn't registered for this
     * package in Google Cloud Console — looked identical to the user just backing out of the
     * account picker. Those are very different problems and need very different fixes.
     */
    private fun describeSignInFailure(e: ApiException): String {
        val codeName = GoogleSignInStatusCodes.getStatusCodeString(e.statusCode)
        return when (e.statusCode) {
            GoogleSignInStatusCodes.SIGN_IN_CANCELLED ->
                "Sign-in was cancelled."
            GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS ->
                "A sign-in is already in progress. Please wait and try again."
            GoogleSignInStatusCodes.SIGN_IN_FAILED ->
                "Sign-in failed. Check your connection and try again."
            10 -> // DEVELOPER_ERROR — not exposed as a named constant on GoogleSignInStatusCodes
                "Sign-in is misconfigured for this app build (DEVELOPER_ERROR). This means the " +
                    "OAuth client / SHA-1 fingerprint for this package isn't registered in " +
                    "Google Cloud Console yet — ask whoever manages the project to add it."
            7 -> // NETWORK_ERROR
                "No network connection. Check your connection and try again."
            else ->
                "Google sign-in failed ($codeName, code ${e.statusCode})."
        }
    }

    private fun runAction(action: PendingBackupAction) {
        when (action) {
            PendingBackupAction.BACKUP -> backupNow()
            PendingBackupAction.RESYNC -> resyncNow()
        }
    }

    private fun backupNow() {
        val account = driveBackupManager.getLastSignedInAccount() ?: return
        _backupState.value = BackupUiState.BackingUp
        viewModelScope.launch {
            val snapshot = repository.getFullBackupSnapshot()
            val json = BackupSerializer.toJson(snapshot)
            driveBackupManager.uploadBackup(account, json)
                .onSuccess {
                    _backupState.value = BackupUiState.Success(
                        "Backed up ${snapshot.matches.size} match(es) to Google Drive."
                    )
                }
                .onFailure {
                    _backupState.value = BackupUiState.Error(
                        it.message ?: "Backup failed. Check your connection and try again."
                    )
                }
        }
    }

    private fun resyncNow() {
        val account = driveBackupManager.getLastSignedInAccount() ?: return
        _backupState.value = BackupUiState.Resyncing
        viewModelScope.launch {
            driveBackupManager.downloadBackup(account)
                .onSuccess { json ->
                    if (json == null) {
                        _backupState.value = BackupUiState.Error("No backup found on Google Drive yet.")
                    } else {
                        val snapshot = BackupSerializer.fromJson(json)
                        repository.restoreFromBackup(snapshot)
                        _backupState.value = BackupUiState.Success(
                            "Restored ${snapshot.matches.size} match(es) from Google Drive."
                        )
                    }
                }
                .onFailure {
                    _backupState.value = BackupUiState.Error(
                        it.message ?: "Resync failed. Check your connection and try again."
                    )
                }
        }
    }

    fun dismissBackupStatus() {
        _backupState.value = BackupUiState.Idle
    }

    // ---------- Cloud Sync: Rooms ----------
    // req: "create a room instead of shared match, whoever has the room code can join there
    // and in that room we can play multiple matches one after another" — a room's code is
    // reused by every match started inside it (see MatchSetupViewModel.startMatch), so it
    // never needs to be re-shared once the two phones are in the room together.

    private val deviceId = CloudDeviceIdStore.getDeviceId(appContext)

    private val _roomState = MutableStateFlow<RoomUiState>(RoomUiState.NotInRoom)
    val roomState: StateFlow<RoomUiState> = _roomState.asStateFlow()

    private var roomListener: ListenerRegistration? = null

    /** Picks back up an already-joined room on app (re)start, so the user doesn't have to
     *  re-enter the code every time they relaunch the app while mid-session. */
    private fun restoreActiveRoomIfAny() {
        val active = RoomStore.getActiveRoom(appContext) ?: return
        _roomState.value = RoomUiState.InRoom(active.roomCode, active.slot, devicesConnected = 1)
        attachRoomListener(active.roomCode, active.slot)
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

    /** Called with the 6-character code shown on the other phone's Room dialog. Claims
     *  whichever slot is free (or this device's own slot, if it already had one) — see
     *  [CloudSync.joinRoom] for the "room is full" case. */
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
     *  device starts it, without a separate "join" action every time (req: play match after
     *  match in the room). Also resolves which team THIS device is scoring for automatically
     *  from the room's slot/team mapping (see [CloudSync.setSlotTeams]), instead of asking. */
    private fun attachRoomListener(code: String, mySlot: Int) {
        roomListener?.remove()
        roomListener = CloudSync.listen(code, deviceId) { snapshot ->
            viewModelScope.launch {
                val match = snapshot.matches.firstOrNull() ?: return@launch
                repository.applyMatchSnapshot(snapshot)
                runCatching { CloudSync.fetchRoom(code) }.getOrNull()?.let { info ->
                    val myTeam = if (mySlot == 1) info.slot1Team else info.slot2Team
                    if (myTeam != null) DeviceMatchRoleStore.setMyTeam(appContext, match.matchId, myTeam)
                    _roomState.value = RoomUiState.InRoom(code, mySlot, info.slotsFilled, match.matchId)
                }
            }
        }
    }

    private fun applyRoomInfo(code: String, mySlot: Int, info: CloudSync.RoomInfo) {
        val currentMatchId = (_roomState.value as? RoomUiState.InRoom)?.currentMatchId
        _roomState.value = RoomUiState.InRoom(code, mySlot, info.slotsFilled, currentMatchId)
    }

    /** req: "there should be an Exit button to exit from the room" — e.g. the person scoring
     *  has to leave the ground mid-match and hands the phone's role off to someone else's
     *  device. Clears this device's membership immediately/locally either way, and best-effort
     *  frees the slot on Firestore so a third device can claim it. */
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

    override fun onCleared() {
        super.onCleared()
        roomListener?.remove()
    }
}
