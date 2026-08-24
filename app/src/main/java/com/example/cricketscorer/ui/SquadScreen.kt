package com.example.cricketscorer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cricketscorer.data.PlayerEntity
import com.example.cricketscorer.data.SquadEntity
import com.example.cricketscorer.viewmodel.SquadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquadScreen(
    viewModel: SquadViewModel,
    onNavigateBack: () -> Unit
) {
    val squads by viewModel.squads.collectAsState()
    val playersBySquad by viewModel.playersBySquad.collectAsState()

    var expandedSquadId by remember { mutableStateOf<Long?>(null) }
    var showAddSquadDialog by remember { mutableStateOf(false) }
    var squadPendingDelete by remember { mutableStateOf<SquadEntity?>(null) }
    var squadPendingRename by remember { mutableStateOf<SquadEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Squads") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Save a team's players once, then reuse the same squad every time you play " +
                    "that team again — no need to retype names for each match.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(onClick = { showAddSquadDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("New Squad")
            }

            if (squads.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        "No squads yet. Create one for each of your regular teams.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    items(squads, key = { it.squadId }) { squad ->
                        LaunchedEffect(squad.squadId) {
                            viewModel.ensurePlayersObserved(squad.squadId)
                        }
                        val players = playersBySquad[squad.squadId] ?: emptyList()
                        val isExpanded = expandedSquadId == squad.squadId

                        SquadCard(
                            squad = squad,
                            players = players,
                            isExpanded = isExpanded,
                            onToggleExpand = {
                                expandedSquadId = if (isExpanded) null else squad.squadId
                            },
                            onRenameSquad = { squadPendingRename = squad },
                            onDeleteSquad = { squadPendingDelete = squad },
                            onAddPlayer = { name -> viewModel.addPlayer(squad.squadId, name) },
                            onRenamePlayer = { player, newName -> viewModel.renamePlayer(player, newName) },
                            onDeletePlayer = { player -> viewModel.deletePlayer(player.playerId) }
                        )
                    }
                }
            }
        }
    }

    if (showAddSquadDialog) {
        TextInputDialog(
            title = "New Squad",
            label = "Team Name",
            initialValue = "",
            confirmLabel = "Create",
            onDismiss = { showAddSquadDialog = false },
            onConfirm = { name ->
                viewModel.createSquad(name)
                showAddSquadDialog = false
            }
        )
    }

    squadPendingRename?.let { squad ->
        TextInputDialog(
            title = "Rename Squad",
            label = "Team Name",
            initialValue = squad.teamName,
            confirmLabel = "Save",
            onDismiss = { squadPendingRename = null },
            onConfirm = { name ->
                viewModel.renameSquad(squad, name)
                squadPendingRename = null
            }
        )
    }

    squadPendingDelete?.let { squad ->
        AlertDialog(
            onDismissRequest = { squadPendingDelete = null },
            title = { Text("Delete Squad") },
            text = { Text("Delete \"${squad.teamName}\" and all its saved players? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSquad(squad.squadId)
                        squadPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { squadPendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SquadCard(
    squad: SquadEntity,
    players: List<PlayerEntity>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRenameSquad: () -> Unit,
    onDeleteSquad: () -> Unit,
    onAddPlayer: (String) -> Unit,
    onRenamePlayer: (PlayerEntity, String) -> Unit,
    onDeletePlayer: (PlayerEntity) -> Unit
) {
    var newPlayerName by remember { mutableStateOf("") }
    var playerPendingRename by remember { mutableStateOf<PlayerEntity?>(null) }
    var playerPendingDelete by remember { mutableStateOf<PlayerEntity?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(
                        squad.teamName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "(${players.size} players)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRenameSquad) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename Squad")
                }
                IconButton(onClick = onDeleteSquad) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Squad", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                    )
                }
            }

            if (isExpanded) {
                Divider()
                if (players.isEmpty()) {
                    Text(
                        "No players added yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    players.forEach { player ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(player.name, modifier = Modifier.weight(1f))
                            IconButton(onClick = { playerPendingRename = player }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Player")
                            }
                            IconButton(onClick = { playerPendingDelete = player }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Player", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newPlayerName,
                        onValueChange = { newPlayerName = it },
                        label = { Text("Add Player") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        if (newPlayerName.isNotBlank()) {
                            onAddPlayer(newPlayerName)
                            newPlayerName = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Player")
                    }
                }
            }
        }
    }

    playerPendingRename?.let { player ->
        TextInputDialog(
            title = "Rename Player",
            label = "Player Name",
            initialValue = player.name,
            confirmLabel = "Save",
            onDismiss = { playerPendingRename = null },
            onConfirm = { name ->
                onRenamePlayer(player, name)
                playerPendingRename = null
            }
        )
    }

    playerPendingDelete?.let { player ->
        AlertDialog(
            onDismissRequest = { playerPendingDelete = null },
            title = { Text("Delete Player") },
            text = { Text("Remove \"${player.name}\" from this squad?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePlayer(player)
                        playerPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { playerPendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank()) onConfirm(value) }, enabled = value.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
