package com.example.cricketscorer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
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
import java.util.TimeZone

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
    val allMatches by viewModel.matches.collectAsState()
    val selectedMatchIds by viewModel.selectedMatchIds.collectAsState()
    val isSelectionMode = selectedMatchIds.isNotEmpty()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // req #3: "show the matches based on the date we played. Keep an Icon like we have in Home
    // page with the date if we select that it should show the list of matches played on that
    // date." — a calendar icon opens a date picker; picking a date filters the list down to
    // just that day's matches instead of the full history.
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }
    val matches = if (selectedDateMillis != null) {
        allMatches.filter { localDateKey(it.createdAt) == utcDateKey(selectedDateMillis!!) }
    } else {
        allMatches
    }

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
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = "Filter by date")
                    }
                    if (matches.isNotEmpty()) {
                        TextButton(onClick = { viewModel.selectAll() }) {
                            Text("Select")
                        }
                    }
                }
            )
        }

        // req #3: shows which date is currently filtering the list, with a quick way to
        // clear it and go back to the full history.
        selectedDateMillis?.let { millis ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text(utcDateDisplay(millis)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.padding(0.dp)
                        )
                    }
                )
                TextButton(onClick = { selectedDateMillis = null }) {
                    Text("Clear")
                }
            }
        }

        if (matches.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = if (selectedDateMillis != null) {
                            "No matches played on ${utcDateDisplay(selectedDateMillis!!)}."
                        } else {
                            "No past matches found. Start a new match to see it here!"
                        },
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(16.dp),
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

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDateMillis = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
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

                // req #4: "we should be able to differentiate the normal match and room
                // match" — a match only ever gets a shareCode when it was started from inside
                // a Room (see MatchSetupViewModel.configureForRoom); a plain "Start Match"
                // from Home never sets one, so its presence alone tells the two apart.
                val isRoomMatch = !match.shareCode.isNullOrBlank()
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isRoomMatch) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = if (isRoomMatch) "Room Match" else "Local Match",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isRoomMatch) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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

// req #3: date-filter helpers. match.createdAt is a plain System.currentTimeMillis() value,
// naturally read in the DEVICE's own timezone; DatePickerState.selectedDateMillis, on the
// other hand, is documented to always be UTC midnight of the day the user tapped —
// comparing the two directly (or via the device timezone on both) would shift the selected
// day by however far the device is from UTC. Formatting each with the timezone it actually
// means keeps "the day I tapped" and "the day this match's timestamp falls on, locally"
// lined up correctly everywhere. SimpleDateFormat is not thread-safe, so a fresh instance is
// created per call rather than shared.
private fun localDateKey(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))

private fun utcDateKey(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(millis))

private fun utcDateDisplay(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(millis))
