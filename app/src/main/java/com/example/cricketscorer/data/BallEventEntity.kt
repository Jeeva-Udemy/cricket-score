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
    val timestamp: Long = System.currentTimeMillis()
)
