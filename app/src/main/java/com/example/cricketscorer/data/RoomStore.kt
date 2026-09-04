package com.example.cricketscorer.data

import android.content.Context

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
 */
object RoomStore {
    private const val PREFS_NAME = "active_room"
    private const val KEY_ROOM_CODE = "room_code"
    private const val KEY_SLOT = "slot"

    data class ActiveRoom(val roomCode: String, val slot: Int)

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
     *  separate, best-effort call, see [com.example.cricketscorer.sync.CloudSync.exitRoom]. */
    fun clearActiveRoom(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
