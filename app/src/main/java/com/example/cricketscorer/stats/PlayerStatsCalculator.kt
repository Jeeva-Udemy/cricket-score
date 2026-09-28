package com.example.cricketscorer.stats

import com.example.cricketscorer.data.BallEventEntity
import com.example.cricketscorer.data.InningsEntity
import com.example.cricketscorer.data.MatchEntity
import com.example.cricketscorer.data.PlayerMergeEntity
import com.example.cricketscorer.model.ExtraType

/**
 * Turns raw ball-by-ball data (the same [BallEventEntity] rows Undo relies on) into the
 * batting/bowling numbers Player Stats, Rankings, and Player of the Match/Series need:
 *  - req: "display the list of players and their details like which team they are playing for
 *    ... how much he scored, wickets taken, best bowling figure, overall score"
 *  - req: "show the list of players by their ranking based on batting and bowling performance
 *    separately ... just like we do it in international cricket"
 *  - req: "for each match we need to show who's the Player of the Match. If we are playing
 *    multiple matches in a single room ... show who's the Player of the series"
 *
 * Nothing here is persisted — every screen recomputes straight from ball events on open, the
 * same way the live score itself is derived. That keeps stats always correct after an Undo (a
 * corrected ball_events table is instantly reflected) without a second source of truth to keep
 * in sync, at the cost of recomputing on every screen open — fine for a local scorer app's data
 * volumes.
 *
 * Scoring rules mirrored here are standard cricket, not this app's invention:
 *  - Runs are credited to the batsman on strike only for NONE/NO_BALL deliveries — byes,
 *    leg-byes and wide-runs go to the team total but never to a batsman's own tally.
 *  - "Balls faced" counts every delivery the batsman was actually at the crease for, which is
 *    every extra type EXCEPT wides (a wide is, by definition, unplayable).
 *  - Bowlers are charged runs off the bat plus no-ball/wide penalties and any runs run off
 *    them — but never byes or leg-byes (those aren't the bowler's fault).
 *  - "Balls bowled" (for economy) counts legal deliveries only — wides and no-balls don't
 *    advance the over and don't count against a bowler's tally.
 *  - Run-outs are never credited to the bowler as a wicket, same as any real scorecard.
 */
object PlayerStatsCalculator {

    data class BattingStats(
        val innings: Int = 0,
        val notOuts: Int = 0,
        val runs: Int = 0,
        val ballsFaced: Int = 0,
        val fours: Int = 0,
        val sixes: Int = 0,
        val highScore: Int = 0,
        val highScoreNotOut: Boolean = false
    ) {
        val strikeRate: Double get() = if (ballsFaced == 0) 0.0 else runs * 100.0 / ballsFaced

        /** Null (shown as "-") when the player has never been dismissed — an average needs at
         *  least one completed innings to mean anything. */
        val average: Double?
            get() {
                val dismissals = innings - notOuts
                return if (dismissals <= 0) null else runs.toDouble() / dismissals
            }
    }

    /** Best-figures ordering: more wickets wins; among equal wickets, fewer runs wins — same
     *  comparison a real scorecard uses to pick "best bowling". */
    data class BowlingFigures(val wickets: Int, val runsConceded: Int) : Comparable<BowlingFigures> {
        override fun compareTo(other: BowlingFigures): Int =
            if (wickets != other.wickets) wickets - other.wickets else other.runsConceded - runsConceded
        override fun toString() = "$wickets/$runsConceded"
    }

    data class BowlingStats(
        val inningsBowled: Int = 0,
        val ballsBowled: Int = 0,
        val runsConceded: Int = 0,
        val wickets: Int = 0,
        val bestFigures: BowlingFigures? = null
    ) {
        val overs: String get() = "${ballsBowled / 6}.${ballsBowled % 6}"
        val economy: Double? get() = if (ballsBowled == 0) null else runsConceded * 6.0 / ballsBowled
        val average: Double? get() = if (wickets == 0) null else runsConceded.toDouble() / wickets
    }

