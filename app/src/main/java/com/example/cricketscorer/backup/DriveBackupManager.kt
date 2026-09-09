package com.example.cricketscorer.backup

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Handles Google Sign-In + upload/download of a single backup file to the signed-in user's
 * Google Drive "App Data" folder (a hidden, app-private space — nothing else in the user's
 * Drive is touched, and the file doesn't show up in their regular Drive UI).
 *
 * Backup: exports every match/innings/ball/squad/player row to JSON and uploads it,
 * overwriting any previous backup.
 * Resync: downloads that JSON and replaces all local data with it — this is what lets
 * match history come back after the app is reinstalled.
 */
class DriveBackupManager(private val appContext: Context) {

    companion object {
        private const val BACKUP_FILE_NAME = "wickt_cricket_scorer_backup.json"
    }

    fun getSignInClient(): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        return GoogleSignIn.getClient(appContext, options)
    }

    fun getSignInIntent(): Intent = getSignInClient().signInIntent

    fun getLastSignedInAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(appContext)?.takeIf {
            GoogleSignIn.hasPermissions(it, Scope(DriveScopes.DRIVE_APPDATA))
        }

    fun signOut() {
        getSignInClient().signOut()
    }

    private fun buildDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(appContext, listOf(DriveScopes.DRIVE_APPDATA))
        val androidAccount = requireNotNull(account.account) { "Signed-in Google account has no linked Android account." }
        credential.setSelectedAccount(androidAccount)
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Wickt: The Cricket Scorer").build()
    }

    private fun findBackupFileId(drive: Drive): String? {
        val result = drive.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = '$BACKUP_FILE_NAME'")
            .setFields("files(id, name)")
            .execute()
        return result.files?.firstOrNull()?.id
    }

    /** Uploads [jsonContent] to Drive, replacing any previous backup. */
    suspend fun uploadBackup(account: GoogleSignInAccount, jsonContent: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val drive = buildDriveService(account)
                val content = ByteArrayContent("application/json", jsonContent.toByteArray(Charsets.UTF_8))
                val existingId = findBackupFileId(drive)
                if (existingId != null) {
                    drive.files().update(existingId, null, content).execute()
                } else {
                    val metadata = File().apply {
                        name = BACKUP_FILE_NAME
                        parents = listOf("appDataFolder")
                    }
                    drive.files().create(metadata, content).setFields("id").execute()
                }
                Result.success(Unit)
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    /** Downloads the backup JSON, or null (success) if the user has never backed up before. */
    suspend fun downloadBackup(account: GoogleSignInAccount): Result<String?> =
        withContext(Dispatchers.IO) {
            try {
                val drive = buildDriveService(account)
                val fileId = findBackupFileId(drive) ?: return@withContext Result.success(null)
                val output = ByteArrayOutputStream()
                drive.files().get(fileId).executeMediaAndDownloadTo(output)
                Result.success(output.toString(Charsets.UTF_8.name()))
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    /** req #3: "There should be an option to delete the existing backup in the gmail drive."
     *  A no-op success (not an error) if there was never a backup to begin with. */
    suspend fun deleteBackup(account: GoogleSignInAccount): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val drive = buildDriveService(account)
                val existingId = findBackupFileId(drive)
                if (existingId != null) {
                    drive.files().delete(existingId).execute()
                }
                Result.success(Unit)
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    /**
     * Recovery path for an accidental overwrite (e.g. tapping "Back Up Now" right after a
     * fresh reinstall, before any local data existed, which used to silently replace a good
     * remote backup with an empty one — see HomeViewModel.backupNow's safety guard). Every
     * [uploadBackup] call updates the SAME Drive file in place via `drive.files().update()`,
     * and Drive keeps prior revisions of a file updated this way rather than discarding them,
     * so the pre-overwrite backup is very likely still recoverable from revision history —
     * but Drive's retention isn't indefinite, so this should be used promptly.
     *
     * Returns revisions newest-first, or an empty list if there's no backup file at all yet.
     */
    suspend fun listBackupRevisions(account: GoogleSignInAccount): Result<List<BackupRevision>> =
        withContext(Dispatchers.IO) {
            try {
                val drive = buildDriveService(account)
                val fileId = findBackupFileId(drive) ?: return@withContext Result.success(emptyList())
                val result = drive.revisions().list(fileId)
                    .setFields("revisions(id, modifiedTime, size)")
                    .execute()
                val revisions = (result.revisions ?: emptyList())
                    // it.size's exact numeric type (Int vs Long) varies by the pinned Drive
                    // API client version — .toLong() normalizes either to the Long? that
                    // BackupRevision expects, so this compiles regardless of which one it is.
                    .map { BackupRevision(it.id, it.modifiedTime?.value ?: 0L, it.size?.toLong()) }
                    .sortedByDescending { it.modifiedTimeMillis }
                Result.success(revisions)
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    /** Downloads one specific past revision's JSON content — see [listBackupRevisions]. */
    suspend fun downloadRevision(account: GoogleSignInAccount, revisionId: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val drive = buildDriveService(account)
                val fileId = findBackupFileId(drive)
                    ?: return@withContext Result.failure(IllegalStateException("No backup file found on Drive."))
                val output = ByteArrayOutputStream()
                drive.revisions().get(fileId, revisionId).executeMediaAndDownloadTo(output)
                Result.success(output.toString(Charsets.UTF_8.name()))
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
}

/** One past revision of the Drive backup file — see [DriveBackupManager.listBackupRevisions]. */
data class BackupRevision(
    val id: String,
    val modifiedTimeMillis: Long,
    val sizeBytes: Long?
)
