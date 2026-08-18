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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.cricketscorer.data.BallEventEntity
import com.example.cricketscorer.model.ExtraType
import com.example.cricketscorer.model.WicketType
import com.example.cricketscorer.viewmodel.ScoringViewModel

@Composable
fun ScoringScreen(
    viewModel: ScoringViewModel,
    matchId: Long,
    inningsId: Long
) {
    LaunchedEffect(matchId, inningsId) {
        viewModel.loadMatch(matchId, inningsId)
    }

    val state by viewModel.uiState.collectAsState()
    var showWicketDialog by remember { mutableStateOf(false) }
    var showExtraDialogFor by remember { mutableStateOf<ExtraType?>(null) }

    if (state.isLoading || state.match == null || state.innings == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val match = state.match!!
    val innings = state.innings!!

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- Score header ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("${innings.battingTeam} vs ${innings.bowlingTeam}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${innings.totalRuns}/${innings.wickets}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Text("Overs: ${state.oversDisplay} / ${match.totalOvers}")
                Text("Run Rate: %.2f".format(state.runRate))
                state.target?.let { target ->
                    Text("Target: $target  •  Need ${state.runsNeeded} from ${state.ballsRemaining} balls")
                }
                Text("Extras: W ${innings.wideRuns} | NB ${innings.noBallRuns} | B ${innings.byeRuns} | LB ${innings.legByeRuns}")
            }
        }

        // ---- Batsmen at the crease ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Striker: Batsman ${innings.strikerBatsmanNumber}*", fontWeight = FontWeight.Bold)
                Text("Non-striker: Batsman ${innings.nonStrikerBatsmanNumber}")
            }
        }

        // ---- This-over ball-by-ball ----
        Text("This over:", style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.currentOverBalls) { ball -> BallChip(ball) }
        }

        Divider()

        val resultMessage = state.matchCompleteMessage
        if (resultMessage != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Match Complete", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(resultMessage)
                }
            }
        } else {
            Text("Runs", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(0, 1, 2, 3, 4, 6).forEach { run ->
                    Button(onClick = { viewModel.recordRuns(run) }, modifier = Modifier.weight(1f)) {
                        Text(run.toString())
                    }
                }
            }

            Text("Extras", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { showExtraDialogFor = ExtraType.WIDE }, modifier = Modifier.weight(1f)) {
                    Text("Wide")
                }
                OutlinedButton(onClick = { showExtraDialogFor = ExtraType.NO_BALL }, modifier = Modifier.weight(1f)) {
                    Text("No Ball")
                }
                OutlinedButton(onClick = { showExtraDialogFor = ExtraType.BYE }, modifier = Modifier.weight(1f)) {
                    Text("Bye")
                }
                OutlinedButton(onClick = { showExtraDialogFor = ExtraType.LEG_BYE }, modifier = Modifier.weight(1f)) {
                    Text("Leg Bye")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { showWicketDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Wicket")
                }
                OutlinedButton(onClick = { viewModel.undoLastBall() }, modifier = Modifier.weight(1f)) {
                    Text("Undo")
                }
            }
        }
    }

    showExtraDialogFor?.let { extraType ->
        ExtraRunsDialog(
            extraType = extraType,
            onDismiss = { showExtraDialogFor = null },
            onConfirm = { runs ->
                viewModel.recordExtra(extraType, runs)
                showExtraDialogFor = null
            }
        )
    }

    if (showWicketDialog) {
        WicketDialog(
            onDismiss = { showWicketDialog = false },
            onConfirm = { wicketType, runsCompleted ->
                viewModel.recordWicket(wicketType, runsCompleted)
                showWicketDialog = false
            }
        )
    }
}

@Composable
private fun BallChip(ball: BallEventEntity) {
    val label = when {
        ball.isWicket -> "W"
        ball.extraType == ExtraType.WIDE -> "Wd" + (if (ball.runsScored > 0) "+${ball.runsScored}" else "")
        ball.extraType == ExtraType.NO_BALL -> "Nb" + (if (ball.runsScored > 0) "+${ball.runsScored}" else "")
        ball.extraType == ExtraType.BYE -> "B${ball.runsScored}"
        ball.extraType == ExtraType.LEG_BYE -> "Lb${ball.runsScored}"
        else -> ball.runsScored.toString()
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.padding(2.dp)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
private fun ExtraRunsDialog(
    extraType: ExtraType,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val title = when (extraType) {
        ExtraType.WIDE -> "Wide — additional runs run?"
        ExtraType.NO_BALL -> "No Ball — runs off the bat?"
        ExtraType.BYE -> "Bye — how many runs?"
        ExtraType.LEG_BYE -> "Leg Bye — how many runs?"
        ExtraType.NONE -> "Runs"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 1, 2, 3, 4).forEach { r ->
                    OutlinedButton(onClick = { onConfirm(r) }) { Text(r.toString()) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun WicketDialog(
    onDismiss: () -> Unit,
    onConfirm: (WicketType, Int) -> Unit
) {
    var selectedType by remember { mutableStateOf<WicketType?>(null) }
    var runsCompleted by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How was the batsman out?") },
        text = {
            Column {
                val types = listOf(
                    WicketType.BOWLED, WicketType.CAUGHT, WicketType.LBW,
                    WicketType.RUN_OUT, WicketType.STUMPED, WicketType.HIT_WICKET
                )
                types.forEach { type ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = selectedType == type, onClick = { selectedType = type })
                        Text(type.name.replace("_", " "))
                    }
                }
                if (selectedType == WicketType.RUN_OUT) {
                    Spacer(Modifier.height(8.dp))
                    Text("Runs completed before the run-out:")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 1, 2, 3).forEach { r ->
                            FilterChip(
                                selected = runsCompleted == r,
                                onClick = { runsCompleted = r },
                                label = { Text(r.toString()) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedType?.let { onConfirm(it, runsCompleted) } },
                enabled = selectedType != null
            ) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
