package com.example.cricketscorer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.example.cricketscorer.viewmodel.RoomUiState
import com.example.cricketscorer.viewmodel.RoomsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Room Detail screen — the room's own QR code + invite code (req: "I need QR code and invite
 * code for the room itself not for the individual match ... keep the QR code along with invite
 * code"), Start/Start Next Match, Exit Room, and the list of matches played inside this room
 * (req: "show the list of matches inside the each Rooms that we created").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailScreen(
    viewModel: RoomsViewModel,
    roomCode: String,
    onNavigateBack: () -> Unit,
    onStartMatch: () -> Unit,
    onOpenMatch: (matchId: Long) -> Unit
) {
    val roomState by viewModel.roomState.collectAsState()
    val isActive = viewModel.isActiveRoom(roomCode)
    val inRoom = (roomState as? RoomUiState.InRoom)?.takeIf { it.roomCode == roomCode }
    val matches by remember(roomCode) { viewModel.matchesForRoom(roomCode) }
        .collectAsState(initial = emptyList())
    var showExitDialog by remember { mutableStateOf(false) }
    // req #1: "keep a delete button to select multiple matches ... at the same time to delete."
    val selectedMatchIds by viewModel.selectedRoomMatchIds.collectAsState()
    val isSelectionMode = selectedMatchIds.isNotEmpty()
    var showDeleteMatchesDialog by remember { mutableStateOf(false) }

    // req: "If we are playing multiple matches in a single room then we need to show who's the
    // Player of the series" — only meaningful once 2+ matches in this room have finished.
    var playerOfTheSeries by remember(roomCode) {
        mutableStateOf<PlayerStatsCalculator.PlayerAward?>(null)
    }
    LaunchedEffect(matches) {
        playerOfTheSeries = viewModel.playerOfTheSeries(matches)
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedMatchIds.size} Selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearRoomMatchSelection() }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAllRoomMatches(matches.map { it.matchId }) }) {
                            Icon(imageVector = Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                        IconButton(onClick = { showDeleteMatchesDialog = true }) {
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
                    title = { Text("Room $roomCode") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (matches.isNotEmpty()) {
                            TextButton(onClick = { viewModel.selectAllRoomMatches(matches.map { it.matchId }) }) {
                                Text("Select")
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                RoomShareCard(roomCode = roomCode, devicesConnected = inRoom?.devicesConnected)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isActive) {
                        Button(onClick = onStartMatch, modifier = Modifier.fillMaxWidth()) {
                            Text(if (matches.isNotEmpty()) "Start Next Match" else "Start Match")
                        }
                        (roomState as? RoomUiState.Error)?.let {
                            Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(
                            onClick = { showExitDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Exit Room")
                        }
                    } else {
                        Text(
                            "You've exited this room. Rejoin to start another match in it — " +
                                "its past matches are still listed below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { viewModel.joinRoomByCode(roomCode) },
                            enabled = roomState !is RoomUiState.Working,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Rejoin Room")
                        }
                        (roomState as? RoomUiState.Error)?.let {
                            Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            playerOfTheSeries?.let { award ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Player of the Series",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "${award.playerName} — ${formatAward(award)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            item {
                Divider()
                Text(
                    "Matches in this room",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (matches.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            "No matches started in this room yet.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(matches, key = { it.matchId }) { match ->
                    val isSelected = selectedMatchIds.contains(match.matchId)
                    RoomMatchCard(
                        match = match,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onFetchPlayerOfTheMatch = { viewModel.playerOfTheMatch(match) },
                        onClick = {
                            if (isSelectionMode) {
                                viewModel.toggleRoomMatchSelection(match.matchId)
                            } else {
                                onOpenMatch(match.matchId)
                            }
                        },
                        onLongClick = { viewModel.toggleRoomMatchSelection(match.matchId) },
                        onCheckedChange = { viewModel.toggleRoomMatchSelection(match.matchId) }
                    )
                }
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit Room?") },
            text = { Text("You can rejoin later using the same code, as long as a slot is free.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.exitRoom()
                        showExitDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Exit Room")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteMatchesDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteMatchesDialog = false },
            title = { Text("Delete Match(es)") },
            text = {
                Text(
                    "Are you sure you want to delete ${selectedMatchIds.size} selected match(es) " +
                        "from this room? This action cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelectedRoomMatches()
                        showDeleteMatchesDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMatchesDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/** The room's own QR code + invite code (req 1: keep both, for the ROOM rather than any one
 *  match) — scanning this or typing the code joins the room, not any particular match. */
@Composable
private fun RoomShareCard(roomCode: String, devicesConnected: Int?) {
    val qrBitmap = remember(roomCode) { generateQrCodeBitmap(roomCode) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            qrBitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Room QR code",
                    modifier = Modifier.size(180.dp)
                )
            }
            Text(
                roomCode,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (devicesConnected != null) {
                    "$devicesConnected/2 devices connected" +
                        if (devicesConnected < 2) " — share this code or QR with the other phone." else "."
                } else {
                    "Share this code or QR with another device to join this room."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                "Play as many matches as you like in this room without sharing a new code.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoomMatchCard(
    match: MatchEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onFetchPlayerOfTheMatch: suspend () -> PlayerStatsCalculator.PlayerAward?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    // req: "For each match we need to show who's the Player of the Match" — computed on demand
    // (see RoomsViewModel.playerOfTheMatch), never persisted, so an Undo can never make it stale.
    var playerOfTheMatch by remember(match.matchId) {
        mutableStateOf<PlayerStatsCalculator.PlayerAward?>(null)
    }
    LaunchedEffect(match.matchId, match.isCompleted) {
        playerOfTheMatch = if (match.isCompleted) onFetchPlayerOfTheMatch() else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 6.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${match.teamAName} vs ${match.teamBName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${match.totalOvers} Overs",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    "Played on: ${dateFormat.format(Date(match.createdAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val statusText = match.resultSummary ?: if (match.isCompleted) "Match Completed" else "In Progress"
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (match.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                )
                playerOfTheMatch?.let { award ->
                    Text(
                        "Player of the Match: ${award.playerName} (${formatAward(award)})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
