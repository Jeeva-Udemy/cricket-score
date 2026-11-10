package com.example.cricketscorer.stats

import com.example.cricketscorer.data.BallEventEntity
import com.example.cricketscorer.data.InningsEntity
import com.example.cricketscorer.data.MatchEntity
import com.example.cricketscorer.data.PlayerMergeEntity
import com.example.cricketscorer.model.ExtraType

/**
 * Overall Dashboard numbers: team-vs-team results, team totals, 4s/6s, per-team records and
 * the Top-10 player lists — all combined across every match played. Recomputed from the raw
 * innings / ball events each time (like Rankings), so it stays right after an Undo or Merge.
 *
 * Rules mirrored from [PlayerStatsCalculator]: a batsman is credited runs only on NONE /
 * NO_BALL deliveries, and a boundary with overthrows never counts as a 4 or 6. Super Over
 * innings are left out of runs / wickets / boundaries / player records (they only decide a
 * tied match's winner).
 */
object OverallDashboardCalculator {

    data class PlayerCount(val name: String, val value: Int)
    data class InningsBest(val name: String, val runs: Int, val notOut: Boolean, val against: String)

    data class TeamRecord(
        val team: String,
        val played: Int,
        val wins: Int,
        val losses: Int,
        val ties: Int,
        val runsScored: Int,
        val wicketsTaken: Int,
        val fours: Int,
        val sixes: Int,
        val mostFours: PlayerCount?,
        val mostSixes: PlayerCount?,
        val highestInnings: InningsBest?,
        val highestOverall: PlayerCount?
    )

    data class HeadToHead(
        val teamA: String,
        val teamB: String,
        val played: Int,
        val winsA: Int,
        val winsB: Int,
        val ties: Int
    )

    data class TopPlayer(val name: String, val team: String, val value: Int)

    /** One batter's innings — "Best Batting Figure" is the single best of these. */
    data class WinningBatting(
        val name: String, val team: String, val runs: Int, val balls: Int, val notOut: Boolean,
        val against: String, val playedAt: Long, val result: String
    )

    /** One bowler's spell — "Best Bowling Figure" is the single best of these. */
    data class WinningBowling(
        val name: String, val team: String, val wickets: Int, val runs: Int, val legalBalls: Int,
        val against: String, val playedAt: Long, val result: String
    ) {
        val overs: String get() = "${legalBalls / 6}.${legalBalls % 6}"
    }

    data class DashboardData(
        val totalMatches: Int,
        val completedMatches: Int,
        val teams: List<TeamRecord>,
        val headToHead: List<HeadToHead>,
        val topOverall: List<TopPlayer>,
        val topBatters: List<TopPlayer>,
        val topBowlers: List<TopPlayer>,
        val bestBattingInWin: List<WinningBatting> = emptyList(),
        val bestBowlingInWin: List<WinningBowling> = emptyList()
    ) {
        companion object { val EMPTY = DashboardData(0, 0, emptyList(), emptyList(), emptyList(), emptyList(), emptyList()) }
    }

    private class TeamAcc(val key: String) {
        val names = mutableMapOf<String, Int>()
        var played = 0; var wins = 0; var losses = 0; var ties = 0
        var runs = 0; var wickets = 0; var fours = 0; var sixes = 0
        val foursBy = mutableMapOf<String, Int>()
        val sixesBy = mutableMapOf<String, Int>()
        val totalBy = mutableMapOf<String, Int>()
        var bestInnings: InningsBest? = null
        fun see(name: String) { names[name] = (names[name] ?: 0) + 1 }
        fun display() = names.maxByOrNull { it.value }?.key ?: key
    }

