package com.example.cricketscorer.stats

import com.example.cricketscorer.data.BallEventEntity
import com.example.cricketscorer.data.InningsEntity
import com.example.cricketscorer.data.MatchEntity
import com.example.cricketscorer.data.PlayerMergeEntity

/**
 * Everything the shareable Match Dashboard image shows, built from one match's data.
 * Pure Kotlin (no Android) so it's easy to test; drawing lives in ui/dashboard.
 */
data class MatchDashboard(
    val title: String,
    val subtitle: String,
    val tossLine: String,
    val result: String,
    val isCompleted: Boolean,
    val innings: List<InningsSummary>,
    val playerOfTheMatch: PlayerStatsCalculator.PlayerAward?,
    val topBatters: List<Performer>,
    val topBowlers: List<Performer>
) {
    data class InningsSummary(
        val label: String,
        val battingTeam: String,
        val runs: Int,
        val wickets: Int,
        val overs: String,
        val oversLimit: Int,
        val runRate: Double,
        val extras: Int,
        val extrasDetail: String,
        val target: Int?,
        val isSuperOver: Boolean,
        val isLive: Boolean,
        val batting: List<ScorecardCalculator.BattingLine>,
        val bowling: List<ScorecardCalculator.BowlingLine>
    ) {
        val score: String get() = "$runs/$wickets"
    }

    data class Performer(val name: String, val team: String, val figure: String, val detail: String)
}

object MatchDashboardBuilder {

    fun build(
        match: MatchEntity,
        innings: List<InningsEntity>,
        ballEvents: List<BallEventEntity>,
        merges: List<PlayerMergeEntity>,
        dateText: String
    ): MatchDashboard {
        val resolver = PlayerMergeResolver(merges)
        val ballsByInnings = ballEvents.groupBy { it.inningsId }
        val sorted = innings.sortedBy { it.inningsNumber }

        val summaries = sorted.map { inn ->
            val balls = ballsByInnings[inn.inningsId].orEmpty().sortedBy { it.ballId }
            // Show the corrected (merged) spelling on the dashboard too.
            val batting = ScorecardCalculator.battingLines(inn, balls)
                .filterNot { isPlaceholder(it.name) && it.balls == 0 && !it.isOut }
                .map { it.copy(name = resolver.resolve(it.name, inn.battingTeam).name) }
            val bowling = ScorecardCalculator.bowlingLines(balls)
                .filterNot { isPlaceholder(it.name) && it.legalBalls == 0 }
                .map { it.copy(name = resolver.resolve(it.name, inn.bowlingTeam).name) }
            val legalBalls = inn.completedOvers * 6 + inn.ballsThisOver
            val superOverIndex = (inn.inningsNumber - 1) / 2
            val label = when {
                inn.isSuperOver -> "Super Over" + (if (superOverIndex > 1) " $superOverIndex" else "") +
                    " — ${inn.battingTeam}"
                inn.inningsNumber == 1 -> "1st Innings — ${inn.battingTeam}"
                else -> "2nd Innings — ${inn.battingTeam}"
            }
            MatchDashboard.InningsSummary(
                label = label,
                battingTeam = inn.battingTeam,
                runs = inn.totalRuns,
                wickets = inn.wickets,
                overs = "${inn.completedOvers}.${inn.ballsThisOver}",
                oversLimit = if (inn.isSuperOver) 1 else match.totalOvers,
                runRate = if (legalBalls > 0) inn.totalRuns * 6.0 / legalBalls else 0.0,
                extras = ScorecardCalculator.extrasTotal(inn),
                extrasDetail = "wd ${inn.wideRuns}, nb ${inn.noBallRuns}, b ${inn.byeRuns}, lb ${inn.legByeRuns}" +
                    if (inn.penaltyRuns > 0) ", pen ${inn.penaltyRuns}" else "",
                target = inn.target,
                isSuperOver = inn.isSuperOver,
                isLive = !inn.isCompleted && !match.isCompleted,
                batting = batting,
                bowling = bowling
            )
        }

        val stats = PlayerStatsCalculator.computePlayerStats(listOf(match), innings, ballEvents, merges)
        val topBatters = stats.filter { it.batting.innings > 0 && (it.batting.runs > 0 || it.batting.ballsFaced > 0) }
            .sortedWith(compareByDescending<PlayerStatsCalculator.PlayerCareerStats> { it.batting.runs }
                .thenBy { it.batting.ballsFaced })
            .take(3)
            .map {
                MatchDashboard.Performer(
                    name = it.playerName,
                    team = it.team,
                    figure = "${it.batting.runs}${if (it.batting.notOuts > 0 && it.batting.innings == it.batting.notOuts) "*" else ""}",
                    detail = "${it.batting.ballsFaced} balls • ${it.batting.fours}x4 • ${it.batting.sixes}x6"
                )
            }
        val topBowlers = stats.filter { it.bowling.ballsBowled > 0 }
            .sortedWith(compareByDescending<PlayerStatsCalculator.PlayerCareerStats> { it.bowling.wickets }
                .thenBy { it.bowling.runsConceded })
            .take(3)
            .map {
                MatchDashboard.Performer(
                    name = it.playerName,
                    team = it.team,
                    figure = "${it.bowling.wickets}/${it.bowling.runsConceded}",
                    detail = "${it.bowling.overs} ov • econ ${"%.1f".format(it.bowling.economy ?: 0.0)}"
                )
            }

        val tossLine = "${match.tossWinnerTeam} won the toss and chose to " +
            if (match.tossDecision.name == "BAT") "bat" else "bowl"

        val result = match.resultSummary ?: run {
            val live = sorted.lastOrNull { !it.isCompleted }
            when {
                match.isCompleted -> "Match completed"
                live?.target != null -> {
                    val need = (live.target - live.totalRuns).coerceAtLeast(0)
                    val totalBalls = (if (live.isSuperOver) 1 else match.totalOvers) * 6
                    val left = (totalBalls - (live.completedOvers * 6 + live.ballsThisOver)).coerceAtLeast(0)
                    "In progress — ${live.battingTeam} need $need run(s) from $left ball(s)"
                }
                live != null -> "In progress — ${live.battingTeam} batting"
                else -> "In progress"
            }
        }

        return MatchDashboard(
            title = "${match.teamAName} vs ${match.teamBName}",
            subtitle = "$dateText • ${match.totalOvers} overs • ${match.playersPerTeam} per side",
            tossLine = tossLine,
            result = result,
            isCompleted = match.isCompleted,
            innings = summaries,
            playerOfTheMatch = if (match.isCompleted) {
                PlayerStatsCalculator.computePlayerOfTheMatch(match, innings, ballEvents, merges)
            } else null,
            topBatters = topBatters,
            topBowlers = topBowlers
        )
    }

    private fun isPlaceholder(name: String) =
        Regex("^(Batsman|Bowler) \\d+$").matches(name.trim())
}
