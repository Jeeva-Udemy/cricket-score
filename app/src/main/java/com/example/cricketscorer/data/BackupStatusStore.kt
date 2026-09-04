package com.example.cricketscorer.data

import android.content.Context

/**
 * Backup & Resync: small local record of automatic-backup state (req: "there shouldn't be any
 * manual configuration for that. If i select the mail id it should automatically backup the
 * data just like we have it in WhatsApp backup").
 *
 * [lastBackupAt] drives the "Last backed up: ..." status line in the Backup & Resync dialog.
 * [lastBackedUpFingerprint] is a cheap summary of what the match list looked like the last time
 * a backup actually ran, so auto-backup can tell "did anything change since the last backup"
 * without re-uploading on every single Home screen visit — including across a HomeViewModel
 * being recreated (e.g. navigating back from Scoring), which has no memory of its own of what
 * was backed up before this instance existed.
 */
object BackupStatusStore {
    private const val PREFS_NAME = "backup_status"
    private const val KEY_LAST_BACKUP_AT = "last_backup_at"
    private const val KEY_LAST_FINGERPRINT = "last_fingerprint"

    fun getLastBackupAt(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = prefs.getLong(KEY_LAST_BACKUP_AT, -1L)
        return if (value < 0) null else value
    }

    fun setLastBackupAt(context: Context, timestamp: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_BACKUP_AT, timestamp).apply()
    }

    fun getLastBackedUpFingerprint(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY_LAST_FINGERPRINT)) prefs.getInt(KEY_LAST_FINGERPRINT, 0) else null
    }

    fun setLastBackedUpFingerprint(context: Context, fingerprint: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_LAST_FINGERPRINT, fingerprint).apply()
    }

    /** Called after "Delete Backup from Drive" (req #3) or disconnecting the account, so the
     *  next match-list change — or the very next app open — knows there's nothing backed up
     *  for whichever account is connected next, and re-uploads instead of assuming it's current. */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
