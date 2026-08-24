package com.example.cricketscorer.data

import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for match/innings/ball data. ViewModels never touch
 * the DAO directly — this keeps persistence details out of the presentation layer.
 */
class CricketRepository(private val dao: CricketDao) {

    // Matches
    suspend fun createMatch(match: MatchEntity): Long = dao.insertMatch(match)
    suspend fun updateMatch(match: MatchEntity) = dao.updateMatch(match)
    fun observeMatch(matchId: Long): Flow<MatchEntity?> = dao.observeMatch(matchId)
    suspend fun getMatch(matchId: Long): MatchEntity? = dao.getMatch(matchId)
    fun observeAllMatches(): Flow<List<MatchEntity>> = dao.observeAllMatches()

    suspend fun deleteMatches(matchIds: List<Long>) {
        if (matchIds.isEmpty()) return
        dao.deleteBallEventsForMatches(matchIds)
        dao.deleteInningsForMatches(matchIds)
        dao.deleteMatches(matchIds)
    }

    // Innings
    suspend fun createInnings(innings: InningsEntity): Long = dao.insertInnings(innings)
    suspend fun updateInnings(innings: InningsEntity) = dao.updateInnings(innings)
    fun observeInnings(inningsId: Long): Flow<InningsEntity?> = dao.observeInnings(inningsId)
    suspend fun getInnings(inningsId: Long): InningsEntity? = dao.getInnings(inningsId)
    suspend fun getInningsForMatch(matchId: Long): List<InningsEntity> = dao.getInningsForMatch(matchId)
    fun observeInningsForMatch(matchId: Long): Flow<List<InningsEntity>> = dao.observeInningsForMatch(matchId)

    // Ball events
    suspend fun addBallEvent(ballEvent: BallEventEntity): Long = dao.insertBallEvent(ballEvent)
    fun observeBallEvents(inningsId: Long): Flow<List<BallEventEntity>> = dao.observeBallEvents(inningsId)
    suspend fun getLastBallEvent(inningsId: Long): BallEventEntity? = dao.getLastBallEvent(inningsId)

    /** Deletes the most recent ball for an innings and returns it so the caller can reverse its score effects. */
    suspend fun undoLastBall(inningsId: Long): BallEventEntity? {
        val last = dao.getLastBallEvent(inningsId) ?: return null
        dao.deleteBallEvent(last.ballId)
        return last
    }

    // Squads
    suspend fun createSquad(squad: SquadEntity): Long = dao.insertSquad(squad)
    suspend fun updateSquad(squad: SquadEntity) = dao.updateSquad(squad)
    suspend fun deleteSquad(squadId: Long) = dao.deleteSquad(squadId)
    fun observeAllSquads(): Flow<List<SquadEntity>> = dao.observeAllSquads()
    suspend fun getSquad(squadId: Long): SquadEntity? = dao.getSquad(squadId)

    // Players
    suspend fun addPlayer(player: PlayerEntity): Long = dao.insertPlayer(player)
    suspend fun updatePlayer(player: PlayerEntity) = dao.updatePlayer(player)
    suspend fun deletePlayer(playerId: Long) = dao.deletePlayer(playerId)
    fun observePlayersForSquad(squadId: Long): Flow<List<PlayerEntity>> = dao.observePlayersForSquad(squadId)
    suspend fun getPlayersForSquad(squadId: Long): List<PlayerEntity> = dao.getPlayersForSquad(squadId)
}
