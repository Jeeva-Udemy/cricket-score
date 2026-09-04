package com.example.cricketscorer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cricketscorer.viewmodel.BackupUiState
import com.example.cricketscorer.viewmodel.HomeViewModel
import com.example.cricketscorer.viewmodel.RoomUiState

private data class HomeAction(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val backgroundColor: Color,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartNewMatch: () -> Unit,
    onOpenMatch: (matchId: Long) -> Unit,
    onManageSquads: () -> Unit,
    onPlayerStats: () -> Unit = {},
    onRankings: () -> Unit = {},
    onTournaments: () -> Unit = {},
    onMatchHistory: () -> Unit = {}
) {
    val backupState by viewModel.backupState.collectAsState()
    var showBackupDialog by remember { mutableStateOf(false) }

    // ---- Cloud Sync: Room (req: play several matches back-to-back in one room instead of
    // re-sharing a code before every single match) ----
    val roomState by viewModel.roomState.collectAsState()
    var showRoomDialog by remember { mutableStateOf(false) }

    // ---- Google Sign-In launcher (req #1: Backup & Resync) ----
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onSignInResult(result.data)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Wickt: The Cricket Scorer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Manage your local and Tournament matches easily",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- Action grid (req #2: restyled to match the reference screenshot) ----
            // req: "Resume Match" removed — Match History already covers getting back into an
            // in-progress match. Backup & Resync and Room are grid tiles here too now, so
            // every home-screen action shares the same look (previously those two were
            // full-width OutlinedButtons below the grid, styled differently from the rest).
            val actions = listOf(
                HomeAction("Start Match", Icons.Default.SportsCricket, Color(0xFFDCEFD9), onStartNewMatch),
                HomeAction("Add Team", Icons.Default.Groups, Color(0xFFDCE8FB), onManageSquads),
                // req #1: Match History now opens its own screen instead of scrolling an
                // inline list on the home page.
                HomeAction("Match History", Icons.Default.History, Color(0xFFF6DCEF), onMatchHistory),
                HomeAction("Player Stats", Icons.Default.Person, Color(0xFFDCF1F5), onPlayerStats),
                HomeAction("Rankings", Icons.Default.Leaderboard, Color(0xFFDCF1F5), onRankings),
                HomeAction("Tournaments", Icons.Default.EmojiEvents, Color(0xFFDCF1F5), onTournaments),
                HomeAction("Backup & Resync", Icons.Default.CloudSync, Color(0xFFFBE9D0), { showBackupDialog = true }),
                HomeAction("Room", Icons.Default.GroupAdd, Color(0xFFDCE8FB), { showRoomDialog = true })
            )

            // req #1: a plain chunked Column/Row grid instead of a height-constrained
            // LazyVerticalGrid. The old fixed "110dp per row" height guess didn't always
            // match the actual card height (which depends on screen width via aspectRatio),
            // so on some devices the grid's real content could run taller than its forced
            // height and clip into — or visually overlap — content right below it. Letting
            // each row size itself from its fixed-height cards means the column height is
            // always exactly right, and the whole screen is scrollable so nothing is ever cut
            // off.
            actions.chunked(2).forEach { rowActions ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowActions.forEach { action ->
                        HomeActionCard(action = action, modifier = Modifier.weight(1f))
                    }
                    // Pad out an odd last row so it doesn't stretch to double width.
                    if (rowActions.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            // ---- Room status (req: the OTHER phone should notice a new match without
            // having to open the Room dialog first) ----
            (roomState as? RoomUiState.InRoom)?.let { room ->
                RoomStatusBanner(
                    room = room,
                    onOpen = { room.currentMatchId?.let(onOpenMatch) },
                    onManage = { showRoomDialog = true }
                )
            }

            // req: the "In Progress Matches" list used to be duplicated here and on the
            // Match History screen. It's now shown only on Match History (which already
            // marks each match's status), so it isn't removed here and repeated there.
        }
    }

    if (showBackupDialog) {
        BackupResyncDialog(
            backupState = backupState,
            onBackupNow = {
                val intent = viewModel.requestBackup()
                if (intent != null) signInLauncher.launch(intent)
            },
            onResyncNow = {
                val intent = viewModel.requestResync()
                if (intent != null) signInLauncher.launch(intent)
            },
            onDismiss = {
                showBackupDialog = false
                viewModel.dismissBackupStatus()
            }
        )
    }

    if (showRoomDialog) {
        RoomDialog(
            roomState = roomState,
            onCreateRoom = { viewModel.createRoom() },
            onJoinRoom = { code -> viewModel.joinRoomByCode(code) },
            onExitRoom = {
                viewModel.exitRoom()
                showRoomDialog = false
            },
            onStartMatch = {
                showRoomDialog = false
                onStartNewMatch()
            },
            onOpenMatch = { matchId ->
                showRoomDialog = false
                onOpenMatch(matchId)
            },
            onDismiss = { showRoomDialog = false },
            onDismissError = { viewModel.dismissRoomError() }
        )
    }
}

/** req: small status card shown on Home whenever this device is in a room, so the OTHER phone
 *  notices a newly-started match (or how many devices are connected) without having to open
 *  the Room dialog first. */