    data class PlayerCareerStats(
        val playerName: String,
        /** req: "sometimes a single player can play for multiple teams" — every distinct team
         *  name this player has batted or bowled under, across whatever scope was passed in.
         *  More than one team only appears once the user has merged entries across teams. */
        val teams: Set<String>,
        val matches: Int,
        val batting: BattingStats,
        val bowling: BowlingStats,
        /** Unique identity (name + team, after merges) — use this, not [playerName], as a list
         *  key: two different players can share a name. */
        val playerKey: String = PlayerIdentity.key(playerName, teams.firstOrNull() ?: ""),
        /** The team this player entry belongs to (what a merge row points at). */
        val team: String = teams.firstOrNull() ?: "",
        /** Other spellings that were merged into this player (for display). */
        val mergedNames: Set<String> = emptySet()
    )

    /**
     * A single match's (or, summed across a room, a series') standout performer (req: Player of
     * the Match / Player of the Series). [points] is a simple, explicitly-documented
     * fantasy-cricket-style score — there's no official ICC formula for a local match — good
     * enough to separate a clear best performance from the pack: 1 point per run, +1 per four,
     * +2 per six, +20 per wicket.
     */
    data class PlayerAward(
        val playerName: String,
        val points: Int,
        val runs: Int,
        val ballsFaced: Int,
        val wickets: Int,
        val ballsBowled: Int,
        val runsConceded: Int
    )

    private class MutableBatting {
        var innings = 0
        var notOuts = 0
        var runs = 0
        var ballsFaced = 0
        var fours = 0
        var sixes = 0
        var highScore = 0
        var highScoreNotOut = false
    }

    private class MutableBowling {
        var inningsBowled = 0
        var ballsBowled = 0
        var runsConceded = 0
        var wickets = 0
        var bestFigures: BowlingFigures? = null
    }

    /** Collects every raw spelling/team seen for one resolved player. */
    private class Identity(val key: String) {
        val nameVotes = mutableMapOf<String, Int>()
        val teamVotes = mutableMapOf<String, Int>()
        var canonicalName: String? = null
        var canonicalTeam: String? = null
        val rawNames = mutableSetOf<String>()
        val teams = linkedMapOf<String, String>() // keyPart -> display
        val matchIds = mutableSetOf<Long>()

        fun displayName(): String = canonicalName ?: nameVotes.maxByOrNull { it.value }?.key ?: key
        fun displayTeam(): String = canonicalTeam ?: teamVotes.maxByOrNull { it.value }?.key ?: ""
    }

