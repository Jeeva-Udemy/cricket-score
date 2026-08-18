package com.example.cricketscorer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cricketscorer.data.BallEventEntity
import com.example.cricketscorer.data.CricketRepository
import com.example.cricketscorer.data.InningsEntity
import com.example.cricketscorer.data.MatchEntity
import com.example.cricketscorer.model.ExtraType
import com.example.cricketscorer.model.WicketType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Breakdown of a single over for displaying over-by-over scores.
 */
data class OverSummary(
    val overNumber: Int, // 1-indexed (e.g. 1 for Over 1)
    val runsInOver: Int,
    val wicketsInOver: Int,
    val cumulativeRuns: Int,
    val cumulativeWickets: Int,
    val balls: List<BallEventEntity>
)

/**
 * Immutable snapshot for the Scoring screen UI.
 */
data class ScoringUiState(
    val match: MatchEntity? = null,
    val allInnings: List<InningsEntity> = emptyList(),
    val allBallEvents: Map<Long, List<BallEventEntity>> = emptyMap(),
    val selectedTabIndex: Int = 0, // 0 = 1st Innings, 1 = 2nd Innings
    val isLoading: Boolean = true,
    val matchCompleteMessage: String? = null
) {
    /** The innings currently selected by the tab bar. */
    val currentInnings: InningsEntity?
        get() {
            if (allInnings.isEmpty()) return null
            return allInnings.firstOrNull { it.inningsNumber == selectedTabIndex + 1 }
                ?: allInnings.firstOrNull()
        }

    /** The live innings that is currently being scored. */
    val liveInnings: InningsEntity?
        get() {
            if (allInnings.isEmpty()) return null
            val activeNumber = match?.currentInningsNumber ?: 1
            return allInnings.firstOrNull { it.inningsNumber == activeNumber }
                ?: allInnings.lastOrNull()
        }

    val isCurrentInningsLive: Boolean
        get() = currentInnings?.inningsId == liveInnings?.inningsId && match?.isCompleted == false

    val selectedInningsBallEvents: List<BallEventEntity>
        get() {
            val innId = currentInnings?.inningsId ?: return emptyList()
            return allBallEvents[innId] ?: emptyList()
        }

    val currentOverBalls: List<BallEventEntity>
        get() {
            val inn = currentInnings ?: return emptyList()
            return selectedInningsBallEvents.filter { it.overNumber == inn.completedOvers }
        }

    val overSummaries: List<OverSummary>
        get() {
            val balls = selectedInningsBallEvents
            if (balls.isEmpty()) return emptyList()

            val grouped = balls.groupBy { it.overNumber }.toSortedMap()
            val summaries = mutableListOf<OverSummary>()
            var runAccumulator = 0
            var wicketAccumulator = 0

            for ((overNum, overBalls) in grouped) {
                val runs = overBalls.sumOf { it.runsScored + it.extraRuns }
                val wickets = overBalls.count { it.isWicket }
                runAccumulator += runs
                wicketAccumulator += wickets

                summaries.add(
                    OverSummary(
                        overNumber = overNum + 1,
                        runsInOver = runs,
                        wicketsInOver = wickets,
                        cumulativeRuns = runAccumulator,
                        cumulativeWickets = wicketAccumulator,
                        balls = overBalls
                    )
                )
            }
            return summaries
        }

    val oversDisplay: String
        get() {
            val inn = currentInnings ?: return "0.0"
            return "${inn.completedOvers}.${inn.ballsThisOver}"
        }

    val runRate: Double
        get() {
            val inn = currentInnings ?: return 0.0
            val ballsBowled = inn.completedOvers * 6 + inn.ballsThisOver
            if (ballsBowled == 0) return 0.0
            return inn.totalRuns.toDouble() / (ballsBowled / 6.0)
        }

    val target: Int? get() = currentInnings?.target

    val runsNeeded: Int?
        get() {
            val t = target ?: return null
            val inn = currentInnings ?: return null
            return (t - inn.totalRuns).coerceAtLeast(0)
        }

    val ballsRemaining: Int?
        get() {
            val m = match ?: return null
            val inn = currentInnings ?: return null
            val totalBalls = m.totalOvers * 6
            val bowled = inn.completedOvers * 6 + inn.ballsThisOver
            return (totalBalls - bowled).coerceAtLeast(0)
        }
}

class ScoringViewModel(private val repository: CricketRepository) : ViewModel() {

    private var matchId: Long = -1
    private var observeJob: Job? = null

