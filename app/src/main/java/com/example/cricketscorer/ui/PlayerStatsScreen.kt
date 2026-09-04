package com.example.cricketscorer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cricketscorer.stats.PlayerStatsCalculator
import com.example.cricketscorer.viewmodel.PlayerStatsViewModel
import kotlin.math.roundToInt

/**
 * req: "display the list of players and their details like which team they are playing for
 * ... How much he scored, wickets taken, best bowling figure overall score, wicket etc."
 * A straight directory (alphabetical), unlike Rankings which sorts by who's actually best —
 * see [RankingsScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerStatsScreen(
    viewModel: PlayerStatsViewModel,
    onNavigateBack: () -> Unit
) {
    val players by viewModel.players.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Player Stats") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            players.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No players yet — stats show up here once a match has been scored.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> LazyColumn(
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(players, key = { it.playerName }) { player ->
                    PlayerStatsCard(player)
                }
            }
        }
    }
}

@Composable
internal fun PlayerStatsCard(player: PlayerStatsCalculator.PlayerCareerStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(player.playerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${player.matches} match" + if (player.matches == 1) "" else "es",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (player.teams.isNotEmpty()) {
                Text(
                    player.teams.sorted().joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Divider()
            if (player.batting.innings > 0) {
                val b = player.batting
                val hs = "${b.highScore}${if (b.highScoreNotOut) "*" else ""}"
                Text(
                    "Bat: ${b.runs} runs, ${b.innings} inns (${b.notOuts} not out) • " +
                        "SR ${formatDecimal(b.strikeRate)} • Avg ${b.average?.let { formatDecimal(it) } ?: "-"} • HS $hs",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (player.bowling.wickets > 0 || player.bowling.ballsBowled > 0) {
                val bo = player.bowling
                Text(
                    "Bowl: ${bo.wickets} wkts, ${bo.overs} overs • " +
                        "Econ ${bo.economy?.let { formatDecimal(it) } ?: "-"} • " +
                        "Avg ${bo.average?.let { formatDecimal(it) } ?: "-"} • " +
                        "Best ${bo.bestFigures ?: "-"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/** One decimal place, e.g. 128.57 -> "128.6" — plenty of precision for a local scorecard. */
internal fun formatDecimal(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded == rounded.toLong().toDouble()) "${rounded.toLong()}.0" else rounded.toString()
}
