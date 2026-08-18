package com.example.cricketscorer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cricketscorer.data.BallEventEntity
import com.example.cricketscorer.data.CricketRepository
import com.example.cricketscorer.data.InningsEntity
import com.example.cricketscorer.data.MatchEntity
import com.example.cricketscorer.model.ExtraType
import com.example.cricketscorer.model.WicketType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Immutable snapshot the Scoring screen renders. Derived fields (overs string,
 * run rate, target chase) are computed here so the Composable stays "dumb".
 */
data class ScoringUiState(
    val match: MatchEntity? = null,
    val innings: InningsEntity? = null,
    val ballEvents: List<BallEventEntity> = emptyList(),
    val isLoading: Boolean = true,
    val matchCompleteMessage: String? = null
) {
    val currentOverBalls: List<BallEventEntity>
        get() {
            val inn = innings ?: return emptyList()
            return ballEvents.filter { it.overNumber == inn.completedOvers }
        }

    val oversDisplay: String
        get() {
            val inn = innings ?: return "0.0"
            return "${inn.completedOvers}.${inn.ballsThisOver}"
        }

    val runRate: Double
        get() {
            val inn = innings ?: return 0.0
            val ballsBowled = inn.completedOvers * 6 + inn.ballsThisOver
            if (ballsBowled == 0) return 0.0
            return inn.totalRuns.toDouble() / (ballsBowled / 6.0)
        }

    val target: Int? get() = innings?.target

    val runsNeeded: Int?
        get() {
            val t = target ?: return null
            val inn = innings ?: return null
            return (t - inn.totalRuns).coerceAtLeast(0)
        }

    val ballsRemaining: Int?
        get() {
            val m = match ?: return null
            val inn = innings ?: return null
            val totalBalls = m.totalOvers * 6
            val bowled = inn.completedOvers * 6 + inn.ballsThisOver
            return (totalBalls - bowled).coerceAtLeast(0)
        }
}

class ScoringViewModel(private val repository: CricketRepository) : ViewModel() {

    private var matchId: Long = -1
    private var inningsId: Long = -1

    private val _uiState = MutableStateFlow(ScoringUiState())
    val uiState: StateFlow<ScoringUiState> = _uiState.asStateFlow()

    /** Call once when the screen is first shown. */
    fun loadMatch(matchId: Long, inningsId: Long) {
        this.matchId = matchId
        observeInnings(inningsId)
    }

    private fun observeInnings(inningsIdToObserve: Long) {
        inningsId = inningsIdToObserve
        viewModelScope.launch {
            combine(
                repository.observeMatch(matchId),
                repository.observeInnings(inningsIdToObserve),
                repository.observeBallEvents(inningsIdToObserve)
            ) { match, innings, balls ->
                ScoringUiState(
                    match = match,
                    innings = innings,
                    ballEvents = balls,
                    isLoading = false,
                    matchCompleteMessage = _uiState.value.matchCompleteMessage
                )
            }.collect { state -> _uiState.value = state }
        }
    }

    // ---------- Public actions used by the UI ----------

    fun recordRuns(runs: Int) {
        applyDelivery(runs = runs, extraType = ExtraType.NONE, extraRuns = 0, wicketType = WicketType.NONE, isWicket = false)
    }

    /** [additionalRuns] = runs actually run by the batsmen in addition to the fixed 1-run penalty. */
    fun recordExtra(extraType: ExtraType, additionalRuns: Int) {
        applyDelivery(runs = additionalRuns, extraType = extraType, extraRuns = 1, wicketType = WicketType.NONE, isWicket = false)
    }

    /** [runsCompleted] matters mainly for run-outs, where the batsmen may have crossed before the throw. */
    fun recordWicket(wicketType: WicketType, runsCompleted: Int = 0) {
        applyDelivery(runs = runsCompleted, extraType = ExtraType.NONE, extraRuns = 0, wicketType = wicketType, isWicket = true)
    }

    fun undoLastBall() {
        viewModelScope.launch {
            val innings = repository.getInnings(inningsId) ?: return@launch
            val lastBall = repository.undoLastBall(inningsId) ?: return@launch

            val wasLegalBall = lastBall.extraType != ExtraType.WIDE && lastBall.extraType != ExtraType.NO_BALL
            val totalRunsFromBall = lastBall.runsScored + lastBall.extraRuns

            var newBallsThisOver = innings.ballsThisOver
            var newCompletedOvers = innings.completedOvers
            if (wasLegalBall) {
                if (newBallsThisOver == 0) {
                    // The removed ball was the over-completing 6th ball of a previous over.
                    newCompletedOvers = (newCompletedOvers - 1).coerceAtLeast(0)
                    newBallsThisOver = 5
                } else {
                    newBallsThisOver -= 1
                }
            }

            val updated = innings.copy(
                totalRuns = (innings.totalRuns - totalRunsFromBall).coerceAtLeast(0),
                wickets = if (lastBall.isWicket) (innings.wickets - 1).coerceAtLeast(0) else innings.wickets,
                completedOvers = newCompletedOvers,
                ballsThisOver = newBallsThisOver,
                wideRuns = if (lastBall.extraType == ExtraType.WIDE) (innings.wideRuns - totalRunsFromBall).coerceAtLeast(0) else innings.wideRuns,
                noBallRuns = if (lastBall.extraType == ExtraType.NO_BALL) (innings.noBallRuns - totalRunsFromBall).coerceAtLeast(0) else innings.noBallRuns,
                byeRuns = if (lastBall.extraType == ExtraType.BYE) (innings.byeRuns - totalRunsFromBall).coerceAtLeast(0) else innings.byeRuns,
                legByeRuns = if (lastBall.extraType == ExtraType.LEG_BYE) (innings.legByeRuns - totalRunsFromBall).coerceAtLeast(0) else innings.legByeRuns
            )
            repository.updateInnings(updated)
        }
    }

