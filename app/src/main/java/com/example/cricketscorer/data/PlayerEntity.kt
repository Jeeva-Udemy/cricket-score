package com.example.cricketscorer.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per player saved to a [SquadEntity]. Players can be selected as
 * batsmen/bowlers during scoring instead of retyping names, and can be
 * renamed or removed from the Squad tab at any time.
 */
@Entity(
    tableName = "players",
    foreignKeys = [ForeignKey(
        entity = SquadEntity::class,
        parentColumns = ["squadId"],
        childColumns = ["squadId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("squadId")]
)
data class PlayerEntity(
    @PrimaryKey(autoGenerate = true) val playerId: Long = 0,
    val squadId: Long,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
