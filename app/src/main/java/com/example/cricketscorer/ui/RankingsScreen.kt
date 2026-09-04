package com.example.cricketscorer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cricketscorer.stats.PlayerStatsCalculator
import com.example.cricketscorer.viewmodel.PlayerStatsViewModel

/**
 * req: "show the list of players by their ranking based on batting and bowling performance
 * separately. In Batters tab show who scored more runs with better strike rate and in Bowlers
 * tab show who took more wickets with better economy and average, just like we do it in
 * international cricket."
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingsScreen(
    viewModel: PlayerStatsViewModel,
    onNavigateBack: () -> Unit
) {
    val players by viewModel.players.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var tabIndex by remember { mutableIntStateOf(0) }

    val batters = remember(players) {
        players.filter { it.batting.innings > 0 }
            .sortedWith(compareByDescending<PlayerStatsCalculator.PlayerCareerStats> { it.batting.runs }
                .thenByDescending { it.batting.strikeRate })
    }
    val bowlers = remember(players) {
        players.filter { it.bowling.wickets > 0 || it.bowling.ballsBowled > 0 }
            .sortedWith(
                compareByDescending<PlayerStatsCalculator.PlayerCareerStats> { it.bowling.wickets }
                    .thenBy { it.bowling.economy ?: Double.MAX_VALUE }
                    .thenBy { it.bowling.average ?: Double.MAX_VALUE }
            )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rankings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Batters") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Bowlers") })
            }

            val list = if (tabIndex == 0) batters else bowlers

            when {
                isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                list.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (tabIndex == 0) "No batting innings recorded yet." else "No bowling recorded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    itemsIndexed(list) { index, player ->
                        if (tabIndex == 0) {
                            BatterRankingRow(rank = index + 1, player = player)
                        } else {
                            BowlerRankingRow(rank = index + 1, player = player)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankBadge(rank: Int) {
    Box(
        modifier = Modifier.width(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BatterRankingRow(rank: Int, player: PlayerStatsCalculator.PlayerCareerStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RankBadge(rank)
            Column(modifier = Modifier.weight(1f)) {
                Text(player.playerName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (player.teams.isNotEmpty()) {
                    Text(
                        player.teams.sorted().joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${player.batting.runs} runs", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    "SR ${formatDecimal(player.batting.strikeRate)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BowlerRankingRow(rank: Int, player: PlayerStatsCalculator.PlayerCareerStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RankBadge(rank)
            Column(modifier = Modifier.weight(1f)) {
                Text(player.playerName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (player.teams.isNotEmpty()) {
                    Text(
                        player.teams.sorted().joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${player.bowling.wickets} wkts", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Econ ${player.bowling.economy?.let { formatDecimal(it) } ?: "-"} • " +
                        "Avg ${player.bowling.average?.let { formatDecimal(it) } ?: "-"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
