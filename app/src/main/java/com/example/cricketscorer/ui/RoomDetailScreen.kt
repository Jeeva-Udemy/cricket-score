package com.example.cricketscorer.ui

import androidx.compose.foundation.Image
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.Composable
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Room $roomCode") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
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
                    RoomMatchCard(match = match, onClick = { onOpenMatch(match.matchId) })
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

@Composable
private fun RoomMatchCard(match: MatchEntity, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
        }
    }
}
