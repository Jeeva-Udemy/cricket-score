package com.example.cricketscorer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cricketscorer.data.MatchEntity
import com.example.cricketscorer.stats.PlayerStatsCalculator
import com.example.cricketscorer.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * req #1: Match History used to be an inline list at the bottom of the Home screen. It now
 * lives on its own screen, reached by tapping the "Match History" card on Home — the Home
 * screen itself only shows "In Progress Matches" now.
 *
 * Reuses [HomeViewModel] since all the matches/selection/delete plumbing already lived there;
 * nothing about that state needed to change, only where it's displayed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchHistoryScreen(
    viewModel: HomeViewModel,
    onNavigateBack: () -> Unit,
    onOpenMatch: (matchId: Long) -> Unit
) {
    val matches by viewModel.matches.collectAsState()
    val selectedMatchIds by viewModel.selectedMatchIds.collectAsState()
    val isSelectionMode = selectedMatchIds.isNotEmpty()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isSelectionMode) {
            TopAppBar(
                title = { Text("${selectedMatchIds.size} Selected") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.clearSelection() }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear Selection")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.selectAll() }) {
                        Icon(imageVector = Icons.Default.SelectAll, contentDescription = "Select All")
                    }
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Selected",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        } else {
            TopAppBar(
                title = { Text("Match History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (matches.isNotEmpty()) {
                        TextButton(onClick = { viewModel.selectAll() }) {
                            Text("Select")
                        }
                    }
                }
            )
        }

        if (matches.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = "No past matches found. Start a new match to see it here!",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                modifier = Modifier.fillMaxWidth().fillMaxSize()
            ) {
                items(matches, key = { it.matchId }) { match ->
                    val isSelected = selectedMatchIds.contains(match.matchId)
                    MatchHistoryCard(
                        match = match,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onFetchPlayerOfTheMatch = { viewModel.playerOfTheMatch(match) },
                        onClick = {
                            if (isSelectionMode) {
                                viewModel.toggleMatchSelection(match.matchId)
                            } else {
                                onOpenMatch(match.matchId)
                            }
                        },
                        onLongClick = {
                            viewModel.toggleMatchSelection(match.matchId)
                        },
                        onCheckedChange = {
                            viewModel.toggleMatchSelection(match.matchId)
                        }
                    )
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Match History") },
            text = {
                Text("Are you sure you want to delete ${selectedMatchIds.size} selected match(es)? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelectedMatches()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MatchHistoryCard(
    match: MatchEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onFetchPlayerOfTheMatch: suspend () -> PlayerStatsCalculator.PlayerAward?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    val dateStr = dateFormat.format(Date(match.createdAt))

    // req: "For each match we need to show who's the Player of the Match" — computed on demand
    // (see HomeViewModel.playerOfTheMatch) rather than stored, so it can never go stale after
    // an Undo. Only worth asking for once the match is actually done.
    var playerOfTheMatch by remember(match.matchId) {
        mutableStateOf<PlayerStatsCalculator.PlayerAward?>(null)
    }
    LaunchedEffect(match.matchId, match.isCompleted) {
        playerOfTheMatch = if (match.isCompleted) onFetchPlayerOfTheMatch() else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 6.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${match.teamAName} vs ${match.teamBName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${match.totalOvers} Overs",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Text(
                    text = "Played on: $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val statusText = match.resultSummary ?: if (match.isCompleted) "Match Completed" else "In Progress"
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (match.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                )

                playerOfTheMatch?.let { award ->
                    Text(
                        text = "Player of the Match: ${award.playerName} (${formatAward(award)})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/** Shared formatting for a Player of the Match / Player of the Series line, e.g.
 *  "45 (32) & 2/15" or just "45 (32)" for a pure batting performance. */
internal fun formatAward(award: PlayerStatsCalculator.PlayerAward): String {
    val battingPart = if (award.ballsFaced > 0) "${award.runs} (${award.ballsFaced})" else null
    val bowlingPart = if (award.ballsBowled > 0) "${award.wickets}/${award.runsConceded}" else null
    return listOfNotNull(battingPart, bowlingPart).joinToString(" & ").ifBlank { "${award.runs} runs" }
}
