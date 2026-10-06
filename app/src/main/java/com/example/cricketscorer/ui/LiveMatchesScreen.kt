package com.example.cricketscorer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import com.example.cricketscorer.sync.CloudSync
import com.example.cricketscorer.viewmodel.LiveMatchesViewModel

/** "Live Match" tab: all matches being played right now, visible to every installed phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMatchesScreen(
    viewModel: LiveMatchesViewModel,
    onNavigateBack: () -> Unit,
    onOpenMatch: (code: String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Live Match") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null && state.matches.isEmpty() -> CenteredMessage(
                "Couldn't load live matches",
                (state.error ?: "") + "\n\nCheck your internet connection and try again.",
                action = { Button(onClick = { viewModel.start() }) { Text("Retry") } }
            )
            state.matches.isEmpty() -> CenteredMessage(
                "No live matches right now",
                "Matches scored inside a Room show up here for everyone while they are being played."
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.matches, key = { it.code }) { LiveMatchCard(it, onClick = { onOpenMatch(it.code) }) }
            }
        }
    }
}

@Composable
private fun CenteredMessage(title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.invoke()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveMatchCard(m: CloudSync.LiveMatchSummary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(Color(0xFFD32F2F), CircleShape))
                Spacer(Modifier.size(6.dp))
                Text("LIVE", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text("${m.totalOvers} overs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${m.teamA} vs ${m.teamB}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            m.innings.forEach { i ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (i.isSuperOver) "${i.team} (Super Over)" else i.team)
                    Text("${i.runs}/${i.wickets}  (${i.overs})", fontWeight = FontWeight.SemiBold)
                }
            }

            val live = m.innings.lastOrNull()
            val target = m.target
            if (target != null && live != null) {
                val need = target - live.runs
                if (need > 0) {
                    Text("${live.team} need $need to win", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }
            }

            if (m.striker.isNotBlank()) {
                Text(
                    "Batting: ${m.striker}*, ${m.nonStriker}   •   Bowling: ${m.bowler}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (m.recentBalls.isNotEmpty()) {
                Text("This over: " + m.recentBalls.joinToString("  "), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            }
        }
    }
}
