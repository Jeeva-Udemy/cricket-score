package com.example.cricketscorer.backup

import com.example.cricketscorer.data.BackupSnapshot
import com.example.cricketscorer.data.BallEventEntity
import com.example.cricketscorer.data.InningsEntity
import com.example.cricketscorer.data.MatchEntity
import com.example.cricketscorer.data.PlayerEntity
import com.example.cricketscorer.data.SquadEntity
import com.example.cricketscorer.model.ExtraType
import com.example.cricketscorer.model.TossDecision
import com.example.cricketscorer.model.WicketType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Converts a [BackupSnapshot] to/from plain JSON so it can be uploaded to (and downloaded
 * from) the user's Google Drive "App Data" folder for the Backup & Resync feature.
 *
 * Uses org.json (built into Android) rather than pulling in a JSON library, and writes
 * every field out explicitly rather than reflection, so the format is stable and readable.
 */
object BackupSerializer {

    private const val SCHEMA_VERSION = 1

    fun toJson(snapshot: BackupSnapshot): String {
        val root = JSONObject()
        root.put("schemaVersion", SCHEMA_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("matches", JSONArray(snapshot.matches.map { it.toJson() }))
        root.put("innings", JSONArray(snapshot.innings.map { it.toJson() }))
        root.put("ballEvents", JSONArray(snapshot.ballEvents.map { it.toJson() }))
        root.put("squads", JSONArray(snapshot.squads.map { it.toJson() }))
        root.put("players", JSONArray(snapshot.players.map { it.toJson() }))
        return root.toString()
    }

    fun fromJson(json: String): BackupSnapshot {
        val root = JSONObject(json)
        val matches = root.optJSONArray("matches")?.toObjectList()?.map { it.toMatchEntity() } ?: emptyList()
        val innings = root.optJSONArray("innings")?.toObjectList()?.map { it.toInningsEntity() } ?: emptyList()
        val ballEvents = root.optJSONArray("ballEvents")?.toObjectList()?.map { it.toBallEventEntity() } ?: emptyList()
        val squads = root.optJSONArray("squads")?.toObjectList()?.map { it.toSquadEntity() } ?: emptyList()
        val players = root.optJSONArray("players")?.toObjectList()?.map { it.toPlayerEntity() } ?: emptyList()
        return BackupSnapshot(matches, innings, ballEvents, squads, players)
    }

    private fun JSONArray.toObjectList(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    // ---- Matches ----

    private fun MatchEntity.toJson(): JSONObject = JSONObject().apply {
        put("matchId", matchId)
        put("teamAName", teamAName)
        put("teamBName", teamBName)
        put("totalOvers", totalOvers)
        put("playersPerTeam", playersPerTeam)
        put("teamASquadId", teamASquadId ?: JSONObject.NULL)
        put("teamBSquadId", teamBSquadId ?: JSONObject.NULL)
        put("tossWinnerTeam", tossWinnerTeam)
        put("tossDecision", tossDecision.name)
        put("currentInningsNumber", currentInningsNumber)
        put("isCompleted", isCompleted)
        put("resultSummary", resultSummary ?: JSONObject.NULL)
        put("createdAt", createdAt)
    }

    private fun JSONObject.toMatchEntity(): MatchEntity = MatchEntity(
        matchId = getLong("matchId"),
        teamAName = getString("teamAName"),
        teamBName = getString("teamBName"),
        totalOvers = getInt("totalOvers"),
        playersPerTeam = optInt("playersPerTeam", 11),
        teamASquadId = if (isNull("teamASquadId")) null else getLong("teamASquadId"),
        teamBSquadId = if (isNull("teamBSquadId")) null else getLong("teamBSquadId"),
        tossWinnerTeam = getString("tossWinnerTeam"),
        tossDecision = TossDecision.valueOf(getString("tossDecision")),
        currentInningsNumber = optInt("currentInningsNumber", 1),
        isCompleted = optBoolean("isCompleted", false),
        resultSummary = if (isNull("resultSummary")) null else getString("resultSummary"),
        createdAt = optLong("createdAt", System.currentTimeMillis())
    )

    // ---- Innings ----

    private fun InningsEntity.toJson(): JSONObject = JSONObject().apply {
        put("inningsId", inningsId)
        put("matchId", matchId)
        put("inningsNumber", inningsNumber)
        put("battingTeam", battingTeam)
        put("bowlingTeam", bowlingTeam)
        put("battingSquadId", battingSquadId ?: JSONObject.NULL)
        put("bowlingSquadId", bowlingSquadId ?: JSONObject.NULL)
        put("totalRuns", totalRuns)
        put("wickets", wickets)
        put("completedOvers", completedOvers)
        put("ballsThisOver", ballsThisOver)
        put("wideRuns", wideRuns)
        put("noBallRuns", noBallRuns)
        put("byeRuns", byeRuns)
        put("legByeRuns", legByeRuns)
        put("penaltyRuns", penaltyRuns)
        put("strikerBatsmanNumber", strikerBatsmanNumber)
        put("nonStrikerBatsmanNumber", nonStrikerBatsmanNumber)
        put("strikerName", strikerName)
        put("nonStrikerName", nonStrikerName)
        put("currentBowlerName", currentBowlerName)
        put("nextBatsmanNumber", nextBatsmanNumber)
        put("target", target ?: JSONObject.NULL)
        put("isCompleted", isCompleted)
    }

    private fun JSONObject.toInningsEntity(): InningsEntity = InningsEntity(
        inningsId = getLong("inningsId"),
        matchId = getLong("matchId"),
        inningsNumber = getInt("inningsNumber"),
        battingTeam = getString("battingTeam"),
        bowlingTeam = getString("bowlingTeam"),
        battingSquadId = if (isNull("battingSquadId")) null else getLong("battingSquadId"),
        bowlingSquadId = if (isNull("bowlingSquadId")) null else getLong("bowlingSquadId"),
        totalRuns = optInt("totalRuns", 0),
        wickets = optInt("wickets", 0),
        completedOvers = optInt("completedOvers", 0),
        ballsThisOver = optInt("ballsThisOver", 0),
        wideRuns = optInt("wideRuns", 0),
        noBallRuns = optInt("noBallRuns", 0),
        byeRuns = optInt("byeRuns", 0),
        legByeRuns = optInt("legByeRuns", 0),
        penaltyRuns = optInt("penaltyRuns", 0),
        strikerBatsmanNumber = optInt("strikerBatsmanNumber", 1),
        nonStrikerBatsmanNumber = optInt("nonStrikerBatsmanNumber", 2),
        strikerName = optString("strikerName", "Batsman 1"),
        nonStrikerName = optString("nonStrikerName", "Batsman 2"),
        currentBowlerName = optString("currentBowlerName", "Bowler 1"),
        nextBatsmanNumber = optInt("nextBatsmanNumber", 3),
        target = if (isNull("target")) null else getInt("target"),
        isCompleted = optBoolean("isCompleted", false)
    )

    // ---- Ball events ----

    private fun BallEventEntity.toJson(): JSONObject = JSONObject().apply {
        put("ballId", ballId)
        put("inningsId", inningsId)
        put("overNumber", overNumber)
        put("ballNumberInOver", ballNumberInOver)
        put("runsScored", runsScored)
        put("extraType", extraType.name)
        put("extraRuns", extraRuns)
        put("wicketType", wicketType.name)
        put("isWicket", isWicket)
        put("strikerBatsmanNumber", strikerBatsmanNumber)
        put("strikerName", strikerName)
        put("dismissedPlayerName", dismissedPlayerName)
        put("bowlerName", bowlerName)
        put("timestamp", timestamp)
        put("preTotalRuns", preTotalRuns)
        put("preWickets", preWickets)
        put("preCompletedOvers", preCompletedOvers)
        put("preBallsThisOver", preBallsThisOver)
        put("preWideRuns", preWideRuns)
        put("preNoBallRuns", preNoBallRuns)
        put("preByeRuns", preByeRuns)
        put("preLegByeRuns", preLegByeRuns)
        put("prePenaltyRuns", prePenaltyRuns)
        put("preStrikerBatsmanNumber", preStrikerBatsmanNumber)
        put("preNonStrikerBatsmanNumber", preNonStrikerBatsmanNumber)
        put("preStrikerName", preStrikerName)
        put("preNonStrikerName", preNonStrikerName)
        put("preNextBatsmanNumber", preNextBatsmanNumber)
        put("preIsCompleted", preIsCompleted)
    }

    private fun JSONObject.toBallEventEntity(): BallEventEntity = BallEventEntity(
        ballId = getLong("ballId"),
        inningsId = getLong("inningsId"),
        overNumber = getInt("overNumber"),
        ballNumberInOver = getInt("ballNumberInOver"),
        runsScored = getInt("runsScored"),
        extraType = ExtraType.valueOf(getString("extraType")),
        extraRuns = getInt("extraRuns"),
        wicketType = WicketType.valueOf(getString("wicketType")),
        isWicket = getBoolean("isWicket"),
        strikerBatsmanNumber = optInt("strikerBatsmanNumber", 1),
        strikerName = optString("strikerName", ""),
        dismissedPlayerName = optString("dismissedPlayerName", ""),
        bowlerName = optString("bowlerName", "Bowler 1"),
        timestamp = optLong("timestamp", System.currentTimeMillis()),
        preTotalRuns = optInt("preTotalRuns", 0),
        preWickets = optInt("preWickets", 0),
        preCompletedOvers = optInt("preCompletedOvers", 0),
        preBallsThisOver = optInt("preBallsThisOver", 0),
        preWideRuns = optInt("preWideRuns", 0),
        preNoBallRuns = optInt("preNoBallRuns", 0),
        preByeRuns = optInt("preByeRuns", 0),
        preLegByeRuns = optInt("preLegByeRuns", 0),
        prePenaltyRuns = optInt("prePenaltyRuns", 0),
        preStrikerBatsmanNumber = optInt("preStrikerBatsmanNumber", 1),
        preNonStrikerBatsmanNumber = optInt("preNonStrikerBatsmanNumber", 2),
        preStrikerName = optString("preStrikerName", ""),
        preNonStrikerName = optString("preNonStrikerName", ""),
        preNextBatsmanNumber = optInt("preNextBatsmanNumber", 3),
        preIsCompleted = optBoolean("preIsCompleted", false)
    )

    // ---- Squads ----

    private fun SquadEntity.toJson(): JSONObject = JSONObject().apply {
        put("squadId", squadId)
        put("teamName", teamName)
        put("createdAt", createdAt)
    }

    private fun JSONObject.toSquadEntity(): SquadEntity = SquadEntity(
        squadId = getLong("squadId"),
        teamName = getString("teamName"),
        createdAt = optLong("createdAt", System.currentTimeMillis())
    )

    // ---- Players ----

    private fun PlayerEntity.toJson(): JSONObject = JSONObject().apply {
        put("playerId", playerId)
        put("squadId", squadId)
        put("name", name)
        put("createdAt", createdAt)
    }

    private fun JSONObject.toPlayerEntity(): PlayerEntity = PlayerEntity(
        playerId = getLong("playerId"),
        squadId = getLong("squadId"),
        name = getString("name"),
        createdAt = optLong("createdAt", System.currentTimeMillis())
    )
}
