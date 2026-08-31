package com.example.cricketscorer.sync

import com.example.cricketscorer.backup.BackupSerializer
import com.example.cricketscorer.data.BackupSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

/**
 * Cloud Sync (req: Mobile1/TeamA + Mobile2/TeamB scoring the same match).
 *
 * How it works:
 *  - When a match is started, the creating device generates a short, easy-to-read
 *    [generateShareCode] and shows it to the user (Scoring screen top bar). That code is the
 *    Firestore document id under the top-level "liveMatches" collection.
 *  - Every time the match/innings/ball_events for that match change locally, the owning
 *    ViewModel pushes a fresh JSON snapshot (built by [BackupSerializer], reusing the same
 *    format as the Google Drive backup feature) to that document via [pushSnapshot].
 *  - The other device enters the code once ("Join Shared Match" on Home), fetches the
 *    current snapshot with [fetchSnapshot], and copies it into its own local Room database
 *    (preserving row ids — see CricketRepository.applyMatchSnapshot). From then on both
 *    devices call [listen] and apply whatever the other device pushes.
 *  - Each write is tagged with a random per-process [deviceId] so a device that receives its
 *    own update echoed back from Firestore can ignore it instead of re-applying/re-pushing.
 *
 * This is a "last write wins" full-state mirror, not a field-level merge: if both phones
 * score a ball within the same instant, one of them wins and the other's ball is overwritten
 * on the next sync round. For a two-person scorer app (one ball at a time, seconds apart)
 * this is an acceptable, simple, and easy-to-reason-about trade-off — it re-uses the exact
 * same "replace rows by id" logic already trusted for Drive Backup & Resync, rather than
 * inventing a new operational-transform / CRDT merge strategy.
 */
object CloudSync {

    private const val COLLECTION = "liveMatches"
    private const val FIELD_PAYLOAD = "payload"
    private const val FIELD_UPDATED_AT = "updatedAt"
    private const val FIELD_UPDATED_BY = "updatedBy"

    private fun matches() = FirebaseFirestore.getInstance().collection(COLLECTION)

    /** Human-friendly 6-character code (no 0/O/1/I ambiguity) to read aloud or type. */
    fun generateShareCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    /** Pushes the current local state of one match up to Firestore. */
    suspend fun pushSnapshot(shareCode: String, snapshot: BackupSnapshot, deviceId: String) {
        val json = BackupSerializer.toJson(snapshot)
        matches().document(shareCode).set(
            mapOf(
                FIELD_PAYLOAD to json,
                FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                FIELD_UPDATED_BY to deviceId
            )
        ).await()
    }

    /** One-off fetch, used when a device first joins a shared match by code. */
    suspend fun fetchSnapshot(shareCode: String): BackupSnapshot? {
        val doc = matches().document(shareCode).get().await()
        if (!doc.exists()) return null
        val payload = doc.getString(FIELD_PAYLOAD) ?: return null
        return BackupSerializer.fromJson(payload)
    }

    /**
     * Subscribes to live changes for [shareCode]. [onRemoteSnapshot] is invoked with the new
     * snapshot every time the *other* device pushes an update (updates tagged with our own
     * [deviceId] are skipped). Call [ListenerRegistration.remove] (e.g. from onCleared) to stop.
     */
    fun listen(
        shareCode: String,
        deviceId: String,
        onRemoteSnapshot: (BackupSnapshot) -> Unit
    ): ListenerRegistration {
        return matches().document(shareCode).addSnapshotListener { snap, error ->
            if (error != null || snap == null || !snap.exists()) return@addSnapshotListener
            if (snap.metadata.hasPendingWrites()) return@addSnapshotListener // our own optimistic write
            if (snap.getString(FIELD_UPDATED_BY) == deviceId) return@addSnapshotListener
            val payload = snap.getString(FIELD_PAYLOAD) ?: return@addSnapshotListener
            onRemoteSnapshot(BackupSerializer.fromJson(payload))
        }
    }
}
