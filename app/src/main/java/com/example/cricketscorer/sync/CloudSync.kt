package com.example.cricketscorer.sync

import com.example.cricketscorer.backup.BackupSerializer
import com.example.cricketscorer.data.BackupSnapshot
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.cricketscorer.model.ExtraType
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
        // Live Match tab: publish a tiny public summary for every phone that has the app.
        // Best-effort — a failure here must never break scoring/sync of the real match.
        runCatching { publishLiveSummary(shareCode, snapshot, deviceId) }
    }

    // ---------- Live Match tab (every installed phone can see matches being played) ----------
    // A small summary doc (no ball-by-ball payload) lives at live_scores/{roomCode}, so the
    // Live Match list stays cheap to download. It is rewritten on every push and flagged
    // isCompleted when the match ends; the list shows only matches that are not completed and
    // were updated recently (see [LIVE_WINDOW_MS]).

    private const val LIVE_COLLECTION = "live_scores"

    /** Live-list document id for a match that is NOT in a Room (so it has no share code). */
    fun localLiveCode(deviceId: String, matchId: Long): String = "L${deviceId.filter { it.isLetterOrDigit() }.take(8)}_$matchId"

    /** Publishes a local (room-less) match to the Live Match tab: the small summary doc, plus
     *  the full snapshot at liveMatches/{code} so viewers can open the read-only scorecard.
     *  Best-effort: never throws. */
    suspend fun pushLiveSummaryOnly(code: String, snapshot: BackupSnapshot, deviceId: String) {
        runCatching {
            matches().document(code).set(
                mapOf(
                    FIELD_PAYLOAD to BackupSerializer.toJson(snapshot),
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                    FIELD_UPDATED_BY to deviceId
                )
            ).await()
        }
        runCatching { publishLiveSummary(code, snapshot, deviceId) }
    }

    /** Removes a local match's Live Match entry (summary + snapshot). Best-effort. */
    suspend fun deleteLiveSummary(code: String) {
        runCatching { FirebaseFirestore.getInstance().collection(LIVE_COLLECTION).document(code).delete().await() }
        runCatching { matches().document(code).delete().await() }
    }

    /** Read-only stream of a live match's full snapshot, for the Live Match detail screen.
     *  Unlike [listen] it does not skip this device's own writes. */
    fun listenLiveSnapshot(
        code: String,
        onUpdate: (BackupSnapshot) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {
        return matches().document(code).addSnapshotListener { snap, error ->
            if (error != null) { onError(error.message ?: "Couldn't load match"); return@addSnapshotListener }
            if (snap == null || !snap.exists()) { onError("This match is no longer live."); return@addSnapshotListener }
            val payload = snap.getString(FIELD_PAYLOAD) ?: run { onError("Scorecard not available yet."); return@addSnapshotListener }
            runCatching { BackupSerializer.fromJson(payload) }
                .onSuccess(onUpdate)
                .onFailure { onError("Couldn't read match data.") }
        }
    }

    /** A match not updated for this long is treated as abandoned and hidden from Live Match. */
    const val LIVE_WINDOW_MS = 12L * 60 * 60 * 1000

    data class LiveInnings(
        val number: Int,
        val team: String,
        val runs: Int,
        val wickets: Int,
        val overs: String,
        val isSuperOver: Boolean
    )

    data class LiveMatchSummary(
        val code: String,
        val teamA: String,
        val teamB: String,
        val totalOvers: Int,
        val isCompleted: Boolean,
        val result: String?,
        val innings: List<LiveInnings>,
        val striker: String,
        val nonStriker: String,
        val bowler: String,
        val target: Int?,
        val recentBalls: List<String>,
        val updatedAtMs: Long
    )

    private fun ballLabel(b: com.example.cricketscorer.data.BallEventEntity): String = when {
        b.isWicket -> "W"
        b.extraType == ExtraType.WIDE -> "Wd"
        b.extraType == ExtraType.NO_BALL -> "Nb"
        b.extraType == ExtraType.BYE -> "${b.runsScored}b"
        b.extraType == ExtraType.LEG_BYE -> "${b.runsScored}lb"
        else -> b.runsScored.toString()
    }

    private suspend fun publishLiveSummary(code: String, snapshot: BackupSnapshot, deviceId: String) {
        val match = snapshot.matches.firstOrNull() ?: return
        val inns = snapshot.innings.filter { it.matchId == match.matchId }.sortedBy { it.inningsNumber }
        val current = inns.lastOrNull { !it.isCompleted } ?: inns.lastOrNull()
        val recent = current?.let { cur ->
            snapshot.ballEvents.filter { it.inningsId == cur.inningsId }
                .sortedBy { it.ballId }.takeLast(8).map { ballLabel(it) }
        } ?: emptyList()
        val data = mapOf(
            "teamA" to match.teamAName,
            "teamB" to match.teamBName,
            "totalOvers" to match.totalOvers,
            "isCompleted" to match.isCompleted,
            "result" to match.resultSummary,
            "innings" to inns.map {
                mapOf(
                    "number" to it.inningsNumber,
                    "team" to it.battingTeam,
                    "runs" to it.totalRuns,
                    "wickets" to it.wickets,
                    "overs" to "${it.completedOvers}.${it.ballsThisOver}",
                    "superOver" to it.isSuperOver
                )
            },
            "striker" to (current?.strikerName ?: ""),
            "nonStriker" to (current?.nonStrikerName ?: ""),
            "bowler" to (current?.currentBowlerName ?: ""),
            "target" to current?.target,
            "recentBalls" to recent,
            FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
            FIELD_UPDATED_BY to deviceId
        )
        FirebaseFirestore.getInstance().collection(LIVE_COLLECTION).document(code).set(data).await()
    }

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toLiveSummary(): LiveMatchSummary? {
        val a = getString("teamA") ?: return null
        val b = getString("teamB") ?: return null
        val innings = (get("innings") as? List<Map<String, Any?>>).orEmpty().map {
            LiveInnings(
                number = (it["number"] as? Number)?.toInt() ?: 1,
                team = it["team"] as? String ?: "",
                runs = (it["runs"] as? Number)?.toInt() ?: 0,
                wickets = (it["wickets"] as? Number)?.toInt() ?: 0,
                overs = it["overs"] as? String ?: "0.0",
                isSuperOver = it["superOver"] as? Boolean ?: false
            )
        }
        return LiveMatchSummary(
            code = id,
            teamA = a,
            teamB = b,
            totalOvers = getLong("totalOvers")?.toInt() ?: 0,
            isCompleted = getBoolean("isCompleted") ?: false,
            result = getString("result"),
            innings = innings,
            striker = getString("striker").orEmpty(),
            nonStriker = getString("nonStriker").orEmpty(),
            bowler = getString("bowler").orEmpty(),
            target = getLong("target")?.toInt(),
            recentBalls = (get("recentBalls") as? List<String>).orEmpty(),
            updatedAtMs = getTimestamp(FIELD_UPDATED_AT)?.toDate()?.time ?: System.currentTimeMillis()
        )
    }

    /**
     * Streams every match currently being played (any phone, any room), newest first. Listens
     * to the whole small summary collection and filters client-side, so no Firestore index is
     * needed. Completed or stale (> [LIVE_WINDOW_MS]) matches are dropped.
     */
    fun listenLiveMatches(
        onUpdate: (List<LiveMatchSummary>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {
        return FirebaseFirestore.getInstance().collection(LIVE_COLLECTION)
            .addSnapshotListener { snap, error ->
                if (error != null) { onError(error.message ?: "Couldn't load live matches"); return@addSnapshotListener }
                if (snap == null) return@addSnapshotListener
                val cutoff = System.currentTimeMillis() - LIVE_WINDOW_MS
                onUpdate(
                    snap.documents.mapNotNull { it.toLiveSummary() }
                        .filter { !it.isCompleted && it.updatedAtMs >= cutoff }
                        .sortedByDescending { it.updatedAtMs }
                )
            }
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
        runCatching { FirebaseFirestore.getInstance().collection(LIVE_COLLECTION).document(code).delete().await() }
    }
}
