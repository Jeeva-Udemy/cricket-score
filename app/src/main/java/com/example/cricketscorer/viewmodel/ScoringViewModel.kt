package com.example.cricketscorer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cricketscorer.data.BallEventEntity
import com.example.cricketscorer.data.CricketRepository
import com.example.cricketscorer.data.InningsEntity
import com.example.cricketscorer.data.MatchEntity
import com.example.cricketscorer.model.DismissedEnd
import com.example.cricketscorer.model.ExtraType
import com.example.cricketscorer.model.WicketType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BatsmanStat(
    val name: String,
    val runs: Int,
    val ballsFaced: Int,
    val fours: Int,
    val sixes: Int,
    val strikeRate: Double,
    val dismissalInfo: String
)

data class BowlerStat(
    val name: String,
    val oversBowled: String,
    val maidens: Int,
    val runsConceded: Int,
    val wickets: Int,
    val economy: Double
)

data class OverSummary(
    val overNumber: Int,
    val bowlerName: String,
    val runsInOver: Int,
    val wicketsInOver: Int,
    val cumulativeRuns: Int,
    val cumulativeWickets: Int,
    val balls: List<BallEventEntity>
)

data class ScoringUiState(
    val match: MatchEntity? = null,
    val allInnings: List<InningsEntity> = emptyList(),
    val allBallEvents: Map<Long, List<BallEventEntity>> = emptyMap(),
    val selectedTabIndex: Int = 0, // 0 = 1st Innings, 1 = 2nd Innings
    val selectedSubTab: Int = 0,   // 0 = Live Score, 1 = Scorecard, 2 = Overs
    val isLoading: Boolean = true,
    val matchCompleteMessage: String? = null,
    // track whether we've already auto-switched to 2nd innings tab (so it only happens once)
    val hasAutoSwitchedToSecondInnings: Boolean = false,
    // Players from the saved squads linked to the live innings, for pick lists (req. #3)
    val battingSquadPlayerNames: List<String> = emptyList(),
    val bowlingSquadPlayerNames: List<String> = emptyList(),
    // Set to the innings number (currently always 2) right when that innings is created, so
    // the Scoring screen can pop up a one-time "pick your openers" dialog instead of leaving
    // the placeholder "Batsman 1 / Batsman 2 / Bowler 1" names in place until the user
    // remembers to tap Edit. Cleared once the dialog has been confirmed/dismissed.
    val openingPlayersPromptForInnings: Int? = null
) {
    val currentInnings: InningsEntity?
        get() {
            if (allInnings.isEmpty()) return null
            val targetNum = if (selectedTabIndex == 1) 2 else 1
            return allInnings.firstOrNull { it.inningsNumber == targetNum }
                ?: allInnings.lastOrNull()
        }

    val liveInnings: InningsEntity?
        get() {
            if (allInnings.isEmpty()) return null
            val activeNumber = match?.currentInningsNumber ?: 1
            return allInnings.firstOrNull { it.inningsNumber == activeNumber }
                ?: allInnings.lastOrNull()
        }

    val isCurrentInningsLive: Boolean
        get() {
            val curr = currentInnings ?: return false
            val live = liveInnings ?: return false
            return curr.inningsNumber == live.inningsNumber && match?.isCompleted == false && !curr.isCompleted
        }

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

    val existingBowlers: List<String>
        get() {
            val balls = selectedInningsBallEvents
            val fromBalls = balls.map { it.bowlerName }.filter { it.isNotBlank() }
            val current = currentInnings?.currentBowlerName ?: ""
            val set = mutableSetOf<String>()
            if (current.isNotBlank()) set.add(current)
            set.addAll(fromBalls)
            set.addAll(bowlingSquadPlayerNames)
            return set.toList()
        }

    /** Names of batsmen already dismissed this innings, so they aren't offered again. */
    val outBatsmanNames: Set<String>
        get() = selectedInningsBallEvents
            .filter { it.isWicket }
            .map { it.dismissedPlayerName.ifBlank { it.strikerName } }
            .filter { it.isNotBlank() }
            .toSet()

    /** Squad players not currently batting and not yet out — offered as "incoming batsman" chips. */
    val availableIncomingBatsmen: List<String>
        get() {
            val inn = currentInnings
            val onCrease = setOfNotNull(inn?.strikerName, inn?.nonStrikerName)
            return battingSquadPlayerNames.filter { it !in onCrease && it !in outBatsmanNames }
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
                val bowler = overBalls.firstOrNull()?.bowlerName ?: "Bowler"

                summaries.add(
                    OverSummary(
                        overNumber = overNum + 1,
                        bowlerName = bowler,
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

    val batsmanStats: List<BatsmanStat>
        get() {
            val inn = currentInnings ?: return emptyList()
            val balls = selectedInningsBallEvents
            val names = mutableSetOf<String>()

            if (inn.strikerName.isNotBlank()) names.add(inn.strikerName)
            if (inn.nonStrikerName.isNotBlank()) names.add(inn.nonStrikerName)
            balls.forEach {
                if (it.strikerName.isNotBlank()) names.add(it.strikerName)
                if (it.dismissedPlayerName.isNotBlank()) names.add(it.dismissedPlayerName)
            }

            val list = mutableListOf<BatsmanStat>()
            for (name in names) {
                val batsmanBalls = balls.filter { it.strikerName == name }
                val runs = batsmanBalls.filter {
                    it.extraType == ExtraType.NONE || it.extraType == ExtraType.NO_BALL
                }.sumOf { it.runsScored }

                val ballsFaced = batsmanBalls.count {
                    it.extraType != ExtraType.WIDE && it.extraType != ExtraType.PENALTY
                }
                val fours = batsmanBalls.count { it.runsScored == 4 }
                val sixes = batsmanBalls.count { it.runsScored == 6 }
                val sr = if (ballsFaced > 0) (runs.toDouble() / ballsFaced) * 100.0 else 0.0

                val dismissalEvent = batsmanBalls.firstOrNull { it.isWicket } ?: balls.firstOrNull {
                    it.isWicket && it.dismissedPlayerName == name
                }
                val dismissal = when {
                    dismissalEvent != null -> dismissalEvent.wicketType.name.replace("_", " ")
                    name == inn.strikerName || name == inn.nonStrikerName -> "Not Out"
                    else -> "Out"
                }

                list.add(
                    BatsmanStat(
                        name = name,
                        runs = runs,
                        ballsFaced = ballsFaced,
                        fours = fours,
                        sixes = sixes,
                        strikeRate = sr,
                        dismissalInfo = dismissal
                    )
                )
            }
            return list
        }

    val bowlerStats: List<BowlerStat>
        get() {
            val balls = selectedInningsBallEvents
            if (balls.isEmpty()) return emptyList()

            val grouped = balls.groupBy { it.bowlerName }
            val list = mutableListOf<BowlerStat>()

            for ((bowlerName, bBalls) in grouped) {
                val legalBalls = bBalls.count {
                    it.extraType != ExtraType.WIDE && it.extraType != ExtraType.NO_BALL && it.extraType != ExtraType.PENALTY
                }
                val overs = legalBalls / 6
                val remBalls = legalBalls % 6
                val oversStr = "$overs.$remBalls"

                val runsConceded = bBalls.sumOf { ball ->
                    when (ball.extraType) {
                        ExtraType.NONE, ExtraType.WIDE, ExtraType.NO_BALL -> ball.runsScored + ball.extraRuns
                        ExtraType.BYE, ExtraType.LEG_BYE, ExtraType.PENALTY -> 0
                    }
                }
                val wickets = bBalls.count { it.isWicket }
                val econ = if (legalBalls > 0) runsConceded.toDouble() / (legalBalls / 6.0) else 0.0

                val maidens = bBalls.groupBy { it.overNumber }.count { (_, overB) ->
                    val overLegal = overB.count {
                        it.extraType != ExtraType.WIDE && it.extraType != ExtraType.NO_BALL && it.extraType != ExtraType.PENALTY
                    }
                    val overRuns = overB.sumOf { it.runsScored + it.extraRuns }
                    overLegal >= 6 && overRuns == 0
                }

                list.add(
                    BowlerStat(
                        name = bowlerName,
                        oversBowled = oversStr,
                        maidens = maidens,
                        runsConceded = runsConceded,
                        wickets = wickets,
                        economy = econ
                    )
                )
            }
            return list
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
    // Track which innings IDs we're already observing to avoid duplicate observers
    private val observedInningsIds = mutableSetOf<Long>()
    // Track which squad ID currently backs each role, so we only resubscribe when it changes
    // (e.g. when the batting/bowling squads swap between the 1st and 2nd innings)
    private var battingSquadObserveJob: Job? = null
    private var observedBattingSquadId: Long? = null
    private var bowlingSquadObserveJob: Job? = null
    private var observedBowlingSquadId: Long? = null
    private var _lastObservedInningsNumber: Int? = null

    private val _uiState = MutableStateFlow(ScoringUiState())
    val uiState: StateFlow<ScoringUiState> = _uiState.asStateFlow()

    fun loadMatch(matchId: Long, initialInningsId: Long) {
        this.matchId = matchId
        observedInningsIds.clear()
        observeMatchData()
    }

    private fun observeMatchData() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            // Observe innings list changes
            launch {
                repository.observeInningsForMatch(matchId).collect { inningsList ->
                    // Subscribe to ball events for any innings we haven't observed yet
                    for (inn in inningsList) {
                        if (inn.inningsId !in observedInningsIds) {
                            observedInningsIds.add(inn.inningsId)
                            launch {
                                repository.observeBallEvents(inn.inningsId).collect { events ->
                                    val map = _uiState.value.allBallEvents.toMutableMap()
                                    map[inn.inningsId] = events
                                    _uiState.value = _uiState.value.copy(allBallEvents = map)
                                }
                            }
                        }
                    }

                    // Auto-switch to 2nd innings tab only once when 2nd innings first appears
                    val has2ndInnings = inningsList.any { it.inningsNumber == 2 }
                    val shouldAutoSwitch = has2ndInnings && !_uiState.value.hasAutoSwitchedToSecondInnings
                    val newTabIndex = if (shouldAutoSwitch) 1 else _uiState.value.selectedTabIndex

                    _uiState.value = _uiState.value.copy(
                        allInnings = inningsList,
                        selectedTabIndex = newTabIndex,
                        hasAutoSwitchedToSecondInnings = if (shouldAutoSwitch) true else _uiState.value.hasAutoSwitchedToSecondInnings,
                        isLoading = false
                    )

                    // Subscribe to squad player lists for the live innings' batting/bowling squads.
                    // Always re-evaluate by clearing the cached IDs so the swap between innings
                    // (where batting and bowling squads exchange roles) is always picked up.
                    val activeNumber = _uiState.value.match?.currentInningsNumber ?: 1
                    val live = inningsList.firstOrNull { it.inningsNumber == activeNumber } ?: inningsList.lastOrNull()
                    // Force re-subscribe by resetting cached IDs when the live innings changes
                    if (live?.inningsNumber != _lastObservedInningsNumber) {
                        _lastObservedInningsNumber = live?.inningsNumber
                        observedBattingSquadId = null
                        observedBowlingSquadId = null
                    }
                    observeSquadPlayers(live?.battingSquadId, isBattingSquad = true)
                    observeSquadPlayers(live?.bowlingSquadId, isBattingSquad = false)
                }
            }

            // Observe match changes separately
            launch {
                repository.observeMatch(matchId).collect { match ->
                    if (match == null) return@collect
                    // req #4: mirror resultSummary directly (no "sticky" fallback to the
                    // previous message). The old fallback kept showing the "Match Complete"
                    // card — hiding every scoring button — even after undoLastBall() cleared
                    // resultSummary and reopened the match, because it always preferred
                    // whatever matchCompleteMessage used to be over the new null. That forced
                    // the user back to the home screen and into the match again just to get
                    // the run buttons back. Tying it straight to resultSummary means the
                    // scoring UI reappears the instant the match is reopened.
                    _uiState.value = _uiState.value.copy(
                        match = match,
                        matchCompleteMessage = match.resultSummary,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun observeSquadPlayers(squadId: Long?, isBattingSquad: Boolean) {
        val currentlyObserved = if (isBattingSquad) observedBattingSquadId else observedBowlingSquadId
        if (squadId == currentlyObserved) return

        if (isBattingSquad) {
            battingSquadObserveJob?.cancel()
            observedBattingSquadId = squadId
        } else {
            bowlingSquadObserveJob?.cancel()
            observedBowlingSquadId = squadId
        }

        if (squadId == null) {
            _uiState.value = if (isBattingSquad) {
                _uiState.value.copy(battingSquadPlayerNames = emptyList())
            } else {
                _uiState.value.copy(bowlingSquadPlayerNames = emptyList())
            }
            return
        }

        val job = viewModelScope.launch {
            repository.observePlayersForSquad(squadId).collect { players ->
                val names = players.map { it.name }
                _uiState.value = if (isBattingSquad) {
                    _uiState.value.copy(battingSquadPlayerNames = names)
                } else {
                    _uiState.value.copy(bowlingSquadPlayerNames = names)
                }
            }
        }
        if (isBattingSquad) battingSquadObserveJob = job else bowlingSquadObserveJob = job
    }

    fun selectInningsTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(selectedTabIndex = tabIndex)
        // When the user manually switches tabs, refresh the squad player lists
        // for whichever innings is now being viewed (batting/bowling squads swap between innings)
        val innings = _uiState.value.allInnings.firstOrNull {
            it.inningsNumber == tabIndex + 1
        } ?: return
        observeSquadPlayers(innings.battingSquadId, isBattingSquad = true)
        observeSquadPlayers(innings.bowlingSquadId, isBattingSquad = false)
    }

    fun selectSubTab(subTabIndex: Int) {
        _uiState.value = _uiState.value.copy(selectedSubTab = subTabIndex)
    }

    fun updateBatsmanNames(strikerName: String, nonStrikerName: String) {
        viewModelScope.launch {
            val liveInn = fetchLiveInnings() ?: return@launch
            val updated = liveInn.copy(
                strikerName = strikerName.ifBlank { liveInn.strikerName },
                nonStrikerName = nonStrikerName.ifBlank { liveInn.nonStrikerName }
            )
            repository.updateInnings(updated)
        }
    }

    fun updateBowlerName(bowlerName: String) {
        viewModelScope.launch {
            val liveInn = fetchLiveInnings() ?: return@launch
            val updated = liveInn.copy(
                currentBowlerName = bowlerName.ifBlank { liveInn.currentBowlerName }
            )
            repository.updateInnings(updated)
        }
    }

    /**
     * Confirms the "new innings" opener picker (req #2): sets striker, non-striker, and
     * opening bowler for the innings that was just started, all in one write, and clears
     * the one-time prompt flag.
     */
    fun confirmOpeningPlayers(strikerName: String, nonStrikerName: String, bowlerName: String) {
        viewModelScope.launch {
            val liveInn = fetchLiveInnings() ?: return@launch
            val updated = liveInn.copy(
                strikerName = strikerName.ifBlank { liveInn.strikerName },
                nonStrikerName = nonStrikerName.ifBlank { liveInn.nonStrikerName },
                currentBowlerName = bowlerName.ifBlank { liveInn.currentBowlerName }
            )
            repository.updateInnings(updated)
            _uiState.value = _uiState.value.copy(openingPlayersPromptForInnings = null)
        }
    }

    /** Dismisses the opener-picker dialog without changing anything (keeps the placeholders). */
    fun dismissOpeningPlayersPrompt() {
        _uiState.value = _uiState.value.copy(openingPlayersPromptForInnings = null)
    }

    /**
     * Manually overrides the target for the live (2nd) innings. Useful when the first
     * innings' score wasn't entered ball-by-ball in the app, so the auto-computed
     * "1st innings total + 1" target never got set correctly — the team already knows
     * what they need to chase and can enter it directly.
     */
    fun setManualTarget(target: Int) {
        viewModelScope.launch {
            val liveInn = fetchLiveInnings() ?: return@launch
            if (liveInn.isCompleted) return@launch
            repository.updateInnings(liveInn.copy(target = target))
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

    fun recordWicket(
        wicketType: WicketType,
        runsCompleted: Int = 0,
        newBatsmanName: String = "",
        dismissedEnd: DismissedEnd = DismissedEnd.STRIKER
    ) {
        applyDelivery(
            runs = runsCompleted,
            extraType = ExtraType.NONE,
            extraRuns = 0,
            wicketType = wicketType,
            isWicket = true,
            incomingBatsmanName = newBatsmanName,
            dismissedEnd = dismissedEnd
        )
    }

    /**
     * Manually ends the current (live) innings right now, regardless of overs/wickets —
     * for local games where the full XI is never on the field so "all out" may never
     * naturally trigger, or the side simply wants to declare / stop early.
     *
     * [manualRuns] / [manualWickets], when provided, override the app-tracked score with
     * the actual final score before completing — for games where the balls weren't all
     * entered live in the app. Whatever score is saved here is what decides the match
     * result (and, for the 1st innings, what the 2nd innings' target is computed from).
     */
    fun completeInningsManually(manualRuns: Int? = null, manualWickets: Int? = null) {
        viewModelScope.launch {
            val match = repository.getMatch(matchId) ?: return@launch
            val allInningsInDb = repository.getInningsForMatch(matchId)
            val innings = allInningsInDb.firstOrNull { it.inningsNumber == match.currentInningsNumber } ?: return@launch
            if (innings.isCompleted || match.isCompleted) return@launch

            val completedInnings = innings.copy(
                totalRuns = manualRuns ?: innings.totalRuns,
                wickets = manualWickets ?: innings.wickets,
                isCompleted = true
            )
            repository.updateInnings(completedInnings)
            finishInnings(match, completedInnings)
        }
    }

    /**
     * Undo restores the innings from the pre-ball snapshot stored on the last
     * [BallEventEntity] rather than reversing individual deltas. This guarantees the
     * striker/non-striker (names AND batsman numbers) and "next batsman" counter all
     * go back to exactly what they were before that ball — including on a wicket,
     * so a run-out recorded against the wrong end (or any other mis-tap) is fixed by
     * a single Undo with no manual "Edit Batsmen" cleanup required afterwards.
     */
    fun undoLastBall() {
        viewModelScope.launch {
            val match = repository.getMatch(matchId) ?: return@launch
            val liveInn = fetchLiveInnings() ?: return@launch
            val inningsId = liveInn.inningsId
            val lastBall = repository.undoLastBall(inningsId) ?: return@launch

            if (match.isCompleted) {
                repository.updateMatch(match.copy(isCompleted = false, resultSummary = null))
            }

            // Re-fetch innings after the possible match reopen so we have the latest state,
            // then restore every mutable field straight from the ball's pre-ball snapshot.
            val freshInn = fetchLiveInnings() ?: return@launch
            val restored = freshInn.copy(
                totalRuns = lastBall.preTotalRuns,
                wickets = lastBall.preWickets,
                completedOvers = lastBall.preCompletedOvers,
                ballsThisOver = lastBall.preBallsThisOver,
                wideRuns = lastBall.preWideRuns,
                noBallRuns = lastBall.preNoBallRuns,
                byeRuns = lastBall.preByeRuns,
                legByeRuns = lastBall.preLegByeRuns,
                penaltyRuns = lastBall.prePenaltyRuns,
                strikerBatsmanNumber = lastBall.preStrikerBatsmanNumber,
                nonStrikerBatsmanNumber = lastBall.preNonStrikerBatsmanNumber,
                strikerName = lastBall.preStrikerName,
                nonStrikerName = lastBall.preNonStrikerName,
                nextBatsmanNumber = lastBall.preNextBatsmanNumber,
                isCompleted = lastBall.preIsCompleted
            )
            repository.updateInnings(restored)
        }
    }

    /** Always fetches the live innings directly from DB to avoid stale state */
    private suspend fun fetchLiveInnings(): InningsEntity? {
        val match = repository.getMatch(matchId) ?: return null
        val allInn = repository.getInningsForMatch(matchId)
        return allInn.firstOrNull { it.inningsNumber == match.currentInningsNumber }
            ?: allInn.lastOrNull()
    }

    private fun applyDelivery(
        runs: Int,
        extraType: ExtraType,
        extraRuns: Int,
        wicketType: WicketType,
        isWicket: Boolean,
        incomingBatsmanName: String = "",
        dismissedEnd: DismissedEnd = DismissedEnd.STRIKER
    ) {
        viewModelScope.launch {
            // Always read fresh from DB — never rely on stale UI state
            val match = repository.getMatch(matchId) ?: return@launch
            val allInningsInDb = repository.getInningsForMatch(matchId)
            val innings = allInningsInDb.firstOrNull { it.inningsNumber == match.currentInningsNumber } ?: return@launch
            if (innings.isCompleted || match.isCompleted) return@launch

            val inningsId = innings.inningsId
            val isLegalBall = extraType != ExtraType.WIDE && extraType != ExtraType.NO_BALL && extraType != ExtraType.PENALTY
            val totalRunsThisBall = runs + extraRuns
            val dismissedName = if (!isWicket) "" else when (dismissedEnd) {
                DismissedEnd.STRIKER -> innings.strikerName
                DismissedEnd.NON_STRIKER -> innings.nonStrikerName
            }

            // 1. Audit log — includes a full pre-ball snapshot so Undo can restore the
            // innings exactly as it was, batsmen included (see BallEventEntity).
            val ballEvent = BallEventEntity(
                inningsId = inningsId,
                overNumber = innings.completedOvers,
                ballNumberInOver = if (isLegalBall) innings.ballsThisOver + 1 else innings.ballsThisOver,
                runsScored = runs,
                extraType = extraType,
                extraRuns = extraRuns,
                wicketType = wicketType,
                isWicket = isWicket,
                strikerBatsmanNumber = innings.strikerBatsmanNumber,
                strikerName = innings.strikerName,
                dismissedPlayerName = dismissedName,
                preTotalRuns = innings.totalRuns,
                preWickets = innings.wickets,
                preCompletedOvers = innings.completedOvers,
                preBallsThisOver = innings.ballsThisOver,
                preWideRuns = innings.wideRuns,
                preNoBallRuns = innings.noBallRuns,
                preByeRuns = innings.byeRuns,
                preLegByeRuns = innings.legByeRuns,
                prePenaltyRuns = innings.penaltyRuns,
                preStrikerBatsmanNumber = innings.strikerBatsmanNumber,
                preNonStrikerBatsmanNumber = innings.nonStrikerBatsmanNumber,
                preStrikerName = innings.strikerName,
                preNonStrikerName = innings.nonStrikerName,
                preNextBatsmanNumber = innings.nextBatsmanNumber,
                preIsCompleted = innings.isCompleted,
                bowlerName = innings.currentBowlerName
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
                // Which end the incoming batsman replaces — usually the striker, but on a
                // run-out it can be the non-striker instead (swap icon in the Wicket dialog).
                when (dismissedEnd) {
                    DismissedEnd.STRIKER -> {
                        strikerNum = nextBatsmanNum
                        strikerName = incomingBatsmanName.ifBlank { "Batsman $nextBatsmanNum" }
                        nextBatsmanNum += 1
                    }
                    DismissedEnd.NON_STRIKER -> {
                        nonStrikerNum = nextBatsmanNum
                        nonStrikerName = incomingBatsmanName.ifBlank { "Batsman $nextBatsmanNum" }
                        nextBatsmanNum += 1
                    }
                }
            } else if (runs % 2 == 1 && extraType != ExtraType.PENALTY) {
                val tempNum = strikerNum; strikerNum = nonStrikerNum; nonStrikerNum = tempNum
                val tempName = strikerName; strikerName = nonStrikerName; nonStrikerName = tempName
            }

            // 4. Extras
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
                val tempNum = strikerNum; strikerNum = nonStrikerNum; nonStrikerNum = tempNum
                val tempName = strikerName; strikerName = nonStrikerName; nonStrikerName = tempName
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
            // "All out" is based on the match's actual player count, not a hardcoded 11 —
            // local games rarely field a full side. A manual "Complete Innings" button is
            // also available in case a side has even fewer players available on the day.
            val allOut = updatedInnings.wickets >= (match.playersPerTeam - 1)
            val targetChased = updatedInnings.target?.let { updatedInnings.totalRuns >= it } ?: false
            val inningsOver = oversUp || allOut || targetChased

            if (!inningsOver) {
                // Save and let the Flow observers update UI automatically
                repository.updateInnings(updatedInnings)
                return@launch
            }

            // Innings complete
            updatedInnings = updatedInnings.copy(isCompleted = true)
            repository.updateInnings(updatedInnings)
            finishInnings(match, updatedInnings)
        }
    }

    /**
     * Shared "wrap up this innings" logic used both when an innings ends naturally
     * (overs up / all out / target chased) and when the user manually completes it.
     */
    private suspend fun finishInnings(match: MatchEntity, completedInnings: InningsEntity) {
        if (completedInnings.inningsNumber == 1) {
            // Create 2nd innings and let the observer pick it up
            val secondInnings = InningsEntity(
                matchId = matchId,
                inningsNumber = 2,
                battingTeam = completedInnings.bowlingTeam,
                bowlingTeam = completedInnings.battingTeam,
                battingSquadId = completedInnings.bowlingSquadId,
                bowlingSquadId = completedInnings.battingSquadId,
                target = completedInnings.totalRuns + 1
            )
            // createInnings returns the real Room-assigned ID
            val secondInningsId = repository.createInnings(secondInnings)

            // Fetch back the entity with the proper ID from DB
            val secondInningsFromDb = repository.getInningsForMatch(matchId)
                .firstOrNull { it.inningsNumber == 2 } ?: secondInnings.copy(inningsId = secondInningsId)

            val updatedMatch = match.copy(currentInningsNumber = 2)
            repository.updateMatch(updatedMatch)

            // Update state with properly-ID'd 2nd innings
            val allInnings = listOf(completedInnings, secondInningsFromDb)
            _uiState.value = _uiState.value.copy(
                match = updatedMatch,
                allInnings = allInnings,
                selectedTabIndex = 1,
                hasAutoSwitchedToSecondInnings = true,
                // req #2: prompt for the 2nd innings' opening batsmen + bowler as soon as it
                // starts, instead of leaving the "Batsman 1 / Batsman 2 / Bowler 1" placeholders
                // in place until the user remembers to tap Edit.
                openingPlayersPromptForInnings = secondInningsFromDb.inningsNumber
            )

            // Explicitly resync the batter/bowler squad pickers for the 2nd innings right
            // now, using the squad IDs we already know are correct (battingSquadId/
            // bowlingSquadId are swapped for innings 2). Don't rely on the next emission
            // from observeInningsForMatch's collector to do this: that collector reads
            // _uiState.value.match?.currentInningsNumber to decide which innings is "live",
            // and the DB write to the innings table above can trigger that collector to
            // re-run before repository.updateMatch's own Flow has propagated the new
            // currentInningsNumber into _uiState. When that race is lost, the collector
            // resubscribes the pickers to the 1st innings' (unswapped) squads instead, and
            // since nothing else re-triggers it, the pickers stay wrong for the whole 2nd
            // innings until a ball is bowled. Setting the ids directly here closes that gap.
            _lastObservedInningsNumber = secondInningsFromDb.inningsNumber
            observedBattingSquadId = null
            observedBowlingSquadId = null
            observeSquadPlayers(secondInningsFromDb.battingSquadId, isBattingSquad = true)
            observeSquadPlayers(secondInningsFromDb.bowlingSquadId, isBattingSquad = false)
        } else {
            val allInn = repository.getInningsForMatch(matchId)
            val firstInn = allInn.first { it.inningsNumber == 1 }
            val resultText = buildResultText(firstInn, completedInnings)
            repository.updateMatch(match.copy(isCompleted = true, resultSummary = resultText))
            // Let the match observer update matchCompleteMessage automatically
        }
    }

    private fun buildResultText(firstInnings: InningsEntity, secondInnings: InningsEntity): String {
        val maxWickets = (_uiState.value.match?.playersPerTeam ?: 11) - 1
        val reason = when {
            secondInnings.target != null && secondInnings.totalRuns >= secondInnings.target -> "(Target Chased)"
            secondInnings.wickets >= maxWickets -> "(All Out)"
            else -> "(Overs/Innings Completed)"
        }
        return when {
            secondInnings.totalRuns > firstInnings.totalRuns -> {
                val wicketsInHand = maxWickets - secondInnings.wickets
                "${secondInnings.battingTeam} won by $wicketsInHand wicket(s) $reason"
            }
            secondInnings.totalRuns < firstInnings.totalRuns -> {
                val margin = firstInnings.totalRuns - secondInnings.totalRuns
                "${firstInnings.battingTeam} won by $margin run(s) $reason"
            }
            else -> "Match tied $reason"
        }
    }
}
