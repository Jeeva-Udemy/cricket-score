package com.example.cricketscorer.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cricketscorer.backup.BackupSerializer
import com.example.cricketscorer.backup.DriveBackupManager
import com.example.cricketscorer.data.CricketRepository
import com.example.cricketscorer.data.MatchEntity
import com.google.android.gms.auth.api.signin.GoogleSignIn
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
            task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            val action = pendingAction
            pendingAction = null
            if (action != null) {
                runAction(action)
            } else {
                _backupState.value = BackupUiState.Idle
            }
        } catch (e: Exception) {
            pendingAction = null
            _backupState.value = BackupUiState.Error("Google sign-in was cancelled or failed.")
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
}