    private val _uiState = MutableStateFlow(ScoringUiState())
    val uiState: StateFlow<ScoringUiState> = _uiState.asStateFlow()

    fun loadMatch(matchId: Long, initialInningsId: Long) {
        this.matchId = matchId
        observeMatchData()
    }

    private fun observeMatchData() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observeMatch(matchId).collect { match ->
                if (match == null) return@collect

                launch {
                    repository.observeInningsForMatch(matchId).collect { inningsList ->
                        val currentMap = _uiState.value.allBallEvents.toMutableMap()
                        for (inn in inningsList) {
                            launch {
                                repository.observeBallEvents(inn.inningsId).collect { events ->
                                    val map = _uiState.value.allBallEvents.toMutableMap()
                                    map[inn.inningsId] = events
                                    _uiState.value = _uiState.value.copy(allBallEvents = map)
                                }
                            }
                        }

                        val activeTab = if (_uiState.value.match == null && match.currentInningsNumber == 2) 1 else _uiState.value.selectedTabIndex
                        val resultMsg = match.resultSummary ?: _uiState.value.matchCompleteMessage

                        _uiState.value = _uiState.value.copy(
                            match = match,
                            allInnings = inningsList,
                            selectedTabIndex = activeTab,
                            isLoading = false,
                            matchCompleteMessage = resultMsg
                        )
                    }
                }
            }
        }
    }

    fun selectInningsTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(selectedTabIndex = tabIndex)
    }

    fun updateBatsmanNames(strikerName: String, nonStrikerName: String) {
        viewModelScope.launch {
            val liveInn = uiState.value.liveInnings ?: return@launch
            val updated = liveInn.copy(
                strikerName = strikerName.ifBlank { liveInn.strikerName },
                nonStrikerName = nonStrikerName.ifBlank { liveInn.nonStrikerName }
            )
            repository.updateInnings(updated)
        }
    }

    fun recordRuns(runs: Int) {
        applyDelivery(runs = runs, extraType = ExtraType.NONE, extraRuns = 0, wicketType = WicketType.NONE, isWicket = false)
    }

    fun recordExtra(extraType: ExtraType, additionalRuns: Int) {
        applyDelivery(runs = additionalRuns, extraType = extraType, extraRuns = 1, wicketType = WicketType.NONE, isWicket = false)
    }

    fun recordPenalty(penaltyRuns: Int) {
        applyDelivery(runs = 0, extraType = ExtraType.PENALTY, extraRuns = penaltyRuns, wicketType = WicketType.NONE, isWicket = false)
    }

    fun recordWicket(wicketType: WicketType, runsCompleted: Int = 0, newBatsmanName: String = "") {
        applyDelivery(
            runs = runsCompleted,
            extraType = ExtraType.NONE,
            extraRuns = 0,
            wicketType = wicketType,
            isWicket = true,
            incomingBatsmanName = newBatsmanName
        )
    }

    fun undoLastBall() {
        viewModelScope.launch {
            val liveInn = uiState.value.liveInnings ?: return@launch
            val inningsId = liveInn.inningsId
            val lastBall = repository.undoLastBall(inningsId) ?: return@launch

            val isLegalBall = lastBall.extraType != ExtraType.WIDE &&
                    lastBall.extraType != ExtraType.NO_BALL &&
                    lastBall.extraType != ExtraType.PENALTY
            val totalRunsFromBall = lastBall.runsScored + lastBall.extraRuns

            var newBallsThisOver = liveInn.ballsThisOver
            var newCompletedOvers = liveInn.completedOvers
            if (isLegalBall) {
                if (newBallsThisOver == 0) {
                    newCompletedOvers = (newCompletedOvers - 1).coerceAtLeast(0)
                    newBallsThisOver = 5
                } else {
                    newBallsThisOver -= 1
                }
            }

            val updated = liveInn.copy(
                totalRuns = (liveInn.totalRuns - totalRunsFromBall).coerceAtLeast(0),
                wickets = if (lastBall.isWicket) (liveInn.wickets - 1).coerceAtLeast(0) else liveInn.wickets,
                completedOvers = newCompletedOvers,
                ballsThisOver = newBallsThisOver,
                wideRuns = if (lastBall.extraType == ExtraType.WIDE) (liveInn.wideRuns - totalRunsFromBall).coerceAtLeast(0) else liveInn.wideRuns,
                noBallRuns = if (lastBall.extraType == ExtraType.NO_BALL) (liveInn.noBallRuns - totalRunsFromBall).coerceAtLeast(0) else liveInn.noBallRuns,
                byeRuns = if (lastBall.extraType == ExtraType.BYE) (liveInn.byeRuns - totalRunsFromBall).coerceAtLeast(0) else liveInn.byeRuns,
                legByeRuns = if (lastBall.extraType == ExtraType.LEG_BYE) (liveInn.legByeRuns - totalRunsFromBall).coerceAtLeast(0) else liveInn.legByeRuns,
                penaltyRuns = if (lastBall.extraType == ExtraType.PENALTY) (liveInn.penaltyRuns - totalRunsFromBall).coerceAtLeast(0) else liveInn.penaltyRuns
            )
            repository.updateInnings(updated)
        }
    }

    private fun applyDelivery(
        runs: Int,
        extraType: ExtraType,
        extraRuns: Int,
        wicketType: WicketType,
        isWicket: Boolean,
        incomingBatsmanName: String = ""
    ) {
        viewModelScope.launch {
            val match = repository.getMatch(matchId) ?: return@launch
            val innings = uiState.value.liveInnings ?: repository.getInningsForMatch(matchId).lastOrNull() ?: return@launch
            if (innings.isCompleted || match.isCompleted) return@launch

            val inningsId = innings.inningsId
            val isLegalBall = extraType != ExtraType.WIDE && extraType != ExtraType.NO_BALL && extraType != ExtraType.PENALTY
            val totalRunsThisBall = runs + extraRuns

            // 1. Audit log
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

            // 2. Counters
            var newBallsThisOver = innings.ballsThisOver
            var newCompletedOvers = innings.completedOvers
            if (isLegalBall) newBallsThisOver += 1

            // 3. Batsman & Wickets
            var strikerNum = innings.strikerBatsmanNumber
            var nonStrikerNum = innings.nonStrikerBatsmanNumber
            var strikerName = innings.strikerName
            var nonStrikerName = innings.nonStrikerName
            var nextBatsmanNum = innings.nextBatsmanNumber
            val newWickets = innings.wickets + if (isWicket) 1 else 0

            if (isWicket) {
                strikerNum = nextBatsmanNum
                strikerName = incomingBatsmanName.ifBlank { "Batsman $nextBatsmanNum" }
                nextBatsmanNum += 1
            } else if (runs % 2 == 1 && extraType != ExtraType.PENALTY) {
                // Strike rotation on odd runs
                val tempNum = strikerNum
                strikerNum = nonStrikerNum
                nonStrikerNum = tempNum

                val tempName = strikerName
                strikerName = nonStrikerName
                nonStrikerName = tempName
            }

            // 4. Extras breakdown
            var wideRuns = innings.wideRuns
            var noBallRuns = innings.noBallRuns
            var byeRuns = innings.byeRuns
            var legByeRuns = innings.legByeRuns
            var penaltyRuns = innings.penaltyRuns

            when (extraType) {
                ExtraType.WIDE -> wideRuns += totalRunsThisBall
                ExtraType.NO_BALL -> noBallRuns += totalRunsThisBall
                ExtraType.BYE -> byeRuns += totalRunsThisBall
                ExtraType.LEG_BYE -> legByeRuns += totalRunsThisBall
                ExtraType.PENALTY -> penaltyRuns += totalRunsThisBall
                ExtraType.NONE -> Unit
            }

            // 5. Over completion
            if (newBallsThisOver >= 6) {
                newCompletedOvers += 1
                newBallsThisOver = 0
                val tempNum = strikerNum
                strikerNum = nonStrikerNum
                nonStrikerNum = tempNum

                val tempName = strikerName
                strikerName = nonStrikerName
                nonStrikerName = tempName
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
                penaltyRuns = penaltyRuns,
                strikerBatsmanNumber = strikerNum,
                nonStrikerBatsmanNumber = nonStrikerNum,
                strikerName = strikerName,
                nonStrikerName = nonStrikerName,
                nextBatsmanNumber = nextBatsmanNum
            )

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
                val secondInnings = InningsEntity(
                    matchId = matchId,
                    inningsNumber = 2,
                    battingTeam = innings.bowlingTeam,
                    bowlingTeam = innings.battingTeam,
                    target = updatedInnings.totalRuns + 1
                )
                repository.createInnings(secondInnings)
                repository.updateMatch(match.copy(currentInningsNumber = 2))
                _uiState.value = _uiState.value.copy(selectedTabIndex = 1)
            } else {
                val allInn = repository.getInningsForMatch(matchId)
                val firstInn = allInn.first { it.inningsNumber == 1 }
                val resultText = buildResultText(firstInn, updatedInnings)
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