@Composable
private fun RoomStatusBanner(
    room: RoomUiState.InRoom,
    onOpen: () -> Unit,
    onManage: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        onClick = onManage
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Room ${room.roomCode} • ${room.devicesConnected}/2 devices",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    if (room.currentMatchId != null) "Match in progress" else "Waiting to start a match",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            if (room.currentMatchId != null) {
                Button(onClick = onOpen) { Text("Open") }
            }
        }
    }
}

/**
 * req: "we can create a room instead of shared match ... in that room we can play multiple
 * matches one after another" + "an Exit button to exit from the room" + "only 2 device should
 * be able to join the room 1 for each team". Replaces the old one-shot "Join Shared Match"
 * dialog — a room's code is reusable across as many matches as the two devices want to play.
 */
@Composable
private fun RoomDialog(
    roomState: RoomUiState,
    onCreateRoom: () -> Unit,
    onJoinRoom: (String) -> Unit,
    onExitRoom: () -> Unit,
    onStartMatch: () -> Unit,
    onOpenMatch: (Long) -> Unit,
    onDismiss: () -> Unit,
    onDismissError: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var scanError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val working = roomState is RoomUiState.Working

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Room") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (roomState) {
                    is RoomUiState.InRoom -> {
                        Text(
                            "Room Code: ${roomState.roomCode}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${roomState.devicesConnected}/2 devices connected" +
                                if (roomState.devicesConnected < 2) " — share the code with the other phone." else ".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Play as many matches as you like in this room without sharing a " +
                                "new code — start the next one straight from here once this " +
                                "one finishes.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(onClick = onStartMatch, modifier = Modifier.fillMaxWidth()) {
                            Text(if (roomState.currentMatchId != null) "Start Next Match" else "Start Match")
                        }
                        roomState.currentMatchId?.let { matchId ->
                            OutlinedButton(
                                onClick = { onOpenMatch(matchId) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open Current Match")
                            }
                        }
                        Divider()
                        // req: "there should be an Exit button to exit from the room" — e.g.
                        // the device scoring the match has to leave the ground mid-match.
                        TextButton(
                            onClick = onExitRoom,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Exit Room")
                        }
                    }
                    is RoomUiState.Working -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.height(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Working…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    else -> {
                        Text(
                            "Create a room and share its code with the other phone — one per " +
                                "team. You can then play several matches back-to-back without " +
                                "re-sharing a code.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(onClick = onCreateRoom, modifier = Modifier.fillMaxWidth()) {
                            Text("Create Room")
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Divider(modifier = Modifier.weight(1f))
                            Text(
                                "  or  ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Divider(modifier = Modifier.weight(1f))
                        }

                        OutlinedButton(
                            onClick = {
                                scanError = null
                                scanQrCodeForMatch(
                                    context = context,
                                    onResult = { scanned -> code = scanned; onJoinRoom(scanned) },
                                    onFailure = { message -> scanError = message }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.height(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Scan Room Code")
                        }
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.uppercase() },
                            label = { Text("Room Code") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { onJoinRoom(code) },
                            enabled = code.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Join Room")
                        }
                        scanError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                (roomState as? RoomUiState.Error)?.let {
                    Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onDismissError) { Text("Try Again") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text("Close") } }
    )
}

@Composable
private fun HomeActionCard(action: HomeAction, modifier: Modifier = Modifier) {
    // req: flexible across screen sizes. A hard `.height(100.dp)` clipped/overlapped its own
    // label on narrower phones or larger system font sizes, where "Resume Match" etc. needs
    // two lines to fit — the fixed-height Card just cut it off instead of growing. Using a
    // *minimum* height instead lets the card grow when it truly needs to, while `maxLines` +
    // `TextOverflow.Ellipsis` guarantee the label itself never overflows the card's edges no
    // matter how the two combine on a given device.
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp),
        colors = CardDefaults.cardColors(containerColor = action.backgroundColor),
        onClick = action.onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.label,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.height(28.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                action.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BackupResyncDialog(
    backupState: BackupUiState,
    onBackupNow: () -> Unit,
    onResyncNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val inProgress = backupState is BackupUiState.SigningIn ||
        backupState is BackupUiState.BackingUp ||
        backupState is BackupUiState.Resyncing

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup & Resync") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Save your match history and saved teams to your Google Drive, or " +
                        "restore them after reinstalling the app.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (inProgress) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (backupState) {
                                is BackupUiState.SigningIn -> "Signing in to Google…"
                                is BackupUiState.BackingUp -> "Backing up…"
                                is BackupUiState.Resyncing -> "Resyncing from Drive…"
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                (backupState as? BackupUiState.Success)?.let {
                    Text(it.message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                (backupState as? BackupUiState.Error)?.let {
                    Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onBackupNow, enabled = !inProgress, modifier = Modifier.weight(1f)) {
                        Text("Backup Now")
                    }
                    OutlinedButton(onClick = onResyncNow, enabled = !inProgress, modifier = Modifier.weight(1f)) {
                        Text("Resync")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
