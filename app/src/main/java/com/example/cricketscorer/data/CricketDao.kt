package com.example.cricketscorer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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

    @Query("DELETE FROM matches WHERE matchId IN (:matchIds)")
    suspend fun deleteMatches(matchIds: List<Long>)

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

    @Query("DELETE FROM innings WHERE matchId IN (:matchIds)")
    suspend fun deleteInningsForMatches(matchIds: List<Long>)

    // ---------- Ball events ----------

    @Insert
    suspend fun insertBallEvent(ballEvent: BallEventEntity): Long

    @Query("SELECT * FROM ball_events WHERE inningsId = :inningsId ORDER BY ballId ASC")
    fun observeBallEvents(inningsId: Long): Flow<List<BallEventEntity>>

    @Query("SELECT * FROM ball_events WHERE inningsId = :inningsId ORDER BY ballId DESC LIMIT 1")
    suspend fun getLastBallEvent(inningsId: Long): BallEventEntity?

    @Query("DELETE FROM ball_events WHERE ballId = :ballId")
    suspend fun deleteBallEvent(ballId: Long)

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

    @Query("SELECT * FROM squads WHERE squadId = :squadId")
    suspend fun getSquad(squadId: Long): SquadEntity?

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
}