    /**
     * Aggregates every player who batted or bowled across [matches]. Scope is entirely the
     * caller's choice — pass every match ever played for career stats/Rankings, or just one
     * room's matches for that room's Player of the Series. [innings] and [ballEvents] must
     * cover (at least) all of [matches].
     *
     * Players are identified by name + team (see [PlayerIdentity]) — two players with the
     * same name in different teams are no longer combined into one — and [merges] (from
     * "Merge players") combine entries the user has confirmed are the same person.
     */
    fun computePlayerStats(
        matches: List<MatchEntity>,
        innings: List<InningsEntity>,
        ballEvents: List<BallEventEntity>,
        merges: List<PlayerMergeEntity> = emptyList()
    ): List<PlayerCareerStats> {
        val resolver = PlayerMergeResolver(merges)
        val matchById = matches.associateBy { it.matchId }
        val ballsByInnings = ballEvents.groupBy { it.inningsId }

        val identities = mutableMapOf<String, Identity>()
        val battingByPlayer = mutableMapOf<String, MutableBatting>()
        val bowlingByPlayer = mutableMapOf<String, MutableBowling>()

        /** Resolves a raw (name, team) to its identity key, recording how it was spelt. */
        fun identify(rawName: String, rawTeam: String, matchId: Long): String {
            val resolved = resolver.resolve(rawName, rawTeam)
            val id = identities.getOrPut(resolved.key) { Identity(resolved.key) }
            val cleanName = PlayerIdentity.clean(rawName)
            val cleanTeam = PlayerIdentity.clean(rawTeam)
            if (resolved.wasMerged) {
                id.canonicalName = resolved.name
                id.canonicalTeam = resolved.team
            } else {
                id.nameVotes[cleanName] = (id.nameVotes[cleanName] ?: 0) + 1
                id.teamVotes[cleanTeam] = (id.teamVotes[cleanTeam] ?: 0) + 1
            }
            id.rawNames += cleanName
            if (cleanTeam.isNotBlank()) id.teams.putIfAbsent(PlayerIdentity.keyPart(cleanTeam), cleanTeam)
            id.matchIds += matchId
            return resolved.key
        }

        for (inn in innings) {
            val match = matchById[inn.matchId] ?: continue
            val balls = ballsByInnings[inn.inningsId].orEmpty()
            val inningsOver = inn.isCompleted || match.isCompleted

            // ---- Batting: everyone who faced a ball, was dismissed (a non-striker run out
            // without facing a ball included), or is still at the crease. Raw names are
            // grouped by their exact spelling first, then resolved to an identity. ----
            val battersInInnings = linkedSetOf<String>()
            balls.forEach { b ->
                if (b.strikerName.isNotBlank()) battersInInnings.add(PlayerIdentity.clean(b.strikerName))
                if (b.isWicket) {
                    val out = ScorecardCalculator.dismissedName(b)
                    if (out.isNotBlank()) battersInInnings.add(PlayerIdentity.clean(out))
                }
            }
            if (inn.strikerName.isNotBlank()) battersInInnings.add(PlayerIdentity.clean(inn.strikerName))
            if (inn.nonStrikerName.isNotBlank()) battersInInnings.add(PlayerIdentity.clean(inn.nonStrikerName))

            // Several raw spellings can resolve to the same player inside one innings only if
            // the user merged them; aggregate per identity so that counts as ONE innings.
            data class InningsBat(var runs: Int = 0, var balls: Int = 0, var fours: Int = 0, var sixes: Int = 0, var out: Boolean = false)
            val perIdentity = linkedMapOf<String, InningsBat>()

            for (batter in battersInInnings) {
                val theirBalls = balls.filter { PlayerIdentity.clean(it.strikerName) == batter }
                var runs = 0
                var ballsFaced = 0
                var fours = 0
                var sixes = 0
                theirBalls.forEach { b ->
                    if (b.extraType != ExtraType.WIDE && b.extraType != ExtraType.PENALTY) ballsFaced++
                    if (b.extraType == ExtraType.NONE || b.extraType == ExtraType.NO_BALL) {
                        runs += b.runsScored
                        if (b.runsScored == 4) fours++
                        if (b.runsScored == 6) sixes++
                    }
                }
                val isOut = balls.any { it.isWicket && PlayerIdentity.clean(ScorecardCalculator.dismissedName(it)) == batter }
                // Skip a batter who's only ever been the *waiting* non-striker so far in a
                // still-live innings — don't record a premature "not out, 0(0)" for someone who
                // simply hasn't come in yet. Once the innings/match is over, everyone who was
                // ever at the crease gets a real entry, including an unbeaten 0.
                if (!isOut && !inningsOver && ballsFaced == 0) continue

                val key = identify(batter, inn.battingTeam, match.matchId)
                val agg = perIdentity.getOrPut(key) { InningsBat() }
                agg.runs += runs
                agg.balls += ballsFaced
                agg.fours += fours
                agg.sixes += sixes
                agg.out = agg.out || isOut
            }

            for ((key, agg) in perIdentity) {
                val stats = battingByPlayer.getOrPut(key) { MutableBatting() }
                stats.innings++
                if (!agg.out) stats.notOuts++
                stats.runs += agg.runs
                stats.ballsFaced += agg.balls
                stats.fours += agg.fours
                stats.sixes += agg.sixes
                if (agg.runs > stats.highScore || (agg.runs == stats.highScore && !agg.out && !stats.highScoreNotOut)) {
                    stats.highScore = agg.runs
                    stats.highScoreNotOut = !agg.out
                }
            }

            // ---- Bowling: every bowler who's sent down at least one ball in this innings ----
            data class InningsBowl(var balls: Int = 0, var runs: Int = 0, var wickets: Int = 0)
            val perBowler = linkedMapOf<String, InningsBowl>()
            balls.filter { it.bowlerName.isNotBlank() }.forEach { b ->
                val key = identify(b.bowlerName, inn.bowlingTeam, match.matchId)
                val agg = perBowler.getOrPut(key) { InningsBowl() }
                if (b.extraType != ExtraType.WIDE && b.extraType != ExtraType.NO_BALL && b.extraType != ExtraType.PENALTY) agg.balls++
                agg.runs += when (b.extraType) {
                    ExtraType.NONE -> b.runsScored
                    ExtraType.WIDE, ExtraType.NO_BALL -> b.extraRuns + b.runsScored
                    ExtraType.BYE, ExtraType.LEG_BYE, ExtraType.PENALTY -> 0
                }
                if (ScorecardCalculator.isBowlerWicket(b)) agg.wickets++
            }
            for ((key, agg) in perBowler) {
                val stats = bowlingByPlayer.getOrPut(key) { MutableBowling() }
                stats.inningsBowled++
                stats.ballsBowled += agg.balls
                stats.runsConceded += agg.runs
                stats.wickets += agg.wickets
                val figures = BowlingFigures(agg.wickets, agg.runs)
                if (stats.bestFigures == null || figures > stats.bestFigures!!) stats.bestFigures = figures
            }
        }

        val allKeys = battingByPlayer.keys + bowlingByPlayer.keys
        return allKeys.map { key ->
            val id = identities.getValue(key)
            val b = battingByPlayer[key]
            val bowl = bowlingByPlayer[key]
            val name = id.displayName()
            val team = id.displayTeam()
            // Primary team first, then any other team merged in.
            val teams = linkedSetOf<String>().apply {
                if (team.isNotBlank()) add(team)
                id.teams.values.forEach { t -> if (none { PlayerIdentity.keyPart(it) == PlayerIdentity.keyPart(t) }) add(t) }
            }
            PlayerCareerStats(
                playerName = name,
                teams = teams,
                matches = id.matchIds.size,
                batting = BattingStats(
                    innings = b?.innings ?: 0,
                    notOuts = b?.notOuts ?: 0,
                    runs = b?.runs ?: 0,
                    ballsFaced = b?.ballsFaced ?: 0,
                    fours = b?.fours ?: 0,
                    sixes = b?.sixes ?: 0,
                    highScore = b?.highScore ?: 0,
                    highScoreNotOut = b?.highScoreNotOut ?: false
                ),
                bowling = BowlingStats(
                    inningsBowled = bowl?.inningsBowled ?: 0,
                    ballsBowled = bowl?.ballsBowled ?: 0,
                    runsConceded = bowl?.runsConceded ?: 0,
                    wickets = bowl?.wickets ?: 0,
                    bestFigures = bowl?.bestFigures
                ),
                playerKey = key,
                team = team,
                mergedNames = id.rawNames.filter { PlayerIdentity.keyPart(it) != PlayerIdentity.keyPart(name) }.toSet()
            )
        }.sortedWith(compareBy({ it.playerName.lowercase() }, { it.team.lowercase() }))
    }

