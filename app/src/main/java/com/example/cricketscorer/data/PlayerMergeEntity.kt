package com.example.cricketscorer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * "Merge players" — one row per player entry that should be counted as a different
 * (canonical) player in Player Stats / Rankings / Player of the Match.
 *
 * Typical use: a name was typed by hand with a spelling mistake ("Jeevaa" instead of "Jeeva")
 * so it showed up as a separate player. Merging adds a row "Jeevaa (Team X) -> Jeeva (Team X)".
 *
 * Non-destructive on purpose: ball-by-ball data is never rewritten, stats just resolve names
 * through these rows at calculation time — so deleting the row ("Undo merge") restores the
 * original separate entries exactly.
 *
 * A player is identified by name + team (see stats/PlayerIdentity), so two different
 * players who happen to share a name in two different teams stay separate unless the user
 * explicitly merges them here (e.g. the same person really did play for two teams).
 */
@Entity(tableName = "player_merges")
data class PlayerMergeEntity(
    @PrimaryKey(autoGenerate = true) val mergeId: Long = 0,
    val fromName: String,
    val fromTeam: String,
    val toName: String,
    val toTeam: String,
    val createdAt: Long = System.currentTimeMillis()
)
