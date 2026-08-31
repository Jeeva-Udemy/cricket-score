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

    // ---------- Cloud Sync (Firestore) ----------
    // A "live match" mirror is much smaller than the full [BackupSnapshot] above: only the
    // one match, its innings, and their ball events — never squads/players (those stay
    // local/per-device) and never other matches, since only the match being actively shared
    // is written to Firestore. See [com.example.cricketscorer.sync.CloudSync].

    /** Builds the payload pushed to Firestore whenever this match's local data changes. */
    suspend fun getSnapshotForMatch(matchId: Long): BackupSnapshot {
        val match = dao.getMatch(matchId)
        val innings = dao.getInningsForMatch(matchId)
        val ballEvents = innings.flatMap { dao.getBallEventsForInnings(it.inningsId) }
        return BackupSnapshot(
            matches = listOfNotNull(match),
            innings = innings,
            ballEvents = ballEvents,
            squads = emptyList(),
            players = emptyList()
        )
    }

    /**
     * Applies a [BackupSnapshot] received from Firestore (the other device's latest state)
     * onto local Room, preserving the original ids so this device's rows line up with the
     * other device's (same trick [restoreFromBackup] already uses for Drive resync).
     * Ball events are fully replaced per-innings rather than merged, so a ball undone on the
     * other device disappears here too instead of only ever being added to.
     */
    suspend fun applyMatchSnapshot(snapshot: BackupSnapshot) {
        snapshot.matches.forEach { dao.restoreMatch(it) }
        snapshot.innings.forEach { dao.restoreInnings(it) }
        val inningsIds = snapshot.innings.map { it.inningsId }
        if (inningsIds.isNotEmpty()) dao.deleteBallEventsForInnings(inningsIds)
        snapshot.ballEvents.forEach { dao.restoreBallEvent(it) }
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

    // ---------- Backup / Resync (Google Drive) ----------

    /** Snapshot of every table, used to build the JSON backup uploaded to Drive. */
    suspend fun getFullBackupSnapshot(): BackupSnapshot = BackupSnapshot(
        matches = dao.getAllMatches(),
        innings = dao.getAllInnings(),
        ballEvents = dao.getAllBallEvents(),
        squads = dao.getAllSquads(),
        players = dao.getAllPlayers()
    )

    /**
     * Wipes all local data and replaces it with the contents of [snapshot], preserving the
     * original row ids so foreign keys (innings -> match, players -> squad, etc.) stay valid.
     * Used only when the user taps "Resync from Drive".
     */
    suspend fun restoreFromBackup(snapshot: BackupSnapshot) {
        dao.clearAllMatches() // cascades: innings, ball_events
        dao.clearAllSquads()  // cascades: players

        snapshot.squads.forEach { dao.restoreSquad(it) }
        snapshot.players.forEach { dao.restorePlayer(it) }
        snapshot.matches.forEach { dao.restoreMatch(it) }
        snapshot.innings.forEach { dao.restoreInnings(it) }
        snapshot.ballEvents.forEach { dao.restoreBallEvent(it) }
    }
}

/** Full point-in-time export of everything the app stores locally. */
data class BackupSnapshot(
    val matches: List<MatchEntity>,
    val innings: List<InningsEntity>,
    val ballEvents: List<BallEventEntity>,
    val squads: List<SquadEntity>,
    val players: List<PlayerEntity>
)
