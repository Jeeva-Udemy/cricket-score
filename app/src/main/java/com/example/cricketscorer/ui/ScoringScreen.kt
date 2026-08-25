package com.example.cricketscorer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cricketscorer.data.BallEventEntity
import com.example.cricketscorer.model.DismissedEnd
import com.example.cricketscorer.model.ExtraType
import com.example.cricketscorer.model.WicketType
import com.example.cricketscorer.viewmodel.ScoringViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoringScreen(
    viewModel: ScoringViewModel,
    matchId: Long,
    inningsId: Long,
    onNavigateBack: () -> Unit
) {
    LaunchedEffect(matchId, inningsId) {
        viewModel.loadMatch(matchId, inningsId)
    }

    val state by viewModel.uiState.collectAsState()
    var showWicketDialog by remember { mutableStateOf(false) }
    var showExtraDialogFor by remember { mutableStateOf<ExtraType?>(null) }
    var showPenaltyDialog by remember { mutableStateOf(false) }
    var showEditBatsmenDialog by remember { mutableStateOf(false) }
    var showEditBowlerDialog by remember { mutableStateOf(false) }
    var showCompleteInningsDialog by remember { mutableStateOf(false) }

    if (state.isLoading || state.match == null || state.currentInnings == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val innings = state.currentInnings!!
    val allInnings = state.allInnings

    // ---- Auto Bowler Selection Dialog Prompt After Every Over ----
    LaunchedEffect(innings.completedOvers) {
        if (innings.completedOvers > 0 && innings.ballsThisOver == 0 && state.isCurrentInningsLive) {
            showEditBowlerDialog = true
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("${innings.battingTeam} vs ${innings.bowlingTeam}") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        // ---- Top Innings Tab Switcher ----
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

        // ---- Sub Tab Bar: Live Score | Scorecard | Overs ----
        TabRow(selectedTabIndex = state.selectedSubTab) {
            Tab(
                selected = state.selectedSubTab == 0,
                onClick = { viewModel.selectSubTab(0) },
                text = { Text("Live Score") }
            )
            Tab(
                selected = state.selectedSubTab == 1,
                onClick = { viewModel.selectSubTab(1) },
                text = { Text("Scorecard") }
            )
            Tab(
                selected = state.selectedSubTab == 2,
                onClick = { viewModel.selectSubTab(2) },
                text = { Text("Overs") }
            )
        }

        // ---- Main Tab Content ----
        when (state.selectedSubTab) {
            0 -> LiveScoreTabContent(
                viewModel = viewModel,
                state = state,
                onEditBatsmen = { showEditBatsmenDialog = true },
                onEditBowler = { showEditBowlerDialog = true },
                onWicketClick = { showWicketDialog = true },
                onExtraClick = { showExtraDialogFor = it },
                onPenaltyClick = { showPenaltyDialog = true },
                onCompleteInningsClick = { showCompleteInningsDialog = true }
            )
            1 -> ScorecardTabContent(state = state)
            2 -> OversTabContent(state = state)
        }
    }

    // ---- Dialogs ----
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

    if (showPenaltyDialog) {
        PenaltyRunsDialog(
            onDismiss = { showPenaltyDialog = false },
            onConfirm = { penaltyRuns ->
                viewModel.recordPenalty(penaltyRuns)
                showPenaltyDialog = false
            }
        )
    }

    if (showEditBatsmenDialog) {
        EditBatsmenDialog(
            currentStriker = innings.strikerName,
            currentNonStriker = innings.nonStrikerName,
            availablePlayers = state.availableIncomingBatsmen,
            onDismiss = { showEditBatsmenDialog = false },
            onConfirm = { sName, nsName ->
                viewModel.updateBatsmanNames(sName, nsName)
                showEditBatsmenDialog = false
            }
        )
    }

    if (showEditBowlerDialog) {
        EditBowlerDialog(
            currentBowler = innings.currentBowlerName,
            existingBowlers = state.existingBowlers,
            onDismiss = { showEditBowlerDialog = false },
            onConfirm = { bName ->
                viewModel.updateBowlerName(bName)
                showEditBowlerDialog = false
            }
        )
    }

    if (showWicketDialog) {
        WicketDialog(
            strikerName = innings.strikerName,
            nonStrikerName = innings.nonStrikerName,
            nextBatsmanDefault = "Batsman ${innings.nextBatsmanNumber}",
            availableIncomingBatsmen = state.availableIncomingBatsmen,
            onDismiss = { showWicketDialog = false },
            onConfirm = { wicketType, runsCompleted, newBatsmanName, dismissedEnd ->
                viewModel.recordWicket(wicketType, runsCompleted, newBatsmanName, dismissedEnd)
                showWicketDialog = false
            }
        )
    }

    if (showCompleteInningsDialog) {
        AlertDialog(
            onDismissRequest = { showCompleteInningsDialog = false },
            title = { Text("Complete Innings?") },
            text = {
                Text(
                    "End this innings now with the current score of ${innings.totalRuns}/${innings.wickets}? " +
                        "Use this when your side doesn't have a full XI on the day and everyone available is out, " +
                        "or you simply want to move on."
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.completeInningsManually()
                    showCompleteInningsDialog = false
                }) { Text("Complete Innings") }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteInningsDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LiveScoreTabContent(
    viewModel: ScoringViewModel,
    state: com.example.cricketscorer.viewmodel.ScoringUiState,
    onEditBatsmen: () -> Unit,
    onEditBowler: () -> Unit,
    onWicketClick: () -> Unit,
    onExtraClick: (ExtraType) -> Unit,
    onPenaltyClick: () -> Unit,
    onCompleteInningsClick: () -> Unit
) {
    val match = state.match!!
    val innings = state.currentInnings!!

    // Two-pane layout: fixed score header always visible at top, scrollable controls below.
    // This prevents the score from disappearing behind the keyboard or when scrolling to buttons.
    Column(modifier = Modifier.fillMaxSize()) {

        // ── Fixed top panel: score + batsmen + bowler + this-over chips ──────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Score row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${innings.totalRuns}/${innings.wickets}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text("Ovs: ${state.oversDisplay}/${match.totalOvers}", style = MaterialTheme.typography.bodyMedium)
                    Text("RR: %.2f".format(state.runRate), style = MaterialTheme.typography.bodySmall)
                    state.target?.let {
                        Text(
                            "Need ${state.runsNeeded} off ${state.ballsRemaining}b",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Batsmen + bowler compact row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("⚔ ${innings.strikerName}*", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("  ${innings.nonStrikerName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("🏏 ${innings.currentBowlerName}", fontSize = 13.sp)
                }
            }

            // This over chips in a single scrollable row
            if (state.currentOverBalls.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Over:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(state.currentOverBalls) { ball -> BallChip(ball) }
                    }
                }
            }

            // Extras compact
            Text(
                "Extras  W:${innings.wideRuns} NB:${innings.noBallRuns} B:${innings.byeRuns} LB:${innings.legByeRuns} P:${innings.penaltyRuns}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Divider()

        // ── Scrollable controls panel ─────────────────────────────────────────
        val resultMessage = state.matchCompleteMessage
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (resultMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🎉 Match Complete", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(resultMessage, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        OutlinedButton(onClick = { viewModel.undoLastBall() }) { Text("Undo Last Ball") }
                    }
                }
            } else if (!state.isCurrentInningsLive) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("Viewing completed innings — switch tab to score the live innings.")
                    }
                }
            } else {
                // Edit names row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onEditBatsmen, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)) {
                        Text("Edit Batsmen", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = onEditBowler, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)) {
                        Text("Change Bowler", fontSize = 12.sp)
                    }
                }

                // Run buttons
                Text("Runs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(0, 1, 2, 3, 4, 6).forEach { run ->
                        Button(onClick = { viewModel.recordRuns(run) }, modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp)) {
                            Text(run.toString())
                        }
                    }
                }

                // Extras
                Text("Extras", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("WD" to ExtraType.WIDE, "NB" to ExtraType.NO_BALL,
                           "BYE" to ExtraType.BYE, "LB" to ExtraType.LEG_BYE).forEach { (label, type) ->
                        OutlinedButton(onClick = { onExtraClick(type) }, modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)) {
                            Text(label, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                    OutlinedButton(onClick = onPenaltyClick, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)) {
                        Text("PEN", fontSize = 11.sp, maxLines = 1)
                    }
                }

                // Wicket + Undo
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onWicketClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)) {
                        Text("Wicket")
                    }
                    OutlinedButton(onClick = { viewModel.undoLastBall() }, modifier = Modifier.weight(1f)) {
                        Text("Undo")
                    }
                }

                OutlinedButton(onClick = onCompleteInningsClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Complete Innings")
                }

                Text(
                    "Players: ${match.playersPerTeam} per side  •  All out at ${match.playersPerTeam - 1} wickets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp)) // breathing room above system nav bar
            }
        }
    }
}

