package com.example.cricketscorer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.cricketscorer.model.TossDecision
import com.example.cricketscorer.viewmodel.MatchSetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchSetupScreen(
    viewModel: MatchSetupViewModel,
    onMatchStarted: (matchId: Long, inningsId: Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("New Match Setup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = viewModel.teamAName,
            onValueChange = { viewModel.teamAName = it },
            label = { Text("Team A Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = viewModel.teamBName,
            onValueChange = { viewModel.teamBName = it },
            label = { Text("Team B Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = viewModel.totalOvers,
            onValueChange = { if (it.all { c -> c.isDigit() }) viewModel.totalOvers = it },
            label = { Text("Total Overs") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Text("Opening Batsmen Names", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = viewModel.strikerName,
                onValueChange = { viewModel.strikerName = it },
                label = { Text("Striker Name") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = viewModel.nonStrikerName,
                onValueChange = { viewModel.nonStrikerName = it },
                label = { Text("Non-Striker Name") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Text("Toss Winner", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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

        viewModel.errorMessage?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { viewModel.startMatch(onMatchStarted) },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
        ) {
            Text("Start Match")
        }
    }
}
