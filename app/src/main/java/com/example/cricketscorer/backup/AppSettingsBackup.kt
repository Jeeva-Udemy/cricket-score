package com.example.cricketscorer.backup

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup & Resync of everything the app keeps OUTSIDE the database, so a restore brings back
 * the whole app, not just squads and matches:
 *  - Rooms list (room history) and the room this phone is currently in
 *  - which team this phone scores for in each shared match
 *  - remembered player names for the batsman/bowler dropdowns
 *  - your profile (name / mobile / email used by Share Data)
 *  - this phone's sync id (so a restored phone is still recognised in its rooms)
 *
 * (Player merges for Rankings / Player Stats live in the database and are backed up with it.)
 *
 * Deliberately NOT backed up: the root-admin login (must be entered again), Drive backup
 * status and Team Sharing sync bookkeeping (those are rebuilt automatically).
 *
 * Stored generically — every value of each listed preferences file with its type — so new
 * settings added to these files later are backed up without touching this code.
 */
object AppSettingsBackup {

    /** SharedPreferences file -> keys to leave out. */
    private val FILES: Map<String, Set<String>> = mapOf(
        "room_history" to emptySet(),
        "active_room" to emptySet(),
        "device_match_roles" to emptySet(),
        "recent_players" to emptySet(),
        "cloud_device_id" to emptySet(),
        "user_profile" to setOf("root_admin_session", "last_team_sync", "published_fingerprints")
    )

    fun export(context: Context): String {
        val root = JSONObject()
        FILES.forEach { (file, excluded) ->
            val prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE)
            val obj = JSONObject()
            prefs.all.forEach { (key, value) ->
                if (key in excluded || value == null) return@forEach
                val entry = JSONObject()
                when (value) {
                    is String -> entry.put("t", "s").put("v", value)
                    is Int -> entry.put("t", "i").put("v", value)
                    is Long -> entry.put("t", "l").put("v", value)
                    is Boolean -> entry.put("t", "b").put("v", value)
                    is Float -> entry.put("t", "f").put("v", value.toDouble())
                    is Set<*> -> entry.put("t", "ss").put("v", JSONArray(value.map { it.toString() }))
                    else -> return@forEach
                }
                obj.put(key, entry)
            }
            root.put(file, obj)
        }
        return root.toString()
    }

    /** Replaces the listed preferences with the backed-up values (excluded keys are kept). */
    fun restore(context: Context, json: String) {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return
        FILES.forEach { (file, excluded) ->
            val obj = root.optJSONObject(file) ?: return@forEach
            val prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            prefs.all.keys.filter { it !in excluded }.forEach { editor.remove(it) }
            obj.keys().forEach { key ->
                if (key in excluded) return@forEach
                val entry = obj.optJSONObject(key) ?: return@forEach
                when (entry.optString("t")) {
                    "s" -> editor.putString(key, entry.optString("v"))
                    "i" -> editor.putInt(key, entry.optInt("v"))
                    "l" -> editor.putLong(key, entry.optLong("v"))
                    "b" -> editor.putBoolean(key, entry.optBoolean("v"))
                    "f" -> editor.putFloat(key, entry.optDouble("v").toFloat())
                    "ss" -> {
                        val arr = entry.optJSONArray("v") ?: JSONArray()
                        editor.putStringSet(key, (0 until arr.length()).map { arr.getString(it) }.toSet())
                    }
                }
            }
            editor.commit()
        }
    }
}