    fun compute(
        matches: List<MatchEntity>,
        innings: List<InningsEntity>,
        ballEvents: List<BallEventEntity>,
        merges: List<PlayerMergeEntity> = emptyList()
    ): DashboardData {
        if (matches.isEmpty()) return DashboardData.EMPTY
        val resolver = PlayerMergeResolver(merges)
        val inningsByMatch = innings.groupBy { it.matchId }
        val ballsByInnings = ballEvents.groupBy { it.inningsId }
        val teams = linkedMapOf<String, TeamAcc>()
        fun team(name: String): TeamAcc {
            val clean = PlayerIdentity.clean(name)
            return teams.getOrPut(PlayerIdentity.keyPart(clean)) { TeamAcc(PlayerIdentity.keyPart(clean)) }.also { it.see(clean) }
        }
        val h2h = mutableMapOf<Pair<String, String>, IntArray>() // [played, winsFirst, winsSecond, ties]
        val winBat = mutableListOf<WinningBatting>()
        val winBowl = mutableListOf<WinningBowling>()

        for (m in matches) {
            val a = team(m.teamAName)
            val b = team(m.teamBName)
            val matchInnings = inningsByMatch[m.matchId].orEmpty().sortedBy { it.inningsNumber }

            // ---- Results (completed matches only) ----
            // A match ended during its 1st innings ("Complete Match" — see MatchCompletion) is a
            // no result: it isn't a win, loss or tie for either side.
            val decided = matchInnings.count { !it.isSuperOver } >= 2
            if (m.isCompleted && decided) {
                a.played++; b.played++
                val winnerKey = winnerKey(matchInnings)
                val pairKey = if (a.key <= b.key) a.key to b.key else b.key to a.key
                val rec = h2h.getOrPut(pairKey) { IntArray(4) }
                rec[0]++
                when (winnerKey) {
                    a.key -> { a.wins++; b.losses++; rec[if (pairKey.first == a.key) 1 else 2]++ }
                    b.key -> { b.wins++; a.losses++; rec[if (pairKey.first == b.key) 1 else 2]++ }
                    else -> { a.ties++; b.ties++; rec[3]++ }
                }
            }

            // Best Batting / Bowling Figure: every innings by either team counts.
            collectFigures(m, matchInnings, ballsByInnings, resolver, winBat, winBowl)

            // ---- Totals, boundaries, individual scores (regular innings only) ----
            for (inn in matchInnings.filter { !it.isSuperOver }) {
                val bat = team(inn.battingTeam)
                val bowl = team(inn.bowlingTeam)
                bat.runs += inn.totalRuns
                bowl.wickets += inn.wickets
                val balls = ballsByInnings[inn.inningsId].orEmpty()
                val perBatter = linkedMapOf<String, IntArray>() // key -> [runs, fours, sixes]
                val displayName = mutableMapOf<String, String>()
                val outKeys = mutableSetOf<String>()
                balls.forEach { b ->
                    if (b.isWicket) {
                        val out = ScorecardCalculator.dismissedName(b)
                        if (out.isNotBlank()) outKeys += resolver.resolve(out, inn.battingTeam).key
                    }
                    if (b.strikerName.isBlank()) return@forEach
                    val r = resolver.resolve(b.strikerName, inn.battingTeam)
                    displayName[r.key] = r.name
                    val acc = perBatter.getOrPut(r.key) { IntArray(3) }
                    if (b.extraType == ExtraType.NONE || b.extraType == ExtraType.NO_BALL) {
                        acc[0] += b.runsScored
                        if (b.runsScored == 4 && !b.isOverthrow) { acc[1]++; bat.fours++ }
                        if (b.runsScored == 6 && !b.isOverthrow) { acc[2]++; bat.sixes++ }
                    }
                }
                perBatter.forEach { (key, v) ->
                    val name = displayName.getValue(key)
                    bat.totalBy[name] = (bat.totalBy[name] ?: 0) + v[0]
                    if (v[1] > 0) bat.foursBy[name] = (bat.foursBy[name] ?: 0) + v[1]
                    if (v[2] > 0) bat.sixesBy[name] = (bat.sixesBy[name] ?: 0) + v[2]
                    val best = bat.bestInnings
                    if (best == null || v[0] > best.runs) {
                        bat.bestInnings = InningsBest(name, v[0], key !in outKeys, bowl.display())
                    }
                }
            }
        }

        val records = teams.values.map { t ->
            fun top(m: Map<String, Int>) = m.maxByOrNull { it.value }?.let { PlayerCount(it.key, it.value) }
            TeamRecord(
                team = t.display(), played = t.played, wins = t.wins, losses = t.losses, ties = t.ties,
                runsScored = t.runs, wicketsTaken = t.wickets, fours = t.fours, sixes = t.sixes,
                mostFours = top(t.foursBy), mostSixes = top(t.sixesBy),
                highestInnings = t.bestInnings?.takeIf { it.runs > 0 },
                highestOverall = top(t.totalBy)?.takeIf { it.value > 0 }
            )
        }.sortedByDescending { it.wins }

        val headToHead = h2h.map { (pair, r) ->
            HeadToHead(teams.getValue(pair.first).display(), teams.getValue(pair.second).display(), r[0], r[1], r[2], r[3])
        }.sortedByDescending { it.played }

        // ---- Top 10 players (same numbers Rankings uses) ----
        val players = PlayerStatsCalculator.computePlayerStats(matches, innings, ballEvents, merges)
        fun topPlayers(sel: (PlayerStatsCalculator.PlayerCareerStats) -> Int) =
            players.map { TopPlayer(it.playerName, it.team, sel(it)) }
                .filter { it.value > 0 }.sortedByDescending { it.value }.take(10)

        return DashboardData(
            totalMatches = matches.size,
            completedMatches = matches.count { it.isCompleted },
            teams = records,
            headToHead = headToHead,
            // Same points system as Player of the Match: 1/run, +1 per four, +2 per six, 20/wicket.
            topOverall = topPlayers { it.batting.runs + it.batting.fours + it.batting.sixes * 2 + it.bowling.wickets * 20 },
            topBatters = topPlayers { it.batting.runs },
            topBowlers = topPlayers { it.bowling.wickets },
            bestBattingInWin = winBat
                .sortedWith(compareByDescending<WinningBatting> { it.runs }.thenBy { it.balls }.thenByDescending { it.notOut })
                .take(1),
            bestBowlingInWin = winBowl
                .sortedWith(compareByDescending<WinningBowling> { it.wickets }.thenBy { it.runs }.thenBy { it.legalBalls })
                .take(1)
        )
    }

