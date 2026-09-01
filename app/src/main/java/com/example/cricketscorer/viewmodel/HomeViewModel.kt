package com.example.cricketscorer.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cricketscorer.backup.BackupSerializer
import com.example.cricketscorer.backup.DriveBackupManager
import com.example.cricketscorer.data.CricketRepository
import com.example.cricketscorer.data.DeviceMatchRoleStore
import com.example.cricketscorer.data.MatchEntity
import com.example.cricketscorer.sync.CloudSync
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
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

/** State for the "Join Shared Match" dialog (Cloud Sync — req: score the same match from
 *  two phones, one per team). */
sealed class JoinMatchUiState {
    object Idle : JoinMatchUiState()
    object Joining : JoinMatchUiState()
    /** req #3/#4: the match/innings/squads have been pulled down successfully, but we still
     *  need to ask this device's user which team THEY are scoring for, so the Scoring screen
     *  can tell the two phones apart and only let one edit at a time. */
    data class NeedsTeamSelection(
        val matchId: Long,
        val teamAName: String,
        val teamBName: String
    ) : JoinMatchUiState()
    data class Success(val matchId: Long) : JoinMatchUiState()
    data class Error(val message: String) : JoinMatchUiState()
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

    // ---------- Cloud Sync: Join Shared Match ----------

    private val _joinMatchState = MutableStateFlow<JoinMatchUiState>(JoinMatchUiState.Idle)
    val joinMatchState: StateFlow<JoinMatchUiState> = _joinMatchState.asStateFlow()

    /** Called with the 6-character code shown on the other phone's Scoring screen ("Match
     *  Code: XXXXXX"). Downloads that match's current state from Firestore and copies it
     *  into local Room (same row ids as the other device) so it opens exactly where the
     *  other phone left off, then keeps listening for further updates from it. */
    fun joinMatchByCode(rawCode: String) {
        val code = rawCode.trim().uppercase()
        if (code.isBlank()) {
            _joinMatchState.value = JoinMatchUiState.Error("Enter the match code.")
            return
        }
        _joinMatchState.value = JoinMatchUiState.Joining
        viewModelScope.launch {
            runCatching { CloudSync.fetchSnapshot(code) }
                .onSuccess { snapshot ->
                    val match = snapshot?.matches?.firstOrNull()
                    if (snapshot == null || match == null) {
                        _joinMatchState.value = JoinMatchUiState.Error(
                            "No shared match found for code \"$code\". Double-check it on the other phone."
                        )
                        return@onSuccess
                    }
                    repository.applyMatchSnapshot(snapshot)
                    // req #3/#4: ask which team this device is scoring for before handing
                    // off to the Scoring screen, instead of leaving it unset (which used to
                    // mean both phones treated themselves as fully editable and stepped on
                    // each other's changes).
                    _joinMatchState.value = JoinMatchUiState.NeedsTeamSelection(
                        matchId = match.matchId,
                        teamAName = match.teamAName,
                        teamBName = match.teamBName
                    )
                }
                .onFailure {
                    _joinMatchState.value = JoinMatchUiState.Error(
                        it.message ?: "Couldn't reach the match. Check your connection and try again."
                    )
                }
        }
    }

    /** Called once the user picks which team they're scoring for on THIS device, from the
     *  NeedsTeamSelection prompt. */
    fun confirmJoinTeam(matchId: Long, teamName: String) {
        DeviceMatchRoleStore.setMyTeam(appContext, matchId, teamName)
        _joinMatchState.value = JoinMatchUiState.Success(matchId)
    }

    fun dismissJoinMatchStatus() {
        _joinMatchState.value = JoinMatchUiState.Idle
    }
}
