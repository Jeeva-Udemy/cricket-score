package com.example.cricketscorer.stats

import com.example.cricketscorer.data.BallEventEntity
import com.example.cricketscorer.data.InningsEntity
import com.example.cricketscorer.data.MatchEntity
import com.example.cricketscorer.model.ExtraType
import com.example.cricketscorer.model.WicketType

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
         *  name this player has batted or bowled under, across whatever scope was passed in. */
        val teams: Set<String>,
        val matches: Int,
        val batting: BattingStats,
        val bowling: BowlingStats
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

    /**
     * Aggregates every player who batted or bowled across [matches]. Scope is entirely the
     * caller's choice — pass every match ever played for career stats/Rankings, or just one
     * room's matches for that room's Player of the Series. [innings] and [ballEvents] must
     * cover (at least) all of [matches].
     */
    fun computePlayerStats(
        matches: List<MatchEntity>,
        innings: List<InningsEntity>,
        ballEvents: List<BallEventEntity>
    ): List<PlayerCareerStats> {
        val matchById = matches.associateBy { it.matchId }
        val ballsByInnings = ballEvents.groupBy { it.inningsId }

        val teamsByPlayer = mutableMapOf<String, MutableSet<String>>()
        val matchesByPlayer = mutableMapOf<String, MutableSet<Long>>()
        val battingByPlayer = mutableMapOf<String, MutableBatting>()
        val bowlingByPlayer = mutableMapOf<String, MutableBowling>()

        fun team(name: String, teamName: String?) {
            if (teamName.isNullOrBlank()) return
            teamsByPlayer.getOrPut(name) { mutableSetOf() }.add(teamName)
        }
        fun playedIn(name: String, matchId: Long) {
            matchesByPlayer.getOrPut(name) { mutableSetOf() }.add(matchId)
        }

        for (inn in innings) {
            val match = matchById[inn.matchId] ?: continue
            val balls = ballsByInnings[inn.inningsId].orEmpty()

            // ---- Batting: every player who's ever appeared as this innings' striker or
            // non-striker (ball-by-ball snapshots catch anyone who came in but never faced a
            // ball too, e.g. a non-striker run out backing up). ----
            val battersInInnings = linkedSetOf<String>()
            balls.forEach { b -> if (b.strikerName.isNotBlank()) battersInInnings.add(b.strikerName) }
            if (inn.strikerName.isNotBlank()) battersInInnings.add(inn.strikerName)
            if (inn.nonStrikerName.isNotBlank()) battersInInnings.add(inn.nonStrikerName)

            for (batter in battersInInnings) {
                val theirBalls = balls.filter { it.strikerName == batter }
                var runs = 0
                var ballsFaced = 0
                var fours = 0
                var sixes = 0
                theirBalls.forEach { b ->
                    if (b.extraType != ExtraType.WIDE) ballsFaced++
                    if (b.extraType == ExtraType.NONE || b.extraType == ExtraType.NO_BALL) {
                        runs += b.runsScored
                        if (b.runsScored == 4) fours++
                        if (b.runsScored == 6) sixes++
                    }
                }
                val isOut = balls.any { it.isWicket && it.dismissedPlayerName == batter }
                // Skip a batter who's only ever been the *waiting* non-striker so far in a
                // still-live innings — don't record a premature "not out, 0(0)" for someone who
                // simply hasn't come in yet. Once the innings/match is over, everyone who was
                // ever at the crease gets a real entry, including an unbeaten 0.
                val inningsOver = inn.isCompleted || match.isCompleted
                if (!isOut && !inningsOver && ballsFaced == 0) continue

                team(batter, inn.battingTeam)
                playedIn(batter, match.matchId)
                val stats = battingByPlayer.getOrPut(batter) { MutableBatting() }
                stats.innings++
                if (!isOut) stats.notOuts++
                stats.runs += runs
                stats.ballsFaced += ballsFaced
                stats.fours += fours
                stats.sixes += sixes
                if (runs > stats.highScore || (runs == stats.highScore && !isOut && !stats.highScoreNotOut)) {
                    stats.highScore = runs
                    stats.highScoreNotOut = !isOut
                }
            }

            // ---- Bowling: every bowler who's sent down at least one ball in this innings ----
            val bowlersInInnings = balls.mapNotNull { it.bowlerName.takeIf { n -> n.isNotBlank() } }.toSet()
            for (bowler in bowlersInInnings) {
                val theirBalls = balls.filter { it.bowlerName == bowler }
                if (theirBalls.isEmpty()) continue

                var ballsBowled = 0
                var runsConceded = 0
                var wickets = 0
                theirBalls.forEach { b ->
                    if (b.extraType != ExtraType.WIDE && b.extraType != ExtraType.NO_BALL) ballsBowled++
                    runsConceded += when (b.extraType) {
                        ExtraType.NONE -> b.runsScored
                        ExtraType.WIDE, ExtraType.NO_BALL -> b.extraRuns + b.runsScored
                        ExtraType.BYE, ExtraType.LEG_BYE, ExtraType.PENALTY -> 0
                    }
                    if (b.isWicket && b.wicketType != WicketType.NONE && b.wicketType != WicketType.RUN_OUT) wickets++
                }

                team(bowler, inn.bowlingTeam)
                playedIn(bowler, match.matchId)
                val stats = bowlingByPlayer.getOrPut(bowler) { MutableBowling() }
                stats.inningsBowled++
                stats.ballsBowled += ballsBowled
                stats.runsConceded += runsConceded
                stats.wickets += wickets
                val figures = BowlingFigures(wickets, runsConceded)
                if (stats.bestFigures == null || figures > stats.bestFigures!!) stats.bestFigures = figures
            }
        }

        val allPlayers = (battingByPlayer.keys + bowlingByPlayer.keys).toSortedSet()
        return allPlayers.map { name ->
            val b = battingByPlayer[name]
            val bowl = bowlingByPlayer[name]
            PlayerCareerStats(
                playerName = name,
                teams = teamsByPlayer[name].orEmpty(),
                matches = matchesByPlayer[name]?.size ?: 0,
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
                )
            )
        }
    }

    /** req: "For each match we need to show who's the Player of the Match." Null only if the
     *  match has no recorded balls at all (e.g. abandoned before a ball was bowled). */
    fun computePlayerOfTheMatch(
        match: MatchEntity,
        innings: List<InningsEntity>,
        ballEvents: List<BallEventEntity>
    ): PlayerAward? = topAward(listOf(match), innings, ballEvents)

    /** req: "If we are playing multiple matches in a single room then we need to show who's
     *  the Player of the series." Same points system, summed across every match passed in. */
    fun computePlayerOfTheSeries(
        matches: List<MatchEntity>,
        innings: List<InningsEntity>,
        ballEvents: List<BallEventEntity>
    ): PlayerAward? = topAward(matches, innings, ballEvents)

    private fun topAward(
        matches: List<MatchEntity>,
        innings: List<InningsEntity>,
        ballEvents: List<BallEventEntity>
    ): PlayerAward? {
        val stats = computePlayerStats(matches, innings, ballEvents)
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
