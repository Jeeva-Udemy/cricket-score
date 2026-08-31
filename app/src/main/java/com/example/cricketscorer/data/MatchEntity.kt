package com.example.cricketscorer.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.cricketscorer.model.TossDecision

/**
 * One row per match created from the Match Setup screen.
 */
@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey(autoGenerate = true) val matchId: Long = 0,
    val teamAName: String,
    val teamBName: String,
    val totalOvers: Int,
    /** Players per side for this match. Local games rarely field a full 11, so this is
     *  configurable per match and drives the "all out" threshold (playersPerTeam - 1). */
    val playersPerTeam: Int = 11,
    /** Saved squad each team was picked from, if any (nullable — teams can still be typed ad-hoc). */
    val teamASquadId: Long? = null,
    val teamBSquadId: Long? = null,
    val tossWinnerTeam: String,
    val tossDecision: TossDecision,
    val currentInningsNumber: Int = 1,
    val isCompleted: Boolean = false,
    val resultSummary: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Cloud Sync (Firestore): short code the other device enters via "Join Shared Match"
     *  to mirror this match live. Null means the match has never been shared. See
     *  [com.example.cricketscorer.sync.CloudSync]. */
    val shareCode: String? = null
)