    /** req: "For each match we need to show who's the Player of the Match." Null only if the
     *  match has no recorded balls at all (e.g. abandoned before a ball was bowled). */
    fun computePlayerOfTheMatch(
        match: MatchEntity,
        innings: List<InningsEntity>,
        ballEvents: List<BallEventEntity>,
        merges: List<PlayerMergeEntity> = emptyList()
    ): PlayerAward? = topAward(listOf(match), innings, ballEvents, merges)

    /** req: "If we are playing multiple matches in a single room then we need to show who's
     *  the Player of the series." Same points system, summed across every match passed in. */
    fun computePlayerOfTheSeries(
        matches: List<MatchEntity>,
        innings: List<InningsEntity>,
        ballEvents: List<BallEventEntity>,
        merges: List<PlayerMergeEntity> = emptyList()
    ): PlayerAward? = topAward(matches, innings, ballEvents, merges)

    private fun topAward(
        matches: List<MatchEntity>,
        innings: List<InningsEntity>,
        ballEvents: List<BallEventEntity>,
        merges: List<PlayerMergeEntity>
    ): PlayerAward? {
        val stats = computePlayerStats(matches, innings, ballEvents, merges)
        return stats
            .map { p ->
                val points = p.batting.runs + p.batting.fours + p.batting.sixes * 2 + p.bowling.wickets * 20
                PlayerAward(
                    playerName = p.playerName,
                    points = points,
                    runs = p.batting.runs,
                    ballsFaced = p.batting.ballsFaced,
                    wickets = p.bowling.wickets,
                    ballsBowled = p.bowling.ballsBowled,
                    runsConceded = p.bowling.runsConceded
                )
            }
            .filter { it.ballsFaced > 0 || it.ballsBowled > 0 }
            .maxWithOrNull(compareBy({ it.points }, { it.runs }))
    }
}
