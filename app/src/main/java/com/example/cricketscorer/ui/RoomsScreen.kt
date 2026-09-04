package com.example.cricketscorer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.cricketscorer.data.RoomStore
import com.example.cricketscorer.viewmodel.RoomUiState
import com.example.cricketscorer.viewmodel.RoomsViewModel

/**
 * Rooms screen (req: "Show the Rooms created inside the Rooms instead of showing it in the
 * Home page") — every room this device has created or joined, most recent first. Tapping one
 * opens [RoomDetailScreen], which is where the room's QR code/invite code and the list of
 * matches played inside it live (req: "keep the QR code along with invite code" for the ROOM,
 * not tied to any one match).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsScreen(
    viewModel: RoomsViewModel,
    onNavigateBack: () -> Unit,
    onOpenRoom: (roomCode: String) -> Unit
) {
    val roomHistory by viewModel.roomHistory.collectAsState()
    val roomState by viewModel.roomState.collectAsState()
    var showJoinDialog by remember { mutableStateOf(false) }

    // Jump straight into a room's detail screen once a CREATE/JOIN call this user just made
    // resolves into InRoom — no reason to make them tap it again from the list they're already
    // looking at. This must NOT fire just because the ViewModel restores an already-active room
    // on construction (see RoomsViewModel.restoreActiveRoomIfAny, which runs before this
    // screen's first composition) — that used to auto-navigate into Room Detail the instant you
    // opened Rooms while already in a room, silently pushing an extra back-stack entry
    // ([home, rooms, roomDetail]) the user never chose to visit. Pressing the back arrow then
    // only returned to the Rooms list they don't remember seeing, not Home — which is exactly
    // the "back button doesn't get me to Home" symptom this flag fixes: auto-navigation only
    // ever fires right after this screen itself calls createRoom()/joinRoomByCode() below.
    var awaitingRoomResult by remember { mutableStateOf(false) }
    LaunchedEffect(roomState) {
        val inRoom = roomState as? RoomUiState.InRoom ?: return@LaunchedEffect
        if (awaitingRoomResult) {
            awaitingRoomResult = false
            showJoinDialog = false
            onOpenRoom(inRoom.roomCode)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rooms") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                viewModel.dismissRoomError()
                showJoinDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Create or Join a Room")
            }
        }
    ) { padding ->
        if (roomHistory.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.GroupAdd,
                    contentDescription = null,
                    modifier = Modifier.width(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "No rooms yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Create a room and share its code (or QR) with another device — you can " +
                        "then play several matches back-to-back without re-sharing a code.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            LazyColumn(
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(roomHistory, key = { it.roomCode }) { room ->
                    RoomListCard(
                        room = room,
                        isActive = viewModel.isActiveRoom(room.roomCode),
                        onClick = { onOpenRoom(room.roomCode) }
                    )
                }
            }
        }
    }

    if (showJoinDialog) {
        CreateOrJoinRoomDialog(
            roomState = roomState,
            onCreateRoom = {
                awaitingRoomResult = true
                viewModel.createRoom()
            },
            onJoinRoom = { code ->
                awaitingRoomResult = true
                viewModel.joinRoomByCode(code)
            },
            onDismiss = { showJoinDialog = false },
            onDismissError = { viewModel.dismissRoomError() }
        )
    }
}

@Composable
private fun RoomListCard(
    room: RoomStore.SavedRoom,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    if (room.myTeam != null && room.otherTeam != null) {
                        "${room.myTeam} vs ${room.otherTeam}"
                    } else {
                        "Room ${room.roomCode}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Code: ${room.roomCode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isActive) {
                Text(
                    "Active",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * req: "we can create a room instead of shared match ... in that room we can play multiple
 * matches one after another" + "only 2 device should be able to join the room 1 for each
 * team". This dialog only handles the one-off create/join step; once in a room, its ongoing
 * QR/code/Start Match/Exit Room controls live on [RoomDetailScreen] instead.
 */
@Composable
private fun CreateOrJoinRoomDialog(
    roomState: RoomUiState,
    onCreateRoom: () -> Unit,
    onJoinRoom: (String) -> Unit,
    onDismiss: () -> Unit,
    onDismissError: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var scanError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val working = roomState is RoomUiState.Working

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create or Join a Room") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (working) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Working…", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
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
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Scan Room Code")
                    }
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase() },
                        label = { Text("Room Code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
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
