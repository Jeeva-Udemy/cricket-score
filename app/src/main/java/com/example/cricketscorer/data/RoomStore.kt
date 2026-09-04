package com.example.cricketscorer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Rooms (Cloud Sync): which room, if any, THIS physical device currently belongs to, and
 * which of the room's two device slots it holds. Local-only, exactly like
 * [DeviceMatchRoleStore] — never written into a [BackupSnapshot] / synced directly (the
 * room's own Firestore document tracks slot occupancy by [CloudDeviceIdStore] id instead —
 * see [com.example.cricketscorer.sync.CloudSync]'s room functions).
 *
 * A room lets the same two devices play several matches back-to-back — req: "what if we
 * play 3 to 5 matches in the same day with the same squad ... whoever has the room code can
 * join there and in that room we can play multiple matches one after another" — without
 * re-sharing a fresh code before every single match. Whichever match is currently live in the
 * room is mirrored at the SAME Firestore document the room code has always pointed to
 * (`liveMatches/{roomCode}`), so starting the next match just overwrites that mirror instead
 * of needing a brand new code (see [MatchSetupViewModel.startMatch]).
 *
 * Only two devices may hold a slot at once (req: "only 2 device should be able to join the
 * room 1 for each team") — enforced app-side when joining (see
 * [com.example.cricketscorer.sync.CloudSync.joinRoom]), matching this app's existing no-login
 * security model rather than adding real authentication.
 *
 * Also keeps a small local HISTORY of every room this device has ever created or joined (req:
 * "Show the Rooms created inside the Rooms instead of showing it in the Home page" — a
 * dedicated screen listing every room, not just whichever one happens to be active right now),
 * so the Rooms screen still has something to show for a room this device has since exited.
 * History entries are a cosmetic cache (code/slot/team names) only — the live source of truth
 * for slot occupancy is always Firestore (see [CloudSync.fetchRoom]).
 */
object RoomStore {
    private const val PREFS_NAME = "active_room"
    private const val KEY_ROOM_CODE = "room_code"
    private const val KEY_SLOT = "slot"

    private const val HISTORY_PREFS_NAME = "room_history"
    private const val KEY_HISTORY_JSON = "rooms"

    data class ActiveRoom(val roomCode: String, val slot: Int)

    /** One room this device has created or joined at some point — kept around purely so the
     *  Rooms screen can list it (and its matches, via a shareCode-filtered match query) even
     *  after this device exits it. */
    data class SavedRoom(
        val roomCode: String,
        val slot: Int,
        val myTeam: String? = null,
        val otherTeam: String? = null,
        val createdAt: Long = System.currentTimeMillis()
    )

    /** The room this device is currently a member of, or null if it never joined/created one
     *  or has since exited (see [clearActiveRoom]). Survives app restarts. */
    fun getActiveRoom(context: Context): ActiveRoom? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_ROOM_CODE, null) ?: return null
        val slot = prefs.getInt(KEY_SLOT, 0)
        if (slot != 1 && slot != 2) return null
        return ActiveRoom(code, slot)
    }

    fun setActiveRoom(context: Context, roomCode: String, slot: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ROOM_CODE, roomCode).putInt(KEY_SLOT, slot).apply()
    }

    /** Called when the user taps "Exit Room" (req: an emergency exit for "the guy who's using
     *  the device to update the score" so a different phone can take the freed slot). Only
     *  clears THIS device's local membership — releasing the slot on the Firestore side is a
     *  separate, best-effort call, see [com.example.cricketscorer.sync.CloudSync.exitRoom].
     *  The room stays in [getRoomHistory] either way, so it's still visible (read-only, with
     *  its past matches) on the Rooms screen afterwards. */
    fun clearActiveRoom(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    // ---------- Room history (req: a dedicated Rooms screen listing every room created/joined,
    // each showing the matches played inside it) ----------

    /** Every room this device has created or joined, most-recently-touched first. */
    fun getRoomHistory(context: Context): List<SavedRoom> {
        val prefs = context.getSharedPreferences(HISTORY_PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_HISTORY_JSON, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                SavedRoom(
                    roomCode = obj.getString("roomCode"),
                    slot = obj.optInt("slot", 1),
                    myTeam = obj.optNullableString("myTeam"),
                    otherTeam = obj.optNullableString("otherTeam"),
                    createdAt = obj.optLong("createdAt", 0L)
                )
            }.sortedByDescending { it.createdAt }
        }.getOrDefault(emptyList())
    }

    /** Adds/updates [room] in the history (matched by [SavedRoom.roomCode]), preserving the
     *  original [SavedRoom.createdAt] if it was already there so the list stays sorted by when
     *  the room was first created/joined, not last touched. Called every time this device
     *  creates or joins a room, or learns the room's team names (see RoomsViewModel). */
    fun upsertRoomHistory(context: Context, room: SavedRoom) {
        val prefs = context.getSharedPreferences(HISTORY_PREFS_NAME, Context.MODE_PRIVATE)
        val existing = getRoomHistory(context).associateBy { it.roomCode }.toMutableMap()
        val previous = existing[room.roomCode]
        existing[room.roomCode] = room.copy(
            createdAt = if (previous != null && previous.createdAt > 0L) previous.createdAt else room.createdAt,
            // Never forget team names we already learned just because a later update (e.g. a
            // fresh rejoin before the room's team-mapping synced back down) doesn't carry them.
            myTeam = room.myTeam ?: previous?.myTeam,
            otherTeam = room.otherTeam ?: previous?.otherTeam
        )
        val array = JSONArray()
        existing.values.sortedByDescending { it.createdAt }.forEach { r ->
            array.put(
                JSONObject().apply {
                    put("roomCode", r.roomCode)
                    put("slot", r.slot)
                    put("myTeam", r.myTeam)
                    put("otherTeam", r.otherTeam)
                    put("createdAt", r.createdAt)
                }
            )
        }
        prefs.edit().putString(KEY_HISTORY_JSON, array.toString()).apply()
    }

    /** req #1: "delete the Room and Matches inside it and it should reflect everywhere" — drops
     *  [roomCode] from this device's local history so a deleted room stops showing up on the
     *  Rooms screen. Does not touch [getActiveRoom]/[clearActiveRoom] — the caller is
     *  responsible for also clearing active membership if the deleted room happened to be it. */
    fun removeRoomFromHistory(context: Context, roomCode: String) {
        val prefs = context.getSharedPreferences(HISTORY_PREFS_NAME, Context.MODE_PRIVATE)
        val remaining = getRoomHistory(context).filter { it.roomCode != roomCode }
        val array = JSONArray()
        remaining.forEach { r ->
            array.put(
                JSONObject().apply {
                    put("roomCode", r.roomCode)
                    put("slot", r.slot)
                    put("myTeam", r.myTeam)
                    put("otherTeam", r.otherTeam)
                    put("createdAt", r.createdAt)
                }
            )
        }
        prefs.edit().putString(KEY_HISTORY_JSON, array.toString()).apply()
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null
}
