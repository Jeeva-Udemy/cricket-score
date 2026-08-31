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
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.cricketscorer.viewmodel.BackupUiState
import com.example.cricketscorer.viewmodel.HomeViewModel
import com.example.cricketscorer.viewmodel.JoinMatchUiState

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
    val matches by viewModel.matches.collectAsState()
    val backupState by viewModel.backupState.collectAsState()
    var showBackupDialog by remember { mutableStateOf(false) }

    // ---- Cloud Sync: Join Shared Match (req: score the same match from two phones) ----
    val joinMatchState by viewModel.joinMatchState.collectAsState()
    var showJoinDialog by remember { mutableStateOf(false) }
    LaunchedEffect(joinMatchState) {
        val state = joinMatchState
        if (state is JoinMatchUiState.Success) {
            showJoinDialog = false
            viewModel.dismissJoinMatchStatus()
            onOpenMatch(state.matchId)
        }
    }

    val inProgressMatches = matches.filter { !it.isCompleted }

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
            val actions = listOf(
                HomeAction("Start Match", Icons.Default.SportsCricket, Color(0xFFDCEFD9), onStartNewMatch),
                HomeAction("Add Team", Icons.Default.Groups, Color(0xFFDCE8FB), onManageSquads),
                HomeAction("Resume Match", Icons.Default.PlayCircle, Color(0xFFFBE9D0), {
                    val target = inProgressMatches.firstOrNull()
                    if (target != null) onOpenMatch(target.matchId)
                }),
                // req #1: Match History now opens its own screen instead of scrolling an
                // inline list on the home page.
                HomeAction("Match History", Icons.Default.History, Color(0xFFF6DCEF), onMatchHistory),
                HomeAction("Player Stats", Icons.Default.Person, Color(0xFFDCF1F5), onPlayerStats),
                HomeAction("Rankings", Icons.Default.Leaderboard, Color(0xFFDCF1F5), onRankings),
                HomeAction("Tournaments", Icons.Default.EmojiEvents, Color(0xFFDCF1F5), onTournaments)
            )

            // req #1: a plain chunked Column/Row grid instead of a height-constrained
            // LazyVerticalGrid. The old fixed "110dp per row" height guess didn't always
            // match the actual card height (which depends on screen width via aspectRatio),
            // so on some devices the grid's real content could run taller than its forced
            // height and clip into — or visually overlap — the Backup & Resync button right
            // below it. Letting each row size itself from its fixed-height cards means the
            // column height is always exactly right, and the whole screen is scrollable so
            // nothing is ever cut off.
            actions.chunked(2).forEach { rowActions ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowActions.forEach { action ->
                        HomeActionCard(action = action, modifier = Modifier.weight(1f))
                    }
                    // Pad out an odd last row (e.g. Tournaments alone) so it doesn't stretch
                    // to double width.
                    if (rowActions.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            // ---- Backup & Resync (req #1) ----
            OutlinedButton(
                onClick = { showBackupDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.height(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Backup & Resync")
            }

            // ---- Join Shared Match (Cloud Sync: TeamA on Mobile1, TeamB on Mobile2) ----
            OutlinedButton(
                onClick = { showJoinDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.height(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Join Shared Match")
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

    if (showJoinDialog) {
        JoinMatchDialog(
            joinMatchState = joinMatchState,
            onJoin = { code -> viewModel.joinMatchByCode(code) },
            onDismiss = {
                showJoinDialog = false
                viewModel.dismissJoinMatchStatus()
            }
        )
    }
}

@Composable
private fun JoinMatchDialog(
    joinMatchState: JoinMatchUiState,
    onJoin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    val inProgress = joinMatchState is JoinMatchUiState.Joining

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join Shared Match") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Enter the Match Code shown at the top of the Scoring screen on the " +
                        "other phone to see and update the same match live.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Match Code") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    enabled = !inProgress,
                    modifier = Modifier.fillMaxWidth()
                )
                if (inProgress) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Joining…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                (joinMatchState as? JoinMatchUiState.Error)?.let {
                    Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onJoin(code) }, enabled = !inProgress && code.isNotBlank()) {
                Text("Join")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !inProgress) { Text("Cancel") } }
    )
}

@Composable
private fun HomeActionCard(action: HomeAction, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp),
        colors = CardDefaults.cardColors(containerColor = action.backgroundColor),
        onClick = action.onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
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
                fontWeight = FontWeight.SemiBold
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
