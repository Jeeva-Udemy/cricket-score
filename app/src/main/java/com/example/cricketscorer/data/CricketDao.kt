package com.example.cricketscorer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CricketDao {

    // ---------- Matches ----------

    @Insert
    suspend fun insertMatch(match: MatchEntity): Long

    @Update
    suspend fun updateMatch(match: MatchEntity)

    @Query("SELECT * FROM matches WHERE matchId = :matchId")
    fun observeMatch(matchId: Long): Flow<MatchEntity?>

    @Query("SELECT * FROM matches WHERE matchId = :matchId")
    suspend fun getMatch(matchId: Long): MatchEntity?

    @Query("SELECT * FROM matches ORDER BY createdAt DESC")
    fun observeAllMatches(): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches ORDER BY createdAt DESC")
    suspend fun getAllMatches(): List<MatchEntity>

    /** Rooms (req: "show the list of matches inside the each Rooms that we created") — every
     *  match created inside a room reuses the room's code as its own [MatchEntity.shareCode]
     *  (see MatchSetupViewModel.startMatch), so this is a plain lookup by that column. */
    @Query("SELECT * FROM matches WHERE shareCode = :roomCode ORDER BY createdAt DESC")
    fun observeMatchesForShareCode(roomCode: String): Flow<List<MatchEntity>>

    @Query("DELETE FROM matches WHERE matchId IN (:matchIds)")
    suspend fun deleteMatches(matchIds: List<Long>)

    /** Deletes every match (and, via cascade, every innings/ball event). Used only by Drive Resync. */
    @Query("DELETE FROM matches")
    suspend fun clearAllMatches()

    // ---------- Innings ----------

    @Insert
    suspend fun insertInnings(innings: InningsEntity): Long

    @Update
    suspend fun updateInnings(innings: InningsEntity)

    @Query("SELECT * FROM innings WHERE inningsId = :inningsId")
    fun observeInnings(inningsId: Long): Flow<InningsEntity?>

    @Query("SELECT * FROM innings WHERE inningsId = :inningsId")
    suspend fun getInnings(inningsId: Long): InningsEntity?

    @Query("SELECT * FROM innings WHERE matchId = :matchId ORDER BY inningsNumber ASC")
    suspend fun getInningsForMatch(matchId: Long): List<InningsEntity>

    @Query("SELECT * FROM innings WHERE matchId = :matchId ORDER BY inningsNumber ASC")
    fun observeInningsForMatch(matchId: Long): Flow<List<InningsEntity>>

    @Query("SELECT * FROM innings")
    suspend fun getAllInnings(): List<InningsEntity>

    @Query("DELETE FROM innings WHERE matchId IN (:matchIds)")
    suspend fun deleteInningsForMatches(matchIds: List<Long>)

    // ---------- Ball events ----------

    @Insert
    suspend fun insertBallEvent(ballEvent: BallEventEntity): Long

    @Query("SELECT * FROM ball_events WHERE inningsId = :inningsId ORDER BY ballId ASC")
    fun observeBallEvents(inningsId: Long): Flow<List<BallEventEntity>>

    @Query("SELECT * FROM ball_events")
    suspend fun getAllBallEvents(): List<BallEventEntity>

    @Query("SELECT * FROM ball_events WHERE inningsId = :inningsId ORDER BY ballId DESC LIMIT 1")
    suspend fun getLastBallEvent(inningsId: Long): BallEventEntity?

    @Query("SELECT * FROM ball_events WHERE inningsId = :inningsId ORDER BY ballId ASC")
    suspend fun getBallEventsForInnings(inningsId: Long): List<BallEventEntity>

    @Query("DELETE FROM ball_events WHERE ballId = :ballId")
    suspend fun deleteBallEvent(ballId: Long)

    /** Cloud Sync: wipes the locally-known balls for these innings before re-inserting the
     *  authoritative set that just arrived from Firestore, so a remote Undo (which removes a
     *  row) is reflected locally instead of only ever adding rows. */
    @Query("DELETE FROM ball_events WHERE inningsId IN (:inningsIds)")
    suspend fun deleteBallEventsForInnings(inningsIds: List<Long>)

    @Query("DELETE FROM ball_events WHERE inningsId IN (SELECT inningsId FROM innings WHERE matchId IN (:matchIds))")
    suspend fun deleteBallEventsForMatches(matchIds: List<Long>)

    // ---------- Squads ----------

    @Insert
    suspend fun insertSquad(squad: SquadEntity): Long

    @Update
    suspend fun updateSquad(squad: SquadEntity)

    @Query("DELETE FROM squads WHERE squadId = :squadId")
    suspend fun deleteSquad(squadId: Long)

    @Query("SELECT * FROM squads ORDER BY teamName ASC")
    fun observeAllSquads(): Flow<List<SquadEntity>>

    @Query("SELECT * FROM squads ORDER BY teamName ASC")
    suspend fun getAllSquads(): List<SquadEntity>

    @Query("SELECT * FROM squads WHERE squadId = :squadId")
    suspend fun getSquad(squadId: Long): SquadEntity?

    /** Deletes every squad (and, via cascade, every player). Used only by Drive Resync. */
    @Query("DELETE FROM squads")
    suspend fun clearAllSquads()

    // ---------- Players ----------

    @Insert
    suspend fun insertPlayer(player: PlayerEntity): Long

    @Update
    suspend fun updatePlayer(player: PlayerEntity)

    @Query("DELETE FROM players WHERE playerId = :playerId")
    suspend fun deletePlayer(playerId: Long)

    @Query("SELECT * FROM players WHERE squadId = :squadId ORDER BY createdAt ASC")
    fun observePlayersForSquad(squadId: Long): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE squadId = :squadId ORDER BY createdAt ASC")
    suspend fun getPlayersForSquad(squadId: Long): List<PlayerEntity>

    @Query("SELECT * FROM players")
    suspend fun getAllPlayers(): List<PlayerEntity>

    // ---------- Restore (Google Drive Resync) ----------
    // These re-insert rows with their ORIGINAL primary keys (REPLACE on conflict) so that
    // foreign keys between matches/innings/ball_events and squads/players stay intact when
    // restoring a full backup onto a fresh install.

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreMatch(match: MatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreInnings(innings: InningsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreBallEvent(ballEvent: BallEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreSquad(squad: SquadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restorePlayer(player: PlayerEntity)

    // ---------- Cloud Sync: apply a live snapshot from the other device ----------

    /**
     * Applies an entire remote [BackupSnapshot] (match + innings + ball events + the squads
     * needed to score it) as ONE atomic transaction instead of a sequence of separate
     * inserts/deletes.
     *
     * This matters a lot for the "constant flickering / jumping tabs" bug: previously the
     * match row, each innings row, and the full delete-then-reinsert of ball events were
     * separate statements, so Room's Flow-based observers (observeMatch/observeInningsForMatch/
     * observeBallEvents) could each fire on the half-applied state in between them — e.g. after
     * the ball events for an innings were deleted but before they were reinserted, or after the
     * 2nd innings row was restored but before currentInningsNumber was updated on the match row.
     * Wrapping the whole thing in @Transaction means every observer sees one clean "before" or
     * "after" state, never anything in between.
     */
    @Transaction
    suspend fun applyLiveMatchSnapshot(snapshot: BackupSnapshot) {
        snapshot.matches.forEach { restoreMatch(it) }
        snapshot.squads.forEach { restoreSquad(it) }
        snapshot.players.forEach { restorePlayer(it) }
        snapshot.innings.forEach { restoreInnings(it) }
        val inningsIds = snapshot.innings.map { it.inningsId }
        if (inningsIds.isNotEmpty()) deleteBallEventsForInnings(inningsIds)
        snapshot.ballEvents.forEach { restoreBallEvent(it) }
    }

    // ---------- Live scoring: atomic multi-row writes ----------
    // Each of these bundles what used to be 2-3 separate DAO calls from ScoringViewModel into
    // one @Transaction. That matters a lot for how responsive scoring feels: Room's Flow-based
    // observers (observeInningsForMatch/observeMatch/observeBallEvents) re-emit on every single
    // write, so a "single tap" that used to be 2 separate writes fired 2 separate emissions —
    // one with the new ball but the old score, then another moments later with the corrected
    // score — which Compose renders as a visible stutter/revert, and which every device sharing
    // the match also had to sync through (and could easily observe mid-glitch). Collapsing each
    // user action into exactly one atomic write means exactly one clean emission, locally and
    // once synced.

    /** Appends the ball's audit-log row and applies its resulting score to the innings, atomically. */
    @Transaction
    suspend fun recordBall(ballEvent: BallEventEntity, updatedInnings: InningsEntity) {
        insertBallEvent(ballEvent)
        updateInnings(updatedInnings)
    }

    /** Deletes the last ball's audit-log row, restores the innings to its pre-ball state, and
     *  (if that ball had just completed the match) reopens the match — atomically. */
    @Transaction
    suspend fun undoBall(ballId: Long, restoredInnings: InningsEntity, reopenedMatch: MatchEntity?) {
        deleteBallEvent(ballId)
        updateInnings(restoredInnings)
        reopenedMatch?.let { updateMatch(it) }
    }

    /**
     * Wraps up an innings: marks it completed and, in the same transaction, either creates the
     * next innings and advances the match onto it, or marks the whole match complete. Returns
     * the newly-created innings' Room-assigned id (null when the match just ended instead).
     *
     * Doing this atomically closes the exact race the old (non-atomic) version of this logic
     * used to hit: the innings-complete write and the match/next-innings write used to be
     * separate, so the "2nd innings exists but match.currentInningsNumber still says 1" (or vice
     * versa) in-between state was directly observable — that's what made the opener-picker and
     * squad lists briefly latch onto the wrong innings right at the innings break.
     */
    @Transaction
    suspend fun finishInningsAtomic(
        completedInnings: InningsEntity,
        nextInnings: InningsEntity?,
        updatedMatch: MatchEntity
    ): Long? {
        updateInnings(completedInnings)
        val nextInningsId = nextInnings?.let { insertInnings(it) }
        updateMatch(updatedMatch)
        return nextInningsId
    }
}