    /**
     * Best Batting / Bowling Figure: every batter (with runs) and every bowler (with wickets)
     * of BOTH teams in this match, regular innings only — the dashboard keeps the single best.
     * Same rules as the scorecard — batter runs on NONE / NO_BALL, balls faced exclude wides;
     * bowler charged bat runs + wides + no-balls (not byes / leg-byes), run-outs don't count.
     */
    private fun collectFigures(
        m: MatchEntity,
        inns: List<InningsEntity>,
        ballsByInnings: Map<Long, List<BallEventEntity>>,
        resolver: PlayerMergeResolver,
        bat: MutableList<WinningBatting>,
        bowl: MutableList<WinningBowling>
    ) {
        val result = m.resultSummary.orEmpty()
        for (inn in inns.filter { !it.isSuperOver }) {
            val balls = ballsByInnings[inn.inningsId].orEmpty().filter { it.extraType != ExtraType.PENALTY }
            run {
                val per = linkedMapOf<String, IntArray>() // [runs, balls, out]
                balls.forEach { b ->
                    val e = per.getOrPut(b.strikerName.ifBlank { "Batsman" }) { IntArray(3) }
                    if (b.extraType != ExtraType.WIDE) e[1]++
                    if (b.extraType == ExtraType.NONE || b.extraType == ExtraType.NO_BALL) e[0] += b.runsScored
                    if (b.isWicket) per.getOrPut(ScorecardCalculator.dismissedName(b)) { IntArray(3) }[2] = 1
                }
                per.forEach { (name, e) ->
                    if (e[0] <= 0) return@forEach
                    val r = resolver.resolve(name, inn.battingTeam)
                    bat += WinningBatting(r.name, r.team, e[0], e[1], e[2] == 0, inn.bowlingTeam, m.createdAt, result)
                }
            }
            run {
                val per = linkedMapOf<String, IntArray>() // [wickets, runs, legal balls]
                balls.filter { it.bowlerName.isNotBlank() }.forEach { b ->
                    val e = per.getOrPut(b.bowlerName) { IntArray(3) }
                    if (b.extraType == ExtraType.NONE || b.extraType == ExtraType.WIDE || b.extraType == ExtraType.NO_BALL) {
                        e[1] += b.runsScored + b.extraRuns
                    }
                    if (ScorecardCalculator.isLegal(b)) e[2]++
                    if (ScorecardCalculator.isBowlerWicket(b)) e[0]++
                }
                per.forEach { (name, e) ->
                    if (e[0] <= 0) return@forEach
                    val r = resolver.resolve(name, inn.bowlingTeam)
                    bowl += WinningBowling(r.name, r.team, e[0], e[1], e[2], inn.battingTeam, m.createdAt, result)
                }
            }
        }
    }

    /** Winning team key from the innings data (Super Over pair decides a tied match); null = tie / no result. */
    private fun winnerKey(inns: List<InningsEntity>): String? {
        fun pairWinner(first: InningsEntity?, second: InningsEntity?): String? {
            if (first == null || second == null) return null
            return when {
                second.totalRuns > first.totalRuns -> PlayerIdentity.keyPart(second.battingTeam)
                second.totalRuns < first.totalRuns -> PlayerIdentity.keyPart(first.battingTeam)
                else -> null
            }
        }
        val regular = inns.filter { !it.isSuperOver }
        pairWinner(regular.getOrNull(0), regular.getOrNull(1))?.let { return it }
        val supers = inns.filter { it.isSuperOver }.sortedBy { it.inningsNumber }
        // Latest completed Super Over pair.
        val pairs = supers.chunked(2).filter { it.size == 2 }
        for (p in pairs.reversed()) pairWinner(p[0], p[1])?.let { return it }
        return null
    }
}
