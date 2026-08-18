package com.example.cricketscorer.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cricketscorer.data.CricketRepository
import com.example.cricketscorer.data.InningsEntity
import com.example.cricketscorer.data.MatchEntity
import com.example.cricketscorer.model.TossDecision
import kotlinx.coroutines.launch

class MatchSetupViewModel(private val repository: CricketRepository) : ViewModel() {

    var teamAName by mutableStateOf("")
    var teamBName by mutableStateOf("")
    var strikerName by mutableStateOf("Batsman 1")
    var nonStrikerName by mutableStateOf("Batsman 2")
    var openingBowlerName by mutableStateOf("")
    var totalOvers by mutableStateOf("20")
    var tossWinnerTeam by mutableStateOf<String?>(null)
    var tossDecision by mutableStateOf(TossDecision.BAT)
    var errorMessage by mutableStateOf<String?>(null)

    /**
     * Validates the form, persists the Match + its first Innings, then hands the
     * generated ids back via [onCreated] so the caller can navigate to scoring.
     */
    fun startMatch(onCreated: (matchId: Long, inningsId: Long) -> Unit) {
        val overs = totalOvers.toIntOrNull()

        if (teamAName.isBlank() || teamBName.isBlank()) {
            errorMessage = "Both team names are required."
            return
        }
        if (teamAName.trim().equals(teamBName.trim(), ignoreCase = true)) {
            errorMessage = "Team names must be different."
            return
        }
        if (overs == null || overs <= 0) {
            errorMessage = "Enter a valid number of overs."
            return
        }
        val winner = tossWinnerTeam
        if (winner == null) {
            errorMessage = "Select the toss winner."
            return
        }
        errorMessage = null

        val teamA = teamAName.trim()
        val teamB = teamBName.trim()

        viewModelScope.launch {
            val match = MatchEntity(
                teamAName = teamA,
                teamBName = teamB,
                totalOvers = overs,
                tossWinnerTeam = winner,
                tossDecision = tossDecision,
                currentInningsNumber = 1
            )
            val matchId = repository.createMatch(match)

            val battingFirst: String
            val bowlingFirst: String
            if (tossDecision == TossDecision.BAT) {
                battingFirst = winner
                bowlingFirst = if (winner == teamA) teamB else teamA
            } else {
                bowlingFirst = winner
                battingFirst = if (winner == teamA) teamB else teamA
            }

            val firstInnings = InningsEntity(
                matchId = matchId,
                inningsNumber = 1,
                battingTeam = battingFirst,
                bowlingTeam = bowlingFirst,
                strikerName = strikerName.ifBlank { "Batsman 1" },
                nonStrikerName = nonStrikerName.ifBlank { "Batsman 2" },
                currentBowlerName = openingBowlerName.ifBlank { "Bowler 1" }
            )
            val inningsId = repository.createInnings(firstInnings)

            onCreated(matchId, inningsId)
        }
    }
}
