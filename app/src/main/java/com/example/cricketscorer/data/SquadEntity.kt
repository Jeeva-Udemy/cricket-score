package com.example.cricketscorer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved team roster that can be reused across multiple matches, so a local
 * team playing several games in a row doesn't need to retype its players
 * every time. A Squad just groups [PlayerEntity] rows under a team name.
 */
@Entity(tableName = "squads")
data class SquadEntity(
    @PrimaryKey(autoGenerate = true) val squadId: Long = 0,
    val teamName: String,
    val createdAt: Long = System.currentTimeMillis()
)
