package com.example.cricketscorer.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.cricketscorer.model.ExtraType
import com.example.cricketscorer.model.WicketType

/**
 * One row per ball bowled. This is the audit log that drives the
 * "this over" ball-by-ball chips and enables Undo.
 *
 * The `pre*` columns are a full snapshot of every innings field that a
 * delivery can change, taken immediately BEFORE the ball was applied.
 * Undo restores the innings directly from this snapshot instead of trying
 * to "reverse" the delta — that's what previously let Undo forget to put
 * the correct batsmen back after a wicket (e.g. a run-out where the wrong
 * end was marked out): reversing deltas only touched the score/overs, not
 * who was on strike, so the batsmen had to be fixed by hand. Restoring the
 * whole snapshot fixes that automatically, for every kind of ball.
 */
@Entity(
    tableName = "ball_events",
    foreignKeys = [ForeignKey(
        entity = InningsEntity::class,
        parentColumns = ["inningsId"],
        childColumns = ["inningsId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("inningsId")]
)
data class BallEventEntity(
    @PrimaryKey(autoGenerate = true) val ballId: Long = 0,
    val inningsId: Long,
    val overNumber: Int,            // 0-indexed completed-overs value at time of bowling
    val ballNumberInOver: Int,      // 1..6 for legal deliveries; unchanged for wide/no-ball
    val runsScored: Int,            // runs off the bat / extra runs run, excluding the fixed "1"
    val extraType: ExtraType,
    val extraRuns: Int,             // fixed penalty run(s) for wide/no-ball (0 for bye/leg-bye/none)
    val wicketType: WicketType,
    val isWicket: Boolean,
    val strikerBatsmanNumber: Int,
    val strikerName: String = "",
    /** Name of the batsman actually dismissed on this ball. Usually equals
     *  strikerName, but on a run-out it may be the non-striker instead. */
    val dismissedPlayerName: String = "",
    val bowlerName: String = "Bowler 1",
    val timestamp: Long = System.currentTimeMillis(),

    // ---- Pre-ball innings snapshot, used exclusively to make Undo exact ----
    val preTotalRuns: Int = 0,
    val preWickets: Int = 0,
    val preCompletedOvers: Int = 0,
    val preBallsThisOver: Int = 0,
    val preWideRuns: Int = 0,
    val preNoBallRuns: Int = 0,
    val preByeRuns: Int = 0,
    val preLegByeRuns: Int = 0,
    val prePenaltyRuns: Int = 0,
    val preStrikerBatsmanNumber: Int = 1,
    val preNonStrikerBatsmanNumber: Int = 2,
    val preStrikerName: String = "",
    val preNonStrikerName: String = "",
    val preNextBatsmanNumber: Int = 3,
    val preIsCompleted: Boolean = false
)
