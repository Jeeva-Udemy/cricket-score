package com.example.cricketscorer.sync

import com.example.cricketscorer.backup.BackupSerializer
import com.example.cricketscorer.data.BackupSnapshot
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Cloud Sync (req: Mobile1/TeamA + Mobile2/TeamB scoring the same match).
 *
 * How it works:
 *  - A device creates or joins a Room (Home > Room — see [createRoom]/[joinRoom] and
 *    [com.example.cricketscorer.data.RoomStore]), which hands out a short, easy-to-read
 *    [generateShareCode]-style room code shared between (at most two) devices, one per team.
 *    That code doubles as the Firestore document id under the top-level "liveMatches"
 *    collection for whichever match is currently being played in the room.
 *  - Every time the match/innings/ball_events for the room's current match change locally,
 *    the owning ViewModel pushes a fresh JSON snapshot (built by [BackupSerializer], reusing
 *    the same format as the Google Drive backup feature) to that document via [pushSnapshot].
 *  - Starting the *next* match in the same room (req: several matches back-to-back without
 *    re-sharing a code) simply reuses the room code as that new match's share code too — it
 *    overwrites the previous match's mirror at the same document, which is fine since that
 *    finished match already lives safely in each device's local database; only the *live*
 *    mirror ever needs to point at the room's current match.
 *  - The other device enters the code once ("Join Room" on Home), fetches the current
 *    snapshot with [fetchSnapshot] (done automatically by the room listener — see
 *    [joinRoom]/[listen]), and copies it into its own local Room database (preserving row ids
 *    — see CricketRepository.applyMatchSnapshot). From then on both devices call [listen] and
 *    apply whatever the other device pushes.
 *  - Each write is tagged with a stable per-device [deviceId] (see
 *    [com.example.cricketscorer.data.CloudDeviceIdStore]) so a device that receives its own
 *    update echoed back from Firestore can ignore it instead of re-applying/re-pushing.
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

    // ---------- Rooms (req: play several matches back-to-back without a new code each time) ----------
    // A room is a lightweight, separate Firestore document from the match mirror above — it
    // only tracks which two devices currently hold its (at most two) slots, and which team
    // name each of those slots is scoring for in the room's *current* match. It deliberately
    // does not duplicate any match/innings/ball data — that still lives solely at
    // liveMatches/{roomCode}, exactly as before rooms existed.

    private const val ROOMS_COLLECTION = "rooms"
    private const val FIELD_SLOT_DEVICE_PREFIX = "slot"
    private const val FIELD_SLOT_DEVICE_SUFFIX = "DeviceId"
    private const val FIELD_SLOT_TEAM_SUFFIX = "Team"

    private fun rooms() = FirebaseFirestore.getInstance().collection(ROOMS_COLLECTION)

    /** Snapshot of one room's membership/config. [slotsFilled] tells the UI whether the room
     *  is full (req: "only 2 device should be able to join the room"). */
    data class RoomInfo(
        val roomCode: String,
        val slot1DeviceId: String?,
        val slot2DeviceId: String?,
        val slot1Team: String?,
        val slot2Team: String?
    ) {
        val slotsFilled: Int get() = listOfNotNull(slot1DeviceId, slot2DeviceId).size
    }

    sealed class JoinRoomResult {
        data class Joined(val slot: Int, val info: RoomInfo) : JoinRoomResult()
        object Full : JoinRoomResult()
        object NotFound : JoinRoomResult()
    }

    private fun DocumentSnapshot.toRoomInfo(code: String) = RoomInfo(
        roomCode = code,
        slot1DeviceId = getString("slot1DeviceId"),
        slot2DeviceId = getString("slot2DeviceId"),
        slot1Team = getString("slot1Team"),
        slot2Team = getString("slot2Team")
    )

    /** Creates a brand-new room and claims slot 1 for [deviceId] (the creating device). */
    suspend fun createRoom(deviceId: String): String {
        val code = generateShareCode()
        rooms().document(code).set(
            mapOf(
                "slot1DeviceId" to deviceId,
                "slot2DeviceId" to null,
                FIELD_UPDATED_AT to FieldValue.serverTimestamp()
            )
        ).await()
        return code
    }

    /**
     * Claims a free slot in room [code] for [deviceId] — or, if this device already holds a
     * slot (e.g. re-opening the app), just returns that same one. Fails with [JoinRoomResult.Full]
     * once both slots belong to *other* devices (req: "only 2 device should be able to join
     * the room 1 for each team ... if 3rd person wants to join than someone has to exit").
     * This is enforced only app-side, matching the app's existing no-login security model —
     * see firestore.rules.
     */
    suspend fun joinRoom(code: String, deviceId: String): JoinRoomResult {
        val docRef = rooms().document(code)
        val snap = docRef.get().await()
        if (!snap.exists()) return JoinRoomResult.NotFound

        val slot1 = snap.getString("slot1DeviceId")
        val slot2 = snap.getString("slot2DeviceId")
        val mySlot = when {
            slot1 == deviceId -> 1
            slot2 == deviceId -> 2
            slot1 == null -> 1
            slot2 == null -> 2
            else -> null
        } ?: return JoinRoomResult.Full

        val alreadyMine = (mySlot == 1 && slot1 == deviceId) || (mySlot == 2 && slot2 == deviceId)
        if (!alreadyMine) {
            docRef.set(
                mapOf(
                    "$FIELD_SLOT_DEVICE_PREFIX$mySlot$FIELD_SLOT_DEVICE_SUFFIX" to deviceId,
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        }
        val fresh = docRef.get().await()
        return JoinRoomResult.Joined(mySlot, fresh.toRoomInfo(code))
    }

    /** One-off fetch of a room's current membership/config, e.g. right after [listen] delivers
     *  a new/updated match so the receiving device can look up which team it's now scoring
     *  for (see [setSlotTeams]). Returns null if the room doesn't exist (or was never a room —
     *  e.g. a code typo). */
    suspend fun fetchRoom(code: String): RoomInfo? {
        val snap = rooms().document(code).get().await()
        if (!snap.exists()) return null
        return snap.toRoomInfo(code)
    }

    /**
     * Records which team each slot is scoring for in the room's *current* match (req: "select
     * who's going to update the score for the 1st innings while creating the match"). Called
     * once by the creating device right after it starts a match in the room — the OTHER
     * device then reads this back (via [fetchRoom]) to learn its own team automatically,
     * instead of being asked to pick one after the fact.
     */
    suspend fun setSlotTeams(code: String, mySlot: Int, myTeam: String, otherTeam: String) {
        val otherSlot = if (mySlot == 1) 2 else 1
        rooms().document(code).set(
            mapOf(
                "$FIELD_SLOT_DEVICE_PREFIX$mySlot$FIELD_SLOT_TEAM_SUFFIX" to myTeam,
                "$FIELD_SLOT_DEVICE_PREFIX$otherSlot$FIELD_SLOT_TEAM_SUFFIX" to otherTeam,
                FIELD_UPDATED_AT to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }

    /** Releases [slot] so a different device can claim it (req: an Exit button for the "had to
     *  leave the ground" case). Best-effort — the caller clears the device's own local
     *  membership ([com.example.cricketscorer.data.RoomStore.clearActiveRoom]) either way, so
     *  a failure here (e.g. offline) never leaves the device stuck thinking it's still in the
     *  room. */
    suspend fun exitRoom(code: String, deviceId: String, slot: Int) {
        rooms().document(code).set(
            mapOf(
                "$FIELD_SLOT_DEVICE_PREFIX$slot$FIELD_SLOT_DEVICE_SUFFIX" to null,
                FIELD_UPDATED_AT to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }

    /** req #1: "delete the Room and Matches inside it and it should reflect everywhere" — wipes
     *  both Firestore documents a room ever touches: its own membership doc (so a stale code
     *  can't be rejoined) and the live-match mirror at the same code (so a device that still
     *  has the code cached can't pull down a "ghost" match for a room that no longer exists).
     *  Best-effort on each half independently — a room doc that was already gone (or a room
     *  that never started a match, so it has no live-match mirror) is not an error. */
    suspend fun deleteRoom(code: String) {
        runCatching { rooms().document(code).delete().await() }
        runCatching { matches().document(code).delete().await() }
    }
}
