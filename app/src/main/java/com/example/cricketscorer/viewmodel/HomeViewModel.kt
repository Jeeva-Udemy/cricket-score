package com.example.cricketscorer.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cricketscorer.backup.BackupSerializer
import com.example.cricketscorer.backup.DriveBackupManager
import com.example.cricketscorer.data.BackupStatusStore
import com.example.cricketscorer.data.CricketRepository
import com.example.cricketscorer.data.MatchEntity
import com.example.cricketscorer.stats.PlayerStatsCalculator
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the pending Google sign-in was for, so we know what to do once it completes. Null
 *  (see [HomeViewModel.pendingAction]) means the user just tapped "Connect Google Account" with
 *  no specific follow-up action — see [HomeViewModel.onSignInResult]. */
private enum class PendingBackupAction { BACKUP, RESYNC, DELETE }

sealed class BackupUiState {
    object Idle : BackupUiState()
    object SigningIn : BackupUiState()
    object BackingUp : BackupUiState()
    object Resyncing : BackupUiState()
    /** req #3: "delete the existing backup in the gmail drive." */
    object DeletingBackup : BackupUiState()
    data class Success(val message: String) : BackupUiState()
    data class Error(val message: String) : BackupUiState()
}

/**
 * Home screen + Match History screen state: the match list/selection, and Backup & Resync.
 *
 * Room state used to live here too, but moved out to [RoomsViewModel] (req: "Show the Rooms
 * created inside the Rooms instead of showing it in the Home page") — that also happened to
 * fix a real bug: the old Room code referenced two properties (`deviceId`, `_roomState`) that
 * were declared textually AFTER this class's `init {}` block, and Kotlin runs property
 * initializers/`init{}` blocks in declaration order, so constructing this ViewModel while the
 * device already belonged to a room threw a NullPointerException immediately. Both reported
 * crashes — backing out of Scoring to Home, and opening Match History — go through a freshly
 * constructed `HomeViewModel` (see MainActivity's NavHost), which is exactly when that NPE
 * fired. Keeping Room state (and its `deviceId`/listener wiring) entirely off this class
 * removes the whole hazard rather than just reordering around it.
 */
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

    /** The connected account's email, for the "Connected as ..." status line — null when not
     *  signed in. */
    val signedInEmail: String?
        get() = driveBackupManager.getLastSignedInAccount()?.email

    /** req #2: "Last backed up ..." status line — persisted (see [BackupStatusStore]) so it
     *  survives this ViewModel being recreated on ordinary navigation. */
    private val _lastBackupAt = MutableStateFlow(BackupStatusStore.getLastBackupAt(appContext))
    val lastBackupAt: StateFlow<Long?> = _lastBackupAt.asStateFlow()

    private var autoBackupJob: Job? = null

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
                scheduleAutoBackupIfChanged(list)
            }
        }
    }

    // ---------- Automatic backup (req #2) ----------

    /**
     * req #2: "there shouldn't be any manual configuration for that. If i select the mail id it
     * should automatically backup the data just like we have it in WhatsApp backup." Once an
     * account is connected, every meaningful change to the match list (a match created, an
     * innings break, a match finishing — the matches table only changes a handful of times per
     * match, never per ball) quietly triggers a background upload, with no "Backup Now" tap
     * required.
     *
     * Comparing against a fingerprint PERSISTED to [BackupStatusStore] (not just an in-memory
     * flag) matters because this ViewModel gets recreated on ordinary navigation — e.g. backing
     * out of Scoring to Home. An in-memory "already backed this up" flag would forget that on
     * every single recreation and either miss a real change made just before recreation, or
     * spam re-uploads of data that hasn't actually changed.
     */
    private fun scheduleAutoBackupIfChanged(list: List<MatchEntity>) {
        val account = driveBackupManager.getLastSignedInAccount() ?: return
        val fingerprint = matchesFingerprint(list)
        if (fingerprint == BackupStatusStore.getLastBackedUpFingerprint(appContext)) return
        autoBackupJob?.cancel()
        autoBackupJob = viewModelScope.launch {
            val snapshot = repository.getFullBackupSnapshot()
            val freshFingerprint = matchesFingerprint(snapshot.matches)
            driveBackupManager.uploadBackup(account, BackupSerializer.toJson(snapshot))
                .onSuccess {
                    val now = System.currentTimeMillis()
                    BackupStatusStore.setLastBackedUpFingerprint(appContext, freshFingerprint)
                    BackupStatusStore.setLastBackupAt(appContext, now)
                    _lastBackupAt.value = now
                }
            // Silent on failure — this was never a user-initiated action, so there's no dialog
            // open to show an error in. The fingerprint was never updated, so the very next
            // match-list change (or the next app open) simply tries again.
        }
    }

    private fun matchesFingerprint(list: List<MatchEntity>): Int =
        list.sortedBy { it.matchId }.fold(0) { acc, m ->
            acc * 31 + java.util.Objects.hash(
                m.matchId, m.isCompleted, m.currentInningsNumber, m.resultSummary,
                m.teamAName, m.teamBName
            )
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

    /** req: "For each match we need to show who's the Player of the Match" — computed on
     *  demand from that one match's ball events (see [PlayerStatsCalculator]), not persisted,
     *  so Match History always reflects the latest scored state. Null for a match with no
     *  balls recorded yet, or one that isn't completed. */
    suspend fun playerOfTheMatch(match: MatchEntity): PlayerStatsCalculator.PlayerAward? {
        if (!match.isCompleted) return null
        val snapshot = repository.getSnapshotForMatch(match.matchId)
        return PlayerStatsCalculator.computePlayerOfTheMatch(match, snapshot.innings, snapshot.ballEvents)
    }

    // ---------- Backup & Resync ----------

    /** Call to start (or continue) a backup; launches sign-in first if needed. */
    fun requestBackup(): Intent? = requestAction(PendingBackupAction.BACKUP)

    /** Call to start (or continue) a resync; launches sign-in first if needed. */
    fun requestResync(): Intent? = requestAction(PendingBackupAction.RESYNC)

    /** req #3: "an option to delete the existing backup in the gmail drive." Launches sign-in
     *  first if needed, exactly like backup/resync (shouldn't normally happen, since the
     *  "Delete Backup" button is only shown once already connected). */
    fun requestDeleteBackup(): Intent? = requestAction(PendingBackupAction.DELETE)

    /** req #2: the plain "Connect Google Account" entry point — no backup/resync/delete
     *  attached, just establishes the connection so auto-backup can take over from here (see
     *  [onSignInResult]). Returns null (nothing to launch) if already connected. */
    fun requestConnectAccount(): Intent? {
        if (driveBackupManager.getLastSignedInAccount() != null) return null
        pendingAction = null
        _backupState.value = BackupUiState.SigningIn
        return driveBackupManager.getSignInIntent()
    }

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
                // req #2: "If i select the mail id it should automatically backup the data" —
                // connecting an account with no specific action pending (the plain "Connect
                // Google Account" button) should still kick off a backup right away, not wait
                // for the next unrelated match-list change to happen to fire one.
                _backupState.value = BackupUiState.Idle
                scheduleAutoBackupIfChanged(_matches.value)
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
            PendingBackupAction.DELETE -> deleteBackupNow()
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
                    val now = System.currentTimeMillis()
                    BackupStatusStore.setLastBackedUpFingerprint(appContext, matchesFingerprint(snapshot.matches))
                    BackupStatusStore.setLastBackupAt(appContext, now)
                    _lastBackupAt.value = now
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

    /** req #3: "an option to delete the existing backup in the gmail drive." */
    private fun deleteBackupNow() {
        val account = driveBackupManager.getLastSignedInAccount() ?: return
        _backupState.value = BackupUiState.DeletingBackup
        viewModelScope.launch {
            driveBackupManager.deleteBackup(account)
                .onSuccess {
                    // Nothing backed up any more — forget the fingerprint/timestamp so the very
                    // next match-list change (or reconnect) starts a fresh backup instead of
                    // wrongly assuming a deleted backup is still "current".
                    BackupStatusStore.clear(appContext)
                    _lastBackupAt.value = null
                    _backupState.value = BackupUiState.Success("Deleted the backup from Google Drive.")
                }
                .onFailure {
                    _backupState.value = BackupUiState.Error(
                        it.message ?: "Couldn't delete the backup. Check your connection and try again."
                    )
                }
        }
    }

    /** Disconnects the Google account (req #2's dialog offers this alongside Connect, so the
     *  user can switch accounts or stop auto-backup). Local match data is untouched — only the
     *  Drive connection and this device's memory of what was last backed up are cleared. */
    fun signOutOfDrive() {
        autoBackupJob?.cancel()
        driveBackupManager.signOut()
        BackupStatusStore.clear(appContext)
        _lastBackupAt.value = null
        _backupState.value = BackupUiState.Idle
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
}
