package com.example.cricketscorer.stats

import com.example.cricketscorer.data.BallEventEntity
import com.example.cricketscorer.data.InningsEntity
import com.example.cricketscorer.model.ExtraType
import com.example.cricketscorer.model.WicketType

/**
 * One innings' scorecard (batting + bowling tables), derived from ball events. Shared by the
 * live Scorecard tab and the shareable Match Dashboard so both always agree.
 *
 * Run-out fix: the batsman who is OUT on a ball is [BallEventEntity.dismissedPlayerName]
 * (which may be the non-striker), never automatically the striker. Previously the scorecard
 * looked for "any wicket ball this batsman faced", so when the non-striker was run out the
 * STRIKER was shown as out instead.
 */
object ScorecardCalculator {

    data class BattingLine(
        val name: String,
        val runs: Int,
        val balls: Int,
        val fours: Int,
        val sixes: Int,
        val isOut: Boolean,
        val dismissal: String
    ) {
        val strikeRate: Double get() = if (balls > 0) runs * 100.0 / balls else 0.0
    }

    data class BowlingLine(
        val name: String,
        val legalBalls: Int,
        val maidens: Int,
        val runs: Int,
        val wickets: Int
    ) {
        val overs: String get() = "${legalBalls / 6}.${legalBalls % 6}"
        val economy: Double get() = if (legalBalls > 0) runs * 6.0 / legalBalls else 0.0
    }

    /** Who was out on [ball] — falls back to the striker for balls recorded by older versions. */
    fun dismissedName(ball: BallEventEntity): String =
        ball.dismissedPlayerName.ifBlank { ball.strikerName }

    fun isLegal(ball: BallEventEntity): Boolean =
        ball.extraType != ExtraType.WIDE && ball.extraType != ExtraType.NO_BALL && ball.extraType != ExtraType.PENALTY

    /** Wickets that count for the bowler — run-outs never do. */
    fun isBowlerWicket(ball: BallEventEntity): Boolean =
        ball.isWicket && ball.wicketType != WicketType.NONE && ball.wicketType != WicketType.RUN_OUT

    fun dismissalText(ball: BallEventEntity): String {
        val bowler = ball.bowlerName.trim()
        return when (ball.wicketType) {
            WicketType.BOWLED -> "b $bowler"
            WicketType.CAUGHT -> "ct b $bowler"
            WicketType.LBW -> "lbw b $bowler"
            WicketType.RUN_OUT -> "run out"
            WicketType.STUMPED -> "st b $bowler"
            WicketType.HIT_WICKET -> "hit wicket b $bowler"
            WicketType.NONE -> "out"
        }
    }

    fun battingLines(innings: InningsEntity, balls: List<BallEventEntity>): List<BattingLine> {
        // Batting order = order each name first appears (as striker, or as the batsman out).
        val order = linkedSetOf<String>()
        balls.forEach { b ->
            if (b.strikerName.isNotBlank()) order += b.strikerName
            if (b.isWicket) dismissedName(b).takeIf { it.isNotBlank() }?.let { order += it }
        }
        val atCrease = listOf(innings.strikerName, innings.nonStrikerName).filter { it.isNotBlank() }
        order += atCrease

        return order.map { name ->
            val faced = balls.filter { it.strikerName == name }
            val runs = faced.filter { it.extraType == ExtraType.NONE || it.extraType == ExtraType.NO_BALL }
                .sumOf { it.runsScored }
            val ballsFaced = faced.count { it.extraType != ExtraType.WIDE && it.extraType != ExtraType.PENALTY }
            val fours = faced.count { (it.extraType == ExtraType.NONE || it.extraType == ExtraType.NO_BALL) && it.runsScored == 4 }
            val sixes = faced.count { (it.extraType == ExtraType.NONE || it.extraType == ExtraType.NO_BALL) && it.runsScored == 6 }
            val outBall = balls.firstOrNull { it.isWicket && dismissedName(it) == name }
            BattingLine(
                name = name,
                runs = runs,
                balls = ballsFaced,
                fours = fours,
                sixes = sixes,
                isOut = outBall != null,
                dismissal = outBall?.let { dismissalText(it) } ?: "not out"
            )
        }
    }

    fun bowlingLines(balls: List<BallEventEntity>): List<BowlingLine> =
        balls.filter { it.bowlerName.isNotBlank() }.groupBy { it.bowlerName }.map { (name, bBalls) ->
            val runs = bBalls.sumOf { ball ->
                when (ball.extraType) {
                    ExtraType.NONE, ExtraType.WIDE, ExtraType.NO_BALL -> ball.runsScored + ball.extraRuns
                    ExtraType.BYE, ExtraType.LEG_BYE, ExtraType.PENALTY -> 0
                }
            }
            val maidens = bBalls.groupBy { it.overNumber }.count { (_, overBalls) ->
                overBalls.count { isLegal(it) } >= 6 && overBalls.sumOf { it.runsScored + it.extraRuns } == 0
            }
            BowlingLine(
                name = name,
                legalBalls = bBalls.count { isLegal(it) },
                maidens = maidens,
                runs = runs,
                wickets = bBalls.count { isBowlerWicket(it) }
            )
        }

    fun extrasTotal(innings: InningsEntity): Int =
        innings.wideRuns + innings.noBallRuns + innings.byeRuns + innings.legByeRuns + innings.penaltyRuns
}