@Composable
private fun ScorecardTabContent(state: com.example.cricketscorer.viewmodel.ScoringUiState) {
    val innings = state.currentInnings ?: return
    val batsmanStats = state.batsmanStats
    val bowlerStats = state.bowlerStats

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Batting — ${innings.battingTeam}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Batsman", fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                    Text("R", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                    Text("B", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                    Text("4s", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                    Text("6s", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                    Text("SR", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
                Divider()
                batsmanStats.forEach { b ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(2f)) {
                            Text(b.name, fontWeight = FontWeight.SemiBold)
                            Text(b.dismissalInfo, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${b.runs}", modifier = Modifier.weight(0.7f))
                        Text("${b.ballsFaced}", modifier = Modifier.weight(0.7f))
                        Text("${b.fours}", modifier = Modifier.weight(0.7f))
                        Text("${b.sixes}", modifier = Modifier.weight(0.7f))
                        Text("%.1f".format(b.strikeRate), modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text("Bowling — ${innings.bowlingTeam}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Bowler", fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                    Text("O", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                    Text("M", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                    Text("R", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                    Text("W", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                    Text("Econ", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
                Divider()
                bowlerStats.forEach { bw ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(bw.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(2f))
                        Text(bw.oversBowled, modifier = Modifier.weight(0.7f))
                        Text("${bw.maidens}", modifier = Modifier.weight(0.7f))
                        Text("${bw.runsConceded}", modifier = Modifier.weight(0.7f))
                        Text("${bw.wickets}", modifier = Modifier.weight(0.7f))
                        Text("%.1f".format(bw.economy), modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun OversTabContent(state: com.example.cricketscorer.viewmodel.ScoringUiState) {
    val overSummaries = state.overSummaries

    if (overSummaries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No overs completed yet in this innings.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(overSummaries) { summary ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Over ${summary.overNumber}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Total: ${summary.cumulativeRuns}/${summary.cumulativeWickets}", fontWeight = FontWeight.Bold)
                    }
                    Text("Bowler: ${summary.bowlerName}  •  ${summary.runsInOver} runs, ${summary.wicketsInOver} wkts", color = MaterialTheme.colorScheme.secondary)
                    Divider()
                    summary.balls.forEach { ball ->
                        val outcomeLabel = when {
                            ball.isWicket -> "Wicket (${ball.wicketType.name})"
                            ball.extraType == ExtraType.WIDE -> "Wide (+${ball.runsScored + ball.extraRuns} runs)"
                            ball.extraType == ExtraType.NO_BALL -> "No Ball (+${ball.runsScored + ball.extraRuns} runs)"
                            ball.extraType == ExtraType.BYE -> "Bye (${ball.runsScored} runs)"
                            ball.extraType == ExtraType.LEG_BYE -> "Leg Bye (${ball.runsScored} runs)"
                            ball.extraType == ExtraType.PENALTY -> "Penalty (+${ball.extraRuns} runs)"
                            else -> "${ball.runsScored} run(s)"
                        }
                        val batsman = ball.strikerName.ifBlank { "Batsman ${ball.strikerBatsmanNumber}" }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Ball ${summary.overNumber - 1}.${ball.ballNumberInOver}: $batsman", fontSize = 13.sp)
                            Text(outcomeLabel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
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
                Text("Enter penalty runs to add to team score:")
                OutlinedTextField(
                    value = customRuns,
                    onValueChange = { if (it.all { c -> c.isDigit() }) customRuns = it },
                    label = { Text("Penalty Runs") },
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
    availablePlayers: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var strikerName by remember { mutableStateOf(currentStriker) }
    var nonStrikerName by remember { mutableStateOf(currentNonStriker) }
    // req #4: Striker -> Non-Striker -> highlight Save button, keyboard closes
    val strikerFocusRequester = remember { FocusRequester() }
    val nonStrikerFocusRequester = remember { FocusRequester() }
    val saveButtonFocusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Batsmen Names") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerPickerField(
                    label = "Striker Name",
                    value = strikerName,
                    onValueChange = { strikerName = it },
                    availablePlayerNames = availablePlayers.filter { it != nonStrikerName },
                    focusRequester = strikerFocusRequester,
                    nextFocusRequester = nonStrikerFocusRequester
                )
                PlayerPickerField(
                    label = "Non-Striker Name",
                    value = nonStrikerName,
                    onValueChange = { nonStrikerName = it },
                    availablePlayerNames = availablePlayers.filter { it != strikerName },
                    focusRequester = nonStrikerFocusRequester,
                    nextFocusRequester = saveButtonFocusRequester
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(strikerName, nonStrikerName) },
                modifier = Modifier.focusRequester(saveButtonFocusRequester)
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditBowlerDialog(
    currentBowler: String,
    existingBowlers: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var bowlerName by remember { mutableStateOf(currentBowler) }
    // req #4: selecting a bowler highlights Save and closes the keyboard
    val bowlerFocusRequester = remember { FocusRequester() }
    val saveButtonFocusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select or Enter Bowler") },
        text = {
            // Single dropdown picker — shows the full bowlers list with scroll,
            // plus allows free-text for a new name not in the list.
            PlayerPickerField(
                label = "Bowler Name",
                value = bowlerName,
                onValueChange = { bowlerName = it },
                availablePlayerNames = existingBowlers,
                focusRequester = bowlerFocusRequester,
                nextFocusRequester = saveButtonFocusRequester
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(bowlerName) },
                modifier = Modifier.focusRequester(saveButtonFocusRequester)
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun WicketDialog(
    strikerName: String,
    nonStrikerName: String,
    nextBatsmanDefault: String,
    availableIncomingBatsmen: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (WicketType, Int, String, DismissedEnd) -> Unit
) {
    var selectedType by remember { mutableStateOf<WicketType?>(null) }
    var runsCompleted by remember { mutableStateOf(0) }
    var newBatsmanName by remember { mutableStateOf(nextBatsmanDefault) }
    var dismissedEnd by remember { mutableStateOf(DismissedEnd.STRIKER) }
    // req #4: selecting the incoming batsman highlights Confirm and closes the keyboard
    val incomingBatsmanFocusRequester = remember { FocusRequester() }
    val confirmButtonFocusRequester = remember { FocusRequester() }

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

                    Spacer(Modifier.height(4.dp))
                    Text("Which batsman is out?", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        val outName = if (dismissedEnd == DismissedEnd.STRIKER) strikerName else nonStrikerName
                        Text(
                            "$outName ${if (dismissedEnd == DismissedEnd.STRIKER) "(Striker)" else "(Non-Striker)"}",
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            dismissedEnd = if (dismissedEnd == DismissedEnd.STRIKER) DismissedEnd.NON_STRIKER else DismissedEnd.STRIKER
                        }) {
                            Icon(Icons.Default.SwapVert, contentDescription = "Swap which batsman is out")
                        }
                    }
                } else {
                    // For every other dismissal type, it's always the batsman facing the ball.
                    LaunchedEffect(selectedType) { dismissedEnd = DismissedEnd.STRIKER }
                }
                Spacer(Modifier.height(4.dp))
                PlayerPickerField(
                    label = "Incoming Batsman Name",
                    value = newBatsmanName,
                    onValueChange = { newBatsmanName = it },
                    availablePlayerNames = availableIncomingBatsmen,
                    focusRequester = incomingBatsmanFocusRequester,
                    nextFocusRequester = confirmButtonFocusRequester
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedType?.let { onConfirm(it, runsCompleted, newBatsmanName, dismissedEnd) } },
                enabled = selectedType != null,
                modifier = Modifier.focusRequester(confirmButtonFocusRequester)
            ) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
