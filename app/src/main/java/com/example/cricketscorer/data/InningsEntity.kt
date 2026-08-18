package com.example.cricketscorer.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per innings (there will be 1 or 2 rows per match).
 * Holds the running score total that the Scoring screen renders.
 *
 * Batsmen are tracked only by an incrementing "batsman number" (1, 2, 3, ...)
 * since the requirements do not call for a player roster — this keeps strike
 * rotation and "next man in" logic simple while remaining fully functional.
 */
@Entity(
    tableName = "innings",
    foreignKeys = [ForeignKey(
        entity = MatchEntity::class,
        parentColumns = ["matchId"],
        childColumns = ["matchId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("matchId")]
)
data class InningsEntity(
    @PrimaryKey(autoGenerate = true) val inningsId: Long = 0,
    val matchId: Long,
    val inningsNumber: Int,
    val battingTeam: String,
    val bowlingTeam: String,
    val totalRuns: Int = 0,
    val wickets: Int = 0,
    val completedOvers: Int = 0,
    val ballsThisOver: Int = 0,
    val wideRuns: Int = 0,
    val noBallRuns: Int = 0,
    val byeRuns: Int = 0,
    val legByeRuns: Int = 0,
    val strikerBatsmanNumber: Int = 1,
    val nonStrikerBatsmanNumber: Int = 2,
    val nextBatsmanNumber: Int = 3,
    val target: Int? = null,
    val isCompleted: Boolean = false
)
