package com.example.cricketscorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cricketscorer.stats.OverallDashboardCalculator.DashboardData
import com.example.cricketscorer.stats.OverallDashboardCalculator.TeamRecord
import com.example.cricketscorer.stats.OverallDashboardCalculator.TopPlayer
import com.example.cricketscorer.viewmodel.OverallDashboardViewModel

private val WinColor = Color(0xFF2E7D32)
private val LossColor = Color(0xFFC62828)
private val TieColor = Color(0xFF9E9E9E)
private val RunsColor = Color(0xFF1565C0)
private val WicketColor = Color(0xFF6A1B9A)
private val FoursColor = Color(0xFF00897B)
private val SixesColor = Color(0xFFEF6C00)
private val BatColor = Color(0xFF1565C0)
private val BowlColor = Color(0xFF6A1B9A)
private val PointsColor = Color(0xFFF9A825)

/** Home → Dashboard: all-time results, team records and Top-10 players, shown as graphs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverallDashboardScreen(viewModel: OverallDashboardViewModel, onNavigateBack: () -> Unit) {
    val data by viewModel.data.collectAsState()
    val loading by viewModel.loading.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Dashboard") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            data.totalMatches == 0 -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Play a match to see the dashboard.", style = MaterialTheme.typography.bodyMedium)
            }
            else -> DashboardContent(data)
        }
    }
}

@Composable
private fun DashboardContent(data: DashboardData) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("Matches", data.totalMatches.toString(), Modifier.weight(1f))
            StatTile("Completed", data.completedMatches.toString(), Modifier.weight(1f))
            StatTile("Teams", data.teams.size.toString(), Modifier.weight(1f))
        }

        // ---- Wins & losses ----
        SectionCard("Wins & Losses") {
            Legend(listOf("Won" to WinColor, "Lost" to LossColor, "Tied" to TieColor))
            data.teams.filter { it.played > 0 }.forEach { t ->
                Text("${t.team}  (${t.played} played)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                SplitBar(listOf(t.wins to WinColor, t.losses to LossColor, t.ties to TieColor))
                Text("W ${t.wins}  •  L ${t.losses}  •  T ${t.ties}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (data.teams.none { it.played > 0 }) Hint("No completed matches yet.")
        }

        // ---- Head to head ----
        SectionCard("Head to Head") {
            if (data.headToHead.isEmpty()) Hint("No completed matches yet.")
            data.headToHead.forEach { h ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${h.teamA} ${h.winsA}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${h.played} played", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${h.winsB} ${h.teamB}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
                SplitBar(listOf(h.winsA to RunsColor, h.ties to TieColor, h.winsB to SixesColor))
                if (h.ties > 0) Hint("${h.ties} tied")
            }
        }

        // ---- Runs & wickets ----
        SectionCard("Most Runs by Team") {
            val sorted = data.teams.sortedByDescending { it.runsScored }
            sorted.firstOrNull()?.takeIf { it.runsScored > 0 }?.let { Highlight("${it.team} — ${it.runsScored} runs") }
            BarChart(sorted.map { it.team to it.runsScored }, RunsColor)
        }
        SectionCard("Most Wickets by Team") {
            val sorted = data.teams.sortedByDescending { it.wicketsTaken }
            sorted.firstOrNull()?.takeIf { it.wicketsTaken > 0 }?.let { Highlight("${it.team} — ${it.wicketsTaken} wickets") }
            BarChart(sorted.map { it.team to it.wicketsTaken }, WicketColor)
        }

        // ---- 4s and 6s ----
        SectionCard("Fours & Sixes by Team") {
            Legend(listOf("4s" to FoursColor, "6s" to SixesColor))
            data.teams.sortedByDescending { it.fours + it.sixes }.forEach { t ->
                Text(t.team, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                val max = maxOf(data.teams.maxOf { it.fours }, data.teams.maxOf { it.sixes }, 1)
                BarRow("4s", t.fours, max, FoursColor)
                BarRow("6s", t.sixes, max, SixesColor)
            }
        }

        // ---- Per-team records ----
        data.teams.forEach { TeamRecordsCard(it) }

        // ---- Top 10 ----
        SectionCard("Top 10 Overall Performers") {
            Hint("Points: 1 per run, +1 per four, +2 per six, 20 per wicket")
            PlayerBarChart(data.topOverall, PointsColor, "pts")
        }
        SectionCard("Top 10 Run Scorers") { PlayerBarChart(data.topBatters, BatColor, "runs") }
        SectionCard("Top 10 Wicket Takers") { PlayerBarChart(data.topBowlers, BowlColor, "wkts") }
    }
}

@Composable
private fun TeamRecordsCard(t: TeamRecord) {
    SectionCard(t.team) {
        RecordLine("Most 4s", t.mostFours?.let { "${it.name} (${it.value})" })
        RecordLine("Most 6s", t.mostSixes?.let { "${it.name} (${it.value})" })
        RecordLine(
            "Highest score in an innings",
            t.highestInnings?.let { "${it.name} ${it.runs}${if (it.notOut) "*" else ""} vs ${it.against}" }
        )
        RecordLine("Highest overall score", t.highestOverall?.let { "${it.name} (${it.value} runs)" })
    }
}

// ---------------------------------------------------------------- building blocks

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Highlight(text: String) =
    Text("🏆 $text", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)

@Composable
private fun RecordLine(label: String, value: String?) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Legend(items: List<Pair<String, Color>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** One horizontal bar split into coloured segments proportional to the values. */
@Composable
private fun SplitBar(parts: List<Pair<Int, Color>>) {
    val total = parts.sumOf { it.first }
    Row(
        Modifier.fillMaxWidth().height(14.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(7.dp))
    ) {
        if (total > 0) parts.filter { it.first > 0 }.forEach { (v, c) ->
            Box(Modifier.weight(v.toFloat()).fillMaxHeight().background(c))
        }
    }
}

@Composable
private fun BarRow(label: String, value: Int, max: Int, color: Color, labelWidth: Int = 28) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(labelWidth.dp))
        Box(Modifier.weight(1f).height(14.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
            if (value > 0) Box(
                Modifier.fillMaxWidth((value.toFloat() / max).coerceIn(0.02f, 1f)).fillMaxHeight().background(color, RoundedCornerShape(4.dp))
            )
        }
        Text(value.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp).padding(start = 6.dp))
    }
}

/** Simple labelled horizontal bar chart. */
@Composable
private fun BarChart(items: List<Pair<String, Int>>, color: Color) {
    val max = (items.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (label, v) ->
            Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BarRow("", v, max, color, labelWidth = 0)
        }
    }
}

@Composable
private fun PlayerBarChart(players: List<TopPlayer>, color: Color, unit: String) {
    if (players.isEmpty()) { Hint("Not enough data yet."); return }
    val max = players.maxOf { it.value }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        players.forEachIndexed { i, p ->
            Text(
                "${i + 1}. ${p.name}" + if (p.team.isNotBlank()) "  (${p.team})" else "",
                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            BarRow("", p.value, max, color, labelWidth = 0)
        }
        Hint("Values in $unit")
    }
}
