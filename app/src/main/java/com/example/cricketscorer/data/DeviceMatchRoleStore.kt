package com.example.cricketscorer.data

import android.content.Context

/**
 * Cloud Sync (req #3/#4): which team THIS physical device is scoring for, per match.
 *
 * This is deliberately never written into [BackupSnapshot] / Firestore — it is a per-device
 * choice (Mobile1 might be scoring for "Team A", Mobile2 for "Team B", for the very same
 * match) and must stay local to each phone, or every device would end up agreeing on the
 * same value and the whole point of it would be lost.
 *
 * It's what lets the Scoring screen answer "is it THIS device's turn to edit?" so only the
 * team currently batting can enter balls and the other phone shows a read-only view instead
 * of both phones fighting over the same ball (see [ScoringUiState.canEditScore] in
 * ScoringViewModel).
 */
object DeviceMatchRoleStore {
    private const val PREFS_NAME = "device_match_roles"

    /** The team name (matches [com.example.cricketscorer.data.MatchEntity.teamAName] or
     *  teamBName) this device is scoring for in [matchId], or null if it was never set
     *  (e.g. a match created before this feature existed, or a purely local/unshared match). */
    fun getMyTeam(context: Context, matchId: Long): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(key(matchId), null)
    }

    fun setMyTeam(context: Context, matchId: Long, teamName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(key(matchId), teamName).apply()
    }

    private fun key(matchId: Long) = "match_$matchId"
}
