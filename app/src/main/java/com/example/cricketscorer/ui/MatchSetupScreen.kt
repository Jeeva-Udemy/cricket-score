package com.example.cricketscorer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.cricketscorer.model.TossDecision
import com.example.cricketscorer.viewmodel.MatchSetupViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MatchSetupScreen(
    viewModel: MatchSetupViewModel,
    onNavigateBack: () -> Unit,
    onManageSquads: () -> Unit,
    onMatchStarted: (matchId: Long, inningsId: Long) -> Unit
) {
    val squads by viewModel.squads.collectAsState()
    val squadAPlayers by viewModel.squadAPlayers.collectAsState()
    val squadBPlayers by viewModel.squadBPlayers.collectAsState()

    // req #4: Striker -> Non-Striker -> Opening Bowler -> Start Match button
    val strikerFocusRequester = remember { FocusRequester() }
    val nonStrikerFocusRequester = remember { FocusRequester() }
    val bowlerFocusRequester = remember { FocusRequester() }
    val startButtonFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("New Match Setup") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()          // req #3: keep the focused field clear of the keyboard
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(onClick = onManageSquads) {
                Text("Manage Saved Squads →")
            }

            // req: this match is being created for an active Room — reuses the room's code
            // (no need to share a new one) instead of a purely local/unshared match.
            viewModel.activeRoomCode?.let { code ->
                Text(
                    "Creating a match for Room $code",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text("Team A", style = MaterialTheme.typography.titleMedium)
            // req #4: squad dropdown and the manual-entry name field side by side instead of
            // stacked, so picking a saved squad and typing a name are equally quick options
            // right next to each other rather than one after the other.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SquadDropdown(
                    label = "Select Squad",
                    squads = squads,
                    selected = viewModel.selectedSquadA,
                    onSelect = { viewModel.selectSquadForTeamA(it) },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = viewModel.teamAName,
                    onValueChange = { viewModel.teamAName = capitalizeFirstLetter(it) },
                    label = { Text("Team A Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.weight(1f)
                )
            }

            Text("Team B", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SquadDropdown(
                    label = "Select Squad",
                    squads = squads,
                    selected = viewModel.selectedSquadB,
                    onSelect = { viewModel.selectSquadForTeamB(it) },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = viewModel.teamBName,
                    onValueChange = { viewModel.teamBName = capitalizeFirstLetter(it) },
                    label = { Text("Team B Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = viewModel.totalOvers,
                    onValueChange = { if (it.all { c -> c.isDigit() }) viewModel.totalOvers = it },
                    label = { Text("Total Overs") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = viewModel.playersPerTeam,
                    onValueChange = { if (it.all { c -> c.isDigit() }) viewModel.playersPerTeam = it },
                    label = { Text("Players per Team") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "Set this to however many players you're actually fielding — the innings will " +
                    "end automatically once that many wickets fall.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("Toss Winner", style = MaterialTheme.typography.titleMedium)
            // req: flexible across screen sizes. A plain Row here doesn't wrap — on a narrow
            // phone, two chips holding long (user-typed) team names could run past the right
            // edge of the screen instead of resizing, since this Row isn't scrollable either.
            // FlowRow wraps the second chip onto its own line instead when it doesn't fit.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val teamAValid = viewModel.teamAName.isNotBlank()
                val teamBValid = viewModel.teamBName.isNotBlank()

                FilterChip(
                    selected = teamAValid && viewModel.tossWinnerTeam == viewModel.teamAName,
                    onClick = { if (teamAValid) viewModel.tossWinnerTeam = viewModel.teamAName },
                    label = { Text(viewModel.teamAName.ifBlank { "Team A" }) }
                )
                FilterChip(
                    selected = teamBValid && viewModel.tossWinnerTeam == viewModel.teamBName,
                    onClick = { if (teamBValid) viewModel.tossWinnerTeam = viewModel.teamBName },
                    label = { Text(viewModel.teamBName.ifBlank { "Team B" }) }
                )
            }

            Text("Toss Decision", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = viewModel.tossDecision == TossDecision.BAT,
                    onClick = { viewModel.tossDecision = TossDecision.BAT },
                    label = { Text("Bat First") }
                )
                FilterChip(
                    selected = viewModel.tossDecision == TossDecision.BOWL,
                    onClick = { viewModel.tossDecision = TossDecision.BOWL },
                    label = { Text("Bowl First") }
                )
            }

            // req: "select who's going to update the score for the 1st innings while creating
            // the match itself" — only relevant when this match belongs to a Room (a purely
            // local match only ever has the one device scoring it).
            if (viewModel.activeRoomCode != null) {
                Text("Who's scoring the 1st innings?", style = MaterialTheme.typography.titleMedium)
                Text(
                    "This phone will be the one entering the score once the match starts — " +
                        "the other phone in the room stays view-only until the innings switches.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val teamAValid = viewModel.teamAName.isNotBlank()
                    val teamBValid = viewModel.teamBName.isNotBlank()

                    FilterChip(
                        selected = teamAValid && viewModel.scoringTeam == viewModel.teamAName,
                        onClick = { if (teamAValid) viewModel.scoringTeam = viewModel.teamAName },
                        label = { Text(viewModel.teamAName.ifBlank { "Team A" }) }
                    )
                    FilterChip(
                        selected = teamBValid && viewModel.scoringTeam == viewModel.teamBName,
                        onClick = { if (teamBValid) viewModel.scoringTeam = viewModel.teamBName },
                        label = { Text(viewModel.teamBName.ifBlank { "Team B" }) }
                    )
                }
            }

            // Whichever team bats first supplies the opener player-picker chips
            val battingFirstIsA = (viewModel.tossDecision == TossDecision.BAT) ==
                (viewModel.tossWinnerTeam == viewModel.teamAName)
            val openingPlayerNames = if (battingFirstIsA) {
                squadAPlayers.map { it.name }
            } else {
                squadBPlayers.map { it.name }
            }
            val bowlingPlayerNames = if (battingFirstIsA) {
                squadBPlayers.map { it.name }
            } else {
                squadAPlayers.map { it.name }
            }

            // req: striker, non-striker, and opening bowler are mandatory — no default
            // placeholder names are ever saved. The user must pick from the dropdown
            // (when a squad is linked) or type a name themselves.
            Text("Opening Batsmen (required)", style = MaterialTheme.typography.titleMedium)
            PlayerPickerField(
                label = "Striker Name *",
                value = viewModel.strikerName,
                onValueChange = { viewModel.strikerName = it },
                availablePlayerNames = openingPlayerNames.filter { it != viewModel.nonStrikerName },
                focusRequester = strikerFocusRequester,
                nextFocusRequester = nonStrikerFocusRequester
            )
            Spacer(Modifier.height(4.dp))
            PlayerPickerField(
                label = "Non-Striker Name *",
                value = viewModel.nonStrikerName,
                onValueChange = { viewModel.nonStrikerName = it },
                availablePlayerNames = openingPlayerNames.filter { it != viewModel.strikerName },
                focusRequester = nonStrikerFocusRequester,
                nextFocusRequester = bowlerFocusRequester
            )

            Text("Opening Bowler (required)", style = MaterialTheme.typography.titleMedium)
            PlayerPickerField(
                label = "Opening Bowler Name *",
                value = viewModel.openingBowlerName,
                onValueChange = { viewModel.openingBowlerName = it },
                availablePlayerNames = bowlingPlayerNames,
                focusRequester = bowlerFocusRequester,
                // Last field: selecting a bowler moves focus straight to (and highlights)
                // the Start Match button and dismisses the keyboard (req #4).
                nextFocusRequester = startButtonFocusRequester
            )

            viewModel.errorMessage?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(8.dp))

            val canStart = viewModel.strikerName.isNotBlank() &&
                viewModel.nonStrikerName.isNotBlank() &&
                !viewModel.strikerName.trim().equals(viewModel.nonStrikerName.trim(), ignoreCase = true) &&
                viewModel.openingBowlerName.isNotBlank() &&
                (viewModel.activeRoomCode == null || viewModel.scoringTeam != null)

            Button(
                onClick = { viewModel.startMatch(onMatchStarted) },
                enabled = canStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .focusRequester(startButtonFocusRequester)
            ) {
                Text("Start Match")
            }
        }
    }
}