    // ---------- Core scoring engine ----------

    private fun applyDelivery(
        runs: Int,
        extraType: ExtraType,
        extraRuns: Int,
        wicketType: WicketType,
        isWicket: Boolean
    ) {
        viewModelScope.launch {
            val match = repository.getMatch(matchId) ?: return@launch
            val innings = repository.getInnings(inningsId) ?: return@launch
            if (innings.isCompleted || match.isCompleted) return@launch

            val isLegalBall = extraType != ExtraType.WIDE && extraType != ExtraType.NO_BALL
            val totalRunsThisBall = runs + extraRuns

            // 1. Persist the raw ball event first (audit trail + undo support).
            val ballEvent = BallEventEntity(
                inningsId = inningsId,
                overNumber = innings.completedOvers,
                ballNumberInOver = if (isLegalBall) innings.ballsThisOver + 1 else innings.ballsThisOver,
                runsScored = runs,
                extraType = extraType,
                extraRuns = extraRuns,
                wicketType = wicketType,
                isWicket = isWicket,
                strikerBatsmanNumber = innings.strikerBatsmanNumber
            )
            repository.addBallEvent(ballEvent)

            // 2. Advance the ball/over counters (legal deliveries only).
            var newBallsThisOver = innings.ballsThisOver
            var newCompletedOvers = innings.completedOvers
            if (isLegalBall) newBallsThisOver += 1

            // 3. Wickets: bring in the next batsman at the striker's end.
            var striker = innings.strikerBatsmanNumber
            var nonStriker = innings.nonStrikerBatsmanNumber
            var nextBatsman = innings.nextBatsmanNumber
            val newWickets = innings.wickets + if (isWicket) 1 else 0

            if (isWicket) {
                striker = nextBatsman
                nextBatsman += 1
            } else if (runs % 2 == 1) {
                // 4. Strike rotates on odd runs actually run by the batsmen.
                val temp = striker
                striker = nonStriker
                nonStriker = temp
            }

            // 5. Track extras breakdown for the scoreboard.
            var wideRuns = innings.wideRuns
            var noBallRuns = innings.noBallRuns
            var byeRuns = innings.byeRuns
            var legByeRuns = innings.legByeRuns
            when (extraType) {
                ExtraType.WIDE -> wideRuns += totalRunsThisBall
                ExtraType.NO_BALL -> noBallRuns += totalRunsThisBall
                ExtraType.BYE -> byeRuns += totalRunsThisBall
                ExtraType.LEG_BYE -> legByeRuns += totalRunsThisBall
                ExtraType.NONE -> Unit
            }

            // 6. End of over: 6 legal balls bowled -> reset counter and rotate strike.
            if (newBallsThisOver >= 6) {
                newCompletedOvers += 1
                newBallsThisOver = 0
                val temp = striker
                striker = nonStriker
                nonStriker = temp
            }

            var updatedInnings = innings.copy(
                totalRuns = innings.totalRuns + totalRunsThisBall,
                wickets = newWickets,
                completedOvers = newCompletedOvers,
                ballsThisOver = newBallsThisOver,
                wideRuns = wideRuns,
                noBallRuns = noBallRuns,
                byeRuns = byeRuns,
                legByeRuns = legByeRuns,
                strikerBatsmanNumber = striker,
                nonStrikerBatsmanNumber = nonStriker,
                nextBatsmanNumber = nextBatsman
            )

            // 7. Check innings-ending conditions: overs used up, all out, or target chased down.
            val oversUp = updatedInnings.completedOvers >= match.totalOvers
            val allOut = updatedInnings.wickets >= 10
            val targetChased = updatedInnings.target?.let { updatedInnings.totalRuns >= it } ?: false
            val inningsOver = oversUp || allOut || targetChased

            if (!inningsOver) {
                repository.updateInnings(updatedInnings)
                return@launch
            }

            updatedInnings = updatedInnings.copy(isCompleted = true)
            repository.updateInnings(updatedInnings)

            if (innings.inningsNumber == 1) {
                // 8a. Start the second innings: teams swap roles, target = first innings + 1.
                val secondInnings = InningsEntity(
                    matchId = matchId,
                    inningsNumber = 2,
                    battingTeam = innings.bowlingTeam,
                    bowlingTeam = innings.battingTeam,
                    target = updatedInnings.totalRuns + 1
                )
                val secondInningsId = repository.createInnings(secondInnings)
                repository.updateMatch(match.copy(currentInningsNumber = 2))
                observeInnings(secondInningsId)
            } else {
                // 8b. Match complete: work out and persist the result.
                val firstInnings = repository.getInningsForMatch(matchId).first { it.inningsNumber == 1 }
                val resultText = buildResultText(firstInnings, updatedInnings)
                repository.updateMatch(match.copy(isCompleted = true, resultSummary = resultText))
                _uiState.value = _uiState.value.copy(matchCompleteMessage = resultText)
            }
        }
    }

    private fun buildResultText(firstInnings: InningsEntity, secondInnings: InningsEntity): String {
        return when {
            secondInnings.totalRuns > firstInnings.totalRuns -> {
                val wicketsInHand = 10 - secondInnings.wickets
                "${secondInnings.battingTeam} won by $wicketsInHand wicket(s)"
            }
            secondInnings.totalRuns < firstInnings.totalRuns -> {
                val margin = firstInnings.totalRuns - secondInnings.totalRuns
                "${firstInnings.battingTeam} won by $margin run(s)"
            }
            else -> "Match tied"
        }
    }
}
