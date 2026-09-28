package com.example.cricketscorer.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cricketscorer.backup.ShareUtils
import com.example.cricketscorer.data.CricketRepository
import com.example.cricketscorer.stats.MatchDashboard
import com.example.cricketscorer.stats.MatchDashboardBuilder
import com.example.cricketscorer.ui.dashboard.DashboardImageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Match Dashboard: builds the one-image summary of a whole match (both innings' scorecards,
 * result, Player of the Match, top performers) that can be shared on WhatsApp.
 */
class MatchDashboardViewModel(
    private val repository: CricketRepository,
    private val appContext: Context
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val dashboard: MatchDashboard? = null,
        val image: Bitmap? = null,
        val error: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var loadedMatchId: Long? = null

    fun load(matchId: Long, force: Boolean = false) {
        if (!force && loadedMatchId == matchId) return
        loadedMatchId = matchId
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching {
                val snapshot = repository.getSnapshotForMatch(matchId)
                val match = snapshot.matches.firstOrNull() ?: error("Match not found")
                val merges = repository.getPlayerMerges()
                val date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(match.createdAt))
                val dashboard = MatchDashboardBuilder.build(match, snapshot.innings, snapshot.ballEvents, merges, date)
                val image = withContext(Dispatchers.Default) { DashboardImageRenderer(dashboard).render() }
                dashboard to image
            }.onSuccess { (dashboard, image) ->
                _state.value = UiState(isLoading = false, dashboard = dashboard, image = image)
            }.onFailure {
                _state.value = UiState(isLoading = false, error = it.message ?: "Couldn't build the dashboard.")
            }
        }
    }

    /** Saves the current image to the share folder and returns its content:// Uri. */
    suspend fun imageUri(): Uri? {
        val image = _state.value.image ?: return null
        val title = _state.value.dashboard?.title ?: "match"
        val safe = title.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').take(40).ifBlank { "match" }
        return withContext(Dispatchers.IO) {
            ShareUtils.saveBitmap(appContext, image, "Wickt_${safe}_${ShareUtils.timestamp()}.png")
        }
    }

    /** Short caption sent along with the image. */
    fun shareMessage(): String {
        val d = _state.value.dashboard ?: return ""
        val scores = d.innings.joinToString("\n") { "${it.battingTeam}: ${it.score} (${it.overs} ov)" }
        val pom = d.playerOfTheMatch?.let { "\nPlayer of the Match: ${it.playerName}" } ?: ""
        return "🏏 ${d.title}\n$scores\n${d.result}$pom"
    }
}
