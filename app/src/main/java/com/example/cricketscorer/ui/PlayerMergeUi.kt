package com.example.cricketscorer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.example.cricketscorer.data.PlayerMergeEntity
import com.example.cricketscorer.stats.PlayerStatsCalculator
import com.example.cricketscorer.viewmodel.PlayerStatsViewModel

/**
 * Shared chrome for Player Stats and Rankings, including "Merge players":
 *  - tap the merge icon (or long-press any player) to enter merge mode
 *  - tick two or more entries that are really the same person (e.g. "Jeeva" / "Jeevaa")
 *  - tap Merge, pick the correct name, confirm — stats combine immediately
 *  - the history icon lists merges, each of which can be undone
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMergeScaffold(
    title: String,
    viewModel: PlayerStatsViewModel,
    onNavigateBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val mergeMode by viewModel.mergeMode.collectAsState()
    val selectedKeys by viewModel.selectedKeys.collectAsState()
    val merges by viewModel.merges.collectAsState()
    val players by viewModel.players.collectAsState()
    val message by viewModel.message.collectAsState()
    var showMergeDialog by remember { mutableStateOf(false) }
    var showMergedList by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (mergeMode) {
                TopAppBar(
                    title = { Text(if (selectedKeys.isEmpty()) "Select players to merge" else "${selectedKeys.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.setMergeMode(false) }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel merge")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { showMergeDialog = true },
                            enabled = selectedKeys.size >= 2
                        ) { Text("Merge") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (merges.isNotEmpty()) {
                            IconButton(onClick = { showMergedList = true }) {
                                Icon(Icons.Default.History, contentDescription = "Merged players")
                            }
                        }
                        if (players.size >= 2) {
                            IconButton(onClick = { viewModel.setMergeMode(true) }) {
                                Icon(Icons.Default.MergeType, contentDescription = "Merge players")
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (mergeMode) {
                MergeHintCard(
                    suggestions = remember(players) { viewModel.suggestions() },
                    onPickSuggestion = { a, b -> viewModel.selectOnly(listOf(a.playerKey, b.playerKey)) }
                )
            }
            content(PaddingValues(0.dp))
        }
    }

    if (showMergeDialog) {
        val selected = viewModel.selectedPlayers()
        if (selected.size >= 2) {
            MergePlayersDialog(
                selected = selected,
                onDismiss = { showMergeDialog = false },
                onConfirm = { target, finalName ->
                    viewModel.mergePlayers(selected, target, finalName)
                    showMergeDialog = false
                }
            )
        } else {
            LaunchedEffect(Unit) { showMergeDialog = false }
        }
    }

    if (showMergedList) {
        MergedPlayersDialog(
            merges = merges,
            onUndo = { viewModel.undoMerges(listOf(it.mergeId)) },
            onDismiss = { showMergedList = false }
        )
    }
}

@Composable
private fun MergeHintCard(
    suggestions: List<com.example.cricketscorer.stats.DuplicateNameSuggester.Suggestion>,
    onPickSuggestion: (PlayerStatsCalculator.PlayerCareerStats, PlayerStatsCalculator.PlayerCareerStats) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Tick every entry that is the same person (e.g. a misspelt name), then tap Merge.",
                style = MaterialTheme.typography.bodySmall
            )
            if (suggestions.isNotEmpty()) {
                Text("Possible duplicates:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(suggestions) { s ->
                        AssistChip(
                            onClick = { onPickSuggestion(s.first, s.second) },
                            label = { Text("${s.first.playerName} / ${s.second.playerName}") }
                        )
                    }
                }
            }
        }
    }
}

/** Card wrapper used by both lists so rows become selectable in merge mode (tap) and a
 *  long-press anywhere starts merge mode with that row selected. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectablePlayerCard(
    player: PlayerStatsCalculator.PlayerCareerStats,
    mergeMode: Boolean,
    isSelected: Boolean,
    onToggle: (String) -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (mergeMode) onToggle(player.playerKey) },
                onLongClick = { onToggle(player.playerKey) }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (mergeMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle(player.playerKey) },
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) { content() }
        }
    }
}

/** Name + team line, showing any other spellings merged into this player. */
@Composable
fun PlayerNameBlock(player: PlayerStatsCalculator.PlayerCareerStats) {
    Column {
        Text(player.playerName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (player.teams.isNotEmpty()) {
            Text(
                player.teams.joinToString(", "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (player.mergedNames.isNotEmpty()) {
            Text(
                "Also: " + player.mergedNames.sorted().joinToString(", "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun MergePlayersDialog(
    selected: List<PlayerStatsCalculator.PlayerCareerStats>,
    onDismiss: () -> Unit,
    onConfirm: (target: PlayerStatsCalculator.PlayerCareerStats, finalName: String) -> Unit
) {
    // Default target: the entry with the most activity (usually the correctly-spelt one).
    val initial = remember(selected) {
        selected.maxByOrNull { it.batting.innings + it.bowling.inningsBowled + it.matches } ?: selected.first()
    }
    var target by remember { mutableStateOf(initial) }
    var finalName by remember { mutableStateOf(initial.playerName) }
    val teamsDiffer = selected.map { it.team.lowercase() }.toSet().size > 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge ${selected.size} players") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())
            ) {
                Text("Keep which entry? Its team is kept.", style = MaterialTheme.typography.bodySmall)
                selected.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = target.playerKey == p.playerKey, onClick = {
                            target = p
                            finalName = p.playerName
                        })
                        Column {
                            Text(p.playerName, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${p.team.ifBlank { "No team" }} • ${p.matches} match(es) • " +
                                    "${p.batting.runs} runs • ${p.bowling.wickets} wkts",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = finalName,
                    onValueChange = { finalName = capitalizeFirstLetter(it) },
                    label = { Text("Correct name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (teamsDiffer) {
                    Text(
                        "These entries are from different teams. Only merge them if it is really the same person.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    "Scorecards are not changed — only stats are combined. You can undo this from the history icon.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(target, finalName) }, enabled = finalName.isNotBlank()) { Text("Merge") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun MergedPlayersDialog(
    merges: List<PlayerMergeEntity>,
    onUndo: (PlayerMergeEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merged players") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())
            ) {
                if (merges.isEmpty()) Text("No merges yet.")
                merges.sortedByDescending { it.createdAt }.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${m.fromName} → ${m.toName}", fontWeight = FontWeight.SemiBold)
                            val teams = if (m.fromTeam.equals(m.toTeam, ignoreCase = true)) m.toTeam
                            else "${m.fromTeam} → ${m.toTeam}"
                            if (teams.isNotBlank()) {
                                Text(teams, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TextButton(onClick = { onUndo(m) }) { Text("Undo") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
