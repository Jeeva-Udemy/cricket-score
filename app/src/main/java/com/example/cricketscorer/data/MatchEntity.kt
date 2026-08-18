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
    val tossWinnerTeam: String,
    val tossDecision: TossDecision,
    val currentInningsNumber: Int = 1,
    val isCompleted: Boolean = false,
    val resultSummary: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
