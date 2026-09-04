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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cricketscorer.viewmodel.BackupUiState
import com.example.cricketscorer.viewmodel.HomeViewModel

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
    onMatchHistory: () -> Unit = {},
    onOpenRooms: () -> Unit = {}
) {
    val backupState by viewModel.backupState.collectAsState()
    var showBackupDialog by remember { mutableStateOf(false) }

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
            // req: Rooms now have their own dedicated screen (see RoomsScreen) instead of a
            // dialog/banner living on Home — the "Room" tile just navigates there.
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
                HomeAction("Room", Icons.Default.GroupAdd, Color(0xFFDCE8FB), onOpenRooms)
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
