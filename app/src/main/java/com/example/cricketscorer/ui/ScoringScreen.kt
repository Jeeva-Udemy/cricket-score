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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.cricketscorer.data.BallEventEntity
import com.example.cricketscorer.model.ExtraType
import com.example.cricketscorer.model.WicketType
import com.example.cricketscorer.viewmodel.OverSummary
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
    var showPenaltyDialog by remember { mutableStateOf(false) }
    var showEditBatsmenDialog by remember { mutableStateOf(false) }

    if (state.isLoading || state.match == null || state.currentInnings == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val match = state.match!!
    val innings = state.currentInnings!!
    val allInnings = state.allInnings

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- Innings Tabs (1st Innings vs 2nd Innings) ----
        TabRow(selectedTabIndex = state.selectedTabIndex) {
            val inn1 = allInnings.firstOrNull { it.inningsNumber == 1 }
            val inn2 = allInnings.firstOrNull { it.inningsNumber == 2 }

            Tab(
                selected = state.selectedTabIndex == 0,
                onClick = { viewModel.selectInningsTab(0) },
                text = { Text("1st Inn: ${inn1?.battingTeam ?: "Team 1"}") }
            )
            Tab(
                selected = state.selectedTabIndex == 1,
                onClick = { viewModel.selectInningsTab(1) },
                text = { Text("2nd Inn: ${inn2?.battingTeam ?: "Team 2"}") }
            )
        }

        // ---- Score header ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "${innings.battingTeam} vs ${innings.bowlingTeam} (${innings.inningsNumber}st/nd Innings)",
                    style = MaterialTheme.typography.titleMedium
                )
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
                Text("Extras: W ${innings.wideRuns} | NB ${innings.noBallRuns} | B ${innings.byeRuns} | LB ${innings.legByeRuns} | PEN ${innings.penaltyRuns}")
            }
        }

        // ---- Batsmen at the crease ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Striker: ${innings.strikerName}*", fontWeight = FontWeight.Bold)
                        Text("Non-striker: ${innings.nonStrikerName}")
                    }
                    if (state.isCurrentInningsLive) {
                        OutlinedButton(onClick = { showEditBatsmenDialog = true }) {
                            Text("Edit Names")
                        }
                    }
                }
            }
        }

        // ---- This-over ball-by-ball ----
        Text("This Over:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.currentOverBalls) { ball -> BallChip(ball) }
        }

        Divider()

        // ---- Score per over breakdown ----
        if (state.overSummaries.isNotEmpty()) {
            Text("Score Per Over:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.overSummaries.forEach { summary ->
                        OverSummaryRow(summary)
                    }
                }
            }
            Divider()
        }

        // ---- Scoring action controls ----
        val resultMessage = state.matchCompleteMessage
        if (resultMessage != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Match Complete", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(resultMessage)
                }
            }
        } else if (!state.isCurrentInningsLive) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("Viewing completed innings (Switch tabs to score active innings)", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            Text("Runs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(0, 1, 2, 3, 4, 6).forEach { run ->
                    Button(onClick = { viewModel.recordRuns(run) }, modifier = Modifier.weight(1f)) {
                        Text(run.toString())
                    }
                }
            }

            Text("Extras", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { showExtraDialogFor = ExtraType.WIDE }, modifier = Modifier.weight(1f)) {
                    Text("Wide")
                }
                OutlinedButton(onClick = { showExtraDialogFor = ExtraType.NO_BALL }, modifier = Modifier.weight(1f)) {
                    Text("NB")
                }
                OutlinedButton(onClick = { showExtraDialogFor = ExtraType.BYE }, modifier = Modifier.weight(1f)) {
                    Text("Bye")
                }
                OutlinedButton(onClick = { showExtraDialogFor = ExtraType.LEG_BYE }, modifier = Modifier.weight(1f)) {
                    Text("LB")
                }
                OutlinedButton(onClick = { showPenaltyDialog = true }, modifier = Modifier.weight(1f)) {
                    Text("PEN")
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

    // ---- Extra Runs Dialog (Scrollable) ----
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

    // ---- Penalty Runs Dialog ----
    if (showPenaltyDialog) {
        PenaltyRunsDialog(
            onDismiss = { showPenaltyDialog = false },
            onConfirm = { penaltyRuns ->
                viewModel.recordPenalty(penaltyRuns)
                showPenaltyDialog = false
            }
        )
    }

    // ---- Edit Batsmen Names Dialog ----
    if (showEditBatsmenDialog) {
        EditBatsmenDialog(
            currentStriker = innings.strikerName,
            currentNonStriker = innings.nonStrikerName,
            onDismiss = { showEditBatsmenDialog = false },
            onConfirm = { sName, nsName ->
                viewModel.updateBatsmanNames(sName, nsName)
                showEditBatsmenDialog = false
            }
        )
    }

    // ---- Wicket Dialog (Prompt for new batsman) ----
    if (showWicketDialog) {
        WicketDialog(
            nextBatsmanDefault = "Batsman ${innings.nextBatsmanNumber}",
            onDismiss = { showWicketDialog = false },
            onConfirm = { wicketType, runsCompleted, newBatsmanName ->
                viewModel.recordWicket(wicketType, runsCompleted, newBatsmanName)
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
        ball.extraType == ExtraType.NO_BALL -> "NB" + (if (ball.runsScored > 0) "+${ball.runsScored}" else "")
        ball.extraType == ExtraType.BYE -> "B${ball.runsScored}"
        ball.extraType == ExtraType.LEG_BYE -> "LB${ball.runsScored}"
        ball.extraType == ExtraType.PENALTY -> "PEN+${ball.extraRuns}"
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
private fun OverSummaryRow(summary: OverSummary) {
    val ballLabels = summary.balls.joinToString(" ") { ball ->
        when {
            ball.isWicket -> "W"
            ball.extraType == ExtraType.WIDE -> "Wd" + (if (ball.runsScored > 0) "+${ball.runsScored}" else "")
            ball.extraType == ExtraType.NO_BALL -> "NB" + (if (ball.runsScored > 0) "+${ball.runsScored}" else "")
            ball.extraType == ExtraType.BYE -> "B${ball.runsScored}"
            ball.extraType == ExtraType.LEG_BYE -> "LB${ball.runsScored}"
            ball.extraType == ExtraType.PENALTY -> "PEN+${ball.extraRuns}"
            else -> ball.runsScored.toString()
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Over ${summary.overNumber}:", fontWeight = FontWeight.Bold)
        Text("$ballLabels  (${summary.runsInOver} runs)")
        Text("${summary.cumulativeRuns}/${summary.cumulativeWickets}", fontWeight = FontWeight.Bold)
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
        ExtraType.NO_BALL -> "NB (No Ball) — runs off the bat?"
        ExtraType.BYE -> "Bye — how many runs?"
        ExtraType.LEG_BYE -> "LB (Leg Bye) — how many runs?"
        else -> "Extra Runs"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                items(listOf(0, 1, 2, 3, 4, 5, 6, 7)) { r ->
                    OutlinedButton(onClick = { onConfirm(r) }) { Text(r.toString()) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PenaltyRunsDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var customRuns by remember { mutableStateOf("5") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Penalty Runs") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Select or enter penalty runs to add to team score:")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 5, 10).forEach { r ->
                        OutlinedButton(onClick = { onConfirm(r) }) { Text("+$r") }
                    }
                }
                OutlinedTextField(
                    value = customRuns,
                    onValueChange = { if (it.all { c -> c.isDigit() }) customRuns = it },
                    label = { Text("Custom Penalty Runs") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val runs = customRuns.toIntOrNull() ?: 5
                onConfirm(runs)
            }) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EditBatsmenDialog(
    currentStriker: String,
    currentNonStriker: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var strikerName by remember { mutableStateOf(currentStriker) }
    var nonStrikerName by remember { mutableStateOf(currentNonStriker) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Batsmen Names") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = strikerName,
                    onValueChange = { strikerName = it },
                    label = { Text("Striker Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nonStrikerName,
                    onValueChange = { nonStrikerName = it },
                    label = { Text("Non-Striker Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(strikerName, nonStrikerName) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun WicketDialog(
    nextBatsmanDefault: String,
    onDismiss: () -> Unit,
    onConfirm: (WicketType, Int, String) -> Unit
) {
    var selectedType by remember { mutableStateOf<WicketType?>(null) }
    var runsCompleted by remember { mutableStateOf(0) }
    var newBatsmanName by remember { mutableStateOf(nextBatsmanDefault) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How was the batsman out?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = newBatsmanName,
                    onValueChange = { newBatsmanName = it },
                    label = { Text("Incoming Batsman Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedType?.let { onConfirm(it, runsCompleted, newBatsmanName) } },
                enabled = selectedType != null
            ) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
