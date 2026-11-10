package com.example.cricketscorer.stats

import com.example.cricketscorer.data.InningsEntity
import com.example.cricketscorer.data.MatchEntity

/**
 * "Complete Match" for the active Room match (and its Undo).
 *
 * Completing ends the match right where it is: the innings being played is marked complete,
 * the match is marked complete and gets a result worked out from the scores so far. That makes
 * it count everywhere completed matches count (Dashboard wins/losses, Player of the Match /
 * Series, web History) without having to bowl out the remaining overs.
 *
 * The result text always ends with [TAG]. That tag is what makes the action undoable — it's
 * synced with the match itself, so both phones (and the web page) know this result came from
 * "Complete Match" rather than from the last ball. Undo only ever reopens the innings the match
 * was on ([MatchEntity.currentInningsNumber]): "Complete Match" never touches any other innings,
 * and never adds or removes balls, so reopening that one innings restores the match exactly.
 */
object MatchCompletion {

    const val TAG = "(Match completed early)"

    fun isEarlyCompleted(match: MatchEntity): Boolean =
        match.isCompleted && match.resultSummary?.endsWith(TAG) == true

    fun resultText(match: MatchEntity, innings: List<InningsEntity>): String {
        val sorted = innings.sortedBy { it.inningsNumber }
        val maxWickets = (match.playersPerTeam - 1).coerceAtLeast(1)
        val liveNum = match.currentInningsNumber

        // A Super Over was under way.
        if (liveNum >= 3) {
            if (liveNum % 2 == 1) return "Match tied — Super Over not finished $TAG"
            val first = sorted.firstOrNull { it.inningsNumber == liveNum - 1 }
            val second = sorted.firstOrNull { it.inningsNumber == liveNum }
            if (first != null && second != null) {
                return when {
                    second.totalRuns > first.totalRuns ->
                        "${second.battingTeam} won the Super Over (Match Tied) $TAG"
                    second.totalRuns < first.totalRuns ->
                        "${first.battingTeam} won the Super Over by ${first.totalRuns - second.totalRuns} run(s) (Match Tied) $TAG"
                    else -> "Scores level after the Super Over — still tied $TAG"
                }
            }
        }

        val regular = sorted.filter { !it.isSuperOver }
        val first = regular.getOrNull(0)
        val second = regular.getOrNull(1)
        if (first == null || second == null) {
            return "No result — ended during the 1st innings $TAG"
        }
        return when {
            second.totalRuns > first.totalRuns ->
                "${second.battingTeam} won by ${(maxWickets - second.wickets).coerceAtLeast(0)} wicket(s) $TAG"
            second.totalRuns < first.totalRuns ->
                "${first.battingTeam} won by ${first.totalRuns - second.totalRuns} run(s) $TAG"
            else -> "Match tied $TAG"
        }
    }
}
